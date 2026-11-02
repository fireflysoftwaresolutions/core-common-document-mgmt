/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;

import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.interfaces.upload.*;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceUploadAdmissionImpl;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.core.authority.*;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Constructed JWT projection isolates admission binding; native decoder and owner persistence are separate gates. */
class AuthenticatedEvidenceUploadAdmissionTest {
    final Clock clock=Clock.systemUTC();final UUID tenant=UUID.randomUUID(),binding=UUID.randomUUID(),party=UUID.randomUUID();
    final EvidenceUploadRequest request=new EvidenceUploadRequest(UUID.randomUUID(),UUID.randomUUID(),party,"proof.pdf","application/pdf","IDENTITY",new byte[]{1,2,3});
    final EvidenceUploadSettings settings=new EvidenceUploadSettings("https://issuer.invalid","document-core","domain-lending","platform",tenant,binding,1,"a".repeat(64),clock.instant().plusSeconds(60));
    final java.util.concurrent.atomic.AtomicBoolean policyLive=new java.util.concurrent.atomic.AtomicBoolean(true);
    final AuthenticatedEvidenceUploadAdmissionImpl authority=new AuthenticatedEvidenceUploadAdmissionImpl(settings,(principal,action,resource,facts)->
        Mono.just(new PolicyEvaluation(policyLive.get()?Decision.permit():Decision.deny("revoked"),Optional.of(new PolicyEvaluationProvenance("evidence-policy-1","decision")))),clock,System::nanoTime);
    @Test void absentAuthenticationCannotReachTheOwner(){
        var calls=new AtomicInteger();StepVerifier.create(authority.invoke(request,value->{calls.incrementAndGet();return Mono.never();})).expectError().verify();assertThat(calls).hasValue(0);
    }
    @Test void signedPartyAndAllowedClientAreRequiredBeforeOwnerDispatch(){
        for(var authentication:List.of(authentication(UUID.randomUUID(),"domain-lending"),authentication(party,"another-client"))){
            var calls=new AtomicInteger();StepVerifier.create(authority.invoke(request,value->{calls.incrementAndGet();return Mono.never();})
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))).expectError().verify();assertThat(calls).hasValue(0);
        }
    }
    @Test void callerMintedIntentCannotCreateAnAdmissionOutsideThePrivateExecution(){
        StepVerifier.create(authority.requireCurrent(UploadedEvidenceIntent.capture(request)).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,"domain-lending")))).expectError().verify();
    }
    @Test void replacingAuthenticationWithEqualClaimsCannotRevalidateTheOriginalPermit(){
        var original=authentication(party,"domain-lending");var replacement=new JwtAuthenticationToken(original.getToken(),original.getPrincipal(),original.getAuthorities());
        StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(UploadedEvidenceIntent.capture(value))
            .flatMap(permit->authority.revalidate(permit).contextWrite(ReactiveSecurityContextHolder.withAuthentication(replacement))).thenReturn(receipt()))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(original))).expectError().verify();
    }
    @Test void oneExactCurrentExecutionCanAcquireAndRevalidateOnePermit(){
        StepVerifier.create(authority.invoke(request,value->authority.requireCurrent(UploadedEvidenceIntent.capture(value))
            .flatMap(permit->authority.revalidate(permit).thenReturn(receipt())))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(party,"domain-lending"))))
            .expectNextCount(1).verifyComplete();
    }
    JwtAuthenticationToken authentication(UUID signedParty,String client){
        var now=clock.instant();var jwt=Jwt.withTokenValue("constructed-not-signature-proof").header("alg","RS256").issuer(settings.issuer()).subject("opaque:human")
            .audience(List.of(settings.audience())).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(30)).claim("client_id",client).claim("principal_kind","HUMAN")
            .claim("tenant_id",settings.platformTenant()).claim("party_id",signedParty.toString()).claim("scope",AuthenticatedEvidenceUploadAdmissionImpl.ACTION).build();
        var factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));
        var principal=factory.fromClaims(jwt.getSubject(),jwt.getIssuer().toString(),jwt.getClaims()).toBuilder().tenantId(settings.platformTenant()).build();
        return new JwtAuthenticationToken(jwt,principal,List.of());
    }
    EvidenceUploadReceipt receipt(){return new EvidenceUploadReceipt(request.actionId(),UUID.randomUUID(),request.applicationId(),party,tenant,"a".repeat(64),3,"b".repeat(64),request.fileName(),request.mediaType(),request.documentTypeCode(),clock.instant(),false);}
}
