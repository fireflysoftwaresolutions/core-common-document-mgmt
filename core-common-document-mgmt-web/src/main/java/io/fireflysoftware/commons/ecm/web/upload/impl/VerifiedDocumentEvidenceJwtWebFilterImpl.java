/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload.impl;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.oauth2.server.resource.web.server.*;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.*;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.*;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Adds an operation-owned native JWT after existing security, preserving its configured rich projection. */
public final class VerifiedDocumentEvidenceJwtWebFilterImpl implements WebFilter {
    private final ReactiveJwtDecoder decoder;
    private final Converter<Jwt,Mono<AbstractAuthenticationToken>> projection;
    private final ServerWebExchangeMatcher route;
    private final String issuer,platformTenant;
    public VerifiedDocumentEvidenceJwtWebFilterImpl(ReactiveJwtDecoder decoder,Converter<Jwt,Mono<AbstractAuthenticationToken>> projection,
            String issuer,String platformTenant,String path){
        this.decoder=Objects.requireNonNull(decoder);this.projection=Objects.requireNonNull(projection);
        this.issuer=Objects.requireNonNull(issuer);this.platformTenant=Objects.requireNonNull(platformTenant);
        this.route=ServerWebExchangeMatchers.pathMatchers(path,path+"/**");
    }
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){return route.matches(exchange).flatMap(match->{
        if(!match.isMatch())return chain.filter(exchange);
        var headers=exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if(headers==null || headers.size()!=1)return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,"One bearer credential required"));
        return Mono.using(()->new AtomicReference<JwtAuthenticationToken>(),owned->{
            var manager=new JwtReactiveAuthenticationManager(decoder);
            manager.setJwtAuthenticationConverter(jwt->Mono.defer(()->projection.convert(jwt)).switchIfEmpty(Mono.error(denied()))
                .map(projected->{
                    if(!projected.isAuthenticated() || !(projected.getPrincipal() instanceof SecurityPrincipal principal)
                        || jwt.getIssuer()==null || !issuer.equals(jwt.getIssuer().toString()) || !issuer.equals(principal.issuer())
                        || !Objects.equals(jwt.getSubject(),principal.subject()) || !platformTenant.equals(principal.tenantId())
                        || !jwt.getClaims().equals(principal.claims()))throw denied();
                    var token=new JwtAuthenticationToken(jwt,principal,projected.getAuthorities());
                    if(!owned.compareAndSet(null,token))throw denied();return token;
                }));
            var filter=new AuthenticationWebFilter(manager);
            filter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
            filter.setServerAuthenticationConverter(request->new ServerBearerTokenAuthenticationConverter().convert(request).switchIfEmpty(Mono.error(denied())));
            filter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(new BearerTokenServerAuthenticationEntryPoint()));
            return filter.filter(exchange,authenticated->new org.springframework.security.web.server.context.SecurityContextServerWebExchangeWebFilter().filter(authenticated,chain));
        },owned->{var token=owned.get();if(token!=null)token.setAuthenticated(false);},true);
    });}
    private static org.springframework.security.oauth2.core.OAuth2AuthenticationException denied(){return new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token","Exact verified document evidence principal required",null));}
}
