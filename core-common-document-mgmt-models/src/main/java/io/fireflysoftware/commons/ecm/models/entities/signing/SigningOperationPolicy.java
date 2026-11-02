/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.signing;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Additive projection; the original SigningOperation record contract remains unchanged. */
@Table(name="operation",schema="document_signing")
public record SigningOperationPolicy(@Id @Column("id") UUID id,@Column("tenant_id") String tenantId,
        @Column("audit_resource_tenant_id") UUID resourceTenantId,@Column("evidence_protocol") Integer evidenceProtocol,
        @Column("reservation_trace_digest") String reservationTraceDigest,@Column("prepared_trace_digest") String preparedTraceDigest) {
    public SigningOperationPolicy {
        if(id==null||tenantId==null||tenantId.isBlank()||resourceTenantId==null||!Integer.valueOf(3).equals(evidenceProtocol)
                ||reservationTraceDigest==null||!reservationTraceDigest.matches("[0-9a-f]{64}")
                ||preparedTraceDigest!=null&&!preparedTraceDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact signing operation policy scope required");
    }
}
