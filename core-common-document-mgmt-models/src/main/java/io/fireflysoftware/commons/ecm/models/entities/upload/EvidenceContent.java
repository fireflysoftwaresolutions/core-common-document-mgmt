/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.upload;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;

@Table(value="content",schema="document_evidence")
public record EvidenceContent(@Id @Column("document_id") UUID documentId,
        @Column("resource_tenant_id") UUID resourceTenantId,@Column("content") byte[] content,
        @Column("content_digest") String contentDigest,@Column("content_length") long contentLength) {
    public EvidenceContent {content=content==null?null:content.clone();}
    @Override public byte[] content(){return content==null?null:content.clone();}
    @Override public String toString(){return "EvidenceContent[redacted]";}
}
