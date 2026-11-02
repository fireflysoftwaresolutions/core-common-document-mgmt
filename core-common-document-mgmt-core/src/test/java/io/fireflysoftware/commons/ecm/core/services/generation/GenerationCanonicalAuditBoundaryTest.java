package io.fireflysoftware.commons.ecm.core.services.generation;

import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Static migration contracts and genuine Kernel negatives; not a PostgreSQL execution test. */
class GenerationCanonicalAuditBoundaryTest {
    Path directory() {
        return Path.of(System.getProperty("core.signing.source"),
                "core-common-document-mgmt-models/src/main/resources/db/optional/document-generation");
    }
    String currentBridge() throws Exception {
        Path forward=directory().resolve("V8__Document_Generation_Canonical_Audit.sql");
        return Files.readString(Files.exists(forward)?forward:directory().resolve("V5__Document_Generation_Original_Audit.sql"));
    }
    @Test void loadedEventRequiresCanonicalHashBeforeNewOrReplayMetadataAcceptance() throws Exception {
        String sql=currentBridge();int read=sql.indexOf("SELECT * INTO STRICT e FROM firefly_audit.audit_event");
        int check=sql.indexOf("PERFORM firefly_audit_private.require_record_hash(e);",read);
        int metadata=sql.indexOf("IF ROW(e.service",read);
        assertTrue(read>=0&&check>read&&metadata>check,"Both new and replay paths must verify the loaded event before trusting metadata");
    }
    @Test void existingAppliedV5IsPreservedByAnOptionalPrivilegeNeutralForwardMigration() throws Exception {
        assertTrue(Files.isRegularFile(directory().resolve("V8__Document_Generation_Canonical_Audit.sql")),"A forward migration is required");
        byte[] old=Files.readAllBytes(directory().resolve("V5__Document_Generation_Original_Audit.sql"));
        assertEquals("2ea4408e735713971ccfc70553067a5278c9389ffa206bb7c0c20327218d7f70",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(old)));
        String sql=currentBridge();assertTrue(sql.contains("CREATE OR REPLACE FUNCTION firefly_audit.document_generation_require_audit("));
        assertTrue(sql.contains("SECURITY DEFINER SET search_path=pg_catalog"));
        assertFalse(sql.contains("GRANT "));assertFalse(sql.contains("CREATE ROLE"));assertFalse(sql.contains("ALTER ROLE"));
    }
    @Test void incompatibleExistingBridgeOrCanonicalDependenciesRefuseBeforeReplacement() throws Exception {
        String sql=currentBridge();int replace=sql.indexOf("CREATE OR REPLACE FUNCTION");
        for(String exact:new String[]{"firefly_audit_private.canonical_text(text,text)","firefly_audit_private.canonical_record_bytes(firefly_audit.audit_event)","firefly_audit_private.require_record_hash(firefly_audit.audit_event)","p.proowner<>guard_oid","p.provolatile<>'i'","p.prorettype<>wanted.result_type","a.grantee<>guard_oid","p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']","a.grantee NOT IN(guard_oid,generation_guard_oid)"}) {
            int check=sql.indexOf(exact);assertTrue(check>=0&&check<replace,exact);
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void genuineKernelRejectsHashOrRecordedTimeChangesOutsideV5MetadataChecks(boolean changeRecordedTime) {
        var stream=new AuditStream(new UUID(1,2),"core-common-document-mgmt","document-generation",new UUID(3,4));
        var actor=new AuditActor("synthetic-issuer","opaque-subject","synthetic-client","opaque-tenant",null,null);
        var event=new AuditEvent(new UUID(5,6),stream,actor,"DOCUMENT_LOCAL_STORAGE_RECORDED",AuditEvent.Outcome.COMPLETED,
                new UUID(7,8),new UUID(9,10),null,Instant.EPOCH,"1".repeat(64),"policy-v1");
        var checkpoint=AuditCheckpoint.genesis(stream);var original=AuditRecordHasher.record(checkpoint,event,Instant.EPOCH);
        String wrong=original.recordHash().equals("f".repeat(64))?"e".repeat(64):"f".repeat(64);
        var changed=new AuditRecord(event,original.sequence(),changeRecordedTime?original.recordedAt().plusNanos(1):original.recordedAt(),
                original.previousHash(),changeRecordedTime?original.recordHash():wrong);
        assertEquals(original.event(),changed.event());assertEquals(original.sequence(),changed.sequence());
        assertEquals(original.previousHash(),changed.previousHash());
        assertNotEquals(changed,AuditRecordHasher.record(checkpoint,changed.event(),changed.recordedAt()));
    }
}
