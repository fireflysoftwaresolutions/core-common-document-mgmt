/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.signing;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningPolicyCustody;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Mono;

public interface SigningPolicyCustodyRepository extends Repository<SigningPolicyCustody,UUID> {
    Mono<SigningPolicyCustody> findByTenantIdAndResourceTenantIdAndOperationIdAndEventIdAndPhase(
            String tenantId,UUID resourceTenantId,UUID operationId,UUID eventId,String phase);
}
