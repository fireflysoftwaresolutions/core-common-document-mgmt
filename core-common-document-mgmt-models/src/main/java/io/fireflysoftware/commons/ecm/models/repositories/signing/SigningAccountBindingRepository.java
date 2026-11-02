/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.signing;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningAccountBinding;
import io.fireflysoftware.commons.ecm.models.repositories.BaseRepository;
public interface SigningAccountBindingRepository extends BaseRepository<SigningAccountBinding,java.util.UUID>{
 @org.springframework.data.relational.repository.Lock(org.springframework.data.relational.core.sql.LockMode.PESSIMISTIC_WRITE)
 reactor.core.publisher.Mono<SigningAccountBinding> findByIdAndTenantIdAndBindingVersion(java.util.UUID id,String tenantId,long bindingVersion);
}
