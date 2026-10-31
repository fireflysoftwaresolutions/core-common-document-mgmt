/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.kernel.audit.*;

/** Only typed identifiers and digests enter audit evidence, never PDF bytes or signer fields. */
public record SigningAuditEvidence(UUID operationId, UUID envelopeId, SigningDraftIntent intent,
        SigningAccountScope account, String sourceFingerprint, String authorityDigest, String contentDigest,
        UUID eventId, AuditEvent predecessor) {
    public SigningAuditEvidence {
        Objects.requireNonNull(operationId); Objects.requireNonNull(envelopeId);
        Objects.requireNonNull(intent); Objects.requireNonNull(account); Objects.requireNonNull(eventId);
        if (!digest(sourceFingerprint) || !digest(authorityDigest) || contentDigest != null && !digest(contentDigest)
                || (contentDigest == null) != (predecessor == null)
                || operationId.equals(new UUID(0,0)) || envelopeId.equals(new UUID(0,0))
                || eventId.equals(new UUID(0,0))) throw invalid();
    }
    public String action() { return contentDigest == null ? "signing.prepare.reserve" : "signing.prepare.complete"; }
    public String digest(UUID resourceTenantId) {
        String value=String.join("\n", "CORE_SIGNING_EFFECT_V2", action(), resourceTenantId.toString(),
                operationId.toString(), envelopeId.toString(), account.bindingId().toString(),
                Long.toString(account.bindingVersion()), intent.signatureId().toString(), intent.documentId().toString(),
                intent.documentVersionId().toString(), intent.providerId().toString(),
                SigningDocumentSnapshot.sha256(intent.canonicalJson().getBytes(StandardCharsets.UTF_8)),
                sourceFingerprint, authorityDigest, contentDigest == null ? "NONE" : contentDigest);
        return SigningDocumentSnapshot.sha256(value.getBytes(StandardCharsets.UTF_8));
    }
    public AuditEvent event(SigningAuditAdmission.Permit permit, Instant now) {
        var stream=new AuditStream(permit.resourceTenantId(), "core-document-mgmt", "signing-operation", operationId);
        if (predecessor != null && (!predecessor.stream().equals(stream)
                || !predecessor.actor().equals(permit.actor()) || !predecessor.processId().equals(permit.processId())
                || !predecessor.correlationId().equals(permit.correlationId())
                || !predecessor.policyRevision().equals(permit.policyRevision())
                || !"signing.prepare.reserve".equals(predecessor.action())
                || predecessor.outcome()!=AuditEvent.Outcome.COMPLETED)) throw invalid();
        return new AuditEvent(eventId,stream,permit.actor(),action(),AuditEvent.Outcome.COMPLETED,
                permit.processId(),permit.correlationId(),predecessor == null ? null : predecessor.eventId(),
                now,digest(permit.resourceTenantId()),permit.policyRevision());
    }
    private static boolean digest(String value) { return value!=null && value.matches("[0-9a-f]{64}"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Exact signing phase evidence required"); }
    @Override @com.fasterxml.jackson.annotation.JsonValue public String toString() { return "SigningAuditEvidence[redacted]"; }
}
