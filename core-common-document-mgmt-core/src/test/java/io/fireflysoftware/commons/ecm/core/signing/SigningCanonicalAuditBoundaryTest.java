package io.fireflysoftware.commons.ecm.core.signing;

import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source boundary and genuine Kernel controls; no SQL engine or exploit is exercised. */
class SigningCanonicalAuditBoundaryTest {
    String sql() throws Exception {
        return Files.readString(Path.of(System.getProperty("core.signing.source"),
                "core-common-document-mgmt-models/src/main/resources/db/optional/document-signing/V7__Signing_Original_Audit.sql"));
    }
    @Test void everyLoadedSelectedEventMustBeCanonicalBeforeMetadataAcceptance() throws Exception {
        String s=sql();int read=s.indexOf("SELECT * INTO STRICT e FROM firefly_audit.audit_event");
        int verify=s.indexOf("PERFORM firefly_audit_private.require_record_hash(e);",read);
        int metadata=s.indexOf("IF ROW(e.service",read);
        assertTrue(read>=0&&verify>read&&metadata>verify,"Loaded current or reservation event needs canonical verification before its metadata is trusted");
    }
    @Test void currentPreparedEventMustBeCanonicalOnThePredecessorPath() throws Exception {
        String s=sql();int read=s.indexOf("SELECT * INTO STRICT active_event FROM firefly_audit.audit_event");
        int verify=s.indexOf("PERFORM firefly_audit_private.require_record_hash(active_event);",read);
        int metadata=s.indexOf("IF ROW(active_event.service",read);
        assertTrue(read>=0&&verify>read&&metadata>verify,"Loaded prepared event needs independent canonical verification");
    }
    @Test void olderOrIncompatibleCanonicalHelperDeploymentMustRefuseInstallation() throws Exception {
        String s=sql();
        for(String helper:new String[]{"firefly_audit_private.canonical_text(text,text)","firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)","firefly_audit_private.require_record_hash(firefly_audit.audit_event)"})assertTrue(s.contains(helper),helper);
        for(String check:new String[]{"p.proowner<>guard_oid","p.prosecdef","p.provolatile<>'i'","p.prokind<>'f'","p.prorettype<>wanted.result_type","p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']","a.grantee<>guard_oid"})assertTrue(s.contains(check),check);
        assertFalse(s.contains("GRANT EXECUTE ON FUNCTION firefly_audit_private"));
    }
    @Test void genuineKernelRejectsAGraphConsistentButNoncanonicalHashSubstitution() {
        var stream=new AuditStream(new UUID(11,12),"core-document-mgmt","signing-operation",new UUID(13,14));
        var actor=new AuditActor("synthetic-issuer","synthetic-subject","synthetic-client","synthetic-platform",null,null);
        var event=new AuditEvent(new UUID(15,16),stream,actor,"signing.prepare.reserve",AuditEvent.Outcome.COMPLETED,
                new UUID(17,18),new UUID(19,20),null,Instant.EPOCH,"1".repeat(64),"policy-v1");
        var previous=AuditCheckpoint.genesis(stream);var genuine=AuditRecordHasher.record(previous,event,Instant.EPOCH);
        String substituted=genuine.recordHash().equals("f".repeat(64))?"e".repeat(64):"f".repeat(64);
        var forged=new AuditRecord(event,genuine.sequence(),genuine.recordedAt(),genuine.previousHash(),substituted);
        assertEquals(genuine.event(),forged.event());assertEquals(genuine.sequence(),forged.sequence());
        assertEquals(genuine.previousHash(),forged.previousHash());assertEquals(genuine.recordedAt(),forged.recordedAt());
        assertNotEquals(forged,AuditRecordHasher.record(previous,forged.event(),forged.recordedAt()));
    }
}
