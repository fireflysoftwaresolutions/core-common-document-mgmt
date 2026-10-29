/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import java.time.Instant;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;

/** Immutable owner bytes; CURRENT does not authorize document generation or storage. */
public record DocumentCurrentSnapshot(DocumentSelection selection, String content, String mediaType,
        String sourceCanonicalJson, String schemaCanonicalJson, String layoutCanonicalJson,
        IssuerCurrentEvidence issuerEvidence, Instant observedAt, Instant effectiveUntil,
        MastersProvenance mastersProvenance, Instant issuerNativeUntil, R2dbcOperationBudget budget) {
    /** Issuer definition bytes are not renderable without its public integrity contract. */
    public record IssuerCurrentEvidence(long generation,long sourceGeneration,Instant observedAt,Instant effectiveUntil) { }
    @Override public String toString(){return "DocumentCurrentSnapshot";}
    public enum MastersProvenance { EFFECTIVE_ONLY }
}
