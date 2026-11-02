/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.util.UUID;

/** Captured bytes, never a mutable storage reference or a promise of historical immutability. */
public final class SigningDocumentSnapshot {
    public static final int MAX_BYTES = io.fireflysoftware.commons.ecm.models.entities.signing.SigningDigest.MAX_BYTES;
    private final UUID documentId;
    private final UUID documentVersionId;
    private final String fileName;
    private final String mediaType;
    private final byte[] content;
    private final String sha256;
    public SigningDocumentSnapshot(UUID documentId, UUID documentVersionId, String fileName, String mediaType, byte[] content) {
        if (documentId == null || documentVersionId == null || !"application/pdf".equals(mediaType)
                || content == null || content.length < 5 || content.length > MAX_BYTES) throw invalid();
        this.documentId = documentId; this.documentVersionId = documentVersionId;
        this.fileName = SigningDraftIntent.text(fileName, 255); this.mediaType = mediaType;
        this.content = content.clone();
        if (this.content[0] != '%' || this.content[1] != 'P' || this.content[2] != 'D' || this.content[3] != 'F' || this.content[4] != '-') throw invalid();
        sha256 = sha256(this.content);
    }
    public UUID documentId() { return documentId; }
    public UUID documentVersionId() { return documentVersionId; }
    public String fileName() { return fileName; }
    public String mediaType() { return mediaType; }
    public int contentLength() { return content.length; }
    public byte[] content() { return content.clone(); }
    public String sha256() { return sha256; }
    public static String sha256(byte[] bytes) {
        return io.fireflysoftware.commons.ecm.models.entities.signing.SigningDigest.sha256(bytes);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Exact bounded PDF snapshot required"); }
    @Override @com.fasterxml.jackson.annotation.JsonValue public String toString() { return "SigningDocumentSnapshot[redacted]"; }
}
