/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

public interface DocumentCurrentSnapshotService {
    Mono<DocumentCurrentSnapshot> observe(DocumentSelection selection, R2dbcOperationBudget original);
}
