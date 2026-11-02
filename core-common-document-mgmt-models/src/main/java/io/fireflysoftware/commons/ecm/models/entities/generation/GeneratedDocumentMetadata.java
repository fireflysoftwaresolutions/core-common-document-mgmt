/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.generation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="metadata",schema="document_generation")
public record GeneratedDocumentMetadata(
    @Id @Column("id") java.util.UUID id,
    @Column("resource_tenant_id") java.util.UUID resourceTenantId,
    @Column("intent_json") String intentJson,
    @Column("request_digest") String requestDigest) {
    @Override public String toString(){return "GeneratedDocumentMetadata";}
}
