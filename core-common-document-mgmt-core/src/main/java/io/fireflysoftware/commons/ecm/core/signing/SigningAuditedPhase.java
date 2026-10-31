/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.time.*;
import java.util.Objects;
import java.util.Optional;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningPolicyCustody;
import io.fireflysoftware.commons.ecm.models.repositories.signing.SigningPolicyCustodyRepository;
import org.springframework.data.r2dbc.core.StatementMapper;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.SpringDataTransactionalAuditAppender;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.kernel.audit.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

/** One owner root pairs the mandatory append and mapped business writes, then verifies completion. */
public final class SigningAuditedPhase {
    public record Receipt(AuditRecord record) { public Receipt { Objects.requireNonNull(record); } }
    public record Plan<T>(SigningAuditEvidence evidence, Function<Receipt,Mono<T>> writes) {
        public Plan { Objects.requireNonNull(evidence); Objects.requireNonNull(writes); }
    }
    public record ReceiptV3(AuditRecord record,SigningPolicyTraceV1 trace) {
        public ReceiptV3 { Objects.requireNonNull(record);Objects.requireNonNull(trace); }
    }
    public record PlanV3<T>(SigningAuditEvidence evidence,Function<ReceiptV3,Mono<T>> writes) {
        public PlanV3 { Objects.requireNonNull(evidence);Objects.requireNonNull(writes); }
    }
    private record WorkPlan<T>(SigningAuditEvidence evidence,BiFunction<Receipt,SigningPolicyTraceV1,Mono<T>> writes) {}
    private final BoundedR2dbcResources resources;
    private final R2dbcEntityTemplate template;
    private final BoundedRootTransaction root;
    private final TransactionalAuditAppender append;
    private final SigningAuditAdmission admission;
    private final Clock clock;
    private final LongSupplier nanos;

    public SigningAuditedPhase(BoundedR2dbcResources resources, DatabaseClient client, R2dbcEntityTemplate template,
            R2dbcTransactionManager manager, CleanupPolicy cleanup, AuditAppendAuthority appendAuthority,
            SigningAuditAdmission admission, Clock clock, LongSupplier nanos) {
        this(resources,client,template,manager,cleanup,
                new SpringDataTransactionalAuditAppender(resources,client,template,manager,
                        Objects.requireNonNull(appendAuthority),clock,nanos,java.util.UUID::randomUUID),
                admission,clock,nanos);
    }
    SigningAuditedPhase(BoundedR2dbcResources resources, DatabaseClient client, R2dbcEntityTemplate template,
            R2dbcTransactionManager manager, CleanupPolicy cleanup, TransactionalAuditAppender append,
            SigningAuditAdmission admission, Clock clock, LongSupplier nanos) {
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);
        this.template=template; this.root=new DefaultBoundedRootTransaction(this.resources,Objects.requireNonNull(cleanup));
        this.append=Objects.requireNonNull(append); this.admission=Objects.requireNonNull(admission);
        this.clock=Objects.requireNonNull(clock); this.nanos=Objects.requireNonNull(nanos);
    }
    public void requireTemplate(R2dbcEntityTemplate expected) {
        if (template!=expected) throw new IllegalArgumentException("Exact signing factory/template graph required");
    }
    public <T> Mono<T> execute(SigningDraftIntent intent, SigningAccountScope account, SigningAuthority authority,
                              Supplier<Mono<Plan<T>>> preparation) {
        return executeInternal(intent,account,authority,()->Mono.defer(preparation)
                .map(plan->new WorkPlan<>(plan.evidence(),(receipt,trace)->plan.writes().apply(receipt))),false);
    }
    public <T> Mono<T> executeV3(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority,
                               Supplier<Mono<PlanV3<T>>> preparation) {
        return executeInternal(intent,account,authority,()->Mono.defer(preparation)
                .map(plan->new WorkPlan<>(plan.evidence(),(receipt,trace)->plan.writes().apply(new ReceiptV3(receipt.record(),trace)))),true);
    }
    private <T> Mono<T> executeInternal(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority,
                                       Supplier<Mono<WorkPlan<T>>> preparation,boolean custody) {
        return Mono.defer(()-> {
            authority.requireLive(clock);
            if(custody&&!(admission instanceof SigningPolicyTracePort))return Mono.error(denied());
            Mono<Void> capability=custody?((SigningPolicyTracePort)admission).requirePolicyCustody():Mono.empty();
            Duration remaining=Duration.between(clock.instant(),authority.notAfter());
            return capability.then(Mono.defer(()->admission.requireCurrent(intent,account,authority)))
                    .switchIfEmpty(Mono.error(denied())).timeout(remaining)
                    .onErrorMap(error->denied()).flatMap(permit->executeCurrent(intent,account,authority,permit,preparation,custody));
        });
    }
    private <T> Mono<T> executeCurrent(SigningDraftIntent intent, SigningAccountScope account, SigningAuthority authority,
            SigningAuditAdmission.Permit permit, Supplier<Mono<WorkPlan<T>>> preparation,boolean custody) {
        var binding=authority.binding();
        if (!binding.resourceTenant().equals(intent.tenantId()) || !authority.actor().issuer().equals(binding.issuer())
                || !authority.actor().subject().equals(binding.subject()) || !authority.actor().platformTenantId().equals(binding.platformTenant())
                || !account.providerId().equals(intent.providerId())
                || !permit.resourceTenant().equals(intent.tenantId()) || !account.tenantId().equals(intent.tenantId())
                || !permit.bindingId().equals(binding.bindingId()) || permit.bindingVersion()!=binding.version()
                || !permit.bindingDigest().equals(binding.definitionDigest()) || !permit.actor().equals(authority.actor())
                || !permit.notAfter().isAfter(clock.instant()) || permit.notAfter().isAfter(authority.notAfter())) return Mono.error(denied());
        long before=nanos.getAsLong(); Instant now=clock.instant();
        Instant until=permit.notAfter().isBefore(now.plusSeconds(5)) ? permit.notAfter() : now.plusSeconds(5);
        var budget=R2dbcOperationBudget.observed(now,until,before,Duration.ofSeconds(5),clock,nanos);
        var expectedFailure=new AtomicReference<RuntimeException>();
        var definition=new DefaultTransactionDefinition();
        definition.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        definition.setReadOnly(false);
        return admission.withinPhase(permit,()->root.execute(budget,definition,scope->
                resources.factory().requireBoundScope(resources,scope).then(Mono.defer(preparation))
                .switchIfEmpty(Mono.error(failure())).flatMap(plan->{
                    if (!plan.evidence().intent().equals(intent) || !plan.evidence().account().equals(account)) return Mono.error(failure());
                    Mono<Optional<SigningPolicyTraceV1>> observed=custody?((SigningPolicyTracePort)admission).currentTrace(permit).map(Optional::of).switchIfEmpty(Mono.error(denied())):Mono.just(Optional.empty());
                    return observed.flatMap(captured->{
                    var trace=captured.orElse(null);var v3=trace==null?null:new SigningAuditEvidenceV3(plan.evidence(),trace);
                    AuditEvent event=v3==null?plan.evidence().event(permit,clock.instant()):v3.event(permit,clock.instant());
                    authority.requireLive(clock); scope.remaining();
                    Mono<TransactionalAuditAppender.ProvisionalAppend> appended=v3==null
                            ?admission.withinExpectedAppend(permit,plan.evidence(),event,()->append.append(scope,event))
                            :((SigningPolicyTracePort)admission).withinExpectedAppendV3(permit,v3,event,()->append.append(scope,event));
                    return appended.switchIfEmpty(Mono.error(failure())).timeout(scope.remaining())
                            .flatMap(provisional->{
                                if (provisional.replay() || provisional.record()==null || !event.equals(provisional.record().event())) return Mono.error(failure());
                                var record=provisional.record();
                                var previous=new AuditCheckpoint(event.stream(),record.sequence()-1,record.previousHash());
                                if (!AuditRecordHasher.record(previous,event,record.recordedAt()).equals(record)) return Mono.error(failure());
                                authority.requireLive(clock); scope.remaining();
                                return resources.factory().requireBoundScope(resources,scope)
                                        .then(v3==null?Mono.empty():persistCustody(scope,v3,record))
                                        .then(Mono.defer(()->{
                                            if(v3!=null){authority.requireLive(clock);scope.remaining();}
                                            return plan.writes().apply(new Receipt(provisional.record()),trace);
                                        }))
                                        .switchIfEmpty(Mono.error(failure())).timeout(scope.remaining())
                                        .map(value->{authority.requireLive(clock);scope.remaining();return value;});
                            });
                    });
                }).onErrorResume(error->{
                    if (!custody && error instanceof SecurityException) expectedFailure.set(denied());
                    if (!custody && error instanceof OptimisticLockingFailureException) expectedFailure.set(new OptimisticLockingFailureException("Signing source or operation scope changed"));
                    return resources.factory().markBoundScopeRollbackOnly(resources,scope)
                            .onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error));
                }))
                .map(completed->{
                    var facts=completed.transactionFacts();
                    if (!facts.nativeAttempted() || !facts.commitAcknowledged() || !facts.boundaryCompleted()
                            || facts.rollbackAcknowledged() || completed.cleanupFact()!=CleanupFact.COMPLETE) throw failure();
                    authority.requireLive(clock); return completed.value();
                }).onErrorMap(error->{
                    if (!custody && error instanceof RootTransactionException rootFailure && rootFailure.cleanupFact()==CleanupFact.COMPLETE
                            && rootFailure.transactionFacts().rollbackAcknowledged() && expectedFailure.get()!=null) return expectedFailure.get();
                    return error;
                }));
    }
    private Mono<Void> persistCustody(RootTransactionScope scope,SigningAuditEvidenceV3 evidence,AuditRecord record) {
        var trace=evidence.trace();var event=record.event();var business=evidence.business();
        var row=new SigningPolicyCustody(java.util.UUID.randomUUID(),business.intent().tenantId(),event.stream().resourceTenantId(),
                business.operationId(),event.eventId(),trace.phase().name(),3,trace.authorization().policyRevision(),trace.authorization().decisionId(),
                trace.admission().policyRevision(),trace.admission().decisionId(),business.digest(event.stream().resourceTenantId()),trace.digest());
        var strategy=template.getDataAccessStrategy();var mapper=strategy.getStatementMapper().forType(SigningPolicyCustody.class);
        StatementMapper.InsertSpec insert=mapper.createInsert(strategy.getTableName(SigningPolicyCustody.class));
        var outbound=strategy.getOutboundRow(row);
        for(var column:outbound.keySet())insert=insert.withColumn(column,outbound.get(column));
        var repository=new org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory(template).getRepository(SigningPolicyCustodyRepository.class);
        return resources.factory().requireBoundScope(resources,scope)
                .then(template.getDatabaseClient().sql(mapper.getMappedObject(insert)).fetch().rowsUpdated())
                .switchIfEmpty(Mono.error(failure())).flatMap(count->{
                    if(count!=1)return Mono.error(failure());
                    return repository.findByTenantIdAndResourceTenantIdAndOperationIdAndEventIdAndPhase(row.tenantId(),row.resourceTenantId(),row.operationId(),row.eventId(),row.phase())
                            .filter(row::equals).switchIfEmpty(Mono.error(failure())).then();
                });
    }
    private static SecurityException denied() { return new SecurityException("Current trusted signing audit admission unavailable"); }
    private static IllegalStateException failure() { return new IllegalStateException("Paired durable signing audit required"); }
}
