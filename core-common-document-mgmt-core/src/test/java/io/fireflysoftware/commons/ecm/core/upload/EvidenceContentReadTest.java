/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import io.fireflysoftware.commons.ecm.interfaces.upload.EvidenceUploadReceipt;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EvidenceContentReadTest {
    @Test void actualFetchedBytesAreHashedInsteadOfEchoingMetadata(){
        byte[] actual={1,2,3};var receipt=receipt(UploadedEvidenceIntent.hash(actual),3);
        assertThat(new EvidenceContentRead(receipt,actual).content()).containsExactly(actual);
        assertThatThrownBy(()->new EvidenceContentRead(receipt,new byte[]{3,2,1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EvidenceContentRead(receipt(UploadedEvidenceIntent.hash(actual),4),actual)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void neitherInputNorReturnedBytesCanMutateVerifiedContent(){byte[] actual={1,2,3};var read=new EvidenceContentRead(receipt(UploadedEvidenceIntent.hash(actual),3),actual);actual[0]=9;read.content()[0]=8;assertThat(read.content()).containsExactly((byte)1,(byte)2,(byte)3);}
    EvidenceUploadReceipt receipt(String digest,long length){return new EvidenceUploadReceipt(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),digest,length,"a".repeat(64),"proof.pdf","application/pdf","IDENTITY",Instant.parse("2026-11-01T00:00:00Z"),true);}
}
