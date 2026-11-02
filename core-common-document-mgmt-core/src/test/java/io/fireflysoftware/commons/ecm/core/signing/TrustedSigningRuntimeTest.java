package io.fireflysoftware.commons.ecm.core.signing;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.adapter.opa.OpaPolicyEvaluationAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import static org.junit.jupiter.api.Assertions.*;

/** Private-context and real SDK/PDP composition controls, not a physical business commit fixture. */
class TrustedSigningRuntimeTest {
    static final class Fixture {
        final GeneratedConfigSigningTenantBindingResolverTest.Fixture transport=new GeneratedConfigSigningTenantBindingResolverTest.Fixture();
        final SigningDraftIntent intent=CurrentSigningPreparationPolicyTest.intent();
        final SigningAccountScope account=CurrentSigningPreparationPolicyTest.account();
        final AtomicInteger policies=new AtomicInteger(),work=new AtomicInteger();
        final AtomicReference<org.fireflyframework.security.api.domain.Decision> evaluated=new AtomicReference<>();
        final AtomicLong ids=new AtomicLong(100);
        final TrustedSigningRuntime runtime;
        Fixture(){
            var engine=new OpaPolicyEvaluationAdapter(WebClient.builder().baseUrl("https://policy.example.test").exchangeFunction(request->{
                policies.incrementAndGet();return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE,"application/json")
                        .body("{\"result\":{\"allow\":true,\"policy_revision\":\"policy-actual-7\"},\"decision_id\":\"decision-actual-9\"}").build());
            }).build(),"core/signing/prepare");
            runtime=new TrustedSigningRuntime(()->Mono.justOrEmpty(transport.principal.get()),transport.resolver(),
                    new CurrentSigningPreparationPolicy(()->Mono.justOrEmpty(transport.principal.get()),(p,a,r,f)->engine.evaluate(p,a,r,f).doOnNext(value->evaluated.set(value.decision())),transport.clock),transport.clock,()->new UUID(90,ids.incrementAndGet()));
        }
        SigningAuditEvidence evidence(){return new SigningAuditEvidence(new UUID(91,92),new UUID(93,94),intent,account,"b".repeat(64),"c".repeat(64),null,new UUID(95,96),null);}
        PreparedSigningDraft marker(){return new PreparedSigningDraft(new UUID(91,92),new UUID(93,94),new UUID(97,98),1,"PREPARED","d".repeat(64));}
        interface PhaseWork {Mono<PreparedSigningDraft> apply(SigningAuthority authority,SigningAuditAdmission.Permit permit);}
        Mono<PreparedSigningDraft> invoke(PhaseWork action){
            return runtime.scoped(request->runtime.resolve(transport.principal.get(),request.tenantId()).flatMap(binding->{
                var actor=new AuditActor(binding.issuer(),binding.subject(),"actual-user-client",binding.platformTenant(),null,null);
                var authority=new SigningAuthority(actor,binding,binding.notAfter());
                return runtime.requireCurrent(request,account,authority).flatMap(permit->action.apply(authority,permit));
            })).prepare(intent);
        }
        Mono<PreparedSigningDraft> append(SigningAuditAdmission.Permit permit,java.util.function.Function<AuditEvent,Mono<PreparedSigningDraft>> action){
            var evidence=evidence();var event=evidence.event(permit,transport.clock.instant());
            return runtime.withinPhase(permit,()->runtime.withinExpectedAppend(permit,evidence,event,()->action.apply(event)));
        }
    }
    @Test void actualSdkPolicyAndPrivateOwnerScopeAuthorizeBothGenuineAppenderChecks(){
        var f=new Fixture();var seen=new ArrayList<org.fireflyframework.core.audit.AuditAppendAuthority.Permit>();
        var result=f.invoke((authority,permit)->f.append(permit,event->f.runtime.requireCurrent(event).doOnNext(seen::add)
                .then(f.runtime.requireCurrent(event).doOnNext(seen::add)).thenReturn(f.marker()))).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals(2,seen.size());assertEquals(seen.getFirst(),seen.getLast());
        assertEquals(GeneratedConfigSigningTenantBindingResolverTest.RESOURCE,seen.getFirst().stream().resourceTenantId());
        assertEquals("opaque:user:42",seen.getFirst().actor().subject());assertNull(seen.getFirst().actor().staffId());
        assertEquals(2,f.transport.sdkCalls.get());assertEquals(1,f.transport.tokens.get());assertEquals(1,f.policies.get());
    }
    @Test void noValueOnlyPermitOrEventCanActivateAuthorityWithoutTrustedContext(){
        var f=new Fixture();var observation=CurrentSigningPreparationPolicyTest.observation();
        var binding=new SigningTenantBinding(observation.bindingId(),7,observation.definitionDigest(),observation.actor().issuer(),observation.actor().subject(),observation.actor().platformTenantId(),f.intent.tenantId(),observation.notAfter());
        var authority=new SigningAuthority(observation.actor(),binding,observation.notAfter());
        assertThrows(SecurityException.class,()->f.runtime.requireCurrent(f.intent,f.account,authority).block());
        assertEquals(0,f.transport.sdkCalls.get());assertEquals(0,f.policies.get());
    }
    @Test void aStructurallyEqualCallerCopyIsNotThePrivatePhasePermit(){
        var f=new Fixture();
        assertThrows(SecurityException.class,()->f.invoke((authority,p)->{
            var copy=new SigningAuditAdmission.Permit(p.resourceTenantId(),p.resourceTenant(),p.bindingId(),p.bindingVersion(),p.bindingDigest(),p.actor(),p.policyRevision(),p.processId(),p.correlationId(),p.notAfter());
            return f.runtime.withinPhase(copy,()->{f.work.incrementAndGet();return Mono.just(f.marker());});
        }).block(Duration.ofSeconds(3)));
        assertEquals(0,f.work.get());
    }
    @Test void aDifferentAuditEventCannotBorrowTheExpectedAppendCapability(){
        var f=new Fixture();
        assertThrows(SecurityException.class,()->f.invoke((authority,p)->f.append(p,event->{
            var changed=new AuditEvent(event.eventId(),event.stream(),event.actor(),event.action(),event.outcome(),event.processId(),event.correlationId(),event.causationId(),event.occurredAt(),"e".repeat(64),event.policyRevision());
            return f.runtime.requireCurrent(changed).then(Mono.fromSupplier(()->{f.work.incrementAndGet();return f.marker();}));
        })).block(Duration.ofSeconds(3)));
        assertEquals(0,f.work.get());
    }
    @Test void repeatedSubscriptionsUseIndependentTechnicalProcessAndCorrelationIds(){
        var f=new Fixture();var process=new ArrayList<UUID>();
        Mono<PreparedSigningDraft> publisher=f.invoke((authority,p)->{process.add(p.processId());return f.append(p,event->f.runtime.requireCurrent(event).thenReturn(f.marker()));});
        assertNotNull(publisher.block(Duration.ofSeconds(3)));assertNotNull(publisher.block(Duration.ofSeconds(3)));
        assertEquals(2,process.size());assertNotEquals(process.getFirst(),process.getLast());assertEquals(2,f.policies.get());
    }
    @Test void aNestedPreparationCannotReuseItsParentsAdmission(){
        var f=new Fixture();
        var nested=f.runtime.scoped(request->f.runtime.scoped(inner->{f.work.incrementAndGet();return Mono.just(f.marker());}).prepare(request));
        assertThrows(SecurityException.class,()->nested.prepare(f.intent).block(Duration.ofSeconds(3)));
        assertEquals(0,f.work.get());assertEquals(0,f.transport.sdkCalls.get());
    }
    @Test void latePhaseCannotStartTheOwningRoot(){
        var f=new Fixture();
        assertThrows(SecurityException.class,()->f.invoke((authority,p)->{
            f.transport.clock.now.set(GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(5));
            return f.runtime.withinPhase(p,()->{f.work.incrementAndGet();return Mono.just(f.marker());});
        }).block(Duration.ofSeconds(3)));
        assertEquals(0,f.work.get());
    }
    @Test void evenCapturedPrivateContextCannotBeUsedAfterCancellation(){
        var f=new Fixture();var captured=new AtomicReference<ContextView>();var event=new AtomicReference<AuditEvent>();
        Mono<PreparedSigningDraft> pending=f.invoke((authority,p)->f.append(p,expected->{event.set(expected);return Mono.deferContextual(context->{captured.set(context);return f.runtime.requireCurrent(expected).then(Mono.<PreparedSigningDraft>never());});}));
        var error=new AtomicReference<Throwable>();var subscription=pending.subscribe(value->fail("Pending work completed"),error::set);
        try{assertNotNull(captured.get());assertNull(error.get());}finally{subscription.dispose();}
        assertThrows(SecurityException.class,()->f.runtime.requireCurrent(event.get()).contextWrite(captured.get()).block());
    }
    @Test void thePrivateCapabilityCannotBeReplayedAsAnotherAppenderCheck(){
        var f=new Fixture();
        assertThrows(SecurityException.class,()->f.invoke((authority,p)->f.append(p,event->f.runtime.requireCurrent(event)
                .then(f.runtime.requireCurrent(event)).then(f.runtime.requireCurrent(event)).thenReturn(f.marker()))).block(Duration.ofSeconds(3)));
    }
    @Test void aConcurrentPhaseRefusesBeforeAnotherConfigurationOrPolicyCall(){
        var f=new Fixture();
        var result=f.invoke((authority,p)->f.runtime.withinPhase(p,()->f.runtime.requireCurrent(f.intent,f.account,authority)
                .flatMap(other->Mono.<PreparedSigningDraft>error(new AssertionError("Concurrent phase accepted")))
                .onErrorResume(SecurityException.class,error->{assertEquals(2,f.transport.sdkCalls.get());assertEquals(1,f.policies.get());return Mono.just(f.marker());}))).block(Duration.ofSeconds(3));
        assertNotNull(result);
    }
    @Test void aLaterObservationCannotExtendTheFirstFiniteBindingWindow(){
        var f=new Fixture();Instant firstUntil=GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(2);
        f.transport.response.set(GeneratedConfigSigningTenantBindingResolverTest.current().replace("\"validUntilEpochMillis\":"+GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(5).toEpochMilli(),"\"validUntilEpochMillis\":"+firstUntil.toEpochMilli()));
        var observed=new AtomicReference<SigningTenantBinding>();
        var flow=f.runtime.scoped(request->f.runtime.resolve(f.transport.principal.get(),request.tenantId()).flatMap(first->{
            f.transport.clock.now.set(GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(1));
            f.transport.response.set(GeneratedConfigSigningTenantBindingResolverTest.current().replace("\"observedAtEpochMillis\":"+GeneratedConfigSigningTenantBindingResolverTest.NOW.toEpochMilli(),"\"observedAtEpochMillis\":"+f.transport.clock.millis()));
            return f.runtime.resolve(f.transport.principal.get(),request.tenantId()).doOnNext(observed::set).thenReturn(f.marker());
        }));
        assertNotNull(flow.prepare(f.intent).block(Duration.ofSeconds(3)));assertNotNull(observed.get());assertEquals(firstUntil,observed.get().notAfter());
    }
    @Test void firstBindingExpiryRefusesAnotherSdkLookupBeforeIo(){
        var f=new Fixture();Instant firstUntil=GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(2);
        f.transport.response.set(GeneratedConfigSigningTenantBindingResolverTest.current().replace("\"validUntilEpochMillis\":"+GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(5).toEpochMilli(),"\"validUntilEpochMillis\":"+firstUntil.toEpochMilli()));
        var flow=f.runtime.scoped(request->f.runtime.resolve(f.transport.principal.get(),request.tenantId()).flatMap(first->{
            f.transport.clock.now.set(firstUntil);return f.runtime.resolve(f.transport.principal.get(),request.tenantId())
                    .flatMap(value->Mono.<PreparedSigningDraft>error(new AssertionError("Expired observation returned")))
                    .onErrorResume(SecurityException.class,error->Mono.just(f.marker()));
        }));
        assertThrows(SecurityException.class,()->flow.prepare(f.intent).block(Duration.ofSeconds(3)));assertEquals(1,f.transport.sdkCalls.get());
    }

    @Test void authorityViewRetainsTheActualSameResponseDecisionObject(){
        var f=new Fixture();var result=new AtomicReference<org.fireflyframework.security.api.domain.Decision>();
        var flow=f.runtime.scoped(request->f.runtime.resolve(f.transport.principal.get(),request.tenantId())
                .then(f.runtime.decisions(f.account).authorize(f.transport.principal.get(),"document.signing.prepare","document:"+request.documentId(),Map.of()))
                .doOnNext(result::set).thenReturn(f.marker()));
        assertNotNull(flow.prepare(f.intent).block(Duration.ofSeconds(3)));assertNotNull(result.get());assertSame(f.evaluated.get(),result.get());assertTrue(result.get().granted());
        assertEquals(1,f.policies.get());
        assertThrows(SecurityException.class,()->f.runtime.decisions(f.account).authorize(f.transport.principal.get(),"document.signing.prepare","document:"+f.intent.documentId(),Map.of()).block());
        assertEquals(1,f.policies.get());
    }

}
