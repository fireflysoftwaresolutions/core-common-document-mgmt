package io.fireflysoftware.commons.ecm.core.signing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SigningRequestAssociationTest {
 @Test void aForeignRequestIdCannotBecomeAPreparedResultOrAppendAnotherAuditEvent(){
  var fixture=new SpringDataSigningPreparationStoreTest();fixture.setup();var t=fixture.begin();
  var wrong=new SigningPreparationStore.Ticket(t.operationId(),t.envelopeId(),SigningDraftIntentTest.id(999),t.generation(),t.intent(),t.account(),t.authority(),t.source(),t.reservationAudit());
  assertThrows(RuntimeException.class,()->fixture.store.complete(wrong,fixture.snapshot(),fixture.authority).block());
  fixture.verifyNoUpdates();verify(fixture.auditResources.append).append(any(),any());
 }

 @Test void aMissingPersistedRequestCannotCompleteTheOperation(){
  var fixture=new SpringDataSigningPreparationStoreTest();fixture.setup();var t=fixture.begin();
  doReturn(reactor.core.publisher.Mono.empty()).when(fixture.requests).findByIdAndOperationIdAndEnvelopeIdAndTenantId(t.requestId(),t.operationId(),t.envelopeId(),t.intent().tenantId());
  assertThrows(RuntimeException.class,()->fixture.store.complete(t,fixture.snapshot(),fixture.authority).block());
  fixture.verifyNoUpdates();verify(fixture.auditResources.append).append(any(),any());
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"account-version","legacy-request","external-recipient"})
 void conflictingImmutableMappingCannotAuthorizeCompletion(String conflict){
  var fixture=new SpringDataSigningPreparationStoreTest();fixture.setup();var t=fixture.begin();
  var row=new io.fireflysoftware.commons.ecm.models.entities.signing.SigningRequestBinding(t.requestId(),t.envelopeId(),t.operationId(),t.intent().tenantId(),t.intent().providerId(),t.account().bindingId(),
   t.account().bindingVersion()+("account-version".equals(conflict)?1:0),t.intent().signatureId(),t.intent().documentId(),t.intent().documentVersionId(),
   "legacy-request".equals(conflict)?SigningDraftIntentTest.id(998):null,1,"external-recipient".equals(conflict)?"unconfirmed-recipient":null,fixture.clock.instant(),0L);
  doReturn(reactor.core.publisher.Mono.just(row)).when(fixture.requests).findByIdAndOperationIdAndEnvelopeIdAndTenantId(t.requestId(),t.operationId(),t.envelopeId(),t.intent().tenantId());
  assertThrows(RuntimeException.class,()->fixture.store.complete(t,fixture.snapshot(),fixture.authority).block());
  fixture.verifyNoUpdates();verify(fixture.auditResources.append).append(any(),any());
 }
}
