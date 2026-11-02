/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.config;

import io.fireflysoftware.commons.ecm.interfaces.enums.*;
import io.fireflysoftware.commons.ecm.models.config.impl.*;
import io.r2dbc.postgresql.codec.EnumCodec;
import io.r2dbc.postgresql.extension.CodecRegistrar;
import java.util.List;

public final class DocumentEvidenceMapping {
    private DocumentEvidenceMapping() {}
    public static CodecRegistrar codecs(){return EnumCodec.builder().withEnum("document_type",DocumentType.class).withEnum("document_status",DocumentStatus.class).withEnum("storage_type",StorageType.class).withEnum("security_level",SecurityLevel.class).build();}
    public static List<Object> converters(){return List.of(new DocumentTypeWriteConverterImpl(),new DocumentStatusWriteConverterImpl(),new StorageTypeWriteConverterImpl(),new SecurityLevelWriteConverterImpl());}
}
