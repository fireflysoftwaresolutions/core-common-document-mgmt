/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation.impl;

import java.time.*;
import java.util.*;
import java.util.function.*;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import io.fireflysoftware.commons.ecm.models.entities.generation.*;
import io.fireflysoftware.commons.ecm.models.repositories.generation.*;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.core.transaction.impl.DefaultBoundedRootTransaction;
import org.fireflyframework.core.audit.TransactionalAuditAppender;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.TransactionDefinition;
import reactor.core.publisher.Mono;

/** Opt-in owner root; no provider call, legacy tenant fallback, or implicit audit authority. */
public final class SpringDataDocumentGenerationCommitService implements DocumentGenerationCommitService {
    private static final Duration LIMIT=Duration.ofSeconds(5);
    private final BoundedR2dbcResources resources;
    private final R2dbcEntityTemplate template;
    private final BoundedRootTransaction root;
    private final TransactionalAuditAppender audit;
    private final DocumentGenerationAdmission admission;
    private final DocumentGenerationNativeRepository nativeContext;
    private final DocumentLocalIssuanceRepository issuances;
    private final Clock clock;
    private final LongSupplier nanos;
    private final Supplier<UUID> nonces;
    public SpringDataDocumentGenerationCommitService(BoundedR2dbcResources resources,DatabaseClient client,
            R2dbcEntityTemplate template,R2dbcTransactionManager manager,CleanupPolicy cleanup,
            TransactionalAuditAppender audit,DocumentGenerationAdmission admission,Clock clock,
            LongSupplier nanos,Supplier<UUID> nonces) {
        this.resources=BoundedR2dbcResources.verify(Objects.requireNonNull(resources).factory(),client,template,manager);
        this.template=template;this.audit=Objects.requireNonNull(audit);this.admission=Objects.requireNonNull(admission);
        this.clock=Objects.requireNonNull(clock);this.nanos=Objects.requireNonNull(nanos);this.nonces=Objects.requireNonNull(nonces);
        this.root=new DefaultBoundedRootTransaction(this.resources,Objects.requireNonNull(cleanup));
        var repositories=new R2dbcRepositoryFactory(template);
        nativeContext=repositories.getRepository(DocumentGenerationNativeRepository.class);
        issuances=repositories.getRepository(DocumentLocalIssuanceRepository.class);
    }
    @Override public Mono<DocumentGenerationCommitService.LocalCommit> store(DocumentGenerationStorage.Request request,R2dbcOperationBudget original) {
        return Mono.defer(()->{
            Objects.requireNonNull(original);original.remaining();
            String intent=DocumentStorageIntent.json(request),digest=DocumentStorageIntent.digest(request);
            if(!digest.equals(request.event().evidenceDigest())||request.event().occurredAt().isAfter(clock.instant()))throw failure();
            request.snapshot().budget().remaining();
            long before=nanos.getAsLong();Instant start=clock.instant();
            var bounded=original.intersect(request.snapshot().budget()).intersect(
                    R2dbcOperationBudget.observed(start,start.plus(LIMIT),before,LIMIT,clock,nanos));
            return admission.requireCurrent(request,digest,original).switchIfEmpty(Mono.error(failure()))
                    .timeout(bounded.remaining()).flatMap(permit->{
                checked(request,digest,permit);bounded.remaining();
                Instant until=min(permit.until(),request.snapshot().effectiveUntil(),start.plus(LIMIT),clock.instant().plus(bounded.remaining()));
                var inherited=bounded.intersect(R2dbcOperationBudget.observed(start,until,before,LIMIT,clock,nanos));
                var definition=new DefaultTransactionDefinition();definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);definition.setReadOnly(false);
                return root.execute(inherited,definition,scope->append(request,intent,digest,permit,scope))
                        .map(completed->{
                            var facts=completed.transactionFacts();
                            if(!facts.nativeAttempted()||!facts.commitAcknowledged()||!facts.boundaryCompleted()
                                    ||facts.rollbackAcknowledged()||completed.cleanupFact()!=CleanupFact.COMPLETE)throw failure();
                            return completed.value();
                        });
            });
        });
    }
    private Mono<DocumentGenerationCommitService.LocalCommit> append(DocumentGenerationStorage.Request request,String intent,
            String digest,DocumentGenerationAdmission.Permit permit,RootTransactionScope scope) {
        return resources.factory().requireBoundScope(resources,scope).then(Mono.defer(()->{
            scope.remaining();
            // No business read precedes this exact mandatory same-root append.
            return audit.append(scope,request.event()).switchIfEmpty(Mono.error(failure())).timeout(scope.remaining()).flatMap(appended->{
                if(appended.record()==null||!request.event().equals(appended.record().event()))return Mono.error(failure());
                UUID nonce=Objects.requireNonNull(nonces.get());if(nonce.equals(new UUID(0,0)))return Mono.error(failure());
                long before=nanos.getAsLong();
                return nativeContext.enter(permit.grantId(),nonce,request.resourceTenantId(),request.documentId(),request.event().eventId(),digest)
                        .switchIfEmpty(Mono.error(failure())).timeout(scope.remaining()).flatMap(stamp->{
                    if(!nonce.equals(stamp.contextNonce())||stamp.dbNowMillis()==null||stamp.untilMillis()==null
                            ||stamp.newIssuance()==null||stamp.newIssuance()==appended.replay()
                            ||stamp.untilMillis()>permit.until().toEpochMilli()||stamp.untilMillis()-1<=stamp.dbNowMillis())throw failure();
                    scope.narrow(R2dbcOperationBudget.observed(Instant.ofEpochMilli(stamp.dbNowMillis()),
                            Instant.ofEpochMilli(stamp.untilMillis()-1),before,LIMIT,clock,nanos));
                    return issuances.findByResourceTenantIdAndId(request.resourceTenantId(),request.documentId()).flatMap(existing->{
                        if(stamp.newIssuance()||!request.event().eventId().equals(existing.eventId())
                                ||!digest.equals(existing.requestDigest())||!"LOCAL_STORED".equals(existing.state()))return Mono.error(failure());
                        return Mono.just(local(request,digest,DocumentStorageIntent.contentDigest(request.renderedContent())));
                    }).switchIfEmpty(Mono.defer(()->{
                        if(!stamp.newIssuance())return Mono.error(failure());
                        return inserts(request,intent,digest,scope);
                    })).flatMap(value->resources.factory().requireBoundScope(resources,scope)
                            .then(nativeContext.complete(nonce)).filter(nonce::equals).switchIfEmpty(Mono.error(failure()))
                            .timeout(scope.remaining()).map(done->{scope.remaining();return value;}));
                });
            });
        })).onErrorResume(error->resources.factory().markBoundScopeRollbackOnly(resources,scope)
                .onErrorResume(markFailure->Mono.error(error)).then(Mono.error(error)));
    }
    private Mono<DocumentGenerationCommitService.LocalCommit> inserts(DocumentGenerationStorage.Request request,String intent,String digest,RootTransactionScope scope) {
        UUID id=request.documentId(),tenant=request.resourceTenantId(),event=request.event().eventId();
        var document=new GeneratedDocument(id,tenant,request.snapshot().mediaType(),request.renderedContent(),DocumentStorageIntent.contentDigest(request.renderedContent()));
        return insert(document,scope).then(insert(new GeneratedDocumentMetadata(id,tenant,intent,digest),scope))
                .then(insert(new DocumentLocalIssuance(id,tenant,event,digest,"LOCAL_STORED"),scope))
                .then(insert(new DocumentProviderIntent(id,tenant,event,digest,"PENDING_PROVIDER"),scope))
                .then(Mono.fromSupplier(()->{scope.remaining();return local(request,digest,document.contentDigest());}));
    }
    private <T> Mono<T> insert(T row,RootTransactionScope scope) {
        return Mono.defer(()->resources.factory().requireBoundScope(resources,scope).then(template.insert(row))
                .timeout(scope.remaining()).map(inserted->{scope.remaining();return inserted;}));
    }
    private void checked(DocumentGenerationStorage.Request request,String digest,DocumentGenerationAdmission.Permit permit) {
        if(permit==null||permit.grantId()==null||permit.grantId().equals(new UUID(0,0))
                ||!request.resourceTenantId().equals(permit.resourceTenantId())||!request.documentId().equals(permit.documentId())
                ||!request.event().eventId().equals(permit.eventId())||!digest.equals(permit.requestDigest())
                ||!request.event().actor().equals(permit.actor())||!request.event().policyRevision().equals(permit.policyRevision())
                ||permit.until()==null||!permit.until().isAfter(clock.instant())||permit.until().isAfter(clock.instant().plus(LIMIT))
                ||permit.until().isAfter(request.snapshot().effectiveUntil()))throw failure();
    }
    private static DocumentGenerationCommitService.LocalCommit local(DocumentGenerationStorage.Request request,String digest,String contentDigest) {
        return new DocumentGenerationCommitService.LocalCommit(new DocumentGenerationStorage.StoredDocument(request.resourceTenantId(),request.documentId(),contentDigest),
                request.event().eventId(),digest,DocumentGenerationCommitService.ProviderState.PENDING_PROVIDER);
    }
    private static Instant min(Instant...values){return Arrays.stream(values).min(Instant::compareTo).orElseThrow();}
    private static IllegalStateException failure(){return new IllegalStateException("Current admitted document storage required");}
}
