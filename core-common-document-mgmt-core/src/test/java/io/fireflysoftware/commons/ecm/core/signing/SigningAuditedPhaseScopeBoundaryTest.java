package io.fireflysoftware.commons.ecm.core.signing;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.fireflyframework.kernel.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningAuditedPhaseScopeBoundaryTest {
    @Test void aTrustedPhaseScopeIsEnteredBeforeAnyOwnedRootWork(){
        var f=new SigningAuditedPhaseTest();f.setup();var entered=new AtomicInteger();
        doAnswer(call->{assertEquals(0,f.creates.get());entered.incrementAndGet();Supplier<Mono<Object>> work=call.getArgument(1);return Mono.defer(work);}).when(f.admission).withinPhase(any(),any());
        assertEquals("reserved",f.execute().block());assertEquals(1,entered.get());assertEquals(1,f.commits.get());
    }
    @Test void missingPrivatePhaseAdmissionRefusesBeforeAcquire(){
        var f=new SigningAuditedPhaseTest();f.setup();doReturn(Mono.error(new SecurityException("controlled"))).when(f.admission).withinPhase(any(),any());
        assertThrows(SecurityException.class,()->f.execute().block());assertEquals(0,f.creates.get());assertEquals(0,f.writes.get());
    }
    @Test void anUnadmittedExpectedAppendCannotAppendOrMutateBusiness(){
        var f=new SigningAuditedPhaseTest();f.setup();doReturn(Mono.error(new SecurityException("controlled"))).when(f.admission).withinExpectedAppend(any(),any(),any(),any());
        assertThrows(RuntimeException.class,()->f.execute().block());verifyNoInteractions(f.append);assertEquals(0,f.writes.get());assertEquals(1,f.rollbacks.get());
    }
    @Test void exactOwnerEvidenceAndEventAreBoundAtTheRealAppendBoundary(){
        var f=new SigningAuditedPhaseTest();f.setup();var checks=new AtomicInteger();
        doAnswer(call->{
            SigningAuditAdmission.Permit permit=call.getArgument(0);SigningAuditEvidence evidence=call.getArgument(1);AuditEvent event=call.getArgument(2);
            assertEquals(evidence.event(permit,event.occurredAt()),event);assertEquals(f.fixture.intent,evidence.intent());assertEquals(f.fixture.account,evidence.account());
            assertEquals("signing.prepare.reserve",event.action());assertEquals("signing-operation",event.stream().resourceType());
            checks.incrementAndGet();Supplier<Mono<Object>> work=call.getArgument(3);return Mono.defer(work);
        }).when(f.admission).withinExpectedAppend(any(),any(),any(),any());
        assertEquals("reserved",f.execute().block());assertEquals(1,checks.get());assertEquals(1,f.writes.get());
    }
}
