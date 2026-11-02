/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.interfaces.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import io.fireflysoftware.commons.ecm.interfaces.upload.validation.BoundedEvidenceContent;
import java.util.UUID;

public record EvidenceUploadRequest(
        @NotNull UUID actionId,@NotNull UUID applicationId,@NotNull UUID partyId,
        @NotBlank @Size(max=255) String fileName,@NotBlank @Size(max=100) String mediaType,
        @NotBlank @Size(max=50) String documentTypeCode,
        @NotNull @BoundedEvidenceContent
        @Schema(type="string",format="byte",minLength=4,maxLength=2796204,description="Base64 of 1 through 2097152 decoded content bytes; the JSON envelope is bounded to 3145728 bytes") byte[] content) {
    public EvidenceUploadRequest {content=content==null?null:content.clone();}
    @Override public byte[] content(){return content==null?null:content.clone();}
    @Override public String toString(){return "EvidenceUploadRequest[redacted]";}
}
