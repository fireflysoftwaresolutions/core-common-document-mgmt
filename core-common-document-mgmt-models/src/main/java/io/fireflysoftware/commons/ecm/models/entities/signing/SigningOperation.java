/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.signing;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name="operation",schema="document_signing")
public record SigningOperation(
    @Id @Column("id") UUID id,
    @Column("envelope_id") UUID envelopeId,
    @Column("tenant_id") String tenantId,
    @Column("provider_id") UUID providerId,
    @Column("account_binding_id") UUID accountBindingId,
    @Column("account_binding_version") long accountBindingVersion,
    @Column("document_signature_id") UUID documentSignatureId,
    @Column("document_id") UUID documentId,
    @Column("document_version_id") UUID documentVersionId,
    @Column("state") String state,
    @Column("generation") long generation,
    @Column("source_fingerprint") String sourceFingerprint,
    @Column("intent_json") String intentJson,
    @Column("intent_digest") String intentDigest,
    @Column("authority_json") String authorityJson,
    @Column("authority_digest") String authorityDigest,
    @Column("authority_until") Instant authorityUntil,
    @Column("expected_content_length") Long expectedContentLength,
    @Column("media_type") String mediaType,
    @Column("content") byte[] content,
    @Column("content_digest") String contentDigest,
    @Column("content_length") Long contentLength,
    @Column("created_at") Instant createdAt,
    @Version @Column("version") Long version,
    @Column("audit_resource_tenant_id") UUID auditResourceTenantId,
    @Column("audit_process_id") UUID auditProcessId,
    @Column("audit_correlation_id") UUID auditCorrelationId,
    @Column("audit_policy_revision") String auditPolicyRevision,
    @Column("reservation_audit_event_id") UUID reservationAuditEventId,
    @Column("prepared_audit_event_id") UUID preparedAuditEventId){

 public SigningOperation(UUID id,UUID envelopeId,String tenantId,UUID providerId,UUID accountBindingId,long accountBindingVersion,
      UUID documentSignatureId,UUID documentId,UUID documentVersionId,String state,long generation,String sourceFingerprint,
      String intentJson,String intentDigest,String authorityJson,String authorityDigest,Instant authorityUntil,Long expectedContentLength,
      String mediaType,byte[] content,String contentDigest,Long contentLength,Instant createdAt,Long version){
  this(id,envelopeId,tenantId,providerId,accountBindingId,accountBindingVersion,documentSignatureId,documentId,documentVersionId,
      state,generation,sourceFingerprint,intentJson,intentDigest,authorityJson,authorityDigest,authorityUntil,expectedContentLength,
      mediaType,content,contentDigest,contentLength,createdAt,version,null,null,null,null,null,null);
 }
 public SigningOperation{
  boolean legacyProjection=auditResourceTenantId==null&&auditProcessId==null&&auditCorrelationId==null&&auditPolicyRevision==null&&reservationAuditEventId==null&&preparedAuditEventId==null;
  if(!legacyProjection&&(auditResourceTenantId==null||auditProcessId==null||auditCorrelationId==null||reservationAuditEventId==null||auditPolicyRevision==null
      ||!auditPolicyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")||("PREPARED".equals(state)!=(preparedAuditEventId!=null))))throw invalid();
  if(id==null||envelopeId==null||tenantId==null||providerId==null||accountBindingId==null||accountBindingVersion<1||documentSignatureId==null||documentId==null||documentVersionId==null
    ||authorityUntil==null||createdAt==null||!createdAt.isBefore(authorityUntil)||expectedContentLength==null||expectedContentLength<1||expectedContentLength>SigningDigest.MAX_BYTES||!"application/pdf".equals(mediaType)
    ||sourceFingerprint==null||!sourceFingerprint.matches("[0-9a-f]{64}")||intentJson==null||authorityJson==null
    ||!SigningDigest.sha256(intentJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(intentDigest)
    ||!SigningDigest.sha256(authorityJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(authorityDigest))throw invalid();
  if(content==null){if(!"PREPARING".equals(state)||generation!=0||contentDigest!=null||contentLength!=null)throw invalid();}
  else{
   content=content.clone();
   if(!"PREPARED".equals(state)||generation!=1||contentLength==null||contentLength!=content.length||!contentLength.equals(expectedContentLength)
      ||!SigningDigest.sha256(content).equals(contentDigest))throw invalid();
  }
 }
 @Override public byte[] content(){return content==null?null:content.clone();}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("Exact scoped signing operation consistency required");}

 @Override @com.fasterxml.jackson.annotation.JsonValue public String toString(){return "SigningOperation[redacted]";}
}
