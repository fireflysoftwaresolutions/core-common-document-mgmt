/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import io.fireflysoftware.commons.ecm.interfaces.enums.*;
import io.fireflysoftware.commons.ecm.models.config.DocumentEvidenceMapping;
import io.fireflysoftware.commons.ecm.models.entities.upload.EvidenceDocumentMetadata;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.DefaultReactiveDataAccessStrategy;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import static org.assertj.core.api.Assertions.*;

class EvidenceDocumentMappingTest {
    @Test void allCustodyMetadataEnumsRetainTheirPublishedPostgresTypes(){
        var strategy=new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE,DocumentEvidenceMapping.converters());
        var row=EvidenceDocumentMetadata.expected(UUID.randomUUID(),"proof.pdf","application/pdf",3,"a".repeat(64),"platform",Instant.parse("2026-11-01T00:00:00Z"));
        var outbound=strategy.getOutboundRow(row);
        var entity=strategy.getMappingContext().getRequiredPersistentEntity(EvidenceDocumentMetadata.class);
        var values=Map.of("documentType",DocumentType.DOCUMENT,"documentStatus",DocumentStatus.UNDER_REVIEW,"storageType",StorageType.DATABASE,"securityLevel",SecurityLevel.RESTRICTED);
        values.forEach((property,expected)->assertThat(outbound.get(entity.getRequiredPersistentProperty(property).getColumnName()).getValue()).isSameAs(expected));
        assertThat(strategy.getTableName(EvidenceDocumentMetadata.class).getReference()).isEqualTo("documents");
        assertThat(entity.getRequiredIdProperty().getName()).isEqualTo("id");
    }
    @Test void optionalLegacyCustodyFieldsRemainNull(){
        var row=EvidenceDocumentMetadata.expected(UUID.randomUUID(),"proof.pdf","application/pdf",3,"a".repeat(64),"platform",Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(Arrays.asList(row.description(),row.fileExtension(),row.folderId(),row.lockedBy(),row.lockedUntil(),row.expirationDate(),row.retentionDate(),row.createdBy(),row.updatedAt(),row.updatedBy())).containsOnlyNulls();
        assertThat(row.encrypted()).isFalse();assertThat(row.indexed()).isFalse();assertThat(row.locked()).isFalse();
        assertThat(row.version()).isZero();
    }
}
