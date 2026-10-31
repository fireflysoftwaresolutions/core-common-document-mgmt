/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.signing;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="account_binding",schema="document_signing")
public record SigningAccountBinding(
    @Id @Column("id") UUID id,
    @Column("tenant_id") String tenantId,
    @Column("provider_id") UUID providerId,
    @Column("binding_version") long bindingVersion,
    @Column("provider_code") String providerCode,
    @Column("account_id") String accountId,
    @Column("endpoint") String endpoint,
    @Column("active") Boolean active,
    @Column("created_at") Instant createdAt,
    @Version @Column("version") Long version){

 @Override public String toString(){return "SigningAccountBinding[redacted]";}
}
