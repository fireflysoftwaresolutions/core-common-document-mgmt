package io.fireflysoftware.commons.ecm.core.signing;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Static installer boundary controls only; no SQL engine or role is created. */
class SigningOwnerBridgeSourceTest {
 Path root(){return Path.of(System.getProperty("core.signing.source"),"core-common-document-mgmt-models/src/main/resources/db/optional/document-signing");}
 @Test void runtimeCannotDependOnRlsReadsAfterAuditCompletion()throws Exception{
  String v6=Files.readString(root().resolve("V6__Signing_Preparation.sql"));
  assertFalse(v6.contains("SELECT * INTO event FROM firefly_audit.audit_event"));
  assertTrue(v6.contains("firefly_audit.document_signing_require_audit("));
 }
 @Test void manualBridgeRequiresExactCompletedOwnerContextAndRestrictedDelegation()throws Exception{
  Path p=root().resolve("V7__Signing_Original_Audit.sql");assertTrue(Files.isRegularFile(p));String sql=Files.readString(p);
  for(String required:new String[]{"SET LOCAL ROLE firefly_audit_guard","SECURITY DEFINER SET search_path=pg_catalog","pg_catalog.pg_current_xact_id_if_assigned()","pg_catalog.pg_backend_pid()","t.db_session_user=session_user","firefly_audit_private.context_for","firefly_audit_private.require_graph","firefly_audit_private.completion","c.new_event IS DISTINCT FROM true","TO document_signing_guard","REVOKE REFERENCES(resource_tenant_id,event_id)"})assertTrue(sql.contains(required),required);
 }
 @Test void bridgeNeverAddsRuntimeAuditReaderOrGuardMembership()throws Exception{
  Path p=root().resolve("V7__Signing_Original_Audit.sql");assertTrue(Files.isRegularFile(p));String sql=Files.readString(p);
  assertFalse(sql.contains("GRANT SELECT ON firefly_audit"));assertFalse(sql.contains("GRANT firefly_audit_guard"));
  assertTrue(sql.contains("FROM PUBLIC,firefly_audit_writer,firefly_audit_reader"));
  assertTrue(sql.contains("Exact separately delegated signing and audit installer required"));
 }

 @Test void accountRowLockPermissionCannotBecomeRuntimeAccountMutation()throws Exception{
  String v6=Files.readString(root().resolve("V6__Signing_Preparation.sql"));String v7=Files.readString(root().resolve("V7__Signing_Original_Audit.sql"));
  assertTrue(v7.contains("GRANT UPDATE(version) ON document_signing.account_binding TO firefly_audit_writer"));
  assertTrue(v6.contains("IF session_user='firefly_audit_writer' THEN"));assertTrue(v6.contains("Runtime signing account mutation is unavailable"));
  assertTrue(v7.contains("Separately governed metadata read and row-lock privileges required"));
 }
}
