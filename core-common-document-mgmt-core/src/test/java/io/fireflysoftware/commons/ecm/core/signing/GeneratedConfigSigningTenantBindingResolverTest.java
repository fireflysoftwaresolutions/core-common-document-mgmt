package io.fireflysoftware.commons.ecm.core.signing;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedConfigSigningTenantBindingResolverTest {
    static final Instant NOW=Instant.parse("2026-10-31T12:00:00Z");
    static final UUID RESOURCE=new UUID(31,32),BINDING=new UUID(33,34);
    static final String ISSUER="https://issuer.example.test",PLATFORM="opaque-platform-tenant";
    static final URI CONFIG=URI.create("https://configuration.example.test");
    static SecurityPrincipal principal(String issuer,String tenant){
        return SecurityPrincipal.builder().issuer(issuer).subject("opaque:user:42").tenantId(tenant)
                .claims(Map.of("client_id","actual-user-client","exp",NOW.plusSeconds(60))).build();
    }
    static String current(){
        return "{\"reference\":{\"bindingId\":\""+BINDING+"\",\"version\":7,\"definitionDigest\":\""+"a".repeat(64)+"\"},"
                +"\"definition\":{\"bindingId\":\""+BINDING+"\",\"namespace\":{\"issuer\":\""+ISSUER+"\",\"platformTenant\":\""+PLATFORM+"\"},"
                +"\"resourceTenant\":{\"value\":\""+RESOURCE+"\"},\"version\":7,\"eligibility\":{\"tenantGeneration\":4,\"statusId\":\""+new UUID(35,36)+"\",\"statusGeneration\":3},\"notAfterEpochMillis\":"+NOW.plusSeconds(120).toEpochMilli()+"},"
                +"\"controlVersion\":9,\"observedAtEpochMillis\":"+NOW.toEpochMilli()+",\"validUntilEpochMillis\":"+NOW.plusSeconds(5).toEpochMilli()+"}";
    }
    static final class MovingClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(NOW);
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){if(!ZoneOffset.UTC.equals(zone))throw new IllegalArgumentException();return this;}
        @Override public Instant instant(){return now.get();}
    }
    static final class Fixture {
        final MovingClock clock=new MovingClock();
        final AtomicInteger tokens=new AtomicInteger(),sdkCalls=new AtomicInteger();
        final AtomicReference<String> response=new AtomicReference<>(current());
        final AtomicReference<SecurityPrincipal> principal=new AtomicReference<>(GeneratedConfigSigningTenantBindingResolverTest.principal(ISSUER,PLATFORM));
        final AtomicReference<Runnable> atTokenResponse=new AtomicReference<>(()->{});
        final AtomicReference<Runnable> atResponse=new AtomicReference<>(()->{});
        final AtomicReference<String> token=new AtomicReference<>("synthetic-opaque-server-token");
        final ReactiveOAuth2AuthorizedClientManager manager;
        final WebClient configClient;
        Fixture(){
            var registration=ClientRegistration.withRegistrationId("configuration-current")
                    .clientId("machine-configuration-client").clientSecret("synthetic-fixture-only")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenUri("https://identity.example.test/token").scope("configuration.current").build();
            var registrations=new InMemoryReactiveClientRegistrationRepository(registration);
            var tokenClient=new WebClientReactiveClientCredentialsTokenResponseClient();
            tokenClient.setWebClient(WebClient.builder().exchangeFunction(request->{
                tokens.incrementAndGet();atTokenResponse.get().run();assertEquals(HttpMethod.POST,request.method());
                assertEquals(URI.create("https://identity.example.test/token"),request.url());
                assertTrue(request.headers().getFirst(HttpHeaders.AUTHORIZATION).startsWith("Basic "));
                return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE,"application/json")
                        .body("{\"access_token\":\""+token.get()+"\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"configuration.current\"}").build());
            }).build());
            var provider=new ClientCredentialsReactiveOAuth2AuthorizedClientProvider();provider.setAccessTokenResponseClient(tokenClient);
            var actual=new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(registrations,new InMemoryReactiveOAuth2AuthorizedClientService(registrations));
            actual.setAuthorizedClientProvider(provider);manager=actual;
            configClient=io.fireflysoftware.common.config.sdk.invoker.ApiClient.buildWebClientBuilder().exchangeFunction(request->{
                sdkCalls.incrementAndGet();assertEquals(HttpMethod.GET,request.method());
                assertEquals(URI.create("https://configuration.example.test/api/v1/tenant-resource-bindings/current"),request.url());
                assertEquals("Bearer "+token.get(),request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                assertNull(request.headers().getFirst("X-Tenant-Id"));assertNull(request.url().getQuery());
                atResponse.get().run();return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE,"application/json").body(response.get()).build());
            }).build();
        }
        GeneratedConfigSigningTenantBindingResolver resolver(){return new GeneratedConfigSigningTenantBindingResolver(
                ()->Mono.justOrEmpty(principal.get()),manager,configClient,CONFIG,"configuration-current",ISSUER,PLATFORM,clock);}
    }
    @Test void actualMachineAuthorizationAndGeneratedSdkReturnAuthoritativeTypedResource(){
        var f=new Fixture();var p=f.principal.get();var result=f.resolver().observe(NOW.plusSeconds(4)).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals(RESOURCE,result.resourceTenantId());assertEquals(BINDING,result.bindingId());
        assertEquals(7,result.bindingVersion());assertEquals("a".repeat(64),result.definitionDigest());
        assertEquals(p.subject(),result.actor().subject());assertEquals(PLATFORM,result.actor().platformTenantId());
        assertEquals(NOW.plusSeconds(4),result.notAfter());assertEquals(1,f.tokens.get());assertEquals(1,f.sdkCalls.get());
    }
    @Test void existingResolverApiUsesObservedUuidAndNeverParsesOpaqueTenant(){
        var f=new Fixture();var result=f.resolver().resolve(f.principal.get(),RESOURCE.toString()).block(Duration.ofSeconds(3));
        assertNotNull(result);assertEquals(RESOURCE.toString(),result.resourceTenant());assertEquals(PLATFORM,result.platformTenant());
        assertThrows(SecurityException.class,()->f.resolver().resolve(f.principal.get(),PLATFORM).block(Duration.ofSeconds(3)));
    }
    @ParameterizedTest @ValueSource(strings={"issuer","tenant","missing","expired"})
    void unavailableOrUnconfiguredCurrentPrincipalRefusesBeforeTokenOrSdk(String kind){
        var f=new Fixture();
        switch(kind){case "issuer"->f.principal.set(principal("https://foreign.example.test",PLATFORM));case "tenant"->f.principal.set(principal(ISSUER,"foreign"));case "missing"->f.principal.set(null);case "expired"->f.principal.set(SecurityPrincipal.builder().issuer(ISSUER).subject("opaque:user:42").tenantId(PLATFORM).claims(Map.of("client_id","actual-user-client","exp",NOW)).build());default->throw new IllegalArgumentException();}
        assertThrows(SecurityException.class,()->f.resolver().observe(NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        assertEquals(0,f.tokens.get());assertEquals(0,f.sdkCalls.get());
    }
    @ParameterizedTest @ValueSource(strings={"namespace","binding","version","digest","zero-resource","expired","future-observation","overlong-window","business-expired","eligibility"})
    void malformedOrConflictingAuthoritativeResponseCannotBecomeAuthority(String kind){
        var f=new Fixture();String value=current();
        value=switch(kind){
            case "namespace"->value.replace(PLATFORM,"foreign");
            case "binding"->value.replaceFirst(BINDING.toString(),new UUID(51,52).toString());
            case "version"->value.replaceFirst("\"version\":7","\"version\":8");
            case "digest"->value.replace("a".repeat(64),"not-a-digest");
            case "zero-resource"->value.replace(RESOURCE.toString(),new UUID(0,0).toString());
            case "expired"->value.replace("\"validUntilEpochMillis\":"+NOW.plusSeconds(5).toEpochMilli(),"\"validUntilEpochMillis\":"+NOW.toEpochMilli());
            case "future-observation"->value.replace("\"observedAtEpochMillis\":"+NOW.toEpochMilli(),"\"observedAtEpochMillis\":"+NOW.plusMillis(1).toEpochMilli());
            case "overlong-window"->value.replace("\"validUntilEpochMillis\":"+NOW.plusSeconds(5).toEpochMilli(),"\"validUntilEpochMillis\":"+NOW.plusSeconds(6).toEpochMilli());
            case "business-expired"->value.replace("\"notAfterEpochMillis\":"+NOW.plusSeconds(120).toEpochMilli(),"\"notAfterEpochMillis\":"+NOW.toEpochMilli());
            case "eligibility"->value.replace("\"tenantGeneration\":4","\"tenantGeneration\":-1");
            default->throw new IllegalArgumentException();};
        f.response.set(value);var error=assertThrows(SecurityException.class,()->f.resolver().observe(NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        assertEquals("Current trusted signing tenant binding unavailable",error.getMessage());assertNull(error.getCause());assertEquals(1,f.sdkCalls.get());
    }
    @Test void exactOriginalDeadlineIsCheckedAfterActualSdkResponse(){
        var f=new Fixture();f.atResponse.set(()->f.clock.now.set(NOW.plusSeconds(4)));
        assertThrows(SecurityException.class,()->f.resolver().observe(NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        assertEquals(1,f.sdkCalls.get());
    }
    @Test void callerPrincipalCannotSubstituteForCurrentTrustedPrincipal(){
        var f=new Fixture();var foreign=SecurityPrincipal.builder().issuer(ISSUER).subject("foreign-subject").tenantId(PLATFORM).claims(Map.of("client_id","actual-user-client","exp",NOW.plusSeconds(60))).build();
        assertThrows(SecurityException.class,()->f.resolver().resolve(foreign,RESOURCE.toString()).block(Duration.ofSeconds(3)));
        assertEquals(0,f.tokens.get());assertEquals(0,f.sdkCalls.get());
    }
    @Test void onlyConfiguredHttpsEndpointCanReceiveTheMachineCredential(){
        var f=new Fixture();assertThrows(IllegalArgumentException.class,()->new GeneratedConfigSigningTenantBindingResolver(
                ()->Mono.just(f.principal.get()),f.manager,f.configClient,URI.create("http://configuration.example.test"),"configuration-current",ISSUER,PLATFORM,f.clock));
        assertEquals(0,f.tokens.get());assertEquals(0,f.sdkCalls.get());
    }
    @Test void latePrincipalCannotStartTokenOrSdkIo(){
        var f=new Fixture();SecurityContextPort delayed=()->Mono.defer(()->{f.clock.now.set(NOW.plusSeconds(4));return Mono.just(f.principal.get());});
        var resolver=new GeneratedConfigSigningTenantBindingResolver(delayed,f.manager,f.configClient,CONFIG,"configuration-current",ISSUER,PLATFORM,f.clock);
        assertThrows(SecurityException.class,()->resolver.observe(NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        assertEquals(0,f.tokens.get());assertEquals(0,f.sdkCalls.get());
    }
    @Test void lateMachineAuthorizationCannotStartTheSdkExchange(){
        var f=new Fixture();f.atTokenResponse.set(()->f.clock.now.set(NOW.plusSeconds(4)));
        assertThrows(SecurityException.class,()->f.resolver().observe(NOW.plusSeconds(4)).block(Duration.ofSeconds(3)));
        assertEquals(1,f.tokens.get());assertEquals(0,f.sdkCalls.get());
    }

}
