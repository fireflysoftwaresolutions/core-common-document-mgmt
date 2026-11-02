package io.fireflysoftware.commons.ecm.core.signing;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningOperation;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningEnvelope;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningRequestBinding;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningAccountBinding;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningDigest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import static org.junit.jupiter.api.Assertions.*;
class SigningStoreMappingTest {
 private SigningOperation operation(byte[] bytes,String digest){
  return new SigningOperation(SigningDraftIntentTest.id(20),SigningDraftIntentTest.id(21),"resource",SigningDraftIntentTest.id(4),SigningDraftIntentTest.id(10),7,
   SigningDraftIntentTest.id(1),SigningDraftIntentTest.id(2),SigningDraftIntentTest.id(3),"PREPARED",1,"a".repeat(64),"{}",SigningDigest.sha256("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"{}",SigningDigest.sha256("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
   Instant.parse("2026-10-31T12:00:05Z"),(long)bytes.length,"application/pdf",bytes,digest,(long)bytes.length,Instant.parse("2026-10-31T12:00:00Z"),0L);
 }
 @Test void mappedOperationOwnsItsContentAndNeverRendersSensitiveFields(){
  byte[] bytes="%PDF-controlled".getBytes(java.nio.charset.StandardCharsets.UTF_8);var operation=operation(bytes,SigningDigest.sha256(bytes));bytes[0]=0;var copy=operation.content();copy[1]=0;
  assertEquals('%',operation.content()[0]);assertEquals('P',operation.content()[1]);assertFalse(operation.toString().contains("PDF"));assertFalse(operation.toString().contains("{}"));
 }
 @Test void inconsistentContentCannotBecomePrepared(){byte[] bytes="%PDF-controlled".getBytes(java.nio.charset.StandardCharsets.UTF_8);assertThrows(IllegalArgumentException.class,()->operation(bytes,"0".repeat(64)));}
 @Test void realSpringDataMapsAllFourRecordsIntoTheOptionalSchema(){
  var mapping=new org.springframework.data.r2dbc.mapping.R2dbcMappingContext();
  mapping.setSimpleTypeHolder(org.springframework.data.r2dbc.convert.R2dbcCustomConversions.of(org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE).getSimpleTypeHolder());
  for(var type:new Class<?>[]{SigningOperation.class,SigningEnvelope.class,SigningRequestBinding.class,SigningAccountBinding.class}){
   var table=mapping.getRequiredPersistentEntity(type).getQualifiedTableName().toSql(IdentifierProcessing.NONE);assertTrue(table.startsWith("document_signing."),table);
  }
  assertEquals("content",mapping.getRequiredPersistentEntity(SigningOperation.class).getRequiredPersistentProperty("content").getColumnName().getReference());
 }
 @Test void genericJsonSerializationCannotExposeStoredBytesOrIntent()throws Exception{
  byte[] bytes="%PDF-controlled".getBytes(java.nio.charset.StandardCharsets.UTF_8);var row=operation(bytes,SigningDigest.sha256(bytes));
  var json=com.fasterxml.jackson.databind.json.JsonMapper.builder().addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build().valueToTree(row);
  for(String field:new String[]{"content","intentJson","authorityJson"})assertFalse(json.has(field),field+" must not be accidentally serialized");
 }


 @Test void genuineR2dbcCodecRoundTripsEveryDeclaredAuditedOperationColumn()throws Exception{
  var fixture=new SpringDataSigningPreparationStoreTest();fixture.setup();fixture.begin();
  var capture=org.mockito.ArgumentCaptor.forClass(SigningOperation.class);org.mockito.Mockito.verify(fixture.operations).save(capture.capture());var expected=capture.getValue();
  var mapping=new org.springframework.data.r2dbc.mapping.R2dbcMappingContext();
  var conversions=org.springframework.data.r2dbc.convert.R2dbcCustomConversions.of(org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE);
  mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
  var converter=new org.springframework.data.r2dbc.convert.MappingR2dbcConverter(mapping,conversions);
  var outbound=new org.springframework.data.r2dbc.mapping.OutboundRow();converter.write(expected,outbound);
  var document=new org.springframework.data.relational.domain.RowDocument();for(var column:outbound.keySet())document.put(column.getReference(),outbound.get(column).getValue());
  String sql=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("core.signing.source"),"core-common-document-mgmt-models/src/main/resources/db/optional/document-signing/V6__Signing_Preparation.sql"));
  String body=sql.split("CREATE TABLE document_signing.operation \\(",2)[1].split("\\n\\);",2)[0];
  var columns=new java.util.TreeSet<String>();var matcher=java.util.regex.Pattern.compile("(?m)^    ([a-z_]+) (?:UUID|VARCHAR|CHAR|TEXT|BIGINT|TIMESTAMPTZ|BYTEA|BOOLEAN|INTEGER)\\b").matcher(body);while(matcher.find())columns.add(matcher.group(1));
  assertEquals(30,columns.size());assertEquals(columns,new java.util.TreeSet<>(document.keySet()));
  var actual=converter.read(SigningOperation.class,document);
  assertEquals(expected,actual);assertEquals(expected.reservationAuditEventId(),actual.reservationAuditEventId());assertEquals(expected.auditResourceTenantId(),actual.auditResourceTenantId());
 }
}
