/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation.impl;

import java.util.Objects;
import java.util.Set;
import java.net.URI;
import java.time.Clock;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import java.util.function.Supplier;
import io.fireflysoftware.common.config.sdk.api.DocumentIssuerApi;
import io.fireflysoftware.common.config.sdk.model.*;
import io.fireflysoftware.common.reference.master.data.sdk.api.*;
import io.fireflysoftware.common.reference.master.data.sdk.model.*;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Supplied genuine SDK clients own transport, endpoint and token configuration. */
public final class SdkDocumentOwnerReader implements DocumentOwnerReader {
    private final DocumentOwnerTokenProvider tokens;
    private final URI mastersBase,configurationBase;
    private final String mastersIssuer,configurationIssuer;
    private final WebClient transport;
    private final Clock clock;
    public SdkDocumentOwnerReader(DocumentOwnerTokenProvider tokens,URI mastersBase,URI configurationBase,
            String mastersIssuer,String configurationIssuer,WebClient transport,Clock clock) {
        this.tokens=Objects.requireNonNull(tokens);this.mastersBase=base(mastersBase);this.configurationBase=base(configurationBase);
        this.mastersIssuer=issuer(mastersIssuer);this.configurationIssuer=issuer(configurationIssuer);
        this.transport=Objects.requireNonNull(transport);this.clock=Objects.requireNonNull(clock);
    }
    @Override public Mono<Observations> read(DocumentSelection selection,R2dbcOperationBudget original) {
        return TransactionSynchronizationManager.forCurrentTransaction().map(ignored->true)
                .onErrorResume(NoTransactionException.class,ignored->Mono.just(false)).flatMap(active->{
            if(active)return Mono.error(new IllegalStateException("Owner HTTP cannot run inside a SQL transaction"));
            return Mono.defer(()->{
            Objects.requireNonNull(selection);Objects.requireNonNull(original);original.remaining();
            var p=selection.product();
            var target=new DocumentReferenceCurrentTarget()
                    .product(new DocumentReferenceProductScope().tenantId(p.tenantId()).rootId(p.rootId())
                            .productId(p.productId()).revisionId(p.revisionId()).publicationId(p.publicationId())
                            .definitionDigest(p.definitionDigest()))
                    .purpose(DocumentReferenceCurrentTarget.PurposeEnum.fromValue(selection.purpose()))
                    .documentType(selection.documentType()).locale(selection.locale())
                    .template(exact(selection.template(),"DOCUMENT_TEMPLATE"))
                    .layout(exact(selection.layout(),"DOCUMENT_LAYOUT"));
            var issuerTarget=new DocumentIssuerPublicationReference().tenantId(p.tenantId().toString())
                    .publicationId(selection.issuer().publicationId().toString()).revision(selection.issuer().revision())
                    .definitionDigest(selection.issuer().definitionDigest())
                    .use(new DocumentIssuerUse().purpose(DocumentIssuerUse.PurposeEnum.fromValue(selection.purpose()))
                            .documentType(selection.documentType()).locale(selection.locale()));
            return bounded(original,()->tokens.authorize(DocumentOwnerTokenProvider.Owner.MASTERS,selection,original)).flatMap(m->{
                checked(m,mastersIssuer,selection);return bounded(original,()->tokens.authorize(DocumentOwnerTokenProvider.Owner.CONFIGURATION,selection,original)).flatMap(c->{
                    checked(c,configurationIssuer,selection);
                    var master=new io.fireflysoftware.common.reference.master.data.sdk.invoker.ApiClient(transport);
                    master.setBasePath(mastersBase.toString());
                    ((io.fireflysoftware.common.reference.master.data.sdk.invoker.auth.HttpBearerAuth)master.getAuthentication("bearerAuth")).setBearerToken(m.token());
                    var config=new io.fireflysoftware.common.config.sdk.invoker.ApiClient(transport);
                    config.setBasePath(configurationBase.toString());
                    ((io.fireflysoftware.common.config.sdk.invoker.auth.HttpBearerAuth)config.getAuthentication("documentIssuerBearer")).setBearerToken(c.token());
                    var current=new DocumentReferenceCurrentControllerApi(master);
                    var layouts=new DocumentLayoutPublicationsApi(master);
                    var revisions=new DocumentTemplateRevisionsApi(master);
                    var issuers=new DocumentIssuerApi(config);
                    return bounded(original,()->{checked(m,mastersIssuer,selection);return current.observeCurrentDocumentReferences(target);}).flatMap(observed->
                            bounded(original,()->{checked(m,mastersIssuer,selection);return layouts.getDocumentLayoutRevision(selection.layout().revisionId());}).flatMap(l->
                            bounded(original,()->{checked(m,mastersIssuer,selection);return revisions.getDocumentTemplateRevision(selection.template().revisionId(),null);}).flatMap(s->
                            bounded(original,()->{checked(c,configurationIssuer,selection);return issuers.observeCurrentDocumentIssuer(p.tenantId().toString(),issuerTarget);})
                                    .map(i->{checked(m,mastersIssuer,selection);checked(c,configurationIssuer,selection);return new Observations(observed,l,s,i,m.until().isBefore(c.until())?m.until():c.until());}))));
                });
            });
            });
        });
    }
    private void checked(DocumentOwnerTokenProvider.AuthorizedToken token,String issuer,DocumentSelection selection) {
        if(token==null||!issuer.equals(token.issuer())||!selection.product().tenantId().equals(token.resourceTenant())
                ||!token.until().isAfter(clock.instant()))throw new IllegalStateException("Owner delegation unavailable");
    }
    private static URI base(URI value) {
        if(value==null||!"https".equals(value.getScheme())||value.getHost()==null||value.getUserInfo()!=null
                ||value.getQuery()!=null||value.getFragment()!=null||!Set.of("","/").contains(value.getPath()))
            throw new IllegalArgumentException("Exact HTTPS owner origin required");
        return URI.create(value.toString().replaceAll("/$",""));
    }
    private static String issuer(String value) {
        if(value==null||value.isBlank())throw new IllegalArgumentException("Exact allowed token issuer required");
        return value;
    }
    private static <T> Mono<T> bounded(R2dbcOperationBudget original,Supplier<Mono<T>> request) {
        return Mono.defer(()->{
            var remaining=original.remaining();
            return request.get().switchIfEmpty(Mono.error(new IllegalStateException("Owner observation unavailable")))
                    .timeout(remaining).map(value->{original.remaining();return value;});
        });
    }
    private static DocumentReferenceExact exact(DocumentSelection.MasterReference ref,String kind) {
        return new DocumentReferenceExact().owner(DocumentReferenceExact.OwnerEnum.fromValue("core-common-reference-master-data"))
                .kind(DocumentReferenceExact.KindEnum.fromValue(kind)).id(ref.revisionId())
                .versionFormat(DocumentReferenceExact.VersionFormatEnum.OPAQUE).versionText(ref.versionText()).digest(ref.digest());
    }
}
