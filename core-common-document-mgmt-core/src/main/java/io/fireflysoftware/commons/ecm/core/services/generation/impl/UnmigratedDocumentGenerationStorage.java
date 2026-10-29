/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation.impl;

import io.fireflysoftware.commons.ecm.core.services.generation.DocumentGenerationStorage;
import org.fireflyframework.core.transaction.*;
import reactor.core.publisher.Mono;

/** Public V1–V3 have no exact-reference issuance aggregate or qualified native audit admission. */
public final class UnmigratedDocumentGenerationStorage implements DocumentGenerationStorage {
    @Override public Mono<StoredDocument> store(Request request,RootTransactionScope owningScope,R2dbcOperationBudget original) {
        return Mono.error(new IllegalStateException("Exact-reference storage schema and native current/audit admission are unavailable"));
    }
}
