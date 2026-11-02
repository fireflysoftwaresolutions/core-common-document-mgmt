/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload.impl;

import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.interfaces.upload.*;
import io.fireflysoftware.commons.ecm.models.entities.upload.*;
import io.fireflysoftware.commons.ecm.models.repositories.upload.EvidenceCustodyRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditRecord;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.data.r2dbc.core.*;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.query.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

/** Actual bytes, legacy metadata, immutable custody and canonical audit share the one owning root. */
public final class SpringDataEvidenceUploadStoreImpl implements EvidenceUploadStore {
    private final EvidenceUploadAuditRootImpl owner;
    private final R2dbcEntityTemplate template;
    private final EvidenceCustodyRepository repository;
    public SpringDataEvidenceUploadStoreImpl(EvidenceUploadAuditRootImpl owner){
        this.owner=Objects.requireNonNull(owner);this.template=owner.template();this.repository=new R2dbcRepositoryFactory(template).getRepository(EvidenceCustodyRepository.class);
    }
    @Override public Mono<EvidenceUploadReceipt> store(EvidenceUploadRequest request){
        final UploadedEvidenceIntent intent;try{intent=UploadedEvidenceIntent.capture(request);}catch(RuntimeException invalid){return Mono.error(invalid());}
        return owner.execute(intent,permit->repository.lockAction(permit.resourceTenantId(),intent.actionId()).filter(Boolean.TRUE::equals).switchIfEmpty(Mono.error(unavailable()))
            .flatMap(locked->retained(permit)).flatMap(existing->{
                if(existing.isPresent())return verified(intent,permit,existing.orElseThrow()).map(row->new EvidenceUploadAuditRootImpl.Plan<>(row.documentId(),row.auditEventId(),row.createdAt(),row.policyRevision(),row.effectDigest(),Optional.of(new EvidenceUploadAuditRootImpl.Replay<>(receipt(row,true),row.auditSequence(),row.auditRecordHash())),null));
                UUID documentId=UUID.randomUUID();Instant storedAt=owner.clock().instant().truncatedTo(ChronoUnit.MICROS);
                String effect=EvidenceUploadEffect.digest(intent,permit,documentId,storedAt);
                return Mono.just(new EvidenceUploadAuditRootImpl.Plan<EvidenceUploadReceipt>(documentId,UUID.randomUUID(),storedAt,permit.policyRevision(),effect,Optional.empty(),(nonce,record)->{
                    var content=new EvidenceContent(documentId,permit.resourceTenantId(),intent.content(),intent.contentDigest(),intent.length());
                    var custody=custody(intent,permit,documentId,storedAt,effect,nonce,record);
                    return insert(EvidenceDocumentMetadata.expected(documentId,intent.fileName(),intent.mediaType(),intent.length(),intent.contentDigest(),permit.actor().platformTenantId(),storedAt))
                        .then(insert(content)).then(insert(custody))
                        .then(retained(permit)).flatMap(read->read.isEmpty()?Mono.error(unavailable()):verified(intent,permit,read.orElseThrow()).map(loaded->{
                            if(!custody.equals(loaded))throw unavailable();return receipt(loaded,false);
                        }));
                }));
            }));
    }
    private Mono<Optional<EvidenceCustody>> retained(EvidenceUploadAdmission.Permit permit){
        return repository.findAllByResourceTenantIdAndActionId(permit.resourceTenantId(),permit.actionId()).take(2).collectList().map(rows->{
            if(rows.size()>1||rows.stream().anyMatch(r->!permit.resourceTenantId().equals(r.resourceTenantId())||!permit.actionId().equals(r.actionId())))throw unavailable();
            return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst());
        });
    }
    private Mono<EvidenceCustody> verified(UploadedEvidenceIntent intent,EvidenceUploadAdmission.Permit permit,EvidenceCustody row){return Mono.defer(()->{
        if(!valid(row.documentId())||!permit.resourceTenantId().equals(row.resourceTenantId())||!permit.actionId().equals(row.actionId())||!valid(row.auditContextNonce())||!valid(row.auditEventId())
                ||row.auditSequence()<1||row.auditRecordHash()==null||!row.auditRecordHash().matches("[0-9a-f]{64}")||row.createdAt()==null
                ||!permit.bindingId().equals(row.bindingId())||permit.bindingVersion()!=row.bindingVersion()||!permit.bindingDigest().equals(row.bindingDigest())
                ||!permit.actor().issuer().equals(row.actorIssuer())||!permit.actor().subject().equals(row.actorSubject())||!permit.actor().clientId().equals(row.actorClientId())
                ||!permit.actor().platformTenantId().equals(row.actorPlatformTenantId())||row.policyRevision()==null||row.policyRevision().isBlank())return Mono.error(unavailable());
        return template.selectOne(Query.query(Criteria.where("documentId").is(row.documentId()).and("resourceTenantId").is(row.resourceTenantId())),EvidenceContent.class)
            .switchIfEmpty(Mono.error(unavailable())).flatMap(content->{
                UploadedEvidenceIntent historical;
                try{historical=UploadedEvidenceIntent.capture(new EvidenceUploadRequest(row.actionId(),row.applicationId(),row.partyId(),row.fileName(),row.mediaType(),row.documentTypeCode(),content.content()));}
                catch(RuntimeException malformed){return Mono.error(unavailable());}
                if(!row.documentId().equals(content.documentId())||!row.resourceTenantId().equals(content.resourceTenantId())||row.contentLength()!=content.contentLength()
                        ||row.contentLength()!=historical.length()||!row.contentDigest().equals(content.contentDigest())||!row.contentDigest().equals(historical.contentDigest())||!row.intentDigest().equals(historical.digest()))return Mono.error(unavailable());
                if(!historical.digest().equals(intent.digest())||!Arrays.equals(historical.content(),intent.content()))return Mono.error(new EvidenceUploadConflict(intent.actionId()));
                var historicalPermit=new EvidenceUploadAdmission.Permit(row.actionId(),row.applicationId(),row.partyId(),row.intentDigest(),row.resourceTenantId(),permit.actor(),
                    row.bindingId(),row.bindingVersion(),row.bindingDigest(),row.policyRevision(),permit.observedAt(),permit.observedNanos(),permit.notAfter());
                if(!row.effectDigest().equals(EvidenceUploadEffect.digest(historical,historicalPermit,row.documentId(),row.createdAt())))return Mono.error(unavailable());
                var expected=EvidenceDocumentMetadata.expected(row.documentId(),row.fileName(),row.mediaType(),row.contentLength(),row.contentDigest(),row.actorPlatformTenantId(),row.createdAt());
                return template.selectOne(Query.query(Criteria.where("id").is(row.documentId())),EvidenceDocumentMetadata.class).filter(expected::equals).switchIfEmpty(Mono.error(unavailable())).thenReturn(row);
            });
    });}
    private <T> Mono<Void> insert(T row){return template.insert(row).switchIfEmpty(Mono.error(unavailable())).then();}
    private static EvidenceCustody custody(UploadedEvidenceIntent intent,EvidenceUploadAdmission.Permit permit,UUID document,Instant storedAt,String effect,UUID nonce,AuditRecord record){
        return new EvidenceCustody(document,permit.resourceTenantId(),intent.actionId(),intent.applicationId(),intent.partyId(),intent.fileName(),intent.mediaType(),intent.documentTypeCode(),intent.contentDigest(),intent.length(),intent.digest(),effect,
            permit.actor().issuer(),permit.actor().subject(),permit.actor().clientId(),permit.actor().platformTenantId(),permit.bindingId(),permit.bindingVersion(),permit.bindingDigest(),nonce,record.event().eventId(),record.sequence(),record.recordHash(),permit.policyRevision(),storedAt);
    }
    static EvidenceUploadReceipt receipt(EvidenceCustody row,boolean replayed){return new EvidenceUploadReceipt(row.actionId(),row.documentId(),row.applicationId(),row.partyId(),row.resourceTenantId(),row.contentDigest(),row.contentLength(),row.intentDigest(),row.fileName(),row.mediaType(),row.documentTypeCode(),row.createdAt(),replayed);}
    private static boolean valid(UUID id){return id!=null&&!id.equals(new UUID(0,0));}
    private static BusinessException invalid(){return new BusinessException(HttpStatus.BAD_REQUEST,"DOCUMENT_EVIDENCE_INPUT_INVALID","Exact bounded evidence content and identity required");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Complete current immutable document evidence required");}
}
