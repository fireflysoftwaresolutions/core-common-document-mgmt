/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload.impl;

import io.fireflysoftware.commons.ecm.core.upload.*;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.audit.impl.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

/** Business read only: exact existing native event scope and completed pair, with no appender or business mutation. */
public final class SpringDataEvidenceReaderImpl implements EvidenceReadStore {
    private final BoundedR2dbcResources resources;private final BoundedRootTransaction root;private final EvidenceStoredReadLoaderImpl loader;
    private final AuditNativeContextRepository nativeContext;private final LocalAuditRepository audit;private final CompletedAuditAppendService completed;
    private final EvidenceReadAdmission authority;private final Clock clock;private final LongSupplier nanos;
    public SpringDataEvidenceReaderImpl(BoundedR2dbcResources resources,DatabaseClient client,R2dbcEntityTemplate template,R2dbcTransactionManager manager,CleanupPolicy cleanup,EvidenceReadAdmission authority,Clock clock,LongSupplier nanos){
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);
        root=new DefaultBoundedRootTransaction(this.resources,Objects.requireNonNull(cleanup));loader=new EvidenceStoredReadLoaderImpl(template);
        nativeContext=new R2dbcRepositoryFactory(template).getRepository(AuditNativeContextRepository.class);audit=new SpringDataLocalAuditRepository(this.resources,client,template,manager);completed=new SpringDataCompletedAuditAppendService(this.resources,client,template,manager,clock,nanos);
    }
    @Override public Mono<EvidenceContentRead> read(EvidenceReadIdentity identity){return Mono.defer(()->{
        Objects.requireNonNull(identity);Instant observed=clock.instant();long before=nanos.getAsLong();
        var initial=R2dbcOperationBudget.observed(observed,observed.plusSeconds(5),before,Duration.ofSeconds(5),clock,nanos);
        return authority.requireCurrent(identity).switchIfEmpty(Mono.error(unavailable())).timeout(initial.remaining())
            .onErrorMap(java.util.concurrent.TimeoutException.class,error->new EvidenceReadFailure(identity.actionId())).flatMap(permit->{
                if(!identity.equals(permit.identity()))return Mono.error(new EvidenceReadFailure(identity.actionId()));
                var budget=R2dbcOperationBudget.observed(permit.observedAt(),permit.notAfter(),permit.observedNanos(),Duration.ofSeconds(5),clock,nanos).intersect(initial);
                // The genuine native scope protocol writes technical context; no business/audit event insert is permitted here.
                var definition=new DefaultTransactionDefinition();definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                return root.execute(budget,definition,scope->resources.factory().requireBoundScope(resources,scope).then(authority.revalidate(permit))
                    .then(loader.load(permit)).flatMap(snapshot->paired(scope,permit,snapshot)).timeout(scope.remaining())
                    .onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope).onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error))))
                    .map(result->{var facts=result.transactionFacts();if(!facts.nativeAttempted()||!facts.commitAcknowledged()||!facts.boundaryCompleted()||facts.rollbackAcknowledged()||result.cleanupFact()!=CleanupFact.COMPLETE)throw new EvidenceReadFailure(identity.actionId());return result.value();})
                    .onErrorMap(RootTransactionException.class,error->new EvidenceReadFailure(identity.actionId()))
                    .flatMap(value->Mono.defer(()->authority.revalidate(permit).then(Mono.fromCallable(()->{budget.remaining();return value;})).timeout(budget.remaining()))
                        .onErrorMap(error->new EvidenceReadFailure(identity.actionId())));
            });
    });}
    private Mono<EvidenceContentRead> paired(RootTransactionScope scope,EvidenceReadAdmission.Permit permit,EvidenceStoredReadLoaderImpl.Snapshot snapshot){return Mono.defer(()->{
        UUID nonce=UUID.randomUUID();var event=snapshot.event();var stream=event.stream();var actor=permit.actor();
        Instant until=Collections.min(List.of(permit.notAfter(),clock.instant().plus(scope.remaining()),clock.instant().plusSeconds(5)));long before=nanos.getAsLong();
        return authority.revalidate(permit).then(nativeContext.enter(nonce,stream.resourceTenantId(),stream.service(),stream.resourceType(),stream.resourceId(),event.eventId(),actor.issuer(),actor.subject(),actor.clientId(),actor.platformTenantId(),actor.staffRealm(),actor.staffId(),OffsetDateTime.ofInstant(until,ZoneOffset.UTC)))
            .switchIfEmpty(Mono.error(unavailable())).flatMap(stamp->{
                if(!nonce.equals(stamp.contextNonce())||!event.eventId().equals(stamp.eventId())||stamp.dbNowMillis()==null||stamp.untilMillis()==null||stamp.dbNowMillis()<=0||stamp.untilMillis()>until.toEpochMilli()||stamp.untilMillis()-1<=stamp.dbNowMillis())return Mono.error(unavailable());
                scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(stamp.dbNowMillis()),Instant.ofEpochMilli(stamp.untilMillis()-1),before,Duration.ofSeconds(5),clock,nanos));
                return audit.find(scope,stream.resourceTenantId(),event.eventId()).filter(record->event.equals(record.event())&&record.sequence()==snapshot.custody().auditSequence()&&record.recordHash().equals(snapshot.custody().auditRecordHash()))
                    .switchIfEmpty(Mono.error(unavailable())).flatMap(record->audit.head(scope,stream)
                        .filter(head->head.lastSequence()>=record.sequence()&&(head.lastSequence()!=record.sequence()||head.lastHash().equals(record.recordHash())))
                        .switchIfEmpty(Mono.error(unavailable())).then(authority.revalidate(permit))
                        .then(resources.factory().requireBoundScope(resources,scope)).then(nativeContext.complete(nonce,record.sequence(),record.recordHash())).filter(nonce::equals).switchIfEmpty(Mono.error(unavailable()))
                        .then(completed.requireCompleted(scope,nonce,new TransactionalAuditAppender.ProvisionalAppend(record,true))).filter(record::equals).switchIfEmpty(Mono.error(unavailable()))
                        .then(authority.revalidate(permit)).then(Mono.fromCallable(()->{scope.remaining();return snapshot.content();})));
            });
    });}
    private static IllegalStateException unavailable(){return new IllegalStateException("Existing completed original evidence event and immutable paired content required");}
}
