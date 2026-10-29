/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.common.reference.master.data.sdk.model.*;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Checks owner bytes without reproducing either owner's canonicalization algorithm. */
public final class DocumentCurrentSnapshotServiceImpl implements DocumentCurrentSnapshotService {
    private static final Duration LIMIT=Duration.ofSeconds(5);
    private static final ObjectMapper JSON=JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final DocumentOwnerReader reader;
    private final Clock clock;
    private final LongSupplier nanos;
    public DocumentCurrentSnapshotServiceImpl(DocumentOwnerReader reader, Clock clock, LongSupplier nanos) {
        this.reader=Objects.requireNonNull(reader); this.clock=Objects.requireNonNull(clock);
        this.nanos=Objects.requireNonNull(nanos);
    }
    @Override public Mono<DocumentCurrentSnapshot> observe(DocumentSelection selection, R2dbcOperationBudget original) {
        return Mono.defer(()->{
            Objects.requireNonNull(selection); Objects.requireNonNull(original); original.remaining();
            long before=nanos.getAsLong(); Instant start=clock.instant();
            return reader.read(selection,original).switchIfEmpty(Mono.error(unavailable()))
                    .timeout(original.remaining().compareTo(LIMIT)<0?original.remaining():LIMIT).map(value->checked(selection,original,value,start,before));
        });
    }
    private DocumentCurrentSnapshot checked(DocumentSelection s,R2dbcOperationBudget original,
            DocumentOwnerReader.Observations v,Instant start,long before) {
        original.remaining();
        if(v==null||v.current()==null||v.layout()==null||v.source()==null||v.issuer()==null)throw unavailable();
        var current=v.current(); var layout=v.layout(); var source=v.source(); var issuer=v.issuer();
        target(s,current.getTarget());
        if(!Objects.equals(s.layout().revisionId(),layout.getLayoutRevisionId())
                ||!Objects.equals(s.template().revisionId(),layout.getSourceRevisionId())
                ||!Objects.equals(s.template().revisionId(),source.getRevisionId())
                ||!s.template().versionText().equals(source.getVersion())||!s.layout().versionText().equals(layout.getVersion())
                ||!s.locale().equals(source.getLocaleCode())||!s.locale().equals(layout.getLocaleCode())
                ||!"PUBLISHED".equals(source.getLifecycle())||!"PUBLISHED".equals(layout.getLifecycle())
                ||!"MASTERS_JSON_V1".equals(source.getCanonicalizationVersion())
                ||!"MASTERS_DOCUMENT_LAYOUT_V1".equals(layout.getCanonicalizationVersion())
                ||!s.template().digest().equals(source.getSourceDigest())
                ||!s.layout().digest().equals(layout.getDefinitionDigest())
                ||layout.getDefinition()==null||!layout.getDefinition().equals(current.getDefinition()))throw unavailable();
        String raw=bounded(source.getSourceSnapshotCanonicalJson(),1048576);
        String schema=bounded(source.getInputSchemaCanonicalJson(),262144);
        String content=bounded(source.getTemplateContent(),262144);
        String style=bounded(source.getStyleContent(),65536);
        String canonical=bounded(layout.getCanonicalJson(),32768);
        if(!sha(raw).equals(source.getSourceDigest())||!sha(schema).equals(source.getSchemaDigest())
                ||!sha(content).equals(source.getContentDigest())||!sha(style).equals(source.getStyleDigest())
                ||!sha("MASTERS_DOCUMENT_LAYOUT_V1\n"+canonical).equals(layout.getDefinitionDigest()))throw unavailable();
        try {
            JsonNode tree=JSON.readTree(raw);
            if(!tree.isObject()||!content.equals(tree.path("templateContent").asText())
                    ||!style.equals(tree.path("styleContent").asText())
                    ||!s.locale().equals(tree.path("localeCode").asText())
                    ||!s.template().versionText().equals(tree.path("version").asText())
                    ||!Objects.equals(source.getMediaType(),tree.path("mediaType").asText())
                    ||!JSON.readTree(schema).equals(tree.path("inputSchema"))
                    ||!JSON.readTree(canonical).equals(JSON.valueToTree(layout.getDefinition())))throw unavailable();
        } catch(java.io.IOException e) {throw unavailable();}
        if(!Set.of("text/html","text/plain").contains(source.getMediaType()))throw unavailable();
        var ref=layout.getDefinition().getSourceReference();
        if(ref==null||layout.getDefinition().getProfileMetadata()==null||layout.getDefinition().getSemanticDefinition()==null
                ||source.getTemplateId()==null||!source.getTemplateId().equals(ref.getTemplateId())
                ||!source.getTemplateId().equals(layout.getTemplateId())
                ||!Objects.equals(source.getTemplateCode(),ref.getTemplateCode())
                ||!Objects.equals(source.getTemplateCode(),layout.getTemplateCode())
                ||!source.getMediaType().equals(String.valueOf(ref.getMediaType()))
                ||!source.getCanonicalizationVersion().equals(String.valueOf(ref.getCanonicalizationVersion()))
                ||!source.getRevisionId().equals(ref.getRevisionId())||!source.getVersion().equals(ref.getVersion())
                ||!source.getLocaleCode().equals(ref.getLocaleCode())||!source.getSourceDigest().equals(ref.getSourceDigest())
                ||!source.getContentDigest().equals(ref.getContentDigest())||!source.getSchemaDigest().equals(ref.getSchemaDigest())
                ||!source.getStyleDigest().equals(ref.getStyleDigest()))throw unavailable();
        var issuerRef=issuer.getReference();var definition=issuer.getDefinition();
        if(issuerRef==null||definition==null||issuerRef.getUse()==null
                ||!s.product().tenantId().toString().equals(issuerRef.getTenantId())
                ||!s.issuer().publicationId().toString().equals(issuerRef.getPublicationId())
                ||!Objects.equals(s.issuer().revision(),issuerRef.getRevision())
                ||!s.issuer().definitionDigest().equals(issuerRef.getDefinitionDigest())
                ||!Objects.equals(issuerRef.getIssuerId(),definition.getIssuerId())
                ||!Objects.equals(issuerRef.getRevision(),definition.getRevision())
                ||!s.purpose().equals(String.valueOf(issuerRef.getUse().getPurpose()))
                ||!s.documentType().equals(issuerRef.getUse().getDocumentType())
                ||!s.locale().equals(issuerRef.getUse().getLocale())
                ||definition.getUses()==null||!definition.getUses().contains(issuerRef.getUse())
                ||issuer.getGeneration()==null||issuer.getGeneration()<1
                ||issuer.getSourceGeneration()==null||issuer.getSourceGeneration()<1)throw unavailable();
        Instant now=clock.instant();
        Instant mastersObserved=window(current.getObservedAtEpochMillis(),current.getEffectiveUntilEpochMillis(),now);
        Instant issuerObserved=window(issuer.getObservedAtEpochMillis(),issuer.getEffectiveUntilEpochMillis(),now);
        Instant nativeUntil=instant(issuer.getNativeUntilEpochMillis());
        if(!nativeUntil.isAfter(issuerObserved)||nativeUntil.isAfter(issuerObserved.plus(LIMIT))
                ||nativeUntil.isBefore(instant(issuer.getEffectiveUntilEpochMillis()))
                ||definition.getEffectiveFromEpochMillis()==null||instant(definition.getEffectiveFromEpochMillis()).isAfter(now)
                ||(definition.getEffectiveUntilEpochMillis()!=null&&!instant(definition.getEffectiveUntilEpochMillis()).isAfter(now)))throw unavailable();
        if(v.delegationUntil()==null||!v.delegationUntil().isAfter(now))throw unavailable();
        Instant cap=min(start.plus(LIMIT),v.delegationUntil(),instant(current.getEffectiveUntilEpochMillis()),
                instant(issuer.getEffectiveUntilEpochMillis()),now.plus(original.remaining()));
        if(definition.getEffectiveUntilEpochMillis()!=null)cap=min(cap,instant(definition.getEffectiveUntilEpochMillis()));
        var retained=original.intersect(R2dbcOperationBudget.observed(start,cap,before,LIMIT,clock,nanos));
        retained.remaining(); Instant delivered=min(cap,clock.instant().plus(retained.remaining()));
        return new DocumentCurrentSnapshot(s,content,source.getMediaType(),raw,schema,canonical,
                    new DocumentCurrentSnapshot.IssuerCurrentEvidence(issuer.getGeneration(),issuer.getSourceGeneration(),issuerObserved,
                            instant(issuer.getEffectiveUntilEpochMillis())),mastersObserved.isAfter(issuerObserved)?mastersObserved:issuerObserved,
                    delivered,DocumentCurrentSnapshot.MastersProvenance.EFFECTIVE_ONLY,nativeUntil,retained);
    }
    private Instant window(Long observed,Long until,Instant now) {
        Instant from=instant(observed),to=instant(until);
        if(from.isAfter(now)||!to.isAfter(now)||!to.isAfter(from)||to.isAfter(from.plus(LIMIT)))throw unavailable();
        return from;
    }
    private static void target(DocumentSelection s,DocumentReferenceCurrentTarget t) {
        if(t==null||t.getProduct()==null)throw unavailable();var p=t.getProduct();var e=s.product();
        if(!e.tenantId().equals(p.getTenantId())||!e.rootId().equals(p.getRootId())||!e.productId().equals(p.getProductId())
                ||!e.revisionId().equals(p.getRevisionId())||!e.publicationId().equals(p.getPublicationId())
                ||!e.definitionDigest().equals(p.getDefinitionDigest())||!s.purpose().equals(String.valueOf(t.getPurpose()))
                ||!s.documentType().equals(t.getDocumentType())||!s.locale().equals(t.getLocale()))throw unavailable();
        exact(s.template(),t.getTemplate(),"DOCUMENT_TEMPLATE"); exact(s.layout(),t.getLayout(),"DOCUMENT_LAYOUT");
    }
    private static void exact(DocumentSelection.MasterReference expected,DocumentReferenceExact actual,String kind) {
        if(actual==null||!"core-common-reference-master-data".equals(String.valueOf(actual.getOwner()))
                ||!kind.equals(String.valueOf(actual.getKind()))||!"OPAQUE".equals(String.valueOf(actual.getVersionFormat()))
                ||!expected.revisionId().equals(actual.getId())||!expected.versionText().equals(actual.getVersionText())
                ||!expected.digest().equals(actual.getDigest()))throw unavailable();
    }
    private static String bounded(String s,int limit) {if(s==null||s.getBytes(StandardCharsets.UTF_8).length>limit)throw unavailable();return s;}
    private static Instant instant(Long millis) {if(millis==null)throw unavailable();return Instant.ofEpochMilli(millis);}
    private static Instant min(Instant...values) {return Arrays.stream(values).min(Instant::compareTo).orElseThrow();}
    private static String sha(String text) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static IllegalStateException unavailable() {return new IllegalStateException("Exact current document owners unavailable");}
}
