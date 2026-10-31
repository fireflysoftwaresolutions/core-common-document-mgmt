package io.fireflysoftware.commons.ecm.core.signing;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.TransactionalAuditAppender;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import io.r2dbc.spi.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningAuditedPhaseTest {
    final Clock clock=Clock.fixed(SigningDraftWorkflowTest.INITIAL,ZoneOffset.UTC);
    final LongSupplier nanos=System::nanoTime;
    final SpringDataSigningPreparationStoreTest fixture=new SpringDataSigningPreparationStoreTest();
    final ConnectionFactory driver=mock(ConnectionFactory.class);
    final Connection connection=mock(Connection.class);
    final AtomicInteger creates=new AtomicInteger(),commits=new AtomicInteger(),rollbacks=new AtomicInteger(),closes=new AtomicInteger(),writes=new AtomicInteger();
    final SigningAuditAdmission admission=mock(SigningAuditAdmission.class,CALLS_REAL_METHODS);
    final TransactionalAuditAppender append=mock(TransactionalAuditAppender.class);
    OwnedLeaseConnectionFactory factory;DatabaseClient client;R2dbcEntityTemplate template;R2dbcTransactionManager manager;BoundedR2dbcResources resources;
    @BeforeEach void setup(){
        fixture.setup();
        when(driver.getMetadata()).thenReturn(()->"PostgreSQL");
        when(driver.create()).thenAnswer(call->Mono.fromSupplier(()->{creates.incrementAndGet();return connection;}));
        when(connection.isAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolationLevel()).thenReturn(IsolationLevel.READ_COMMITTED);
        when(connection.beginTransaction(any(io.r2dbc.spi.TransactionDefinition.class))).thenReturn(Mono.empty());
        when(connection.beginTransaction()).thenReturn(Mono.empty());
        when(connection.commitTransaction()).thenAnswer(call->Mono.fromRunnable(commits::incrementAndGet));
        when(connection.rollbackTransaction()).thenAnswer(call->Mono.fromRunnable(rollbacks::incrementAndGet));
        when(connection.close()).thenAnswer(call->Mono.fromRunnable(closes::incrementAndGet));
        when(connection.setAutoCommit(anyBoolean())).thenReturn(Mono.empty());
        factory=new OwnedLeaseConnectionFactory(driver);client=DatabaseClient.create(factory);template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);manager=new R2dbcTransactionManager(factory);resources=BoundedR2dbcResources.verify(factory,client,template,manager);
        when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.just(permit()));
        when(append.append(any(),any())).thenAnswer(call->{RootTransactionScope scope=call.getArgument(0);AuditEvent event=call.getArgument(1);
            return factory.requireBoundScope(resources,scope).thenReturn(new TransactionalAuditAppender.ProvisionalAppend(AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,clock.instant()),false));});
    }
    SigningAuditAdmission.Permit permit(){var a=fixture.authority;return new SigningAuditAdmission.Permit(SigningDraftIntentTest.id(100),fixture.intent.tenantId(),a.binding().bindingId(),a.binding().version(),a.binding().definitionDigest(),a.actor(),"actual-policy-v1",SigningDraftIntentTest.id(101),SigningDraftIntentTest.id(102),a.notAfter());}
    SigningAuditedPhase phase(){return new SigningAuditedPhase(resources,client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),append,admission,clock,nanos);}
    SigningAuditEvidence evidence(){return new SigningAuditEvidence(SigningDraftIntentTest.id(20),SigningDraftIntentTest.id(21),fixture.intent,fixture.account,"b".repeat(64),"c".repeat(64),null,SigningDraftIntentTest.id(30),null);}
    Mono<String> execute(){return phase().execute(fixture.intent,fixture.account,fixture.authority,()->Mono.just(new SigningAuditedPhase.Plan<>(evidence(),receipt->Mono.fromSupplier(()->{writes.incrementAndGet();return "reserved";}))));}
    @Test void genuineOwnerRootCommitsOnlyAfterExactAppendThenWrites(){assertEquals("reserved",execute().block());assertEquals(1,writes.get());assertEquals(1,commits.get());assertEquals(0,rollbacks.get());assertEquals(1,closes.get());verify(append).append(any(),any());}
    @Test void appendFailureRollsBackAndPreventsBusinessWrite(){doReturn(Mono.error(new IllegalStateException("CONTROLLED_PRIVATE_AUDIT_ERROR"))).when(append).append(any(),any());assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,writes.get());assertEquals(0,commits.get());assertEquals(1,rollbacks.get());assertEquals(1,closes.get());}
    @Test void businessFailureAfterAppendRollsBackTheSameRoot(){var work=phase().execute(fixture.intent,fixture.account,fixture.authority,()->Mono.just(new SigningAuditedPhase.Plan<>(evidence(),receipt->Mono.error(new IllegalStateException("business")))));assertThrows(RuntimeException.class,work::block);assertEquals(0,commits.get());assertEquals(1,rollbacks.get());assertEquals(1,closes.get());verify(append).append(any(),any());}
    @Test void aPendingCommitCannotPublishTheBusinessResult(){Sinks.Empty<Void> gate=Sinks.empty();when(connection.commitTransaction()).thenReturn(gate.asMono());StepVerifier.create(execute()).expectSubscription().then(()->{assertEquals(1,writes.get());assertEquals(0,closes.get());}).then(()->gate.tryEmitEmpty()).expectNext("reserved").verifyComplete();assertEquals(1,closes.get());}
    @Test void missingTrustedAdmissionRefusesBeforeAnyLeaseOrPlan(){when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.empty());assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,creates.get());assertEquals(0,writes.get());verifyNoInteractions(append);}
    @Test void anInventedOrForeignResourceTenantBindingRefusesBeforeAcquire(){var p=permit();when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.just(new SigningAuditAdmission.Permit(p.resourceTenantId(),"foreign",p.bindingId(),p.bindingVersion(),p.bindingDigest(),p.actor(),p.policyRevision(),p.processId(),p.correlationId(),p.notAfter())));assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,creates.get());verifyNoInteractions(append);}
    @Test void replayCannotAuthorizeNewBusinessEffects(){doAnswer(call->{AuditEvent e=call.getArgument(1);return Mono.just(new TransactionalAuditAppender.ProvisionalAppend(AuditRecordHasher.record(AuditCheckpoint.genesis(e.stream()),e,clock.instant()),true));}).when(append).append(any(),any());assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,writes.get());assertEquals(0,commits.get());assertEquals(1,rollbacks.get());}
    @Test void anEmptyAppendCannotBecomeSuccessfulBusinessCompletion(){doReturn(Mono.empty()).when(append).append(any(),any());assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,writes.get());assertEquals(1,rollbacks.get());}
    @Test void mixedFactoryClientTemplateGraphRefusesBeforeAcquire(){var otherFactory=mock(ConnectionFactory.class);when(otherFactory.getMetadata()).thenReturn(()->"PostgreSQL");var other=DatabaseClient.create(otherFactory);assertThrows(IllegalArgumentException.class,()->new SigningAuditedPhase(resources,other,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),append,admission,clock,nanos));assertEquals(0,creates.get());}

    @Test void anAdmissionStillPendingCannotAcquireOrRunThePlan(){
        when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.never());
        StepVerifier.withVirtualTime(this::execute).thenAwait(Duration.ofSeconds(6)).expectError(SecurityException.class).verify(Duration.ofSeconds(1));
        assertEquals(0,creates.get());assertEquals(0,writes.get());verifyNoInteractions(append);
    }
    @Test void aCloseFailureCannotPublishAPreparedResultAfterCommit(){
        when(connection.close()).thenReturn(Mono.error(new IllegalStateException("CONTROLLED_PRIVATE_CLOSE")));
        var failure=assertThrows(RuntimeException.class,()->execute().block());assertFalse(failure.toString().contains("CONTROLLED_PRIVATE_CLOSE"));assertEquals(1,commits.get());
    }
    @Test void anUnrelatedAppendRecordCannotAuthorizeTheBusinessWrite(){
        doAnswer(call->{AuditEvent e=call.getArgument(1);var foreign=new AuditEvent(SigningDraftIntentTest.id(999),e.stream(),e.actor(),e.action(),e.outcome(),e.processId(),e.correlationId(),e.causationId(),e.occurredAt(),e.evidenceDigest(),e.policyRevision());return Mono.just(new TransactionalAuditAppender.ProvisionalAppend(AuditRecordHasher.record(AuditCheckpoint.genesis(foreign.stream()),foreign,clock.instant()),false));}).when(append).append(any(),any());
        assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,writes.get());assertEquals(0,commits.get());assertEquals(1,rollbacks.get());
    }

    @Test void originalBindingForAnotherResourceTenantCannotBeReboundByAnAuditPermit(){
        var original=fixture.authority;var b=original.binding();
        var foreign=new SigningAuthority(original.actor(),new SigningTenantBinding(b.bindingId(),b.version(),b.definitionDigest(),b.issuer(),b.subject(),b.platformTenant(),"another-resource",b.notAfter()),original.notAfter());
        var work=phase().execute(fixture.intent,fixture.account,foreign,()->Mono.just(new SigningAuditedPhase.Plan<>(evidence(),receipt->Mono.just("wrong"))));
        assertThrows(SecurityException.class,work::block);assertEquals(0,creates.get());verifyNoInteractions(append);
    }
    @Test void recordHashMustMatchActualKernelCanonicalHashBeforeBusinessEffects(){
        doAnswer(call->{AuditEvent e=call.getArgument(1);var correct=AuditRecordHasher.record(AuditCheckpoint.genesis(e.stream()),e,clock.instant());return Mono.just(new TransactionalAuditAppender.ProvisionalAppend(new AuditRecord(e,correct.sequence(),correct.recordedAt(),correct.previousHash(),"d".repeat(64)),false));}).when(append).append(any(),any());
        assertThrows(RuntimeException.class,()->execute().block());assertEquals(0,writes.get());assertEquals(0,commits.get());assertEquals(1,rollbacks.get());
    }

    @Test void publicStoreCompositionUsesGenuineAppenderAndDerivedRepositoriesButDoesNotAcquireDuringConstruction(){
        when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.empty());
        var appendAuthority=mock(org.fireflyframework.core.audit.AuditAppendAuthority.class);
        var store=new io.fireflysoftware.commons.ecm.core.signing.impl.SpringDataSigningPreparationStore(resources,client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),appendAuthority,admission,clock,nanos);
        assertEquals(0,creates.get());assertThrows(SecurityException.class,()->store.begin(fixture.intent,fixture.account,fixture.authority).block());
        assertEquals(0,creates.get());verifyNoInteractions(appendAuthority);
    }

    @Test void ownerPhaseUsesExplicitWritableReadCommittedIsolation(){
        var definition=new java.util.concurrent.atomic.AtomicReference<io.r2dbc.spi.TransactionDefinition>();
        doAnswer(call->{definition.set(call.getArgument(0));return Mono.empty();}).when(connection).beginTransaction(any(io.r2dbc.spi.TransactionDefinition.class));
        assertEquals("reserved",execute().block());assertNotNull(definition.get());
        assertEquals(IsolationLevel.READ_COMMITTED,definition.get().getAttribute(io.r2dbc.spi.TransactionDefinition.ISOLATION_LEVEL));
        assertEquals(Boolean.FALSE,definition.get().getAttribute(io.r2dbc.spi.TransactionDefinition.READ_ONLY));
    }
}
