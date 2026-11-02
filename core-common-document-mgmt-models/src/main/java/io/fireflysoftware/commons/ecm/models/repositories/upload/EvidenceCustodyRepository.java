/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.upload;

import io.fireflysoftware.commons.ecm.models.entities.upload.*;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;

@NoRepositoryBean
public interface EvidenceCustodyRepository extends ReactiveCrudRepository<EvidenceCustody,UUID> {
    Flux<EvidenceCustody> findAllByResourceTenantIdAndActionId(UUID resourceTenantId,UUID actionId);
    Flux<EvidenceCustody> findAllByResourceTenantIdAndActionIdAndApplicationIdAndPartyId(UUID resourceTenantId,UUID actionId,UUID applicationId,UUID partyId);
    @Query("SELECT TRUE AS locked FROM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('document-evidence-action:' || CAST(:tenant AS text) || ':' || CAST(:action AS text),0))")
    Mono<Boolean> lockAction(UUID tenant,UUID action);
}
