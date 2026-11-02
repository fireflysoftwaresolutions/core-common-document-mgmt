/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import java.util.UUID;

/** Exact owner references are inputs to observation, never grants of issuance authority. */
public record DocumentSelection(Product product, String purpose, String documentType, String locale,
        MasterReference template, MasterReference layout, IssuerReference issuer) {
    public DocumentSelection {
        if (product == null || template == null || layout == null || issuer == null
                || !"DEPOSIT_ACCOUNT_ENROLLMENT".equals(purpose)
                || documentType == null || !documentType.matches("[A-Z][A-Z0-9_]{0,99}")
                || locale == null || !locale.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,49}")
                || template.revisionId().equals(layout.revisionId())
                || !template.versionText().equals(layout.versionText()))
            throw new IllegalArgumentException("Exact document selection required");
    }
    public record Product(UUID tenantId, UUID rootId, UUID productId, UUID revisionId,
            UUID publicationId, String definitionDigest) {
        public Product {
            if (tenantId == null || rootId == null || productId == null || revisionId == null
                    || publicationId == null) throw new IllegalArgumentException("Exact product scope required");
            id(tenantId);id(rootId);id(productId);id(revisionId);id(publicationId);
            digest(definitionDigest);
        }
    }
    public record MasterReference(UUID revisionId, String versionText, String digest) {
        public MasterReference {
            id(revisionId);
            if (versionText == null || !versionText.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,19}"))
                throw new IllegalArgumentException("Exact opaque owner version required");
            DocumentSelection.digest(digest);
        }
    }
    public record IssuerReference(UUID publicationId, long revision, String definitionDigest) {
        public IssuerReference {
            id(publicationId);
            if (revision < 1) throw new IllegalArgumentException("Exact issuer publication required");
            digest(definitionDigest);
        }
    }
    private static void id(UUID value) {
        if (value == null || value.equals(new UUID(0,0))) throw new IllegalArgumentException("Nonzero owner UUID required");
    }
    private static void digest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Lowercase SHA-256 required");
    }
}
