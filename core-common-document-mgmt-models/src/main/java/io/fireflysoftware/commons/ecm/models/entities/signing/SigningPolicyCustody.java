/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.signing;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Original two-response policy provenance; durable only after the paired owner transaction commits. */
@Table(name="policy_custody",schema="document_signing")
public record SigningPolicyCustody(
        @Id @Column("id") UUID id,
        @Column("tenant_id") String tenantId,
        @Column("resource_tenant_id") UUID resourceTenantId,
        @Column("operation_id") UUID operationId,
        @Column("event_id") UUID eventId,
        @Column("phase") String phase,
        @Column("protocol") int protocol,
        @Column("authorization_revision") String authorizationRevision,
        @Column("authorization_decision_id") String authorizationDecisionId,
        @Column("admission_revision") String admissionRevision,
        @Column("admission_decision_id") String admissionDecisionId,
        @Column("business_digest") String businessDigest,
        @Column("trace_digest") String traceDigest) {
    public SigningPolicyCustody {
        if(id==null||tenantId==null||tenantId.isBlank()||resourceTenantId==null||operationId==null||eventId==null
                ||protocol!=3||!("RESERVE".equals(phase)||"PREPARED".equals(phase))
                ||businessDigest==null||!businessDigest.matches("[0-9a-f]{64}"))throw invalid();
        if(!canonicalDigest(phase,authorizationRevision,authorizationDecisionId,admissionRevision,admissionDecisionId).equals(traceDigest))throw invalid();
    }
    private static String canonicalDigest(String phase,String ar,String aid,String pr,String pid) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            text(out,"CORE_SIGNING_POLICY_TRACE");out.writeInt(1);text(out,phase);
            evaluation(out,"RESERVE".equals(phase)?"INITIAL_AUTHORIZATION":"AFTER_STORAGE_REVALIDATION",ar,aid);
            evaluation(out,"RESERVE".equals(phase)?"RESERVE_ADMISSION":"PREPARED_ADMISSION",pr,pid);
            out.flush();return SigningDigest.sha256(bytes.toByteArray());
        }catch(IOException error){throw invalid();}
    }
    private static void evaluation(DataOutputStream out,String role,String revision,String decision)throws IOException {
        if(revision==null||!revision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")||decision==null||decision.isBlank()
                ||decision.length()>256||decision.codePoints().anyMatch(Character::isISOControl))throw invalid();
        text(out,role);text(out,"PERMIT");out.writeInt(0);text(out,revision);text(out,decision);
    }
    private static void text(DataOutputStream out,String value)throws IOException {
        try {
            var encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            out.writeInt(encoded.remaining());byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);out.write(bytes);
        }catch(CharacterCodingException error){throw invalid();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Exact original signing policy custody required");}
    @Override @com.fasterxml.jackson.annotation.JsonValue public String toString(){return "SigningPolicyCustody[redacted]";}
}
