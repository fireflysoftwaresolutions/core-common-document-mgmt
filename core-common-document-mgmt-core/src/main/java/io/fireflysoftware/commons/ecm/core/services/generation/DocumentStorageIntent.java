/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Versioned document-owner intent bytes; not another owner's canonicalization algorithm. */
public final class DocumentStorageIntent {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON=JsonMapper.builder().build();
    private DocumentStorageIntent() { }
    public static String json(DocumentGenerationStorage.Request request){
        if(request==null||request.resourceTenantId()==null||request.documentId()==null||request.snapshot()==null
                ||request.event()==null||request.renderedContent()==null||request.renderedContent().length==0
                ||request.renderedContent().length>2_097_152)throw invalid();
        var snapshot=request.snapshot();var event=request.event();var stream=event.stream();
        if(snapshot.selection()==null||!request.resourceTenantId().equals(snapshot.selection().product().tenantId())
                ||!request.resourceTenantId().equals(stream.resourceTenantId())||!request.documentId().equals(stream.resourceId())
                ||!"core-common-document-mgmt".equals(stream.service())||!"document-generation".equals(stream.resourceType())
                ||!"DOCUMENT_LOCAL_STORAGE_RECORDED".equals(event.action())
                ||event.outcome()!=org.fireflyframework.kernel.audit.AuditEvent.Outcome.COMPLETED
                ||snapshot.issuerEvidence()==null||snapshot.mastersProvenance()!=DocumentCurrentSnapshot.MastersProvenance.EFFECTIVE_ONLY
                ||snapshot.observedAt()==null||snapshot.effectiveUntil()==null||snapshot.issuerNativeUntil()==null
                ||snapshot.sourceCanonicalJson()==null||snapshot.schemaCanonicalJson()==null||snapshot.layoutCanonicalJson()==null
                ||snapshot.budget()==null||!Set.of("text/html","text/plain").contains(snapshot.mediaType()))throw invalid();
        var value=new TreeMap<String,Object>();
        value.put("protocol","DOCUMENT_LOCAL_STORAGE_V1");value.put("resourceTenantId",request.resourceTenantId());
        value.put("documentId",request.documentId());value.put("eventId",event.eventId());
        value.put("contentDigest",contentDigest(request.renderedContent()));value.put("contentLength",request.renderedContent().length);
        value.put("mediaType",snapshot.mediaType());value.put("selection",snapshot.selection());
        value.put("sourceCanonicalJson",snapshot.sourceCanonicalJson());value.put("schemaCanonicalJson",snapshot.schemaCanonicalJson());
        value.put("layoutCanonicalJson",snapshot.layoutCanonicalJson());value.put("observedAtEpochMillis",snapshot.observedAt().toEpochMilli());
        value.put("effectiveUntilEpochMillis",snapshot.effectiveUntil().toEpochMilli());value.put("mastersProvenance",snapshot.mastersProvenance());
        value.put("issuerNativeUntilEpochMillis",snapshot.issuerNativeUntil().toEpochMilli());value.put("issuerGeneration",snapshot.issuerEvidence().generation());
        value.put("issuerSourceGeneration",snapshot.issuerEvidence().sourceGeneration());value.put("issuerObservedAtEpochMillis",snapshot.issuerEvidence().observedAt().toEpochMilli());
        value.put("issuerEffectiveUntilEpochMillis",snapshot.issuerEvidence().effectiveUntil().toEpochMilli());
        value.put("actor",event.actor());value.put("policyRevision",event.policyRevision());value.put("processId",event.processId());
        value.put("correlationId",event.correlationId());value.put("causationId",event.causationId());
        value.put("occurredSeconds",event.occurredAt().getEpochSecond());value.put("occurredNanos",event.occurredAt().getNano());
        try {return JSON.writeValueAsString(value);}catch(java.io.IOException error){throw invalid();}
    }
    /** The event evidenceDigest is excluded to avoid a circular hash; the service requires exact equality. */
    public static String digest(DocumentGenerationStorage.Request request){return contentDigest(json(request).getBytes(StandardCharsets.UTF_8));}
    public static String contentDigest(byte[] value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(value)));}
        catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Exact document storage intent required");}
}
