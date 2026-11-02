/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import io.fireflysoftware.commons.ecm.core.upload.impl.EvidenceUploadAuditRootImpl;
import io.fireflysoftware.commons.ecm.interfaces.upload.EvidenceUploadRequest;
import io.r2dbc.spi.ConnectionFactory;
import java.time.*;
import java.util.*;
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

class EvidenceUploadAdmissionDeadlineTest {
    @Test void pendingInitialAuthorityCannotAcquireLeaseOrPrepareAfterFiveSeconds(){
        var acquisitions=new AtomicInteger();var plans=new AtomicInteger();
        var driver=mock(ConnectionFactory.class);when(driver.getMetadata()).thenReturn(()->"PostgreSQL");
        when(driver.create()).thenAnswer(call->{acquisitions.incrementAndGet();return Mono.never();});
        var factory=new OwnedLeaseConnectionFactory(driver);var client=DatabaseClient.create(factory);
        var template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);var manager=new R2dbcTransactionManager(factory);
        var resources=BoundedR2dbcResources.verify(factory,client,template,manager);
        var authority=mock(EvidenceUploadAdmission.class);when(authority.requireCurrent(any(UploadedEvidenceIntent.class))).thenReturn(Mono.never());
        LongSupplier nanos=System::nanoTime;
        var root=new EvidenceUploadAuditRootImpl(resources,client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),authority,Clock.systemUTC(),nanos);
        var intent=UploadedEvidenceIntent.capture(new EvidenceUploadRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"proof.pdf","application/pdf","IDENTITY",new byte[]{1,2,3}));
        StepVerifier.withVirtualTime(()->root.execute(intent,permit->{plans.incrementAndGet();return Mono.never();}))
            .thenAwait(Duration.ofSeconds(5)).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(EvidenceUploadFailure.class);assertThat(((EvidenceUploadFailure)error).outcome()).isEqualTo(EvidenceUploadFailure.Outcome.REFUSED);})
            .verify(Duration.ofSeconds(2));
        assertThat(acquisitions).hasValue(0);assertThat(plans).hasValue(0);
    }
}
