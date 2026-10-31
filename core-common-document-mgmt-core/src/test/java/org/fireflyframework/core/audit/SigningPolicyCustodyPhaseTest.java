package org.fireflyframework.core.audit;

import io.fireflysoftware.commons.ecm.core.signing.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.api.domain.PolicyEvaluationProvenance;
import org.fireflyframework.core.transaction.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningPolicyCustodyPhaseTest {
    static final Instant NOW=Instant.parse("2026-10-31T12:00:00Z");
    final AuditTestFixture f=new AuditTestFixture();
    final AtomicInteger writes=new AtomicInteger();
    final SigningAuditAdmission admission=mock(SigningAuditAdmission.class,withSettings().extraInterfaces(SigningPolicyTracePort.class).defaultAnswer(CALLS_REAL_METHODS));
    final SigningPolicyTracePort tracePort=(SigningPolicyTracePort)admission;
    final io.fireflysoftware.commons.ecm.core.signing.SigningPolicyCustodyFixture values=new io.fireflysoftware.commons.ecm.core.signing.SigningPolicyCustodyFixture(f.clock);
    SigningPolicyCustodyPhaseTest(){
        when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.just(values.permit()));
        when(tracePort.requirePolicyCustody()).thenReturn(Mono.empty());
        when(tracePort.currentTrace(any())).thenReturn(Mono.just(values.trace()));
        doAnswer(call->{java.util.function.Supplier<?> work=call.getArgument(3);return work.get();}).when(tracePort).withinExpectedAppendV3(any(),any(),any(),any());
    }
    SigningAuditedPhase phase(){return new SigningAuditedPhase(f.resources,f.client,f.template,f.manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),
            event->Mono.just(new AuditAppendAuthority.Permit(event.stream(),event.actor(),values.authority().notAfter())),admission,f.clock,f.nanos);}
    Mono<String> execute(){return phase().executeV3(values.intent(),values.account(),values.authority(),()->Mono.just(new SigningAuditedPhase.PlanV3<>(values.evidence(),receipt->Mono.fromSupplier(()->{writes.incrementAndGet();assertEquals(values.trace(),receipt.trace());return "reserved";}))));}
    @Test void actualMappedInsertAndScopedReadbackPairOriginalIdsWithCanonicalAudit(){assertEquals("reserved",execute().onErrorMap(error->new AssertionError("Controlled mapped statements="+f.driver.sql+"; bindings="+f.driver.bindings.size(),error)).block());assertEquals(1,f.driver.commits);assertEquals(0,f.driver.rollbacks);assertEquals(1,writes.get());assertEquals(1,f.driver.tables.get("policy_custody").size());var row=f.driver.tables.get("policy_custody").getFirst();assertEquals("original: \u2028\ud83d\ude00",row.get("authorization_decision_id"));assertEquals(values.trace().digest(),row.get("trace_digest"));assertEquals(values.evidence().digest(values.permit().resourceTenantId()),row.get("business_digest"));assertEquals(new SigningAuditEvidenceV3(values.evidence(),values.trace()).digest(values.permit().resourceTenantId()),f.driver.tables.get("audit_event").getFirst().get("evidence_digest"));assertEquals(4,f.driver.writes);}
    @Test void zeroCustodyInsertRollsBackCanonicalAppendWithoutBusiness(){f.driver.countAtWrite=4;f.driver.writeCount=0;refused();}
    @Test void multipleCustodyInsertCountCannotBecomeSuccessfulPreparation(){f.driver.countAtWrite=4;f.driver.writeCount=2;refused();}
    @Test void emptyCustodyInsertCountCannotBecomeSuccessfulPreparation(){f.driver.countAtWrite=4;f.driver.emptyCount=true;refused();}
    @Test void emptyScopedReadbackRollsBackCanonicalAppendAndCustody(){f.driver.beforeRead=()->{if(f.driver.tables.containsKey("policy_custody"))f.driver.tables.get("policy_custody").clear();};refused();}
    @Test void differentReadbackIdentityCannotBeAcceptedByMatchingTrace(){f.driver.beforeRead=()->{var rows=f.driver.tables.get("policy_custody");if(rows!=null&&!rows.isEmpty())rows.getFirst().put("id",UUID.randomUUID());};refused();}
    @Test void laterBusinessFailureRollsBackTheSameAuditAndCustodyRoot(){assertThrows(RuntimeException.class,()->phase().executeV3(values.intent(),values.account(),values.authority(),()->Mono.just(new SigningAuditedPhase.PlanV3<>(values.evidence(),receipt->Mono.error(new IllegalStateException("private business failure"))))).block());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());}
    @Test void unsupportedV3CapabilityRefusesBeforeLeaseOrAppender(){when(tracePort.requirePolicyCustody()).thenReturn(Mono.error(new SecurityException("unsupported")));assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,f.driver.creates);assertEquals(0,f.driver.writes);}
    @Test void identicalV3ReplayPreservesOriginalCustodyButCannotAuthorizeAnotherBusinessEffect(){assertEquals("reserved",execute().block());var before=f.driver.copy();assertThrows(RuntimeException.class,()->execute().block());assertEquals(before,f.driver.tables);assertEquals(1,writes.get());assertEquals(1,f.driver.commits);assertEquals(1,f.driver.rollbacks);}
    @Test void newDecisionIdsCannotRewriteOriginalEventOrCustodyUnderTheSameEventId(){assertEquals("reserved",execute().block());var before=f.driver.copy();var t=values.trace();when(tracePort.currentTrace(any())).thenReturn(Mono.just(new SigningPolicyTraceV1(t.phase(),new PolicyEvaluationProvenance(t.authorization().policyRevision(),"new-admission-for-replay"),t.admission())));assertThrows(RuntimeException.class,()->execute().block());assertEquals(before,f.driver.tables);assertEquals(1,writes.get());assertEquals(1,f.driver.commits);assertEquals(1,f.driver.rollbacks);}
    @Test void V3DoesNotClassifyAnInsideRootOptimisticFailureUsingTheLegacyMarker(){var error=assertThrows(RootTransactionException.class,()->phase().executeV3(values.intent(),values.account(),values.authority(),()->Mono.just(new SigningAuditedPhase.PlanV3<>(values.evidence(),receipt->Mono.error(new org.springframework.dao.OptimisticLockingFailureException("private conflict"))))).block());assertEquals(CleanupFact.COMPLETE,error.cleanupFact());assertEquals(1,f.driver.rollbacks);assertEquals(0,f.driver.commits);assertTrue(f.driver.tables.isEmpty());}
    @Test void V3DoesNotClassifyAnInsideRootSecurityFailureUsingTheLegacyMarker(){var error=assertThrows(RootTransactionException.class,()->phase().executeV3(values.intent(),values.account(),values.authority(),()->Mono.just(new SigningAuditedPhase.PlanV3<>(values.evidence(),receipt->Mono.error(new SecurityException("private refusal"))))).block());assertEquals(CleanupFact.COMPLETE,error.cleanupFact());assertEquals(1,f.driver.rollbacks);assertEquals(0,f.driver.commits);assertTrue(f.driver.tables.isEmpty());}
    void refused(){assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,writes.get());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());}
}
