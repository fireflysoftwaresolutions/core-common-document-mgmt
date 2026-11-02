package io.fireflysoftware.commons.ecm.core.signing;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiFunction;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.api.domain.PolicyEvaluation;
import org.fireflyframework.security.adapter.opa.OpaPolicyEvaluationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningPolicyTraceRuntimeTest {
    static final class Fixture {
        final TrustedSigningRuntimeTest.Fixture f=new TrustedSigningRuntimeTest.Fixture();
        final List<PolicyEvaluation> evaluations=new ArrayList<>();final AtomicInteger calls=new AtomicInteger();
        final TrustedSigningRuntime runtime;
        Fixture(){
            var engine=new OpaPolicyEvaluationAdapter(WebClient.builder().baseUrl("https://policy.example.test").exchangeFunction(request->{
                int call=calls.incrementAndGet();return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE,"application/json")
                        .body("{\"result\":{\"allow\":true,\"policy_revision\":\"policy-actual-7\"},\"decision_id\":\"actual-evaluation-"+call+"\"}").build());
            }).build(),"core/signing/prepare");
            runtime=new TrustedSigningRuntime(()->Mono.just(f.transport.principal.get()),f.transport.resolver(),
                    new CurrentSigningPreparationPolicy(()->Mono.just(f.transport.principal.get()),(p,a,r,facts)->engine.evaluate(p,a,r,facts).doOnNext(evaluations::add),f.transport.clock),
                    f.transport.clock,()->new UUID(90,f.ids.incrementAndGet()));
        }
        Mono<PreparedSigningDraft> invoke(BiFunction<SigningAuthority,SigningAuditAdmission.Permit,Mono<PreparedSigningDraft>> work){
            return runtime.scoped(intent->runtime.resolve(f.transport.principal.get(),intent.tenantId()).flatMap(binding->{
                var actor=new AuditActor(binding.issuer(),binding.subject(),"actual-user-client",binding.platformTenant(),null,null);var authority=new SigningAuthority(actor,binding,binding.notAfter());
                return runtime.decisions(f.account).authorize(f.transport.principal.get(),"document.signing.prepare","document:"+intent.documentId(),Map.of())
                        .then(runtime.requireCurrent(intent,f.account,authority)).flatMap(permit->work.apply(authority,permit));
            })).prepare(f.intent);
        }
    }
    @Test void fourManagedEvaluationsMapToExactWorkflowPairsWithoutAdditionalCalls(){
        var t=new Fixture();var f=t.f;var traces=new ArrayList<SigningPolicyTraceV1>();var events=new ArrayList<AuditEvent>();byte[] bytes=SigningDraftWorkflowTest.PDF.clone();
        var store=new SigningPreparationStore(){
            @Override public Mono<Ticket> begin(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority){
                return t.runtime.requireCurrent(intent,account,authority).flatMap(permit->t.runtime.withinPhase(permit,()->t.runtime.currentTrace(permit).map(trace->{
                    traces.add(trace);events.add(new SigningAuditEvidenceV3(f.evidence(),trace).event(permit,f.transport.clock.instant()));
                    return new Ticket(f.marker().operationId(),f.marker().envelopeId(),f.marker().requestId(),0,intent,account,authority,new SourceStamp("b".repeat(64),"agreement.pdf","application/pdf",bytes.length));
                })));
            }
            @Override public Mono<PreparedSigningDraft> complete(Ticket ticket,SigningDocumentSnapshot snapshot,SigningAuthority authority){
                return t.runtime.requireCurrent(ticket.intent(),ticket.account(),authority).flatMap(permit->t.runtime.withinPhase(permit,()->t.runtime.currentTrace(permit).flatMap(trace->{
                    traces.add(trace);var base=f.evidence();var complete=new SigningAuditEvidence(base.operationId(),base.envelopeId(),base.intent(),base.account(),base.sourceFingerprint(),base.authorityDigest(),snapshot.sha256(),new UUID(123,2),events.getFirst());
                    events.add(new SigningAuditEvidenceV3(complete,trace).event(permit,f.transport.clock.instant()));
                    return t.runtime.currentTrace(permit).map(repeated->{assertSame(trace,repeated);return new PreparedSigningDraft(ticket.operationId(),ticket.envelopeId(),ticket.requestId(),1,"PREPARED",snapshot.sha256());});
                })));
            }
        };
        var envelopes=mock(SignatureEnvelopePort.class);when(envelopes.accountBinding()).thenReturn(Mono.just(f.account.descriptor()));
        var content=mock(DocumentContentPort.class);when(content.getContent(f.intent.documentVersionId())).thenReturn(Mono.just(bytes));
        var authority=new SigningAuthorityAdapter(()->Mono.just(f.transport.principal.get()),t.runtime.decisions(f.account),t.runtime,f.transport.clock);
        var result=t.runtime.scoped(new SigningDraftPreparation(authority,store,f.account,envelopes,content)).prepare(f.intent).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals("PREPARED",result.state());assertEquals(4,t.calls.get());assertEquals(4,f.transport.sdkCalls.get());assertEquals(4,t.evaluations.size());assertEquals(2,traces.size());
        assertEquals(SigningPolicyTraceV1.Phase.RESERVE,traces.getFirst().phase());assertEquals(SigningPolicyTraceV1.Phase.PREPARED,traces.getLast().phase());
        for(int phase=0;phase<2;phase++){
            assertSame(t.evaluations.get(phase*2).provenance().orElseThrow(),traces.get(phase).authorization());assertSame(t.evaluations.get(phase*2+1).provenance().orElseThrow(),traces.get(phase).admission());
            assertEquals("actual-evaluation-"+(phase*2+1),traces.get(phase).authorization().decisionId());assertEquals("actual-evaluation-"+(phase*2+2),traces.get(phase).admission().decisionId());
        }
        assertEquals(events.getFirst().eventId(),events.getLast().causationId());verify(content).getContent(f.intent.documentVersionId());verifyNoMoreInteractions(content);verify(envelopes,times(2)).accountBinding();verifyNoMoreInteractions(envelopes);
    }
    @Test void traceRequiresPrivateEnteredPermitAndNeverInventsMissingAuthorization(){
        var old=new TrustedSigningRuntimeTest.Fixture();assertThrows(SecurityException.class,()->old.invoke((authority,p)->old.runtime.withinPhase(p,()->old.runtime.currentTrace(p).thenReturn(old.marker()))).block(Duration.ofSeconds(3)));
        var before=new Fixture();assertThrows(SecurityException.class,()->before.invoke((authority,p)->before.runtime.currentTrace(p).thenReturn(before.f.marker())).block(Duration.ofSeconds(3)));
        var t=new Fixture();var attempted=new AtomicInteger();
        assertNotNull(t.invoke((authority,p)->t.runtime.withinPhase(p,()->{
            var copy=new SigningAuditAdmission.Permit(p.resourceTenantId(),p.resourceTenant(),p.bindingId(),p.bindingVersion(),p.bindingDigest(),p.actor(),p.policyRevision(),p.processId(),p.correlationId(),p.notAfter());
            return t.runtime.currentTrace(copy).doOnNext(value->attempted.incrementAndGet()).onErrorResume(SecurityException.class,error->t.runtime.currentTrace(p)).thenReturn(t.f.marker());
        })).block(Duration.ofSeconds(3)));assertEquals(0,attempted.get());assertEquals(2,t.calls.get());
    }
    @Test void completedAndCancelledContextsCannotReviveTheirTrace(){
        for(boolean cancel:List.of(false,true)){
            var t=new Fixture();var captured=new AtomicReference<ContextView>();var permit=new AtomicReference<SigningAuditAdmission.Permit>();
            var flow=t.invoke((authority,p)->t.runtime.withinPhase(p,()->t.runtime.currentTrace(p).flatMap(trace->Mono.deferContextual(context->{captured.set(context);permit.set(p);return cancel?Mono.never():Mono.just(t.f.marker());}))));
            if(cancel){var error=new AtomicReference<Throwable>();var subscription=flow.subscribe(value->fail("Pending phase completed"),error::set);try{assertNotNull(captured.get());assertNull(error.get());}finally{subscription.dispose();}}
            else assertNotNull(flow.block(Duration.ofSeconds(3)));
            assertThrows(SecurityException.class,()->t.runtime.currentTrace(permit.get()).contextWrite(captured.get()).block(Duration.ofSeconds(3)));assertEquals(2,t.calls.get());
        }
    }
    @Test void extraAuthorizationCannotBeRelabeledAsAValidWorkflowPair(){
        var t=new Fixture();assertThrows(SecurityException.class,()->t.invoke((authority,p)->t.runtime.decisions(t.f.account).authorize(t.f.transport.principal.get(),"document.signing.prepare","document:"+t.f.intent.documentId(),Map.of())
                .then(t.runtime.withinPhase(p,()->t.runtime.currentTrace(p).thenReturn(t.f.marker())))).block(Duration.ofSeconds(3)));assertEquals(3,t.calls.get());
    }
}
