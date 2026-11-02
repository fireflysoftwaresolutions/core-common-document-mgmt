/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import io.fireflysoftware.common.config.sdk.api.TenantResourceBindingCurrentApi;
import io.fireflysoftware.common.config.sdk.invoker.ApiClient;
import io.fireflysoftware.common.config.sdk.model.TenantResourceBindingCurrent;
import java.net.URI;
import java.time.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

/** Uses the genuine generated CURRENT operation and a configured machine client, never the user's raw token. */
public final class GeneratedConfigSigningTenantBindingResolver implements SigningTenantBindingResolver,SigningTenantBindingObservationPort {
    private final SecurityContextPort principals;
    private final TenantResourceBindingCurrentApi current;
    private final String issuer,platformTenant;
    private final Clock clock;
    private final Object lookupDeadline=new Object();

    public GeneratedConfigSigningTenantBindingResolver(SecurityContextPort principals,
            ReactiveOAuth2AuthorizedClientManager manager,WebClient client,URI endpoint,
            String registrationId,String issuer,String platformTenant,Clock clock) {
        this.principals=Objects.requireNonNull(principals);Objects.requireNonNull(manager);Objects.requireNonNull(client);
        this.clock=Objects.requireNonNull(clock);this.issuer=namespace(issuer,500);this.platformTenant=namespace(platformTenant,1024);
        if(registrationId==null||!registrationId.matches("[A-Za-z0-9._-]{1,100}")||endpoint==null
                ||!"https".equals(endpoint.getScheme())||endpoint.getHost()==null||endpoint.getUserInfo()!=null
                ||endpoint.getQuery()!=null||endpoint.getFragment()!=null||!endpoint.normalize().equals(endpoint)
                ||endpoint.getRawPath().contains("%")||endpoint.getPort()==0||endpoint.getPort()>65535)throw invalid();
        String base=endpoint.toASCIIString().replaceFirst("/$","");
        URI exact=URI.create(base+"/api/v1/tenant-resource-bindings/current");
        var machine=UsernamePasswordAuthenticationToken.authenticated("core-signing-machine",null,List.of());
        WebClient authorized=client.mutate().filter((request,next)->Mono.deferContextual(context->{
            Instant deadline=context.getOrDefault(lookupDeadline,null);
            if(deadline==null||!clock.instant().isBefore(deadline))return Mono.error(denied());
            if(request.method()!=HttpMethod.GET||!exact.equals(request.url())||request.headers().containsHeader(HttpHeaders.AUTHORIZATION))return Mono.error(denied());
            return Mono.defer(()->manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(registrationId).principal(machine).build()))
                    .switchIfEmpty(Mono.error(denied())).flatMap(authorization->{
                        if(!clock.instant().isBefore(deadline))return Mono.error(denied());
                        var token=authorization.getAccessToken();
                        if(!registrationId.equals(authorization.getClientRegistration().getRegistrationId())
                                ||!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getClientRegistration().getAuthorizationGrantType())
                                ||token==null||!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())
                                ||token.getTokenValue().isBlank()||token.getExpiresAt()==null||!clock.instant().isBefore(token.getExpiresAt()))return Mono.error(denied());
                        return next.exchange(ClientRequest.from(request).headers(headers->headers.setBearerAuth(token.getTokenValue())).build());
                    });
        })).build();
        current=new TenantResourceBindingCurrentApi(new ApiClient(authorized).setBasePath(base));
    }
    @Override public Mono<Observation> observe(Instant originalNotAfter) {
        return Mono.defer(()->{
            Instant started=clock.instant();
            if(originalNotAfter==null||!started.isBefore(originalNotAfter))return Mono.error(denied());
            return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(denied())).flatMap(principal->{
                Instant expiry=principal.claim("exp",Instant.class);String client=principal.claim("client_id",String.class);
                if(!issuer.equals(principal.issuer())||!platformTenant.equals(principal.tenantId())
                        ||principal.subject()==null||principal.subject().isBlank()||client==null||client.isBlank()
                        ||expiry==null||!started.isBefore(expiry))return Mono.error(denied());
                Instant until=min(originalNotAfter,expiry,started.plusSeconds(5));
                if(!clock.instant().isBefore(until))return Mono.error(denied());
                var actor=new AuditActor(principal.issuer(),principal.subject(),client,principal.tenantId(),null,null);
                return Mono.defer(current::observeCurrentTenantResourceBinding)
                        .switchIfEmpty(Mono.error(denied())).map(value->snapshot(value,actor,started,until))
                        .timeout(Duration.between(clock.instant(),until))
                        .contextWrite(context->context.put(lookupDeadline,until));
            }).timeout(Duration.between(started,min(originalNotAfter,started.plusSeconds(5))));
        }).onErrorMap(error->denied());
    }
    @Override public Mono<SigningTenantBinding> resolve(SecurityPrincipal expected,String resourceTenant) {
        return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(denied())).flatMap(principal->{
            if(expected==null||!expected.equals(principal))return Mono.error(denied());
            Instant expiry=principal.claim("exp",Instant.class);
            return observe(expiry).flatMap(value->{
                var actor=value.actor();
                if(!value.resourceTenantId().toString().equals(resourceTenant)||!principal.issuer().equals(actor.issuer())
                        ||!principal.subject().equals(actor.subject())||!principal.tenantId().equals(actor.platformTenantId())
                        ||!Objects.equals(principal.claim("client_id",String.class),actor.clientId()))return Mono.error(denied());
                return Mono.just(new SigningTenantBinding(value.bindingId(),value.bindingVersion(),value.definitionDigest(),
                        actor.issuer(),actor.subject(),actor.platformTenantId(),value.resourceTenantId().toString(),value.notAfter()));
            });
        }).onErrorMap(error->denied());
    }
    private Observation snapshot(TenantResourceBindingCurrent value,AuditActor actor,Instant started,Instant originalUntil) {
        var reference=value.getReference();var definition=value.getDefinition();
        if(reference==null||definition==null||definition.getNamespace()==null||definition.getResourceTenant()==null
                ||definition.getEligibility()==null)throw denied();
        var namespace=definition.getNamespace();var eligibility=definition.getEligibility();
        UUID binding=reference.getBindingId(),resource=definition.getResourceTenant().getValue();Long version=reference.getVersion();
        String digest=reference.getDefinitionDigest();Long observed=value.getObservedAtEpochMillis(),valid=value.getValidUntilEpochMillis(),business=definition.getNotAfterEpochMillis();
        Instant now=clock.instant();
        if(binding==null||!binding.equals(definition.getBindingId())||resource==null||resource.equals(new UUID(0,0))
                ||version==null||version<1||version==Long.MAX_VALUE||!version.equals(definition.getVersion())
                ||digest==null||!digest.matches("[0-9a-f]{64}")||!issuer.equals(namespace.getIssuer())||!platformTenant.equals(namespace.getPlatformTenant())
                ||value.getControlVersion()==null||value.getControlVersion()<1||value.getControlVersion()==Long.MAX_VALUE
                ||eligibility.getTenantGeneration()==null||eligibility.getTenantGeneration()<0||eligibility.getTenantGeneration()==Long.MAX_VALUE
                ||eligibility.getStatusGeneration()==null||eligibility.getStatusGeneration()<0||eligibility.getStatusGeneration()==Long.MAX_VALUE
                ||eligibility.getStatusId()==null||eligibility.getStatusId().equals(new UUID(0,0))
                ||observed==null||valid==null||business==null||business>253402300799999L||observed<started.toEpochMilli()||observed>now.toEpochMilli()
                ||valid<=observed||valid-observed>5000||valid>business||!now.isBefore(originalUntil)
                ||valid<=now.toEpochMilli()||business<=now.toEpochMilli())throw denied();
        return new Observation(resource,binding,version,digest,actor,min(originalUntil,Instant.ofEpochMilli(valid),Instant.ofEpochMilli(business)));
    }
    private static Instant min(Instant first,Instant... values){for(Instant value:values)if(value.isBefore(first))first=value;return first;}
    private static String namespace(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))throw invalid();return value;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Exact configured signing namespace and HTTPS endpoint required");}
    private static SecurityException denied(){return new SecurityException("Current trusted signing tenant binding unavailable");}
}
