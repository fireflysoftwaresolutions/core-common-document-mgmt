package io.fireflysoftware.commons.ecm.core.signing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SigningAuditAuthorityDigestTest {
 @Test void otherwiseIdenticalPhaseEvidenceCannotSwapOriginalAuthorityProvenance(){
  var fixture=new SpringDataSigningPreparationStoreTest();
  var first=new SigningAuditEvidence(SigningDraftIntentTest.id(20),SigningDraftIntentTest.id(21),fixture.intent,fixture.account,"b".repeat(64),"c".repeat(64),null,SigningDraftIntentTest.id(30),null);
  var changed=new SigningAuditEvidence(SigningDraftIntentTest.id(20),SigningDraftIntentTest.id(21),fixture.intent,fixture.account,"b".repeat(64),"d".repeat(64),null,SigningDraftIntentTest.id(30),null);
  assertNotEquals(first.digest(SigningDraftIntentTest.id(100)),changed.digest(SigningDraftIntentTest.id(100)));
 }
}
