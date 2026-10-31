/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.signing;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningRequestBinding;
import io.fireflysoftware.commons.ecm.models.repositories.BaseRepository;
import java.util.UUID;
import reactor.core.publisher.Mono;
public interface SigningRequestBindingRepository extends BaseRepository<SigningRequestBinding,UUID>{
    Mono<SigningRequestBinding> findByIdAndOperationIdAndEnvelopeIdAndTenantId(
            UUID id, UUID operationId, UUID envelopeId, String tenantId);
}
