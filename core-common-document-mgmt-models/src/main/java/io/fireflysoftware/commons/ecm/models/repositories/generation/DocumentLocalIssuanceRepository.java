/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.generation;

import java.util.UUID;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Mono;
import io.fireflysoftware.commons.ecm.models.entities.generation.DocumentLocalIssuance;

/** Immutable rows have no application save/update/delete API. */
public interface DocumentLocalIssuanceRepository extends Repository<DocumentLocalIssuance,UUID> {
    Mono<DocumentLocalIssuance> findByResourceTenantIdAndId(UUID resourceTenantId,UUID id);
}
