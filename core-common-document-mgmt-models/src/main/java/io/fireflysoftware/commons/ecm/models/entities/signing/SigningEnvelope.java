/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.signing;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="envelope",schema="document_signing")
public record SigningEnvelope(
    @Id @Column("id") UUID id,
    @Column("tenant_id") String tenantId,
    @Column("provider_id") UUID providerId,
    @Column("account_binding_id") UUID accountBindingId,
    @Column("account_binding_version") long accountBindingVersion,
    @Column("account_id") String accountId,
    @Column("endpoint") String endpoint,
    @Column("document_signature_id") UUID documentSignatureId,
    @Column("document_id") UUID documentId,
    @Column("document_version_id") UUID documentVersionId,
    @Column("state") String state,
    @Column("external_envelope_id") String externalEnvelopeId,
    @Column("created_at") Instant createdAt,
    @Version @Column("version") Long version){

 @Override public String toString(){return "SigningEnvelope[redacted]";}
}
