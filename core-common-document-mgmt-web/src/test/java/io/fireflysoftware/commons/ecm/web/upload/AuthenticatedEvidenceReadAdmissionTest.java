/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;
import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.interfaces.upload.EvidenceUploadReceipt;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceReadAdmissionImpl;
import java.time.*;
import java.util.*;
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

/** Current context and read-purpose boundary only; constructed JWT is not a native decoder/provider proof. */
class AuthenticatedEvidenceReadAdmissionTest {
    final Clock clock=Clock.systemUTC();final UUID app=UUID.randomUUID(),action=UUID.randomUUID(),party=UUID.randomUUID(),tenant=UUID.randomUUID(),binding=UUID.randomUUID();
    final EvidenceUploadSettings settings=new EvidenceUploadSettings("https://issuer.invalid","document-core","domain-lending","platform",tenant,binding,1,"a".repeat(64),clock.instant().plusSeconds(60));
    final AuthenticatedEvidenceReadAdmissionImpl authority=new AuthenticatedEvidenceReadAdmissionImpl(settings,(principal,operation,resource,facts)->Mono.just(new PolicyEvaluation(Decision.permit(),Optional.of(new PolicyEvaluationProvenance("read-policy-1","decision")))),clock,System::nanoTime);
    @Test void nativeSignedPartyAndPathChooseTheReadIdentity(){
        StepVerifier.create(authority.invoke(app,action,identity->{assertThat(identity).isEqualTo(new EvidenceReadIdentity(action,app,party));return authority.requireCurrent(identity).flatMap(permit->authority.revalidate(permit).thenReturn(content()));})
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(AuthenticatedEvidenceReadAdmissionImpl.ACTION)))).expectNextCount(1).verifyComplete();
    }
    @Test void ambientAuthenticationCannotMintAReadPermit(){StepVerifier.create(authority.requireCurrent(new EvidenceReadIdentity(action,app,party)).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication(AuthenticatedEvidenceReadAdmissionImpl.ACTION)))).expectError().verify();}
    @Test void equalClaimsAuthenticationCannotReplaceTheOriginalReadCaller(){
        var original=authentication(AuthenticatedEvidenceReadAdmissionImpl.ACTION);var replacement=new JwtAuthenticationToken(original.getToken(),original.getPrincipal(),original.getAuthorities());
        StepVerifier.create(authority.invoke(app,action,identity->authority.requireCurrent(identity).flatMap(permit->authority.revalidate(permit).contextWrite(ReactiveSecurityContextHolder.withAuthentication(replacement)).thenReturn(content())))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(original))).expectError().verify();
    }
    @Test void anUploadScopeDoesNotBecomeReadAuthority(){
        StepVerifier.create(authority.invoke(app,action,identity->{throw new AssertionError("Upload authority cannot dispatch a read");})
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication("document:evidence:upload")))).expectError().verify();
    }
    JwtAuthenticationToken authentication(String scope){var now=clock.instant();var jwt=Jwt.withTokenValue("constructed-not-signature-proof").header("alg","RS256").issuer(settings.issuer()).subject("opaque:human").audience(List.of(settings.audience())).notBefore(now.minusSeconds(1)).expiresAt(now.plusSeconds(30))
        .claim("client_id",settings.allowedClientId()).claim("principal_kind","HUMAN").claim("tenant_id",settings.platformTenant()).claim("party_id",party.toString()).claim("scope",scope).build();
        var factory=new PrincipalFactory(new ConfigurableAuthorityMapper(new AuthorityMappingProperties(List.of("roles"),List.of("scope"),"ROLE_")));return new JwtAuthenticationToken(jwt,factory.fromClaims(jwt.getSubject(),jwt.getIssuer().toString(),jwt.getClaims()).toBuilder().tenantId(settings.platformTenant()).build(),List.of());}
    EvidenceContentRead content(){byte[] bytes={1,2,3};return new EvidenceContentRead(new EvidenceUploadReceipt(action,UUID.randomUUID(),app,party,tenant,UploadedEvidenceIntent.hash(bytes),3,"a".repeat(64),"proof.pdf","application/pdf","IDENTITY",clock.instant(),true),bytes);}
}
