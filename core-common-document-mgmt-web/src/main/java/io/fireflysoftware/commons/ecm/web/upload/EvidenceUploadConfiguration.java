/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;

import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.core.upload.impl.*;
import io.fireflysoftware.commons.ecm.models.config.DocumentEvidenceMapping;
import io.fireflysoftware.commons.ecm.web.upload.impl.*;
import io.r2dbc.spi.ConnectionFactory;
import java.time.*;
import java.util.UUID;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.core.*;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="document.evidence.upload.enabled",havingValue="true")
public class EvidenceUploadConfiguration implements WebFluxConfigurer {
    @Override public void configureHttpMessageCodecs(ServerCodecConfigurer codecs){codecs.defaultCodecs().maxInMemorySize(3*1024*1024);}
    @Bean EvidenceUploadSettings evidenceUploadSettings(Environment env){String p="document.evidence.upload.";
        return new EvidenceUploadSettings(env.getRequiredProperty(p+"issuer"),env.getRequiredProperty(p+"audience"),env.getRequiredProperty(p+"allowed-client-id"),env.getRequiredProperty(p+"platform-tenant"),
            UUID.fromString(env.getRequiredProperty(p+"resource-tenant-id")),UUID.fromString(env.getRequiredProperty(p+"binding-id")),Long.parseLong(env.getRequiredProperty(p+"binding-version")),env.getRequiredProperty(p+"binding-digest"),Instant.parse(env.getRequiredProperty(p+"not-after")));
    }
    @Bean AuthenticatedEvidenceUploadAdmissionImpl evidenceUploadAdmission(EvidenceUploadSettings settings,ObjectProvider<PolicyEvaluationPort> policies){
        var policy=policies.getIfUnique();if(policy==null)throw new IllegalStateException("Exactly one current document policy evaluator required");
        return new AuthenticatedEvidenceUploadAdmissionImpl(settings,policy,Clock.systemUTC(),System::nanoTime);
    }
    @Bean EvidenceUploadStore evidenceUploadStore(@Qualifier("connectionFactory") ConnectionFactory business,AuthenticatedEvidenceUploadAdmissionImpl authority){
        var factory=new OwnedLeaseConnectionFactory(business);var client=DatabaseClient.create(factory);
        var template=new R2dbcEntityTemplate(client,new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE,DocumentEvidenceMapping.converters()));
        var manager=new R2dbcTransactionManager(factory);
        return new SpringDataEvidenceUploadStoreImpl(new EvidenceUploadAuditRootImpl(BoundedR2dbcResources.verify(factory,client,template,manager),client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,Clock.systemUTC(),System::nanoTime));
    }
    @Bean @Order(-50) WebFilter documentEvidenceVerifiedSubject(ReactiveJwtDecoder decoder,
            @Qualifier("fireflyJwtAuthenticationConverter") Converter<Jwt,Mono<AbstractAuthenticationToken>> projection,EvidenceUploadSettings settings){
        return new VerifiedDocumentEvidenceJwtWebFilterImpl(decoder,projection,settings.issuer(),settings.platformTenant(),"/api/v1/managed-document-evidence");
    }
}
