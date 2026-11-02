package io.fireflysoftware.commons.ecm.core.signing;
import org.fireflyframework.security.api.domain.PolicyEvaluationProvenance;
public final class SigningPolicyCustodyFixture {
    private final SpringDataSigningPreparationStoreTest f=new SpringDataSigningPreparationStoreTest();
    public SigningDraftIntent intent(){return f.intent;}
    public SigningAccountScope account(){return f.account;}
    private final SigningAuthority current;
    public SigningPolicyCustodyFixture(){this(java.time.Clock.systemUTC());}
    public SigningPolicyCustodyFixture(java.time.Clock clock){var a=f.authority;var b=a.binding();var until=clock.instant().plusSeconds(5);current=new SigningAuthority(a.actor(),new SigningTenantBinding(b.bindingId(),b.version(),b.definitionDigest(),b.issuer(),b.subject(),b.platformTenant(),b.resourceTenant(),until),until);}
    public SigningAuthority authority(){return current;}
    public SigningAuditAdmission.Permit permit(){var b=f.authority.binding();return new SigningAuditAdmission.Permit(SigningDraftIntentTest.id(100),f.intent.tenantId(),b.bindingId(),b.version(),b.definitionDigest(),f.authority.actor(),"policy-actual-7",SigningDraftIntentTest.id(101),SigningDraftIntentTest.id(102),current.notAfter());}
    public SigningPolicyTraceV1 trace(){return new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,new PolicyEvaluationProvenance("policy-actual-7","original: \u2028\ud83d\ude00"),new PolicyEvaluationProvenance("policy-actual-7","admission-original"));}
    public SigningAuditEvidence evidence(){return new SigningAuditEvidence(SigningDraftIntentTest.id(20),SigningDraftIntentTest.id(21),f.intent,f.account,"b".repeat(64),"c".repeat(64),null,SigningDraftIntentTest.id(30),null);}
}
