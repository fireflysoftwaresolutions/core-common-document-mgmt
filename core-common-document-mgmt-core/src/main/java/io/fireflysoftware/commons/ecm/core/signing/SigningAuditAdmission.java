/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.kernel.audit.AuditEvent;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Required owner authority; no default derives a resource UUID or policy revision from claims. */
public interface SigningAuditAdmission {
    record Permit(UUID resourceTenantId, String resourceTenant, UUID bindingId, long bindingVersion,
                  String bindingDigest, AuditActor actor, String policyRevision, UUID processId,
                  UUID correlationId, Instant notAfter) {
        public Permit {
            Objects.requireNonNull(resourceTenantId); Objects.requireNonNull(bindingId);
            Objects.requireNonNull(actor); Objects.requireNonNull(processId);
            Objects.requireNonNull(correlationId); Objects.requireNonNull(notAfter);
            SigningDraftIntent.text(resourceTenant, 100);
            if (bindingVersion < 1 || bindingDigest == null || !bindingDigest.matches("[0-9a-f]{64}")
                    || policyRevision == null || !policyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                    || resourceTenantId.equals(new UUID(0,0)) || bindingId.equals(new UUID(0,0))
                    || processId.equals(new UUID(0,0)) || correlationId.equals(new UUID(0,0))) {
                throw new IllegalArgumentException("Trusted signing audit admission required");
            }
        }
        @Override public String toString() { return "SigningAuditPermit[redacted]"; }
    }
    Mono<Permit> requireCurrent(SigningDraftIntent intent, SigningAccountScope account, SigningAuthority authority);
    /** Optional owner composition scope; actual append authority remains independently required. */
    default <T> Mono<T> withinPhase(Permit permit,Supplier<Mono<T>> work){return Mono.defer(work);}
    default <T> Mono<T> withinExpectedAppend(Permit permit,SigningAuditEvidence evidence,AuditEvent event,
            Supplier<Mono<T>> work){return Mono.defer(work);}

}
