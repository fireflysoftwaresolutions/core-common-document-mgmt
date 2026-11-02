/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.kernel.audit.AuditActor;
import reactor.core.publisher.Mono;

/** Trusted policy/native-grant bridge, never implemented by caller assertions or a default sandbox. */
public interface DocumentGenerationAdmission {
    record Permit(UUID grantId,UUID resourceTenantId,UUID documentId,UUID eventId,String requestDigest,
            AuditActor actor,String policyRevision,Instant until) {
        @Override public String toString(){return "DocumentGenerationAdmission.Permit";}
    }
    Mono<Permit> requireCurrent(DocumentGenerationStorage.Request request,String requestDigest,R2dbcOperationBudget original);
}
