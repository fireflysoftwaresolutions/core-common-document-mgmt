/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;

import io.fireflysoftware.commons.ecm.web.controllers.EvidenceUploadController;
import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceUploadAdmissionImpl;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.web.idempotency.cache.IdempotencyCache;
import org.fireflyframework.web.idempotency.config.IdempotencyProperties;
import org.fireflyframework.web.idempotency.filter.IdempotencyWebFilter;
import org.fireflyframework.web.idempotency.scope.AuthenticatedIdempotencyScopeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.boot.web.context.reactive.AnnotationConfigReactiveWebApplicationContext;
import org.springframework.web.server.WebFilter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Genuine filter/handler selection and authority check; the controlled owner counts calls, not persisted documents. */
class EvidenceUploadIdempotencyTest {
    @Test void sameHttpKeyCannotReplayCachedSuccessAfterCurrentPolicyRevocation(){
        var fixture=new AuthenticatedEvidenceUploadAdmissionTest();var effects=new AtomicInteger();var cache=mock(IdempotencyCache.class);
        try(var context=new AnnotationConfigReactiveWebApplicationContext()){
            context.register(CodecFixtureImpl.class);
            context.registerBean(AuthenticatedEvidenceUploadAdmissionImpl.class,()->fixture.authority);
            context.registerBean(EvidenceUploadStore.class,()->request->fixture.authority.requireCurrent(UploadedEvidenceIntent.capture(request)).flatMap(permit->fixture.authority.revalidate(permit).thenReturn(fixture.receipt())).doOnNext(receipt->effects.incrementAndGet()));
            context.registerBean(EvidenceUploadController.class);
            context.registerBean("constructedAuthentication",ConstructedAuthenticationFixtureImpl.class,()->new ConstructedAuthenticationFixtureImpl(fixture));
            context.registerBean(IdempotencyWebFilter.class,()->{
                var properties=new IdempotencyProperties();properties.setEnabled(true);properties.setNamespace("owned-document-evidence-test");
                return new IdempotencyWebFilter(properties,cache,new AuthenticatedIdempotencyScopeResolver(),()->new ArrayList<>(context.getBeansOfType(HandlerMapping.class).values()));
            });
            context.refresh();var client=WebTestClient.bindToApplicationContext(context).build();
            String path="/api/v1/managed-document-evidence/applications/"+fixture.request.applicationId()+"/actions/"+fixture.request.actionId();
            client.post().uri(path).header("X-Idempotency-Key","same-original-key").contentType(MediaType.APPLICATION_JSON).bodyValue(fixture.request).exchange().expectStatus().isCreated();
            fixture.policyLive.set(false);
            client.post().uri(path).header("X-Idempotency-Key","same-original-key").contentType(MediaType.APPLICATION_JSON).bodyValue(fixture.request).exchange().expectStatus().isForbidden();
            assertThat(effects).hasValue(1);verifyNoInteractions(cache);
        }
    }
    static final class ConstructedAuthenticationFixtureImpl implements WebFilter,org.springframework.core.Ordered {
        final AuthenticatedEvidenceUploadAdmissionTest fixture;ConstructedAuthenticationFixtureImpl(AuthenticatedEvidenceUploadAdmissionTest fixture){this.fixture=fixture;}
        @Override public int getOrder(){return -100;}
        @Override public reactor.core.publisher.Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange,org.springframework.web.server.WebFilterChain chain){
            return new org.springframework.security.web.server.context.SecurityContextServerWebExchangeWebFilter().filter(exchange,chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.authentication(fixture.party,"domain-lending")));
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableWebFlux static class CodecFixtureImpl implements WebFluxConfigurer {
        @Override public void configureHttpMessageCodecs(ServerCodecConfigurer codecs){new EvidenceUploadConfiguration().configureHttpMessageCodecs(codecs);}
    }
}
