/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;
import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.core.upload.impl.*;
import io.fireflysoftware.commons.ecm.web.controllers.EvidenceUploadController;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceUploadAdmissionImpl;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceUploadRecoveryEnvelopeTest {
    @Test void providerFailureBeforeAnyLeaseIsTypedRefusedWithOriginalAction(){
        var fixture=new AuthenticatedEvidenceUploadAdmissionTest();var acquisitions=new AtomicInteger();
        var authority=new AuthenticatedEvidenceUploadAdmissionImpl(fixture.settings,(principal,action,resource,facts)->Mono.error(new IllegalStateException("private-pdp-fixture-marker")),fixture.clock,System::nanoTime);
        var driver=mock(ConnectionFactory.class);when(driver.getMetadata()).thenReturn(()->"PostgreSQL");when(driver.create()).thenAnswer(call->{acquisitions.incrementAndGet();return Mono.error(new AssertionError("No lease permitted before current authority"));});
        var factory=new OwnedLeaseConnectionFactory(driver);var client=DatabaseClient.create(factory);var template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);var manager=new R2dbcTransactionManager(factory);LongSupplier nanos=System::nanoTime;
        var owner=new SpringDataEvidenceUploadStoreImpl(new EvidenceUploadAuditRootImpl(BoundedR2dbcResources.verify(factory,client,template,manager),client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,fixture.clock,nanos));
        response(fixture,authority,owner,"REFUSED");assertThat(acquisitions).hasValue(0);
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<AuthenticatedEvidenceUploadAdmissionImpl> unavailable=mock(org.springframework.beans.factory.ObjectProvider.class);
        when(unavailable.getIfUnique()).thenThrow(new IllegalStateException("fixture-provider-construction-unavailable"));var beans=new StaticListableBeanFactory();beans.addBean("owner",owner);
        response(fixture,new EvidenceUploadController(unavailable,beans.getBeanProvider(EvidenceUploadStore.class)),"REFUSED");assertThat(acquisitions).hasValue(0);
    }
    @Test void knownCommitFailureCannotBecomeUnknownInTheHttpRecoveryEnvelope(){
        var fixture=new AuthenticatedEvidenceUploadAdmissionTest();
        // Failure-mapping contract only; this controlled port makes no persistence claim.
        response(fixture,fixture.authority,request->Mono.error(new EvidenceUploadFailure(request.actionId(),EvidenceUploadFailure.Outcome.COMMITTED_RESPONSE_UNAVAILABLE)),"COMMITTED_RESPONSE_UNAVAILABLE");
    }
    @Test void unclassifiedFailureAfterOwnerDispatchRetainsUnknownOriginalAction(){
        var fixture=new AuthenticatedEvidenceUploadAdmissionTest();var sent=new AtomicInteger();
        response(fixture,fixture.authority,request->{sent.incrementAndGet();return Mono.error(new IllegalStateException("fixture-post-dispatch-response-lost"));},"OUTCOME_UNKNOWN");assertThat(sent).hasValue(1);
    }
    @Test void genuinePreworkAuthorizationRefusalDoesNotDispatchOwner(){
        var fixture=new AuthenticatedEvidenceUploadAdmissionTest();var sent=new AtomicInteger();
        var beans=new StaticListableBeanFactory();beans.addBean("authority",fixture.authority);beans.addBean("owner",(EvidenceUploadStore)request->{sent.incrementAndGet();return Mono.error(new AssertionError("No owner dispatch before signed-party admission"));});
        var controller=new EvidenceUploadController(beans.getBeanProvider(AuthenticatedEvidenceUploadAdmissionImpl.class),beans.getBeanProvider(EvidenceUploadStore.class));
        WebTestClient.bindToController(controller).webFilter((exchange,chain)->chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.authentication(java.util.UUID.randomUUID(),"domain-lending")))).configureClient().build()
            .post().uri("/api/v1/managed-document-evidence/applications/"+fixture.request.applicationId()+"/actions/"+fixture.request.actionId()).contentType(MediaType.APPLICATION_JSON).bodyValue(fixture.request).exchange().expectStatus().isForbidden();
        assertThat(sent).hasValue(0);
    }
    void response(AuthenticatedEvidenceUploadAdmissionTest fixture,AuthenticatedEvidenceUploadAdmissionImpl authority,EvidenceUploadStore owner,String outcome){
        var beans=new StaticListableBeanFactory();beans.addBean("authority",authority);beans.addBean("owner",owner);
        var controller=new EvidenceUploadController(beans.getBeanProvider(AuthenticatedEvidenceUploadAdmissionImpl.class),beans.getBeanProvider(EvidenceUploadStore.class));
        response(fixture,controller,outcome);
    }
    void response(AuthenticatedEvidenceUploadAdmissionTest fixture,EvidenceUploadController controller,String outcome){
        var client=WebTestClient.bindToController(controller).webFilter((exchange,chain)->chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(fixture.authentication(fixture.party,"domain-lending"))))
            .configureClient().build();
        client.post().uri("/api/v1/managed-document-evidence/applications/"+fixture.request.applicationId()+"/actions/"+fixture.request.actionId()).contentType(MediaType.APPLICATION_JSON).bodyValue(fixture.request).exchange()
            .expectStatus().isEqualTo(503).expectBody().jsonPath("$.actionId").isEqualTo(fixture.request.actionId().toString()).jsonPath("$.outcome").isEqualTo(outcome)
            .jsonPath("$.recovery").isEqualTo("SAME_ACTION_OBSERVE_OR_EXACT_REPLAY").jsonPath("$.documentId").doesNotExist();
    }
}
