/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.generation;

import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;
import io.fireflysoftware.commons.ecm.models.entities.generation.GeneratedDocument;

/** Fixed native admission/integrity only; ordinary persistence uses entity operations. */
public interface DocumentGenerationNativeRepository extends Repository<GeneratedDocument,UUID> {
    record Stamp(UUID contextNonce,Long dbNowMillis,Long untilMillis,Boolean newIssuance) { }
    @Query("SELECT * FROM document_generation.enter(:grantId,:nonce,:tenant,:document,:event,:digest)")
    Mono<Stamp> enter(@Param("grantId")UUID grantId,@Param("nonce")UUID nonce,@Param("tenant")UUID tenant,
            @Param("document")UUID document,@Param("event")UUID event,@Param("digest")String digest);
    @Query("SELECT document_generation.complete(:nonce)")
    Mono<UUID> complete(@Param("nonce")UUID nonce);
}
