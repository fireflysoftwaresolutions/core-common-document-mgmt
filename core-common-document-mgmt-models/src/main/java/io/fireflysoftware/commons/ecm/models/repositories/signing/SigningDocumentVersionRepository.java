/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.repositories.signing;
import io.fireflysoftware.commons.ecm.models.entities.DocumentVersion;
import org.springframework.data.repository.Repository;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.relational.core.sql.LockMode;
import reactor.core.publisher.Mono;
public interface SigningDocumentVersionRepository extends Repository<DocumentVersion,java.util.UUID>{
 @Lock(LockMode.PESSIMISTIC_WRITE)
 Mono<DocumentVersion> findByIdAndDocumentIdAndTenantId(java.util.UUID id,java.util.UUID documentId,String tenantId);
}
