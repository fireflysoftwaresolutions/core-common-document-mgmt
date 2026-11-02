/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.interfaces.upload;

import java.time.Instant;
import java.util.UUID;

/** STORED content is pending business verification and has not yet been associated by Loan Core. */
public record EvidenceUploadReceipt(UUID actionId,UUID documentId,UUID applicationId,UUID partyId,
        UUID resourceTenantId,String contentDigest,long contentLength,String intentDigest,
        String fileName,String mediaType,String documentTypeCode,Instant storedAt,boolean replayed) {
    @Override public String toString(){return "EvidenceUploadReceipt[redacted]";}
}
