package io.fireflysoftware.commons.ecm.core.signing;

import io.fireflysoftware.commons.ecm.models.entities.signing.SigningPolicyCustody;
import java.util.UUID;
import org.fireflyframework.security.api.domain.PolicyEvaluationProvenance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningPolicyCustodyValueTest {
    static SigningPolicyTraceV1 trace(String id){return new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,
            new PolicyEvaluationProvenance("policy-actual-7",id),new PolicyEvaluationProvenance("policy-actual-7","admission:\u2028\ud83d\ude00"));}
    static SigningPolicyCustody row(String id){var t=trace(id);return new SigningPolicyCustody(new UUID(1,1),"tenant",new UUID(2,2),new UUID(3,3),new UUID(4,4),"RESERVE",3,
            t.authorization().policyRevision(),id,t.admission().policyRevision(),t.admission().decisionId(),"a".repeat(64),t.digest());}
    @Test void exactOpaqueIdsAndOriginalRevisionsAreImmutableMappedValues(){var value=row("original: \u2029\ud83d\ude00");assertEquals("original: \u2029\ud83d\ude00",value.authorizationDecisionId());assertEquals(3,value.protocol());assertEquals(trace(value.authorizationDecisionId()).digest(),value.traceDigest());assertFalse(value.toString().contains(value.authorizationDecisionId()));}
    @Test void malformedControlBlankAndUtf16OversizedIdsRefuse(){for(String id:new String[]{"", "\u2000", "bad\n", "\ud800", "x".repeat(257),"\ud83d\ude00".repeat(129)})assertThrows(IllegalArgumentException.class,()->row(id));assertNotNull(row("\ud83d\ude00".repeat(128)));}
    @Test void forgedTraceDigestCannotBeMappedAsOriginalCustody(){var x=row("original");assertThrows(IllegalArgumentException.class,()->new SigningPolicyCustody(x.id(),x.tenantId(),x.resourceTenantId(),x.operationId(),x.eventId(),x.phase(),3,x.authorizationRevision(),x.authorizationDecisionId(),x.admissionRevision(),x.admissionDecisionId(),x.businessDigest(),"f".repeat(64)));}
}
