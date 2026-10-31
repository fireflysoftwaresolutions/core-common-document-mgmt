/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditEvent;

/** Candidate V3 commitment; actual same-transaction mapped custody remains separately required. */
public record SigningAuditEvidenceV3(SigningAuditEvidence business,SigningPolicyTraceV1 trace) {
    public SigningAuditEvidenceV3 {
        Objects.requireNonNull(business);Objects.requireNonNull(trace);
        if((business.contentDigest()==null)!=(trace.phase()==SigningPolicyTraceV1.Phase.RESERVE))throw invalid();
    }
    public String digest(UUID resourceTenantId) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            text(out,"CORE_SIGNING_EFFECT");out.writeInt(3);text(out,business.digest(resourceTenantId));text(out,trace.digest());
            out.flush();return SigningDocumentSnapshot.sha256(bytes.toByteArray());
        } catch(IOException error) { throw new IllegalStateException("Canonical signing policy evidence unavailable"); }
    }
    public AuditEvent event(SigningAuditAdmission.Permit permit,Instant now) {
        if(!trace.admission().policyRevision().equals(permit.policyRevision()))throw invalid();
        var original=business.event(permit,now);
        return new AuditEvent(original.eventId(),original.stream(),original.actor(),original.action(),original.outcome(),
                original.processId(),original.correlationId(),original.causationId(),original.occurredAt(),
                digest(permit.resourceTenantId()),original.policyRevision());
    }
    private static void text(DataOutputStream out,String value)throws IOException {
        byte[] encoded=value.getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Exact signing V3 phase evidence required"); }
    @Override @com.fasterxml.jackson.annotation.JsonValue public String toString() { return "SigningAuditEvidenceV3[redacted]"; }
}
