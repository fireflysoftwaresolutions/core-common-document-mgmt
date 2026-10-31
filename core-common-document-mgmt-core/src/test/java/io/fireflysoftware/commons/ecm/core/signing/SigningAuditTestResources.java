package io.fireflysoftware.commons.ecm.core.signing;

import java.time.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.TransactionalAuditAppender;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import io.r2dbc.spi.*;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;

/** Actual bounded-root/Spring transaction machinery over a controlled, networkless SPI lease. */
final class SigningAuditTestResources {
    final ConnectionFactory driver=mock(ConnectionFactory.class);
    final Connection connection=mock(Connection.class);
    final OwnedLeaseConnectionFactory factory;
    final DatabaseClient client;
    final R2dbcEntityTemplate template;
    final R2dbcTransactionManager manager;
    final BoundedR2dbcResources resources;
    final SigningAuditAdmission admission=mock(SigningAuditAdmission.class,CALLS_REAL_METHODS);
    final TransactionalAuditAppender append=mock(TransactionalAuditAppender.class);
    final SigningAuditedPhase phase;
    SigningAuditTestResources(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority,Clock clock) {
        LongSupplier nanos=System::nanoTime;
        when(driver.getMetadata()).thenReturn(()->"PostgreSQL");when(driver.create()).thenAnswer(call->Mono.just(connection));
        when(connection.isAutoCommit()).thenReturn(true);when(connection.getTransactionIsolationLevel()).thenReturn(IsolationLevel.READ_COMMITTED);
        when(connection.beginTransaction(any(io.r2dbc.spi.TransactionDefinition.class))).thenReturn(Mono.empty());when(connection.beginTransaction()).thenReturn(Mono.empty());
        when(connection.commitTransaction()).thenReturn(Mono.empty());when(connection.rollbackTransaction()).thenReturn(Mono.empty());
        when(connection.close()).thenReturn(Mono.empty());when(connection.setAutoCommit(anyBoolean())).thenReturn(Mono.empty());
        factory=new OwnedLeaseConnectionFactory(driver);client=DatabaseClient.create(factory);
        template=spy(new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE));manager=new R2dbcTransactionManager(factory);
        resources=BoundedR2dbcResources.verify(factory,client,template,manager);
        var a=authority.binding();
        when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.just(new SigningAuditAdmission.Permit(
                SigningDraftIntentTest.id(100),intent.tenantId(),a.bindingId(),a.version(),a.definitionDigest(),authority.actor(),
                "actual-policy-v1",SigningDraftIntentTest.id(101),SigningDraftIntentTest.id(102),authority.notAfter())));
        when(append.append(any(),any())).thenAnswer(call->{RootTransactionScope scope=call.getArgument(0);AuditEvent event=call.getArgument(1);
            return factory.requireBoundScope(resources,scope).thenReturn(new TransactionalAuditAppender.ProvisionalAppend(
                    AuditRecordHasher.record(AuditCheckpoint.genesis(event.stream()),event,clock.instant()),false));});
        phase=new SigningAuditedPhase(resources,client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),append,admission,clock,nanos);
        clearInvocations(template);
    }
}
