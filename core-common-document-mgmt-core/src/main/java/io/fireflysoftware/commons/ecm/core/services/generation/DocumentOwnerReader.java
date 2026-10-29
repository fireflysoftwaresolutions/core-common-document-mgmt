/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import io.fireflysoftware.common.config.sdk.model.DocumentIssuerObservation;
import io.fireflysoftware.common.reference.master.data.sdk.model.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** Actual owner reads; implementations cannot grant document issuance or storage authority. */
public interface DocumentOwnerReader {
    record Observations(DocumentReferenceCurrent current, DocumentLayoutPublicationDTO layout,
            DocumentTemplateRevisionDTO source, DocumentIssuerObservation issuer, java.time.Instant delegationUntil) { }
    Mono<Observations> read(DocumentSelection selection, R2dbcOperationBudget original);
}
