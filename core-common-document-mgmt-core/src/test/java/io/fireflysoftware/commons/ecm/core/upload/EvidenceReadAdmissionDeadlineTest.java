/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import io.fireflysoftware.commons.ecm.core.upload.impl.SpringDataEvidenceReaderImpl;
import io.r2dbc.spi.ConnectionFactory;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.fireflyframework.core.transaction.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceReadAdmissionDeadlineTest {
    @Test void hangingInitialReadAuthorityCannotAcquireAnyLease(){
        var acquisitions=new AtomicInteger();var driver=mock(ConnectionFactory.class);when(driver.getMetadata()).thenReturn(()->"PostgreSQL");when(driver.create()).thenAnswer(call->{acquisitions.incrementAndGet();return Mono.never();});
        var factory=new OwnedLeaseConnectionFactory(driver);var client=DatabaseClient.create(factory);var template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);var manager=new R2dbcTransactionManager(factory);
        var authority=mock(EvidenceReadAdmission.class);when(authority.requireCurrent(any())).thenReturn(Mono.never());LongSupplier nanos=System::nanoTime;
        var reader=new SpringDataEvidenceReaderImpl(BoundedR2dbcResources.verify(factory,client,template,manager),client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,Clock.systemUTC(),nanos);
        var identity=new EvidenceReadIdentity(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        StepVerifier.withVirtualTime(()->reader.read(identity)).thenAwait(Duration.ofSeconds(5)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(EvidenceReadFailure.class);assertThat(((EvidenceReadFailure)error).getMetadata()).containsEntry("actionId",identity.actionId().toString()).containsEntry("outcome","OBSERVATION_UNAVAILABLE").doesNotContainKeys("documentId","contentDigest");}).verify(Duration.ofSeconds(2));
        assertThat(acquisitions).hasValue(0);
    }
}
