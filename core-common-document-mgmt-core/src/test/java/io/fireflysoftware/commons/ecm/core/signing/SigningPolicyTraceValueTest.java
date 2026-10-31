package io.fireflysoftware.commons.ecm.core.signing;

import java.util.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.fireflyframework.kernel.audit.AuditEvent;
import org.fireflyframework.security.api.domain.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningPolicyTraceValueTest {
    PolicyEvaluationProvenance provenance(String revision,String id){return new PolicyEvaluationProvenance(revision,id);}
    SigningPolicyTraceV1 trace(){return new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,
            provenance("policy-actual-7","decision-original-1"),provenance("policy-actual-7","decision-phase-2"));}
    @Test void canonicalTraceHasIndependentGoldenHashAndDefensiveBytes(){
        var value=trace();assertEquals("0f095213bc84286f60f87496c7f6a36d45a915f2955c11b2ce93b3acc56d38cf",value.digest());
        byte[] bytes=value.canonicalBytes();assertEquals(199,bytes.length);Arrays.fill(bytes,(byte)0);
        assertEquals("0f095213bc84286f60f87496c7f6a36d45a915f2955c11b2ce93b3acc56d38cf",value.digest());
    }
    @Test void checkedTraceKeepsTheExactSameResponseProvenanceAndRefusesUnsupportedDecisions(){
        var a=provenance("policy-actual-7","decision-original-1");var b=provenance("policy-actual-7","decision-phase-2");
        var allowed=new PolicyEvaluation(Decision.permit(),Optional.of(a));var current=new PolicyEvaluation(Decision.permit(),Optional.of(b));
        var value=SigningPolicyTraceV1.checked(SigningPolicyTraceV1.Phase.RESERVE,allowed,current);assertSame(a,value.authorization());assertSame(b,value.admission());
        for(var bad:List.of(new PolicyEvaluation(Decision.deny("PRIVATE-REASON"),Optional.of(a)),new PolicyEvaluation(Decision.indeterminate("PRIVATE-REASON"),Optional.of(a)),
                new PolicyEvaluation(Decision.permit(List.of(Obligation.of("unfulfilled",Map.of("PRIVATE","SECRET")))),Optional.of(a)),new PolicyEvaluation(Decision.permit(),Optional.empty()))){
            var error=assertThrows(IllegalArgumentException.class,()->SigningPolicyTraceV1.checked(SigningPolicyTraceV1.Phase.RESERVE,bad,current));assertFalse(error.toString().contains("PRIVATE"));assertNull(error.getCause());
        }
    }
    @Test void opaqueDecisionIdsKeepUnicodeAndSpacesWhileMalformedUtf8AndBoundsRefuse(){
        String opaque="opaque decision:α/🔒\u2028suffix";var p=provenance("policy-actual-7",opaque);var value=new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,p,p);
        assertEquals(opaque,value.authorization().decisionId());assertNotEquals(value.digest(),trace().digest());
        var other=provenance("policy-actual-7",opaque.replace('α','β'));assertNotEquals(value.digest(),new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,other,other).digest());
        var maximum=provenance("policy-actual-7","x".repeat(256));assertDoesNotThrow(()->new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,maximum,maximum));
        for(String id:List.of("\ud800","a\udfff","\ud800\ud800")){
            var malformed=provenance("policy-actual-7",id);assertThrows(IllegalArgumentException.class,()->new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,malformed,p));
        }
        assertThrows(IllegalArgumentException.class,()->provenance("policy-actual-7","x".repeat(257)));
        var unsupportedRevision=provenance("opaque policy revision","id");assertThrows(IllegalArgumentException.class,()->new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,unsupportedRevision,p));
    }
    @Test void everyProvenanceFieldRoleAndOrderChangesTheCommitment(){
        var original=trace();var auth=original.authorization();var admission=original.admission();
        for(var changed:List.of(new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.PREPARED,auth,admission),new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,admission,auth),
                new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,provenance("policy-new","decision-original-1"),admission),
                new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,provenance("policy-actual-7","different-original"),admission),
                new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,auth,provenance("policy-new","decision-phase-2")),
                new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,auth,provenance("policy-actual-7","different-phase"))))assertNotEquals(original.digest(),changed.digest());
    }
    @Test void v3ChangesOnlyEvidenceDigestAndDoesNotRewriteTheV2Event(){
        var f=new SigningAuditedPhaseTest();f.setup();var base=f.evidence();var permit=f.permit();var p=provenance(permit.policyRevision(),"actual-decision");
        var value=new SigningAuditEvidenceV3(base,new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,p,p));
        var old=base.event(permit,f.clock.instant());var current=value.event(permit,f.clock.instant());assertNotEquals(old.evidenceDigest(),current.evidenceDigest());
        assertEquals(old,new AuditEvent(current.eventId(),current.stream(),current.actor(),current.action(),current.outcome(),current.processId(),current.correlationId(),current.causationId(),current.occurredAt(),old.evidenceDigest(),current.policyRevision()));
        assertEquals(old,base.event(permit,f.clock.instant()));assertEquals(value.digest(permit.resourceTenantId()),current.evidenceDigest());
    }
    @Test void v3PhaseRevisionAndAccidentalSerializationFailClosed()throws Exception{
        var f=new SigningAuditedPhaseTest();f.setup();var p=provenance(f.permit().policyRevision(),"PRIVATE-DECISION");var wrongPhase=new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.PREPARED,p,p);
        assertThrows(IllegalArgumentException.class,()->new SigningAuditEvidenceV3(f.evidence(),wrongPhase));
        var wrongRevision=new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,p,provenance("foreign-policy","PRIVATE-DECISION"));var wrapper=new SigningAuditEvidenceV3(f.evidence(),wrongRevision);
        assertThrows(IllegalArgumentException.class,()->wrapper.event(f.permit(),f.clock.instant()));
        var json=JsonMapper.builder().build();assertFalse(json.writeValueAsString(wrapper).contains("PRIVATE"));assertFalse(json.writeValueAsString(wrongPhase).contains("PRIVATE"));assertFalse(wrapper.toString().contains("PRIVATE"));assertFalse(wrongPhase.toString().contains("PRIVATE"));
    }
}
