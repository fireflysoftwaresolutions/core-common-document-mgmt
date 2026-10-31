package io.fireflysoftware.commons.ecm.core.signing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SigningMissingAuditBoundaryTest {
 @Test void ordinaryTransactionHelperCannotCommitUnauditedReservation(){
  var original=new SpringDataSigningPreparationStoreTest();original.setup();
  var manager=mock(org.springframework.transaction.ReactiveTransactionManager.class);
  var store=new io.fireflysoftware.commons.ecm.core.signing.impl.SpringDataSigningPreparationStore(original.accounts,original.envelopes,original.operations,original.requests,original.documents,original.versions,original.providers,original.signatures,original.template,new SigningTransactions(org.springframework.transaction.reactive.TransactionalOperator.create(manager)),original.clock);
  assertThrows(IllegalStateException.class,()->store.begin(original.intent,original.account,original.authority).block());
  verifyNoInteractions(original.accounts,original.providers,original.documents,original.versions,original.signatures,original.envelopes,original.operations,original.requests);
 }
}
