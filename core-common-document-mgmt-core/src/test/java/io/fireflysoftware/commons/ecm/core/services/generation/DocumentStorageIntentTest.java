package io.fireflysoftware.commons.ecm.core.services.generation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.fireflyframework.kernel.audit.*;
import io.fireflysoftware.commons.ecm.models.entities.generation.GeneratedDocument;

class DocumentStorageIntentTest extends DocumentStorageFixtures {
    @Test void renderedBytesAreBoundToStorageIntent(){
        var request=request(new byte[]{1,2});var changed=new DocumentGenerationStorage.Request(request.resourceTenantId(),request.documentId(),request.snapshot(),request.event(),new byte[]{1,3});
        assertNotEquals(DocumentStorageIntent.digest(request),DocumentStorageIntent.digest(changed));
    }
    @Test void completeOwnerSnapshotBytesAreBoundToIntent(){
        var request=request(new byte[]{1,2});assertTrue(DocumentStorageIntent.json(request).contains("sourceCanonicalJson"));assertTrue(DocumentStorageIntent.json(request).contains(request.snapshot().sourceCanonicalJson().replace("\"","\\\"")));
    }
    @Test void missingExplicitResourceScopeRefuses(){assertThrows(IllegalArgumentException.class,()->DocumentStorageIntent.digest(new DocumentGenerationStorage.Request(null,source,null,null,new byte[]{1})));}
    @Test void noCrossTenantAuditScope(){var r=request(new byte[]{1});assertThrows(IllegalArgumentException.class,()->DocumentStorageIntent.digest(new DocumentGenerationStorage.Request(UUID.randomUUID(),r.documentId(),r.snapshot(),r.event(),r.renderedContent())));}
    @Test void mappedContentHasNoMutableAlias(){byte[] input={1,2};var row=new GeneratedDocument(source,tenant,"text/html",input,"a".repeat(64));input[0]=9;assertEquals(1,row.content()[0]);row.content()[0]=8;assertEquals(1,row.content()[0]);}
    @Test void mappedMetadataDoesNotPrintRawSourceOrActorData(){
        String poison="PRIVATE_SOURCE_ACTOR_CREDENTIAL_SENTINEL";var row=new io.fireflysoftware.commons.ecm.models.entities.generation.GeneratedDocumentMetadata(source,tenant,poison,"a".repeat(64));
        assertFalse(row.toString().contains(poison));
    }
}
