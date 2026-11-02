/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.documents.render;

/** Deployment capability pins an exact owner profile; it never grants issuance or asset authority. */
public record GovernedRenderProfile(String code,String version,String mediaType,String manifestDigest,
        int maxContentBytes,int maxOutputBytes,int maxInstructions,boolean logoRequired,
        io.fireflysoftware.commons.ecm.core.services.generation.DocumentSelection.MasterReference reviewedSource,
        io.fireflysoftware.commons.ecm.core.services.generation.DocumentSelection.MasterReference reviewedLayout,
        ExecutionScope executionScope) {
    /** Element/output guards are not a hard CPU/heap sandbox. General templates require an isolated worker. */
    public enum ExecutionScope { REVIEWED_OWNER_SOURCE_AND_LAYOUT_INLINE }
    public GovernedRenderProfile {
        if(reviewedSource==null||reviewedLayout==null||executionScope!=ExecutionScope.REVIEWED_OWNER_SOURCE_AND_LAYOUT_INLINE||code==null||code.isBlank()||version==null||version.isBlank()||!"text/html".equals(mediaType)
                ||manifestDigest==null||!manifestDigest.matches("[0-9a-f]{64}")
                ||maxContentBytes<1||maxContentBytes>262144||maxOutputBytes<1||maxOutputBytes>2097152
                ||maxInstructions<1||maxInstructions>100000)throw new IllegalArgumentException("Explicit bounded render profile required");
    }
}
