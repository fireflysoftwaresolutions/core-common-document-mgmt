package io.fireflysoftware.commons.ecm.core.services.generation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.UnmigratedDocumentGenerationStorage;

class UnmigratedDocumentGenerationStorageTest extends DocumentSnapshotFixtures {
    @Test void publicSchemaCannotSilentlyReturnStorageSuccessOrEmptySuccess(){
        var error=assertThrows(IllegalStateException.class,()->new UnmigratedDocumentGenerationStorage().store(null,null,budget()).block());
        assertEquals("Exact-reference storage schema and native current/audit admission are unavailable",error.getMessage());
    }
    @Test void renderedBytesCannotBeModifiedThroughContractAlias(){
        byte[] bytes={1,2,3};var request=new DocumentGenerationStorage.Request(tenant,source,null,null,bytes);
        bytes[0]=4;assertEquals(1,request.renderedContent()[0]);request.renderedContent()[0]=5;assertEquals(1,request.renderedContent()[0]);
    }
}
