package io.fireflysoftware.commons.ecm.core.services.generation;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.r2dbc.spi.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.AuditEvent;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import reactor.core.publisher.Mono;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.SpringDataDocumentGenerationCommitService;

class DocumentOwnedStorageTest extends DocumentStorageFixtures {
    final AtomicInteger acquired=new AtomicInteger(),authorized=new AtomicInteger();
    final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(new ConnectionFactory(){
        public org.reactivestreams.Publisher<? extends Connection> create(){acquired.incrementAndGet();return Mono.error(new AssertionError("No SQL allowed in refusal test"));}
        public ConnectionFactoryMetadata getMetadata(){return ()->"PostgreSQL";}
    });
    final DatabaseClient client=DatabaseClient.create(factory);
    final R2dbcEntityTemplate template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);
    final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);
    final BoundedR2dbcResources resources=BoundedR2dbcResources.verify(factory,client,template,manager);
    SpringDataDocumentGenerationCommitService service(DocumentGenerationAdmission provider){
        return new SpringDataDocumentGenerationCommitService(resources,client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),
                (scope,event)->Mono.error(new AssertionError("No audit before admission")),provider,clock,nanos,UUID::randomUUID);
    }
    DocumentGenerationStorage.Request signedRequest(){
        var r=request(new byte[]{1,2,3});var e=r.event();var bound=new AuditEvent(e.eventId(),e.stream(),e.actor(),e.action(),e.outcome(),e.processId(),e.correlationId(),e.causationId(),e.occurredAt(),DocumentStorageIntent.digest(r),e.policyRevision());
        return new DocumentGenerationStorage.Request(r.resourceTenantId(),r.documentId(),r.snapshot(),bound,r.renderedContent());
    }
    @Test void absentGenuineAdmissionRefusesBeforeAnySql(){
        var error=assertThrows(IllegalStateException.class,()->service((r,d,b)->{authorized.incrementAndGet();return Mono.empty();}).store(signedRequest(),budget()).block());
        assertEquals("Current admitted document storage required",error.getMessage());assertEquals(1,authorized.get());assertEquals(0,acquired.get());
    }
    @Test void unboundAuditDigestRefusesBeforeAuthorityOrSql(){
        assertThrows(IllegalStateException.class,()->service((r,d,b)->{authorized.incrementAndGet();return Mono.empty();}).store(request(new byte[]{1}),budget()).block());
        assertEquals(0,authorized.get());assertEquals(0,acquired.get());
    }
    @Test void wrongTenantGrantRefusesBeforeSql(){
        var r=signedRequest();assertThrows(IllegalStateException.class,()->service((x,d,b)->Mono.just(new DocumentGenerationAdmission.Permit(UUID.randomUUID(),UUID.randomUUID(),r.documentId(),r.event().eventId(),d,r.event().actor(),r.event().policyRevision(),clock.instant().plusSeconds(1)))).store(r,budget()).block());assertEquals(0,acquired.get());
    }
    @Test void expiredOriginalRefusesBeforeAdmissionOrSql(){
        var r=signedRequest();var original=budget();ticks.set(3_000_000_000L);assertThrows(RuntimeException.class,()->service((x,d,b)->{authorized.incrementAndGet();return Mono.empty();}).store(r,original).block());assertEquals(0,authorized.get());assertEquals(0,acquired.get());
    }
    @Test void mismatchedPhysicalTemplateGraphIsRejectedAtConstruction(){
        var other=DatabaseClient.create(factory);assertThrows(IllegalArgumentException.class,()->new SpringDataDocumentGenerationCommitService(resources,other,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),(scope,event)->Mono.empty(),(r,d,b)->Mono.empty(),clock,nanos,UUID::randomUUID));
    }
    @Test void grantCannotOutliveOwnerSnapshotBeforeAnySql(){
        var r=signedRequest();assertThrows(IllegalStateException.class,()->service((x,d,b)->Mono.just(new DocumentGenerationAdmission.Permit(UUID.randomUUID(),r.resourceTenantId(),r.documentId(),r.event().eventId(),d,r.event().actor(),r.event().policyRevision(),r.snapshot().effectiveUntil().plusMillis(1)))).store(r,budget()).block());assertEquals(0,acquired.get());
    }
    @Test void realOwningRootReportsAcquisitionFailureWithoutAnySuccessOrRetry(){
        var r=signedRequest();var failure=assertThrows(RootTransactionException.class,()->service((x,d,b)->Mono.just(new DocumentGenerationAdmission.Permit(UUID.randomUUID(),r.resourceTenantId(),r.documentId(),r.event().eventId(),d,r.event().actor(),r.event().policyRevision(),clock.instant().plusSeconds(1)))).store(r,budget()).block());
        assertTrue(failure.transactionFacts().nativeAttempted());assertFalse(failure.transactionFacts().commitAcknowledged());assertEquals(1,acquired.get());
    }
}
