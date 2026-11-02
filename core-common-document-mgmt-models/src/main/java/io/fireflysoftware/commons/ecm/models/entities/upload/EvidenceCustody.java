/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.upload;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;

@Table(value="custody",schema="document_evidence")
public record EvidenceCustody(@Id @Column("document_id") UUID documentId,
        @Column("resource_tenant_id") UUID resourceTenantId,@Column("action_id") UUID actionId,
        @Column("application_id") UUID applicationId,@Column("party_id") UUID partyId,
        @Column("file_name") String fileName,@Column("media_type") String mediaType,@Column("document_type_code") String documentTypeCode,
        @Column("content_digest") String contentDigest,@Column("content_length") long contentLength,
        @Column("intent_digest") String intentDigest,@Column("effect_digest") String effectDigest,
        @Column("actor_issuer") String actorIssuer,@Column("actor_subject") String actorSubject,
        @Column("actor_client_id") String actorClientId,@Column("actor_platform_tenant_id") String actorPlatformTenantId,
        @Column("binding_id") UUID bindingId,@Column("binding_version") long bindingVersion,@Column("binding_digest") String bindingDigest,
        @Column("audit_context_nonce") UUID auditContextNonce,@Column("audit_event_id") UUID auditEventId,
        @Column("audit_sequence") long auditSequence,@Column("audit_record_hash") String auditRecordHash,
        @Column("policy_revision") String policyRevision,@Column("created_at") Instant createdAt) {
    @Override public String toString(){return "EvidenceCustody[redacted]";}
}
