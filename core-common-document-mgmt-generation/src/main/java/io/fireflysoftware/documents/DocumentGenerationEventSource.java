/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.documents;
import java.util.UUID;
import io.fireflysoftware.commons.ecm.core.services.generation.DocumentCurrentSnapshot;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.fireflyframework.kernel.audit.AuditEvent;
import reactor.core.publisher.Mono;
/**
 * Deployment-owned actual actor/policy/native issuance authority binds the exact rendered intent.
 * No default implementation exists. A returned event does not replace the mandatory current
 * native admission or same-root immutable audit append in DocumentGenerationCommitService.
 */
@FunctionalInterface public interface DocumentGenerationEventSource {
    Mono<AuditEvent> bind(UUID documentId,DocumentCurrentSnapshot snapshot,byte[] content,R2dbcOperationBudget original);
}
