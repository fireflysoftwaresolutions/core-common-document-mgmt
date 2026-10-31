package io.fireflysoftware.commons.ecm.core.signing;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningPhaseTerminationTest {
    @Test void actualPreparationCanPassDirectlyFromReservationThroughStorageToCompletion(){
        var f=new TrustedSigningRuntimeTest.Fixture();var begins=new AtomicInteger();var completes=new AtomicInteger();
        byte[] bytes=SigningDraftWorkflowTest.PDF.clone();
        var store=new SigningPreparationStore(){
            @Override public Mono<Ticket> begin(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority){
                return f.runtime.requireCurrent(intent,account,authority).flatMap(permit->f.runtime.withinPhase(permit,()->Mono.fromSupplier(()->{
                    begins.incrementAndGet();return new Ticket(f.marker().operationId(),f.marker().envelopeId(),f.marker().requestId(),0,intent,account,authority,new SourceStamp("b".repeat(64),"agreement.pdf","application/pdf",bytes.length));
                })));
            }
            @Override public Mono<PreparedSigningDraft> complete(Ticket ticket,SigningDocumentSnapshot snapshot,SigningAuthority authority){
                return f.runtime.requireCurrent(ticket.intent(),ticket.account(),authority).flatMap(permit->f.runtime.withinPhase(permit,()->Mono.fromSupplier(()->{
                    completes.incrementAndGet();return new PreparedSigningDraft(ticket.operationId(),ticket.envelopeId(),ticket.requestId(),1,"PREPARED",snapshot.sha256());
                })));
            }
        };
        var envelopes=mock(SignatureEnvelopePort.class);when(envelopes.accountBinding()).thenReturn(Mono.just(f.account.descriptor()));
        var content=mock(DocumentContentPort.class);when(content.getContent(f.intent.documentVersionId())).thenReturn(Mono.just(bytes));
        var authority=new SigningAuthorityAdapter(()->Mono.just(f.transport.principal.get()),f.runtime.decisions(f.account),f.runtime,f.transport.clock);
        var workflow=f.runtime.scoped(new SigningDraftPreparation(authority,store,f.account,envelopes,content));
        var prepared=workflow.prepare(f.intent).block(Duration.ofSeconds(3));
        assertNotNull(prepared);assertEquals("PREPARED",prepared.state());assertEquals(SigningDocumentSnapshot.sha256(bytes),prepared.contentDigest());
        assertEquals(1,begins.get());assertEquals(1,completes.get());assertEquals(4,f.policies.get());assertEquals(4,f.transport.sdkCalls.get());
        verify(content).getContent(f.intent.documentVersionId());verifyNoMoreInteractions(content);
        verify(envelopes,times(2)).accountBinding();verifyNoMoreInteractions(envelopes);
    }
    @Test void errorRecoveryCanBeginNextPhaseBeforeDownstreamErrorHandlerReturns(){
        var f=new TrustedSigningRuntimeTest.Fixture();var failure=new IllegalStateException("controlled");
        var result=f.invoke((authority,first)->f.runtime.<PreparedSigningDraft>withinPhase(first,()->Mono.error(failure))
                .onErrorResume(error->{assertSame(failure,error);return f.runtime.requireCurrent(f.intent,f.account,authority)
                        .flatMap(second->f.runtime.withinPhase(second,()->Mono.just(f.marker())));})).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals(2,f.policies.get());
    }
    @Test void emptyCompletionReleasesPhaseBeforeThenSubscribesToNextPhase(){
        var f=new TrustedSigningRuntimeTest.Fixture();
        var result=f.invoke((authority,first)->f.runtime.withinPhase(first,Mono::empty)
                .then(Mono.defer(()->f.runtime.requireCurrent(f.intent,f.account,authority)))
                .flatMap(second->f.runtime.withinPhase(second,()->Mono.just(f.marker())))).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals(2,f.policies.get());
    }
    @Test void completedPermitCannotBeReenteredWhileNextPhaseIsActive(){
        var f=new TrustedSigningRuntimeTest.Fixture();var forbidden=new AtomicInteger();
        var result=f.invoke((authority,first)->f.runtime.withinPhase(first,()->Mono.just(f.marker()))
                .flatMap(done->f.runtime.requireCurrent(f.intent,f.account,authority))
                .flatMap(second->f.runtime.withinPhase(second,()->f.runtime.withinPhase(first,()->{
                    forbidden.incrementAndGet();return Mono.just(f.marker());
                }).onErrorResume(SecurityException.class,error->Mono.just(f.marker()))))).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals(0,forbidden.get());assertEquals(2,f.policies.get());
    }
}
