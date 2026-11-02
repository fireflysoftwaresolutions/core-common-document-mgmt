/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import reactor.core.publisher.Mono;
import java.util.function.Supplier;
import org.fireflyframework.kernel.audit.AuditEvent;

/** Requires the entered private phase; returned values grant no authority or persistence. */
public interface SigningPolicyTracePort {
    Mono<SigningPolicyTraceV1> currentTrace(SigningAuditAdmission.Permit permit);
    default Mono<Void> requirePolicyCustody() {
        return Mono.error(new SecurityException("Trusted V3 signing append capability unavailable"));
    }
    default <T> Mono<T> withinExpectedAppendV3(SigningAuditAdmission.Permit permit,SigningAuditEvidenceV3 evidence,
            AuditEvent event,Supplier<Mono<T>> work) {
        return Mono.error(new SecurityException("Trusted V3 signing append capability unavailable"));
    }
}
