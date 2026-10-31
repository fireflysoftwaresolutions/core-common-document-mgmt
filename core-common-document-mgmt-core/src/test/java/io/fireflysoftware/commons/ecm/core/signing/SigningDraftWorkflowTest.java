package io.fireflysoftware.commons.ecm.core.signing;

import java.time.*;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeAccountBinding;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.Obligation;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.reactive.TransactionContextManager;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningDraftWorkflowTest {
 static final Instant INITIAL=Instant.parse("2026-10-31T12:00:00Z");
 static final byte[] PDF="%PDF-1.7\ncontrolled immutable bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 static UUID id(int n){return new UUID(1,n);}
 static class MutableClock extends Clock {
  Instant now=INITIAL;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
 }
 final MutableClock clock=new MutableClock();
 final SecurityContextPort context=mock(SecurityContextPort.class);
 final PolicyDecisionPort policy=mock(PolicyDecisionPort.class);
 final SigningTenantBindingResolver tenants=mock(SigningTenantBindingResolver.class);
 final SigningPreparationStore store=mock(SigningPreparationStore.class);
 final SignatureEnvelopePort envelopes=mock(SignatureEnvelopePort.class);
 final DocumentContentPort content=mock(DocumentContentPort.class);
 final SigningDraftIntent intent=SigningDraftIntent.from(SigningDraftIntentTest.valid());
 final SignatureEnvelopeAccountBinding descriptor=new SignatureEnvelopeAccountBinding(SignatureProvider.DOCUSIGN,"configured-account","https://owned.invalid/restapi");
 final SigningAccountScope account=new SigningAccountScope(id(10),7,intent.providerId(),intent.tenantId(),descriptor);
 SigningDraftPreparation workflow;
 SecurityPrincipal principal(String subject){return SecurityPrincipal.builder().issuer("https://issuer.owned.invalid").subject(subject).tenantId("opaque signed platform tenant")
  .claims(Map.of("client_id","owned-client","exp",INITIAL.plusSeconds(30))).build();}
 SigningTenantBinding binding(Instant until){return new SigningTenantBinding(id(11),3,"a".repeat(64),"https://issuer.owned.invalid","opaque subject","opaque signed platform tenant",intent.tenantId(),until);}
 @BeforeEach void setup(){
  when(context.currentPrincipal()).thenReturn(Mono.just(principal("opaque subject")));
  when(policy.authorize(any(),any(),any(),any())).thenReturn(Mono.just(Decision.permit()));
  when(tenants.resolve(any(),any())).thenReturn(Mono.just(binding(INITIAL.plusSeconds(5))));
  when(envelopes.accountBinding()).thenReturn(Mono.just(descriptor));when(content.getContent(intent.documentVersionId())).thenReturn(Mono.just(PDF.clone()));
  when(store.begin(any(),any(),any())).thenAnswer(call->Mono.just(new SigningPreparationStore.Ticket(id(20),id(21),id(22),0,intent,account,call.getArgument(2),new SigningPreparationStore.SourceStamp("b".repeat(64),"agreement.pdf","application/pdf",PDF.length))));
  when(store.complete(any(),any(),any())).thenAnswer(call->{SigningAuthority verified=call.getArgument(2);verified.requireLive(clock);SigningDocumentSnapshot snapshot=call.getArgument(1);
   return Mono.just(new PreparedSigningDraft(id(20),id(21),id(22),1,"PREPARED",snapshot.sha256()));});
  workflow=new SigningDraftPreparation(new SigningAuthorityAdapter(context,policy,tenants,clock),store,account,envelopes,content);
 }
 PreparedSigningDraft prepare(){return workflow.prepare(intent).block(Duration.ofSeconds(2));}
 @Test void exactVersionBytesAreFetchedOnceAndOpaqueActorIsPreservedForLocalPreparation(){
  var prepared=prepare();assertEquals("PREPARED",prepared.state());assertEquals(SigningDocumentSnapshot.sha256(PDF),prepared.contentDigest());
  verify(content).getContent(intent.documentVersionId());verifyNoMoreInteractions(content);verify(context,times(2)).currentPrincipal();verify(policy,times(2)).authorize(any(),any(),any(),any());
  var authority=org.mockito.ArgumentCaptor.forClass(SigningAuthority.class);verify(store).complete(any(),any(),authority.capture());
  assertEquals("opaque subject",authority.getValue().actor().subject());assertEquals("opaque signed platform tenant",authority.getValue().actor().platformTenantId());
  assertNull(authority.getValue().actor().staffId());assertThrows(SigningIntegrationUnavailableException.class,()->workflow.dispatch(prepared).block());
  verify(envelopes,times(2)).accountBinding();verifyNoMoreInteractions(envelopes);
 }
 @Test void missingPrincipalFailsBeforeDatabaseOrStorage(){when(context.currentPrincipal()).thenReturn(Mono.empty());assertThrows(SecurityException.class,this::prepare);verifyNoInteractions(store,content);}
 @Test void missingTrustedTenantBindingCannotBeReplacedByStringEquality(){when(tenants.resolve(any(),any())).thenReturn(Mono.empty());assertThrows(SecurityException.class,this::prepare);verifyNoInteractions(store,content);}
 @Test void unresolvedPolicyFailsBeforeDatabaseOrStorage(){when(policy.authorize(any(),any(),any(),any())).thenReturn(Mono.empty());assertThrows(SecurityException.class,this::prepare);verifyNoInteractions(store,content);}
 @Test void unfulfilledPolicyObligationsAreRefused(){when(policy.authorize(any(),any(),any(),any())).thenReturn(Mono.just(Decision.permit(List.of(Obligation.of("row-filter",Map.of())))));assertThrows(SecurityException.class,this::prepare);verifyNoInteractions(store,content);}
 @Test void unsupportedAccountLookupFailsBeforeAnyAuthorityOrStorage(){when(envelopes.accountBinding()).thenReturn(Mono.error(new UnsupportedOperationException("unsupported")));assertThrows(UnsupportedOperationException.class,this::prepare);verifyNoInteractions(store,content,context);}
 @Test void originalAuthorityExpiryDuringStorageLeavesPreparationIncomplete(){when(content.getContent(any())).thenAnswer(call->Mono.fromSupplier(()->{clock.now=INITIAL.plusSeconds(6);return PDF.clone();}));assertThrows(SecurityException.class,this::prepare);verify(store).begin(any(),any(),any());verify(store,never()).complete(any(),any(),any());verify(tenants).resolve(any(),any());}
 @Test void currentPrincipalIsRecheckedAfterStorage(){when(context.currentPrincipal()).thenReturn(Mono.just(principal("opaque subject"))).thenReturn(Mono.just(principal("different subject")));assertThrows(SecurityException.class,this::prepare);verify(store,never()).complete(any(),any(),any());}
 @Test void currentPolicyDenialIsNotHiddenByOriginalPermit(){when(policy.authorize(any(),any(),any(),any())).thenReturn(Mono.just(Decision.permit())).thenReturn(Mono.just(Decision.deny("changed")));assertThrows(SecurityException.class,this::prepare);verify(store,never()).complete(any(),any(),any());}
 @Test void changedServerAccountAfterStorageCannotWritePrepared(){var foreign=new SignatureEnvelopeAccountBinding(SignatureProvider.DOCUSIGN,"other-account",descriptor.endpoint());when(envelopes.accountBinding()).thenReturn(Mono.just(descriptor)).thenReturn(Mono.just(foreign));assertThrows(SecurityException.class,this::prepare);verify(store,never()).complete(any(),any(),any());}
 @Test void shorterCurrentAuthorityIsClippedAndNeverRenewed(){when(tenants.resolve(any(),any())).thenReturn(Mono.just(binding(INITIAL.plusSeconds(5)))).thenReturn(Mono.just(binding(INITIAL.plusSeconds(2))));prepare();var authority=org.mockito.ArgumentCaptor.forClass(SigningAuthority.class);verify(store).complete(any(),any(),authority.capture());assertEquals(INITIAL.plusSeconds(2),authority.getValue().notAfter());}
 @Test void staleOperationFailureIsPropagatedWithoutRefetchOrRetry(){var failure=new OptimisticLockingFailureException("controlled stale generation");doReturn(Mono.error(failure)).when(store).complete(any(),any(),any());assertSame(failure,assertThrows(OptimisticLockingFailureException.class,this::prepare));verify(store).complete(any(),any(),any());verify(content).getContent(intent.documentVersionId());}
 @Test void ambientTransactionCannotSpanStorage(){
  var failure=workflow.prepare(intent).contextWrite(ctx->{var tx=ctx.get(org.springframework.transaction.reactive.TransactionContext.class);new TransactionSynchronizationManager(tx).setActualTransactionActive(true);return ctx;}).contextWrite(TransactionContextManager.getOrCreateContext()).contextWrite(TransactionContextManager.getOrCreateContextHolder());
  assertThrows(IllegalStateException.class,()->failure.block(Duration.ofSeconds(2)));verifyNoInteractions(content,store,envelopes,context);
 }
 @Test void storageFailureCannotLeakPayloadInAnException(){when(content.getContent(any())).thenReturn(Mono.error(new IllegalStateException("CONTROLLED_SECRET_STORAGE_PAYLOAD")));var failure=assertThrows(RuntimeException.class,this::prepare);assertFalse(failure.getMessage().contains("CONTROLLED_SECRET_STORAGE_PAYLOAD"));assertNull(failure.getCause());verify(store,never()).complete(any(),any(),any());}

 @Test void neverCompletingStorageIsBoundByTheOriginalAuthorityWindow(){
  when(content.getContent(any())).thenReturn(Mono.never());
  reactor.test.StepVerifier.withVirtualTime(()->workflow.prepare(intent)).thenAwait(Duration.ofSeconds(6)).expectError(SecurityException.class).verify(Duration.ofSeconds(1));
  verify(store).begin(any(),any(),any());verify(store,never()).complete(any(),any(),any());
 }
 @Test void aDifferentProviderCannotBeRegisteredAsThisDocuSignPreparation(){
  var descriptor=new SignatureEnvelopeAccountBinding(SignatureProvider.ADOBE_SIGN,"configured-account","https://owned.invalid/restapi");
  assertThrows(IllegalArgumentException.class,()->new SigningAccountScope(id(10),7,intent.providerId(),intent.tenantId(),descriptor));
 }

 @Test void aForeignPreparedAssociationCannotBecomeThisOperationsResult(){doReturn(Mono.just(new PreparedSigningDraft(id(99),id(21),id(22),1,"PREPARED",SigningDocumentSnapshot.sha256(PDF)))).when(store).complete(any(),any(),any());assertThrows(IllegalStateException.class,this::prepare);}
 @Test void anEmptyStoreResultCannotMasqueradeAsPreparationCompletion(){doReturn(Mono.empty()).when(store).complete(any(),any(),any());assertThrows(IllegalStateException.class,this::prepare);}

}
