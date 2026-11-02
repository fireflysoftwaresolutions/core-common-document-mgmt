package io.fireflysoftware.commons.ecm.core.signing;
import io.fireflysoftware.commons.ecm.core.signing.impl.SpringDataSigningPreparationStore;
import io.fireflysoftware.commons.ecm.interfaces.enums.*;
import io.fireflysoftware.commons.ecm.models.entities.Document;
import io.fireflysoftware.commons.ecm.models.entities.DocumentVersion;
import io.fireflysoftware.commons.ecm.models.entities.DocumentSignature;
import io.fireflysoftware.commons.ecm.models.entities.SignatureProvider;
import io.fireflysoftware.commons.ecm.models.entities.signing.*;
import io.fireflysoftware.commons.ecm.models.repositories.signing.*;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeAccountBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SpringDataSigningPreparationStoreTest {
 final SigningDraftIntent intent=SigningDraftIntent.from(SigningDraftIntentTest.valid());
 final SigningDraftWorkflowTest.MutableClock clock=new SigningDraftWorkflowTest.MutableClock();
 final SigningAccountBindingRepository accounts=mock(SigningAccountBindingRepository.class);
 final SigningEnvelopeRepository envelopes=mock(SigningEnvelopeRepository.class);
 final SigningOperationRepository operations=mock(SigningOperationRepository.class);
 final SigningRequestBindingRepository requests=mock(SigningRequestBindingRepository.class);
 final SigningDocumentRepository documents=mock(SigningDocumentRepository.class);
 final SigningDocumentVersionRepository versions=mock(SigningDocumentVersionRepository.class);
 final SigningProviderRepository providers=mock(SigningProviderRepository.class);
 final SigningSignatureRepository signatures=mock(SigningSignatureRepository.class);
 R2dbcEntityTemplate template;
 SigningAuditTestResources auditResources;
 final SigningAccountScope account=new SigningAccountScope(SigningDraftIntentTest.id(10),7,intent.providerId(),intent.tenantId(),new SignatureEnvelopeAccountBinding(org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider.DOCUSIGN,"configured-account","https://owned.invalid/restapi"));
 final SigningAuthority authority=new SigningAuthority(new AuditActor("https://issuer.owned.invalid","opaque subject","owned-client","opaque platform tenant",null,null),
  new SigningTenantBinding(SigningDraftIntentTest.id(11),3,"a".repeat(64),"https://issuer.owned.invalid","opaque subject","opaque platform tenant",intent.tenantId(),SigningDraftWorkflowTest.INITIAL.plusSeconds(5)),SigningDraftWorkflowTest.INITIAL.plusSeconds(5));
 final java.util.concurrent.atomic.AtomicReference<SigningRequestBinding> persistedRequest=new java.util.concurrent.atomic.AtomicReference<>();
 DocumentVersion version;
 SpringDataSigningPreparationStore store;
 @BeforeEach void setup(){
  auditResources=new SigningAuditTestResources(intent,account,authority,clock);template=auditResources.template;
  var registered=new SigningAccountBinding(account.bindingId(),intent.tenantId(),intent.providerId(),7,"DOCUSIGN",account.descriptor().accountId(),account.descriptor().endpoint(),true,SigningDraftWorkflowTest.INITIAL,0L);
  when(accounts.findByIdAndTenantIdAndBindingVersion(account.bindingId(),intent.tenantId(),7)).thenReturn(Mono.just(registered));
  when(providers.findByIdAndTenantId(intent.providerId(),intent.tenantId())).thenReturn(Mono.just(SignatureProvider.builder().id(intent.providerId()).tenantId(intent.tenantId()).providerCode("DOCUSIGN").isActive(true).version(0L).build()));
  when(documents.findByIdAndTenantId(intent.documentId(),intent.tenantId())).thenReturn(Mono.just(Document.builder().id(intent.documentId()).tenantId(intent.tenantId()).version(0L).build()));
  version=DocumentVersion.builder().id(intent.documentVersionId()).documentId(intent.documentId()).tenantId(intent.tenantId()).versionNumber(1).fileName("agreement.pdf").fileExtension("pdf").mimeType("application/pdf").fileSize((long)SigningDraftWorkflowTest.PDF.length).isEncrypted(false).storageType(StorageType.DATABASE).storagePath("controlled-version").build();
  when(versions.findByIdAndDocumentIdAndTenantId(intent.documentVersionId(),intent.documentId(),intent.tenantId())).thenReturn(Mono.defer(()->Mono.just(version)));
  var signature=DocumentSignature.builder().id(intent.signatureId()).documentId(intent.documentId()).documentVersionId(intent.documentVersionId()).signatureProviderId(intent.providerId()).tenantId(intent.tenantId())
   .signerName(intent.signerName()).signerEmail(intent.signerEmail()).signatureType(SignatureType.BASIC).signatureFormat(SignatureFormat.PDF_VISIBLE).signatureStatus(SignatureStatus.PENDING)
   .signaturePositionX(intent.x()).signaturePositionY(intent.y()).signaturePage(intent.page()).signatureWidth(intent.width()).signatureHeight(intent.height()).version(0L).build();
  when(signatures.findByIdAndTenantId(intent.signatureId(),intent.tenantId())).thenReturn(Mono.just(signature));
  when(envelopes.save(any())).thenAnswer(call->Mono.just(call.getArgument(0)));when(operations.save(any())).thenAnswer(call->Mono.just(call.getArgument(0)));when(requests.save(any())).thenAnswer(call->{SigningRequestBinding row=call.getArgument(0);persistedRequest.set(row);return Mono.just(row);});
  when(requests.findByIdAndOperationIdAndEnvelopeIdAndTenantId(any(),any(),any(),any())).thenAnswer(call->Mono.defer(()->Mono.justOrEmpty(persistedRequest.get())
    .filter(row->java.util.Objects.equals(row.id(),call.getArgument(0))&&java.util.Objects.equals(row.operationId(),call.getArgument(1))
      &&java.util.Objects.equals(row.envelopeId(),call.getArgument(2))&&java.util.Objects.equals(row.tenantId(),call.getArgument(3)))));
  doReturn(Mono.just(1L)).when(template).update(any(Query.class),any(Update.class),eq(SigningOperation.class));doReturn(Mono.just(1L)).when(template).update(any(Query.class),any(Update.class),eq(SigningEnvelope.class));
  store=io.fireflysoftware.commons.ecm.core.signing.impl.SigningStoreTestAccess.create(accounts,envelopes,operations,requests,documents,versions,providers,signatures,template,auditResources.phase,clock);
  clearInvocations(template);
 }
 void verifyNoUpdates(){verify(template,never()).update(any(Query.class),any(Update.class),eq(SigningOperation.class));verify(template,never()).update(any(Query.class),any(Update.class),eq(SigningEnvelope.class));}
 SigningPreparationStore.Ticket begin(){return store.begin(intent,account,authority).block(Duration.ofSeconds(1));}
 SigningDocumentSnapshot snapshot(){return new SigningDocumentSnapshot(intent.documentId(),intent.documentVersionId(),"agreement.pdf","application/pdf",SigningDraftWorkflowTest.PDF);}
 @Test void preparingRowsHaveDistinctIdentitiesAndNoGuessedExternalRecipient(){
  var ticket=begin();assertNotEquals(ticket.operationId(),ticket.envelopeId());assertNotEquals(ticket.operationId(),ticket.requestId());
  var request=org.mockito.ArgumentCaptor.forClass(SigningRequestBinding.class);verify(requests).save(request.capture());assertNull(request.getValue().externalRecipientId());assertNull(request.getValue().legacyRequestId());assertEquals(1,request.getValue().plannedSlot());
  var operation=org.mockito.ArgumentCaptor.forClass(SigningOperation.class);verify(operations).save(operation.capture());assertEquals("PREPARING",operation.getValue().state());assertNull(operation.getValue().content());assertEquals(0,operation.getValue().generation());
  assertFalse(operation.getValue().toString().contains(intent.signerEmail()));
 }
 @Test void finalizationUsesStateGenerationTenantAccountAndExactCountFence(){
  var ticket=begin();var prepared=store.complete(ticket,snapshot(),authority).block(Duration.ofSeconds(1));assertEquals("PREPARED",prepared.state());assertEquals(1,prepared.generation());
  var query=org.mockito.ArgumentCaptor.forClass(Query.class);var update=org.mockito.ArgumentCaptor.forClass(Update.class);verify(template).update(query.capture(),update.capture(),eq(SigningOperation.class));
  String fence=query.getValue().getCriteria().orElseThrow().toString();for(String field:new String[]{"id","envelopeId","tenantId","accountBindingId","accountBindingVersion","providerId","documentId","documentVersionId","state","generation","sourceFingerprint","intentDigest","authorityDigest"})assertTrue(fence.contains(field),field+" missing from CAS");
  var values=update.getValue().getAssignments();assertTrue(values.values().stream().anyMatch(v->"PREPARED".equals(v)));assertTrue(values.values().stream().anyMatch(v->v instanceof byte[] bytes&&java.util.Arrays.equals(bytes,SigningDraftWorkflowTest.PDF)));
 }
 @Test void zeroAffectedRowsCannotReportPreparedOrAdvanceEnvelope(){var ticket=begin();doReturn(Mono.just(0L)).when(template).update(any(Query.class),any(Update.class),eq(SigningOperation.class));assertThrows(OptimisticLockingFailureException.class,()->store.complete(ticket,snapshot(),authority).block());verify(template,never()).update(any(Query.class),any(Update.class),eq(SigningEnvelope.class));}
 @Test void changedVersionMetadataAfterStorageRefusesBeforeFinalUpdate(){var ticket=begin();version.setStoragePath("changed-version");assertThrows(OptimisticLockingFailureException.class,()->store.complete(ticket,snapshot(),authority).block());verifyNoUpdates();}
 @Test void expiredAuthorityCannotFinishAnIncompleteOperation(){var ticket=begin();clock.now=SigningDraftWorkflowTest.INITIAL.plusSeconds(6);assertThrows(SecurityException.class,()->store.complete(ticket,snapshot(),authority).block());verifyNoUpdates();}
 @Test void unknownTenantAccountHasNoWrites(){when(accounts.findByIdAndTenantIdAndBindingVersion(any(),any(),anyLong())).thenReturn(Mono.empty());assertThrows(SecurityException.class,this::begin);verifyNoInteractions(envelopes,operations,requests);verifyNoUpdates();}
 @Test void unknownEncryptionStateRefusesBeforePreparationWrites(){version.setIsEncrypted(null);assertThrows(OptimisticLockingFailureException.class,this::begin);verifyNoInteractions(envelopes,operations,requests);verifyNoUpdates();}
 @Test void foreignDocumentReturnedByAnAdapterCannotSatisfyTheTenantScope(){when(documents.findByIdAndTenantId(any(),any())).thenReturn(Mono.just(Document.builder().id(intent.documentId()).tenantId("another tenant").version(0L).build()));assertThrows(OptimisticLockingFailureException.class,this::begin);verifyNoInteractions(envelopes,operations,requests);verifyNoUpdates();}

 @Test void databaseFailureCannotLeakPayloadInAnExceptionOrCause(){var ticket=begin();doReturn(Mono.error(new org.springframework.dao.DataIntegrityViolationException("CONTROLLED_SECRET_SQL_PAYLOAD"))).when(template).update(any(Query.class),any(Update.class),eq(SigningOperation.class));
  var failure=assertThrows(RuntimeException.class,()->store.complete(ticket,snapshot(),authority).block());assertFalse(failure.getMessage().contains("CONTROLLED_SECRET_SQL_PAYLOAD"));assertNull(failure.getCause());}


 @Test void reservationAppendFailurePreventsEveryBusinessInsert(){
  doReturn(Mono.error(new IllegalStateException("CONTROLLED_AUDIT_FAILURE"))).when(auditResources.append).append(any(),any());
  assertThrows(IllegalStateException.class,this::begin);verifyNoInteractions(envelopes,operations,requests);verifyNoUpdates();
  verify(auditResources.connection).rollbackTransaction();verify(auditResources.connection,never()).commitTransaction();
 }
 @Test void preparedAppendFailureLeavesOnlyTheEarlierAuditedReservation(){
  var ticket=begin();clearInvocations(auditResources.connection);doReturn(Mono.error(new IllegalStateException("CONTROLLED_AUDIT_FAILURE"))).when(auditResources.append).append(any(),any());
  assertThrows(IllegalStateException.class,()->store.complete(ticket,snapshot(),authority).block());verifyNoUpdates();
  verify(envelopes).save(any());verify(operations).save(any());verify(requests).save(any());
  verify(auditResources.connection).rollbackTransaction();verify(auditResources.connection,never()).commitTransaction();
 }
 @Test void bothPhaseAuditEventsBindTheSameOpaqueActorAndExactContentCandidate(){
  var ticket=begin();store.complete(ticket,snapshot(),authority).block();
  var events=org.mockito.ArgumentCaptor.forClass(org.fireflyframework.kernel.audit.AuditEvent.class);verify(auditResources.append,times(2)).append(any(),events.capture());
  var reserved=events.getAllValues().get(0);var prepared=events.getAllValues().get(1);
  assertEquals("signing.prepare.reserve",reserved.action());assertEquals("signing.prepare.complete",prepared.action());
  assertEquals(reserved.eventId(),prepared.causationId());assertEquals(reserved.stream(),prepared.stream());assertEquals(authority.actor(),prepared.actor());assertNull(prepared.actor().staffId());
  assertEquals(ticket.operationId(),prepared.stream().resourceId());assertNotEquals(reserved.evidenceDigest(),prepared.evidenceDigest());
  var stored=org.mockito.ArgumentCaptor.forClass(SigningOperation.class);verify(operations).save(stored.capture());
  assertEquals(new SigningAuditEvidence(ticket.operationId(),ticket.envelopeId(),intent,account,ticket.source().fingerprint(),stored.getValue().authorityDigest(),snapshot().sha256(),prepared.eventId(),reserved).digest(prepared.stream().resourceTenantId()),prepared.evidenceDigest());
  assertFalse(prepared.toString().contains(intent.signerEmail()));assertFalse(prepared.toString().contains("%PDF"));
  var query=org.mockito.ArgumentCaptor.forClass(Query.class);var update=org.mockito.ArgumentCaptor.forClass(Update.class);verify(template).update(query.capture(),update.capture(),eq(SigningOperation.class));
  for(String key:new String[]{"auditResourceTenantId","auditProcessId","auditCorrelationId","auditPolicyRevision","reservationAuditEventId","preparedAuditEventId"})assertTrue(query.getValue().getCriteria().orElseThrow().toString().contains(key));
  assertTrue(update.getValue().getAssignments().values().contains(prepared.eventId()));
 }
}
