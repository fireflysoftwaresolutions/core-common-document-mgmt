package io.fireflysoftware.commons.ecm.core.signing;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeAccountBinding;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.adapter.opa.OpaPolicyEvaluationAdapter;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class CurrentSigningPreparationPolicyTest {
    static final Instant NOW=GeneratedConfigSigningTenantBindingResolverTest.NOW;
    static final UUID RESOURCE=GeneratedConfigSigningTenantBindingResolverTest.RESOURCE;
    static SigningDraftIntent intent(){return new SigningDraftIntent(new UUID(61,62),new UUID(63,64),new UUID(65,66),new UUID(67,68),RESOURCE.toString(),null,"Synthetic Signer","signer@example.test",1,2,1,20,10,"en","Signer",null,"EMAIL");}
    static SigningAccountScope account(){return new SigningAccountScope(new UUID(69,70),3,intent().providerId(),RESOURCE.toString(),new SignatureEnvelopeAccountBinding(SignatureProvider.DOCUSIGN,"server-account","https://docusign.example.test/restapi"));}
    static SigningTenantBindingObservationPort.Observation observation(){var p=GeneratedConfigSigningTenantBindingResolverTest.principal(GeneratedConfigSigningTenantBindingResolverTest.ISSUER,GeneratedConfigSigningTenantBindingResolverTest.PLATFORM);return new SigningTenantBindingObservationPort.Observation(RESOURCE,new UUID(71,72),7,"a".repeat(64),new AuditActor(p.issuer(),p.subject(),"actual-user-client",p.tenantId(),null,null),NOW.plusSeconds(5));}
    @Test void genuineOpaSameResponseRevisionAndDecisionAreRequiredAndRetained(){
        var p=GeneratedConfigSigningTenantBindingResolverTest.principal(GeneratedConfigSigningTenantBindingResolverTest.ISSUER,GeneratedConfigSigningTenantBindingResolverTest.PLATFORM);
        var calls=new AtomicInteger();var engine=new OpaPolicyEvaluationAdapter(WebClient.builder().baseUrl("https://policy.example.test").exchangeFunction(request->{
            calls.incrementAndGet();assertEquals(HttpMethod.POST,request.method());assertEquals("/v1/data/core/signing/prepare",request.url().getPath());
            return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE,"application/json").body("{\"result\":{\"allow\":true,\"policy_revision\":\"actual-policy-7\"},\"decision_id\":\"actual-decision-9\"}").build());
        }).build(),"core/signing/prepare");
        var result=new CurrentSigningPreparationPolicy(()->Mono.just(p),engine,Clock.fixed(NOW,ZoneOffset.UTC)).requireCurrent(p,intent(),account(),observation(),NOW.plusSeconds(4)).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals("actual-policy-7",result.provenance().orElseThrow().policyRevision());assertEquals("actual-decision-9",result.provenance().orElseThrow().decisionId());assertEquals(1,calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"deny","missing-provenance","obligation","unsupported-revision","empty"})
    void noFallbackCanTurnAnIncompleteEvaluationIntoAuthority(String kind){
        var p=GeneratedConfigSigningTenantBindingResolverTest.principal(GeneratedConfigSigningTenantBindingResolverTest.ISSUER,GeneratedConfigSigningTenantBindingResolverTest.PLATFORM);
        PolicyEvaluationPort engine=(principal,action,resource,facts)->{
            assertEquals("document.signing.prepare",action);assertEquals("document:"+intent().documentId(),resource);
            assertEquals(RESOURCE,facts.get("resourceTenantId"));assertEquals(observation().bindingId(),facts.get("configurationBindingId"));
            assertFalse(facts.values().contains("Synthetic Signer"));assertFalse(facts.values().contains("signer@example.test"));
            if("empty".equals(kind))return Mono.empty();
            var decision="deny".equals(kind)?Decision.deny("not-authorized"):"obligation".equals(kind)?Decision.permit(List.of(new Obligation("unsupported",Map.of()))):Decision.permit();
            return Mono.just(new PolicyEvaluation(decision,"missing-provenance".equals(kind)?Optional.empty():Optional.of(new PolicyEvaluationProvenance("unsupported-revision".equals(kind)?"unsafe revision":"policy-1","decision-1"))));
        };
        var policy=new CurrentSigningPreparationPolicy(()->Mono.just(p),engine,Clock.fixed(NOW,ZoneOffset.UTC));
        var error=assertThrows(SecurityException.class,()->policy.requireCurrent(p,intent(),account(),observation(),NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        assertEquals("Current signing preparation policy unavailable",error.getMessage());assertNull(error.getCause());
    }
    @Test void currentPrincipalAndMappedResourceAreRecheckedBeforePolicyIo(){
        var p=GeneratedConfigSigningTenantBindingResolverTest.principal(GeneratedConfigSigningTenantBindingResolverTest.ISSUER,GeneratedConfigSigningTenantBindingResolverTest.PLATFORM);var calls=new AtomicInteger();
        PolicyEvaluationPort engine=(principal,action,resource,facts)->{calls.incrementAndGet();return Mono.just(new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("policy-1","decision-1"))));};
        var foreign=SecurityPrincipal.builder().issuer(p.issuer()).subject("foreign").tenantId(p.tenantId()).claims(p.claims()).build();
        assertThrows(SecurityException.class,()->new CurrentSigningPreparationPolicy(()->Mono.just(foreign),engine,Clock.fixed(NOW,ZoneOffset.UTC)).requireCurrent(p,intent(),account(),observation(),NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        var mapped=observation();var wrong=new SigningTenantBindingObservationPort.Observation(new UUID(81,82),mapped.bindingId(),7,mapped.definitionDigest(),mapped.actor(),mapped.notAfter());
        assertThrows(SecurityException.class,()->new CurrentSigningPreparationPolicy(()->Mono.just(p),engine,Clock.fixed(NOW,ZoneOffset.UTC)).requireCurrent(p,intent(),account(),wrong,NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        assertEquals(0,calls.get());
    }
    @Test void expiryOrIdentityChangeDuringEvaluationCannotPublishAUsableDecision(){
        var p=GeneratedConfigSigningTenantBindingResolverTest.principal(GeneratedConfigSigningTenantBindingResolverTest.ISSUER,GeneratedConfigSigningTenantBindingResolverTest.PLATFORM);
        var clock=new GeneratedConfigSigningTenantBindingResolverTest.MovingClock();var current=new AtomicReference<>(p);
        PolicyEvaluationPort engine=(principal,action,resource,facts)->{clock.now.set(NOW.plusSeconds(4));return Mono.just(new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("policy-1","decision-1"))));};
        assertThrows(SecurityException.class,()->new CurrentSigningPreparationPolicy(()->Mono.just(current.get()),engine,clock).requireCurrent(p,intent(),account(),observation(),NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        clock.now.set(NOW);PolicyEvaluationPort switched=(principal,action,resource,facts)->{current.set(SecurityPrincipal.builder().issuer(p.issuer()).subject("foreign").tenantId(p.tenantId()).claims(p.claims()).build());return Mono.just(new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("policy-1","decision-1"))));};
        assertThrows(SecurityException.class,()->new CurrentSigningPreparationPolicy(()->Mono.just(current.get()),switched,clock).requireCurrent(p,intent(),account(),observation(),NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
    }
}
