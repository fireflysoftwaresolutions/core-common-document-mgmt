package io.fireflysoftware.commons.ecm.core.signing;

import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentSignatureDTO;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureType;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningDraftIntentTest {
 static UUID id(int suffix) { return new UUID(1,suffix); }
 static DocumentSignatureDTO valid() {
  return DocumentSignatureDTO.builder().id(id(1)).documentId(id(2)).documentVersionId(id(3)).signatureProviderId(id(4))
   .tenantId("opaque resource tenant").signerName("Controlled signer").signerEmail("signer@owned.invalid")
   .signatureType(SignatureType.BASIC).signatureFormat(SignatureFormat.PDF_VISIBLE)
   .signaturePositionX(10).signaturePositionY(20).signaturePage(1).signatureWidth(100).signatureHeight(40)
   .signerLanguage("en").signerRole("Signer").signingOrder(1).signatureRequired(true).authenticationMethod("EMAIL").build();
 }
 @Test void capturesExactIntentBeforeMutableDtoCanChange() {
  var dto=valid();var intent=SigningDraftIntent.from(dto);dto.setSignerEmail("other@owned.invalid");dto.setSignaturePositionX(999);
  assertEquals("signer@owned.invalid",intent.signerEmail());assertEquals(10,intent.x());assertEquals(id(3),intent.documentVersionId());
  assertFalse(intent.toString().contains("signer@"));assertFalse(intent.canonicalJson().contains("other@"));
 }
 @Test void doesNotInventVersionOrTenantIdentity() {
  var noVersion=valid();noVersion.setDocumentVersionId(null);assertThrows(IllegalArgumentException.class,()->SigningDraftIntent.from(noVersion));
  var dto=valid();dto.setTenantId(null);var missing=dto;assertThrows(IllegalArgumentException.class,()->SigningDraftIntent.from(missing));
 }
 @Test void rejectsUncontractedSettingsRatherThanDroppingThem() {
  var dto=valid();dto.setSignerTimeZone("UTC");assertThrows(IllegalArgumentException.class,()->SigningDraftIntent.from(dto));
 }
 @Test void visiblePlacementRequiresExactDimensionsAndPage() {
  var dto=valid();dto.setSignaturePage(0);assertThrows(IllegalArgumentException.class,()->SigningDraftIntent.from(dto));
 }
 @Test void snapshotCopiesExactlyTheBytesWhoseDigestItStores() {
  byte[] bytes="%PDF-1.7\ncontrolled".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var snap=new SigningDocumentSnapshot(id(2),id(3),"agreement.pdf","application/pdf",bytes);bytes[0]=0;
  byte[] returned=snap.content();returned[1]=0;
  assertEquals('%',snap.content()[0]);assertEquals('P',snap.content()[1]);
  assertEquals(snap.content().length,snap.contentLength());assertEquals(SigningDocumentSnapshot.sha256(snap.content()),snap.sha256());
  assertFalse(snap.toString().contains("controlled"));
 }
 @Test void genericJsonSerializationCannotExposeSignerIntent()throws Exception{
  var intent=SigningDraftIntent.from(valid());String json=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(intent);
  assertFalse(json.contains("signer@owned.invalid"));assertFalse(json.contains("Controlled signer"));assertFalse(json.contains("authenticationMethod"));
  assertTrue(intent.canonicalJson().contains("signer@owned.invalid"));
 }

}
