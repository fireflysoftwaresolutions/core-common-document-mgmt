/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import io.fireflysoftware.commons.ecm.interfaces.upload.EvidenceUploadReceipt;
import java.util.Objects;

/** Immutable actual bytes; construction independently checks the published stored digest and decoded length. */
public record EvidenceContentRead(EvidenceUploadReceipt receipt,byte[] content) {
    public EvidenceContentRead{
        Objects.requireNonNull(receipt);content=Objects.requireNonNull(content).clone();
        if(content.length<1||content.length>UploadedEvidenceIntent.MAX_CONTENT_BYTES||receipt.contentLength()!=content.length||!UploadedEvidenceIntent.hash(content).equals(receipt.contentDigest()))throw new IllegalArgumentException("Actual stored evidence content digest and length required");
    }
    @Override public byte[] content(){return content.clone();}
    @Override public String toString(){return "EvidenceContentRead[redacted]";}
}
