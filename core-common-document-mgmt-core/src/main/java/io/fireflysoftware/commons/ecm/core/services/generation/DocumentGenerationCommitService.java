/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Owns one actual local SQL transaction; provider delivery is a separate durable lifecycle. */
public interface DocumentGenerationCommitService {
    enum ProviderState { PENDING_PROVIDER }
    record LocalCommit(DocumentGenerationStorage.StoredDocument local,java.util.UUID eventId,String requestDigest,ProviderState providerState) { }
    Mono<LocalCommit> store(DocumentGenerationStorage.Request request,R2dbcOperationBudget original);
}
