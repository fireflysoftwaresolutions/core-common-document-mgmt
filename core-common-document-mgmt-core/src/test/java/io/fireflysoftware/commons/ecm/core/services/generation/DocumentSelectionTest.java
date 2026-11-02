package io.fireflysoftware.commons.ecm.core.services.generation;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentSelectionTest {
    private static final String DIGEST="a".repeat(64);
    private static DocumentSelection.Product product() {
        return new DocumentSelection.Product(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),DIGEST);
    }
    @Test void requiresActualTenant() {
        assertThrows(IllegalArgumentException.class,()->new DocumentSelection.Product(null,UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),DIGEST));
    }
    @Test void rejectsNonDigest() {
        assertThrows(IllegalArgumentException.class,()->new DocumentSelection.MasterReference(UUID.randomUUID(),"2026.A", "A".repeat(64)));
    }
    @Test void retainsOpaqueOwnerVersionWithoutNumericCoercion() {
        assertEquals("2026.A",new DocumentSelection.MasterReference(UUID.randomUUID(),"2026.A",DIGEST).versionText());
    }
    @Test void rejectsBlankOrOversizeOwnerVersion() {
        for(String value:new String[]{"", " ", "x".repeat(21)})
            assertThrows(IllegalArgumentException.class,()->new DocumentSelection.MasterReference(UUID.randomUUID(),value,DIGEST));
    }
    @Test void issuerRevisionIsPositiveNumericOwnerRevision() {
        assertThrows(IllegalArgumentException.class,()->new DocumentSelection.IssuerReference(UUID.randomUUID(),0,DIGEST));
    }
    @Test void requiresCanonicalExistingPurpose() {
        assertThrows(IllegalArgumentException.class,()->new DocumentSelection(product(),"DEPOSIT_ACCOUNT_ORIGINATION","CONTRACT","en-US",
                new DocumentSelection.MasterReference(UUID.randomUUID(),"A",DIGEST),
                new DocumentSelection.MasterReference(UUID.randomUUID(),"A",DIGEST),
                new DocumentSelection.IssuerReference(UUID.randomUUID(),1,DIGEST)));
    }
    @Test void sourceAndLayoutHaveSameOwnerVersion() {
        assertThrows(IllegalArgumentException.class,()->new DocumentSelection(product(),"DEPOSIT_ACCOUNT_ENROLLMENT","CONTRACT","en-US",
                new DocumentSelection.MasterReference(UUID.randomUUID(),"A",DIGEST),
                new DocumentSelection.MasterReference(UUID.randomUUID(),"B",DIGEST),
                new DocumentSelection.IssuerReference(UUID.randomUUID(),1,DIGEST)));
    }
    @Test void ownerVersionGrammarRejectsInventedOpaqueValues() {
        for(String version:new String[]{"a/b","é",".one","a b"})
            assertThrows(IllegalArgumentException.class,()->new DocumentSelection.MasterReference(UUID.randomUUID(),version,DIGEST));
    }
    @Test void nilIdentityIsNotAnOwnerRevision() {
        assertThrows(IllegalArgumentException.class,()->new DocumentSelection.MasterReference(new UUID(0,0),"A",DIGEST));
    }
    @Test void sourceAndLayoutCannotBeSameRevisionIdentity() {
        UUID same=UUID.randomUUID();assertThrows(IllegalArgumentException.class,()->new DocumentSelection(product(),"DEPOSIT_ACCOUNT_ENROLLMENT","CONTRACT","en-US",
                new DocumentSelection.MasterReference(same,"A",DIGEST),new DocumentSelection.MasterReference(same,"A",DIGEST),new DocumentSelection.IssuerReference(UUID.randomUUID(),1,DIGEST)));
    }
    @Test void ownerLocaleAndDocumentTypeBoundsArePreserved() {
        assertDoesNotThrow(()->new DocumentSelection(product(),"DEPOSIT_ACCOUNT_ENROLLMENT","A".repeat(100),"zh_Hans",
                new DocumentSelection.MasterReference(UUID.randomUUID(),"A",DIGEST),new DocumentSelection.MasterReference(UUID.randomUUID(),"A",DIGEST),new DocumentSelection.IssuerReference(UUID.randomUUID(),1,DIGEST)));
    }
}
