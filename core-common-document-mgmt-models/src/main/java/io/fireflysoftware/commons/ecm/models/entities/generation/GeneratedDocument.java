/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.generation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="document",schema="document_generation")
public record GeneratedDocument(
    @Id @Column("id") java.util.UUID id,
    @Column("resource_tenant_id") java.util.UUID resourceTenantId,
    @Column("media_type") String mediaType,
    @Column("content") byte[] content,
    @Column("content_digest") String contentDigest) {
    public GeneratedDocument { content=content==null?null:content.clone(); }
    @Override public byte[] content(){return content==null?null:content.clone();}
    @Override public String toString(){return "GeneratedDocument";}
}
