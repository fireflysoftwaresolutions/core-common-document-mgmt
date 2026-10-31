/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.time.Instant;
import org.fireflyframework.security.api.domain.PolicyEvaluation;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;

/** Actual current evaluation and metadata; not a reusable permission or native grant. */
public interface SigningPreparationPolicyPort {
    Mono<PolicyEvaluation> requireCurrent(SecurityPrincipal principal,SigningDraftIntent intent,
            SigningAccountScope account,SigningTenantBindingObservationPort.Observation binding,Instant originalUntil);
}
