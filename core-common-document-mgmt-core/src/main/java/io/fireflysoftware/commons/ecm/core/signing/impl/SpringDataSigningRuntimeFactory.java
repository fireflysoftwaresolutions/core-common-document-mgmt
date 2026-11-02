/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing.impl;

import io.fireflysoftware.commons.ecm.core.signing.*;
import io.r2dbc.spi.ConnectionFactory;
import java.net.URI;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.core.InfrastructureProxy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.connection.SingleConnectionFactory;
import org.springframework.r2dbc.connection.TransactionAwareConnectionFactoryProxy;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Constructs the genuine mapped store/appender/workflow over one explicitly supplied owned database graph. */
public final class SpringDataSigningRuntimeFactory implements SigningPreparationRuntimeFactory {
    private final BoundedR2dbcResources resources;
    private final DatabaseClient client;
    private final R2dbcEntityTemplate template;
    private final R2dbcTransactionManager manager;
    private final CleanupPolicy cleanup;
    private final Clock clock;
    private final LongSupplier nanos;
    private final TrustedSigningRuntime runtime;
    private final SecurityContextPort principals;
    public SpringDataSigningRuntimeFactory(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,
            R2dbcTransactionManager manager,CleanupPolicy cleanup,SecurityContextPort principals,PolicyEvaluationPort evaluations,
            ReactiveOAuth2AuthorizedClientManager machineClients,WebClient configClient,URI configEndpoint,
            String registrationId,String issuer,String platformTenant,Clock clock,LongSupplier nanos) {
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);
        independent(resources.factory().getTargetConnectionFactory());
        this.client=client;this.template=template;this.manager=manager;this.cleanup=Objects.requireNonNull(cleanup);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.principals=Objects.requireNonNull(principals);
        var observed=new GeneratedConfigSigningTenantBindingResolver(principals,machineClients,configClient,configEndpoint,registrationId,issuer,platformTenant,clock);
        runtime=new TrustedSigningRuntime(principals,observed,new CurrentSigningPreparationPolicy(principals,evaluations,clock),clock,UUID::randomUUID);
    }
    @Override public Mono<SigningDraftWorkflow> create(SigningAccountScope account,DocumentContentPort content,SignatureEnvelopePort envelopes) {
        return create(account,content,envelopes,false);
    }
    @Override public Mono<SigningDraftWorkflow> createPolicyCustody(SigningAccountScope account,DocumentContentPort content,SignatureEnvelopePort envelopes) {
        return create(account,content,envelopes,true);
    }
    private Mono<SigningDraftWorkflow> create(SigningAccountScope account,DocumentContentPort content,SignatureEnvelopePort envelopes,boolean custody) {
        return Mono.defer(()->{
            if(account==null||content==null||envelopes==null)return Mono.error(new SigningIntegrationUnavailableException());
            return Mono.defer(envelopes::accountBinding).switchIfEmpty(Mono.error(new SigningIntegrationUnavailableException())).map(snapshot->{
                account.requireSame(snapshot);
                var store=custody?SpringDataSigningPreparationStore.policyCustody(resources,client,template,manager,cleanup,runtime,runtime,clock,nanos)
                        :new SpringDataSigningPreparationStore(resources,client,template,manager,cleanup,runtime,runtime,clock,nanos);
                var authority=new SigningAuthorityAdapter(principals,runtime.decisions(account),runtime,clock);
                return runtime.scoped(new SigningDraftPreparation(authority,store,account,envelopes,content));
            });
        }).onErrorMap(error->new SigningIntegrationUnavailableException());
    }
    private static void independent(ConnectionFactory source) {
        if(unsafe(source))throw invalid();
        if(source instanceof InfrastructureProxy proxy){
            Object target=proxy.getWrappedObject();
            if(!(target instanceof ConnectionFactory)||target==source||unsafe(target)||target instanceof InfrastructureProxy)throw invalid();
        }
    }
    private static boolean unsafe(Object source){return source==null||source instanceof ScopedObject
            ||source instanceof SingleConnectionFactory||source instanceof TransactionAwareConnectionFactoryProxy;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Independent signing connection factory required");}
}
