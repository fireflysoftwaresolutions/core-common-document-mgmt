package io.fireflysoftware.commons.ecm.core.signing;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Source contract only; PostgreSQL execution remains a separate gate. */
class SigningSchemaSourceTest {
 private String source()throws Exception{
  var path=Path.of(System.getProperty("core.signing.source"),"core-common-document-mgmt-models/src/main/resources/db/optional/document-signing/V6__Signing_Preparation.sql");
  assertTrue(Files.isRegularFile(path),"Optional manual signing V6 source missing");return Files.readString(path).replaceAll("\\s+"," ").toLowerCase(java.util.Locale.ROOT);
 }
 @Test void additiveOptionalSchemaPreservesLegacyDataAndRequiresScopedReferences()throws Exception{
  String sql=source();assertEquals(4,sql.split("create table document_signing\\.",-1).length-1);
  for(String required:new String[]{"references public.document_versions(id, document_id, tenant_id)","references public.document_signatures(id, document_id, document_version_id, signature_provider_id, tenant_id)","references document_signing.account_binding(id, binding_version, tenant_id, provider_id, account_id, endpoint)","foreign key (envelope_id, tenant_id, provider_id, account_binding_id, account_binding_version, document_signature_id, document_id, document_version_id)"})assertTrue(sql.contains(required),required);
  assertTrue(sql.contains("security definer set search_path=pg_catalog"));
  assertTrue(sql.contains("separately delegated signing schema installer required"));
  assertTrue(sql.contains("revoke references(id,tenant_id) on public.documents,public.signature_providers from document_signing_guard"));
  for(String forbidden:new String[]{"drop ","truncate ","delete from","create extension","create role","grant select on firefly_audit","grant firefly_audit_guard"})assertFalse(sql.contains(forbidden),forbidden);
 }
 @Test void preparedBytesHaveDatabaseLengthAndHashConsistency()throws Exception{
  String sql=source();assertTrue(sql.contains("pg_catalog.sha256(content)"));assertTrue(sql.contains("pg_catalog.octet_length(content)"));assertTrue(sql.contains("content_length = expected_content_length"));
  assertTrue(sql.contains("pg_catalog.sha256(pg_catalog.convert_to(intent_json, 'utf8'))"));assertTrue(sql.contains("pg_catalog.sha256(pg_catalog.convert_to(authority_json, 'utf8'))"));
 }
 @Test void immutableIntentAndCapturedBytesCannotBeReplacedAndNoExternalSuccessIsInvented()throws Exception{
  String sql=source();assertTrue(sql.contains("before insert or update on document_signing.operation"));assertTrue(sql.contains("old.content is not null"));assertTrue(sql.contains("new.intent_json"));assertTrue(sql.contains("old.intent_json"));
  assertTrue(sql.contains("check (external_envelope_id is null)"));assertTrue(sql.contains("check (external_recipient_id is null)"));assertTrue(sql.contains("check (legacy_request_id is null)"));
  assertFalse(sql.contains("'sent'"));assertFalse(sql.contains("'signed'"));
 }
}
