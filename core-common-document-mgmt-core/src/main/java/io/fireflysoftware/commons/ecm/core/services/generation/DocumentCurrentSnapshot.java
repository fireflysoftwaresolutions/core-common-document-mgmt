/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import java.time.Instant;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;

/** Immutable owner bytes; CURRENT does not authorize document generation or storage. */
public record DocumentCurrentSnapshot(DocumentSelection selection, String content, String mediaType,
        String sourceCanonicalJson, String schemaCanonicalJson, String layoutCanonicalJson,
        IssuerCurrentEvidence issuerEvidence, Instant observedAt, Instant effectiveUntil,
        MastersProvenance mastersProvenance, Instant issuerNativeUntil, R2dbcOperationBudget budget,
        IssuerPresentation issuerPresentation, java.util.List<Notice> notices) {
    public DocumentCurrentSnapshot { notices=notices==null?java.util.List.of():java.util.List.copyOf(notices); }
    public DocumentCurrentSnapshot(DocumentSelection selection,String content,String mediaType,String sourceCanonicalJson,
            String schemaCanonicalJson,String layoutCanonicalJson,IssuerCurrentEvidence issuerEvidence,Instant observedAt,
            Instant effectiveUntil,MastersProvenance mastersProvenance,Instant issuerNativeUntil,R2dbcOperationBudget budget) {
        this(selection,content,mediaType,sourceCanonicalJson,schemaCanonicalJson,layoutCanonicalJson,issuerEvidence,
                observedAt,effectiveUntil,mastersProvenance,issuerNativeUntil,budget,null,java.util.List.of());
    }
    /** Finite native owner evidence; presentation separately records its owner-attested provenance. */
    public record IssuerCurrentEvidence(long generation,long sourceGeneration,Instant observedAt,Instant effectiveUntil) { }
    public enum IssuerProvenance { OWNER_ATTESTED }
    public record IssuerPresentation(String displayName,String contactEmail,String tenantName,String primaryColor,
            String footerText,String logoReference,String brandingId,long brandingVersion,IssuerProvenance provenance) {
        @Override public String toString(){return "IssuerPresentation[OWNER_ATTESTED]";}
    }
    public record Notice(String role,String placement,DocumentSelection.MasterReference reference,
            java.util.UUID templateId,String templateCode,String locale,String mediaType,String content,
            String sourceCanonicalJson,String schemaCanonicalJson,String styleContent,String contentDigest,
            String schemaDigest,String styleDigest) {
        @Override public String toString(){return "DocumentNoticeSnapshot";}
    }
    @Override public String toString(){return "DocumentCurrentSnapshot";}
    public enum MastersProvenance { EFFECTIVE_ONLY }
}
