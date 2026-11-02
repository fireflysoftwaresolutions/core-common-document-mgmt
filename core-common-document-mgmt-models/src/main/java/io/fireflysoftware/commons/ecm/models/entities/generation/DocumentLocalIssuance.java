/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.generation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="issuance",schema="document_generation")
public record DocumentLocalIssuance(
    @Id @Column("id") java.util.UUID id,
    @Column("resource_tenant_id") java.util.UUID resourceTenantId,
    @Column("event_id") java.util.UUID eventId,
    @Column("request_digest") String requestDigest,
    @Column("state") String state) { }
