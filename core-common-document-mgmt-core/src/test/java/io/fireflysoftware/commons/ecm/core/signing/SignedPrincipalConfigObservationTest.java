package io.fireflysoftware.commons.ecm.core.signing;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.fireflyframework.security.core.authority.ConfigurableAuthorityMapper;
import org.fireflyframework.security.core.context.PrincipalFactory;
import org.fireflyframework.security.core.tenant.ConfigurableTenantResolver;
import org.fireflyframework.security.rs.JwtToFireflyPrincipalConverter;
import org.fireflyframework.security.webflux.context.ReactorSecurityContextAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

/** Actual signature/issuer/audience decoding and framework context projection; no network IdP/JWKS or provider is contacted. */
class SignedPrincipalConfigObservationTest {
    static KeyPair key()throws GeneralSecurityException {var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();}
    static String token(KeyPair key,String issuer,String audience)throws JOSEException {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject("opaque:user:42").audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(1))).expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .claim("client_id","actual-user-client").claim("tenant_ids",List.of(GeneratedConfigSigningTenantBindingResolverTest.PLATFORM)).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("source-fixture-only").build(),claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey)key.getPrivate()));return jwt.serialize();
    }
    static NimbusReactiveJwtDecoder decoder(KeyPair key){
        var decoder=NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey)key.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(GeneratedConfigSigningTenantBindingResolverTest.ISSUER),
                new JwtClaimValidator<List<String>>("aud",values->values!=null&&values.contains("core-documents"))));return decoder;
    }
    static JwtToFireflyPrincipalConverter converter(){return new JwtToFireflyPrincipalConverter(new PrincipalFactory(new ConfigurableAuthorityMapper(null)),new ConfigurableTenantResolver(List.of("tenant_ids")),true);}
    @Test void aRealValidatedJwtProjectsOpaqueActorIntoTheGenuineSdkMachineCall()throws Exception {
        var key=key();String signed=token(key,GeneratedConfigSigningTenantBindingResolverTest.ISSUER,"core-documents");
        var authentication=decoder(key).decode(signed).flatMap(converter()::convert).block(Duration.ofSeconds(3));assertNotNull(authentication);assertEquals("",authentication.getCredentials());
        var transport=new GeneratedConfigSigningTenantBindingResolverTest.Fixture();Instant now=Instant.now();transport.clock.now.set(now);
        transport.response.set(GeneratedConfigSigningTenantBindingResolverTest.current()
                .replace(Long.toString(GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(120).toEpochMilli()),Long.toString(now.plusSeconds(120).toEpochMilli()))
                .replace(Long.toString(GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(5).toEpochMilli()),Long.toString(now.plusSeconds(5).toEpochMilli()))
                .replace(Long.toString(GeneratedConfigSigningTenantBindingResolverTest.NOW.toEpochMilli()),Long.toString(now.toEpochMilli())));
        var observer=new GeneratedConfigSigningTenantBindingResolver(new ReactorSecurityContextAdapter(),transport.manager,transport.configClient,
                GeneratedConfigSigningTenantBindingResolverTest.CONFIG,"configuration-current",GeneratedConfigSigningTenantBindingResolverTest.ISSUER,
                GeneratedConfigSigningTenantBindingResolverTest.PLATFORM,transport.clock);
        var result=observer.observe(now.plusSeconds(4)).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals("opaque:user:42",result.actor().subject());assertNull(result.actor().staffId());
        assertEquals(GeneratedConfigSigningTenantBindingResolverTest.RESOURCE,result.resourceTenantId());assertEquals(GeneratedConfigSigningTenantBindingResolverTest.PLATFORM,result.actor().platformTenantId());
        assertEquals(1,transport.tokens.get());assertEquals(1,transport.sdkCalls.get());assertNotEquals(signed,transport.token.get());
        assertThrows(SecurityException.class,()->observer.observe(now.plusSeconds(4)).block());
    }
    @ParameterizedTest @ValueSource(strings={"issuer","audience"})
    void issuerOrAudienceRefusalCannotReachTheObserverOrMachineClient(String wrong)throws Exception {
        var key=key();var transport=new GeneratedConfigSigningTenantBindingResolverTest.Fixture();
        String signed=token(key,"issuer".equals(wrong)?"https://foreign.example.test":GeneratedConfigSigningTenantBindingResolverTest.ISSUER,
                "audience".equals(wrong)?"foreign-audience":"core-documents");
        var observer=new GeneratedConfigSigningTenantBindingResolver(new ReactorSecurityContextAdapter(),transport.manager,transport.configClient,
                GeneratedConfigSigningTenantBindingResolverTest.CONFIG,"configuration-current",GeneratedConfigSigningTenantBindingResolverTest.ISSUER,
                GeneratedConfigSigningTenantBindingResolverTest.PLATFORM,transport.clock);
        Mono<SigningTenantBindingObservationPort.Observation> flow=decoder(key).decode(signed).flatMap(converter()::convert)
                .flatMap(authentication->observer.observe(GeneratedConfigSigningTenantBindingResolverTest.NOW.plusSeconds(4))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)));
        assertThrows(JwtException.class,()->flow.block(Duration.ofSeconds(3)));assertEquals(0,transport.tokens.get());assertEquals(0,transport.sdkCalls.get());
    }
}
