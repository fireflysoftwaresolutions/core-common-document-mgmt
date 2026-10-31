/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.signing;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningOperation;
import io.fireflysoftware.commons.ecm.models.repositories.BaseRepository;
public interface SigningOperationRepository extends BaseRepository<SigningOperation,java.util.UUID>{
 reactor.core.publisher.Mono<SigningOperation> findByIdAndTenantIdAndAccountBindingIdAndAccountBindingVersion(java.util.UUID id,String tenantId,java.util.UUID accountBindingId,long accountBindingVersion);
}
