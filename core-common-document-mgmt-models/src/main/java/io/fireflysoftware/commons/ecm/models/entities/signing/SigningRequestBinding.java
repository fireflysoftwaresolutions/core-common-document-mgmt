/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.signing;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="request_binding",schema="document_signing")
public record SigningRequestBinding(
    @Id @Column("id") UUID id,
    @Column("envelope_id") UUID envelopeId,
    @Column("operation_id") UUID operationId,
    @Column("tenant_id") String tenantId,
    @Column("provider_id") UUID providerId,
    @Column("account_binding_id") UUID accountBindingId,
    @Column("account_binding_version") long accountBindingVersion,
    @Column("document_signature_id") UUID documentSignatureId,
    @Column("document_id") UUID documentId,
    @Column("document_version_id") UUID documentVersionId,
    @Column("legacy_request_id") UUID legacyRequestId,
    @Column("planned_slot") int plannedSlot,
    @Column("external_recipient_id") String externalRecipientId,
    @Column("created_at") Instant createdAt,
    @Version @Column("version") Long version){

 @Override public String toString(){return "SigningRequestBinding[redacted]";}
}
