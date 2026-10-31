package io.fireflysoftware.commons.ecm.core.signing;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SigningAuditSchemaSourceTest {
 String sql()throws Exception{var root=Path.of(System.getProperty("core.signing.source")).resolve("core-common-document-mgmt-models/src/main/resources/db/optional/document-signing");return Files.readString(root.resolve("V6__Signing_Preparation.sql"))+Files.readString(root.resolve("V7__Signing_Original_Audit.sql"));}
 @Test void bothPhasesReferenceCanonicalImmutableAuditRowsNotAParallelJournal()throws Exception{var s=sql();assertTrue(s.contains("reservation_audit_event_id UUID NOT NULL"));assertTrue(s.contains("prepared_audit_event_id UUID"));assertTrue(s.contains("REFERENCES firefly_audit.audit_event(resource_tenant_id, event_id)"));assertEquals(4,s.split("CREATE TABLE document_signing\\.",-1).length-1);}
 @Test void canonicalEventScopeActorAndSafePhaseDigestMustMatchOperation()throws Exception{var s=sql();for(String required:new String[]{"CORE_SIGNING_EFFECT_V2","signing.prepare.reserve","signing.prepare.complete","actor_subject","actor_platform_tenant_id","evidence_digest","audit_policy_revision","audit_process_id","audit_correlation_id","operation.authority_digest::text"})assertTrue(s.contains(required),required);}
 @Test void deferredEnvelopeGraphCannotCommitUnauditedOrMismatchedState()throws Exception{var s=sql();assertTrue(s.contains("CREATE CONSTRAINT TRIGGER signing_envelope_audit_graph"));assertTrue(s.contains("DEFERRABLE INITIALLY DEFERRED"));assertTrue(s.contains("operation.state IS DISTINCT FROM NEW.state"));assertFalse(s.contains("GRANT SELECT ON firefly_audit"));assertFalse(s.contains("GRANT firefly_audit_guard"));}
}
