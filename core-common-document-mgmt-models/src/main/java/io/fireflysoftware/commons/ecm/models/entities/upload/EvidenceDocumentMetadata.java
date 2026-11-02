/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.upload;

import io.fireflysoftware.commons.ecm.interfaces.enums.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;

/** Complete legacy-row projection; no optional author, retention or verification is invented. */
@Table("documents")
public record EvidenceDocumentMetadata(@Id UUID id,String name,String description,@Column("file_name") String fileName,
        @Column("file_extension") String fileExtension,@Column("mime_type") String mimeType,@Column("file_size") Long fileSize,
        @Column("document_type") DocumentType documentType,@Column("document_status") DocumentStatus documentStatus,
        @Column("storage_type") StorageType storageType,@Column("storage_path") String storagePath,@Column("security_level") SecurityLevel securityLevel,
        @Column("folder_id") UUID folderId,@Column("is_encrypted") Boolean encrypted,@Column("is_indexed") Boolean indexed,
        @Column("is_locked") Boolean locked,@Column("locked_by") String lockedBy,@Column("locked_until") Instant lockedUntil,
        @Column("expiration_date") Instant expirationDate,@Column("retention_date") Instant retentionDate,@Column("tenant_id") String tenantId,
        @Column("created_at") Instant createdAt,@Column("created_by") String createdBy,@Column("updated_at") Instant updatedAt,
        @Column("updated_by") String updatedBy,Long version,String checksum) {
    public static EvidenceDocumentMetadata expected(UUID id,String fileName,String mediaType,long length,String digest,String platformTenant,Instant createdAt){
        return new EvidenceDocumentMetadata(id,fileName,null,fileName,null,mediaType,length,DocumentType.DOCUMENT,DocumentStatus.UNDER_REVIEW,StorageType.DATABASE,path(id),SecurityLevel.RESTRICTED,
                null,false,false,false,null,null,null,null,platformTenant,createdAt,null,null,null,0L,digest);
    }
    public static String path(UUID id){return "document-evidence/"+id;}
    @Override public String toString(){return "EvidenceDocumentMetadata[redacted]";}
}
