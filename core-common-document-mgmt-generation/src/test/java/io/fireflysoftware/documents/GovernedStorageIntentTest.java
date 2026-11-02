package io.fireflysoftware.documents;
import java.util.*;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import io.fireflysoftware.documents.render.GovernedFreeMarkerTest;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GovernedStorageIntentTest {
    private DocumentGenerationStorage.Request request(DocumentCurrentSnapshot s){
        var id=UUID.fromString("00000001-0000-4000-8000-000000000001");
        var e=new AuditEvent(id,new AuditStream(s.selection().product().tenantId(),"core-common-document-mgmt","document-generation",id),
                new AuditActor("https://unit.invalid","subject","client",s.selection().product().tenantId().toString(),null,null),"DOCUMENT_LOCAL_STORAGE_RECORDED",AuditEvent.Outcome.COMPLETED,id,id,null,GovernedFreeMarkerTest.NOW,"0".repeat(64),"UNIT_ONLY");
        return new DocumentGenerationStorage.Request(s.selection().product().tenantId(),id,s,e,new byte[]{1,2,3});
    }
    private DocumentCurrentSnapshot copy(DocumentCurrentSnapshot s,DocumentCurrentSnapshot.IssuerPresentation p,List<DocumentCurrentSnapshot.Notice> n){
        return new DocumentCurrentSnapshot(s.selection(),s.content(),s.mediaType(),s.sourceCanonicalJson(),s.schemaCanonicalJson(),s.layoutCanonicalJson(),s.issuerEvidence(),s.observedAt(),s.effectiveUntil(),s.mastersProvenance(),s.issuerNativeUntil(),s.budget(),p,n);
    }
    @Test void presentationAndEveryOrderedNoticeComponentBindWholeIntent(){
        var s=GovernedFreeMarkerTest.snapshot("Owner template");String before=DocumentStorageIntent.digest(request(s));
        var p=s.issuerPresentation();var n=s.notices().getFirst();
        var changedPresentation=new DocumentCurrentSnapshot.IssuerPresentation("Altered owner",p.contactEmail(),p.tenantName(),p.primaryColor(),p.footerText(),p.logoReference(),p.brandingId(),p.brandingVersion(),p.provenance());
        assertNotEquals(before,DocumentStorageIntent.digest(request(copy(s,changedPresentation,s.notices()))));
        for(String field:List.of("body","schema","reference","canonical")){
            var changed=new DocumentCurrentSnapshot.Notice(n.role(),n.placement(),field.equals("reference")?new DocumentSelection.MasterReference(UUID.randomUUID(),"A.1",GovernedFreeMarkerTest.DIGEST):n.reference(),
                    n.templateId(),n.templateCode(),n.locale(),n.mediaType(),field.equals("body")?"Altered body":n.content(),field.equals("canonical")?"{\"altered\":true}":n.sourceCanonicalJson(),field.equals("schema")?"{\"type\":\"string\"}":n.schemaCanonicalJson(),n.styleContent(),n.contentDigest(),n.schemaDigest(),n.styleDigest());
            assertNotEquals(before,DocumentStorageIntent.digest(request(copy(s,p,List.of(changed)))),field);
        }
        var second=new DocumentCurrentSnapshot.Notice("PRIVACY_NOTICE","BODY_NOTE",new DocumentSelection.MasterReference(UUID.randomUUID(),"A.1",GovernedFreeMarkerTest.DIGEST),n.templateId(),"PRIVACY",n.locale(),n.mediaType(),"Privacy",n.sourceCanonicalJson(),n.schemaCanonicalJson(),n.styleContent(),n.contentDigest(),n.schemaDigest(),n.styleDigest());
        assertNotEquals(DocumentStorageIntent.digest(request(copy(s,p,List.of(n,second)))),DocumentStorageIntent.digest(request(copy(s,p,List.of(second,n)))));
        assertTrue(DocumentStorageIntent.json(request(s)).contains("OWNER_ATTESTED"));
    }
    @Test void oldReferenceOnlySnapshotsRetainOldIntentShape(){
        var s=GovernedFreeMarkerTest.snapshot("Owner template");var old=new DocumentCurrentSnapshot(s.selection(),s.content(),s.mediaType(),s.sourceCanonicalJson(),s.schemaCanonicalJson(),s.layoutCanonicalJson(),s.issuerEvidence(),s.observedAt(),s.effectiveUntil(),s.mastersProvenance(),s.issuerNativeUntil(),s.budget());
        String intent=DocumentStorageIntent.json(request(old));assertFalse(intent.contains("issuerPresentation"));assertFalse(intent.contains("noticeSnapshots"));
    }
}
