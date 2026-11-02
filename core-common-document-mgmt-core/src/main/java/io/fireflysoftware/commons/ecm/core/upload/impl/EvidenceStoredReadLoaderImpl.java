/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload.impl;

import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.interfaces.upload.EvidenceUploadRequest;
import io.fireflysoftware.commons.ecm.models.entities.upload.*;
import io.fireflysoftware.commons.ecm.models.repositories.upload.EvidenceCustodyRepository;
import java.util.*;
import org.fireflyframework.kernel.audit.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.relational.core.query.*;
import reactor.core.publisher.Mono;

/** Mapped immutable business reads only. The caller must subsequently prove the original canonical pair in the same Root. */
final class EvidenceStoredReadLoaderImpl {
    record Snapshot(EvidenceCustody custody,AuditEvent event,EvidenceContentRead content){@Override public String toString(){return "EvidenceStoredReadSnapshot[redacted]";}}
    private final R2dbcEntityTemplate template;private final EvidenceCustodyRepository repository;
    EvidenceStoredReadLoaderImpl(R2dbcEntityTemplate template){this.template=Objects.requireNonNull(template);repository=new R2dbcRepositoryFactory(template).getRepository(EvidenceCustodyRepository.class);}
    Mono<Snapshot> load(EvidenceReadAdmission.Permit permit){return repository.findAllByResourceTenantIdAndActionIdAndApplicationIdAndPartyId(permit.resourceTenantId(),permit.identity().actionId(),permit.identity().applicationId(),permit.identity().partyId()).take(2).collectList().flatMap(rows->{
        if(rows.size()!=1)return Mono.error(unavailable());var row=rows.getFirst();var identity=permit.identity();var actor=permit.actor();
        if(!identity.actionId().equals(row.actionId())||!identity.applicationId().equals(row.applicationId())||!identity.partyId().equals(row.partyId())||!permit.resourceTenantId().equals(row.resourceTenantId())
            ||!valid(row.documentId())||!valid(row.auditEventId())||row.auditSequence()<1||!digest(row.auditRecordHash())||row.createdAt()==null
            ||!permit.bindingId().equals(row.bindingId())||permit.bindingVersion()!=row.bindingVersion()||!permit.bindingDigest().equals(row.bindingDigest())
            ||!actor.issuer().equals(row.actorIssuer())||!actor.subject().equals(row.actorSubject())||!actor.clientId().equals(row.actorClientId())||!actor.platformTenantId().equals(row.actorPlatformTenantId())
            ||row.policyRevision()==null||!row.policyRevision().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))return Mono.error(unavailable());
        return template.selectOne(Query.query(Criteria.where("documentId").is(row.documentId()).and("resourceTenantId").is(row.resourceTenantId())),EvidenceContent.class)
            .switchIfEmpty(Mono.error(unavailable())).flatMap(content->{
                var original=UploadedEvidenceIntent.capture(new EvidenceUploadRequest(row.actionId(),row.applicationId(),row.partyId(),row.fileName(),row.mediaType(),row.documentTypeCode(),content.content()));
                if(!row.documentId().equals(content.documentId())||!row.resourceTenantId().equals(content.resourceTenantId())||row.contentLength()!=content.contentLength()||row.contentLength()!=original.length()
                    ||!row.contentDigest().equals(content.contentDigest())||!row.contentDigest().equals(original.contentDigest())||!row.intentDigest().equals(original.digest())
                    ||!row.effectDigest().equals(EvidenceUploadEffect.digest(original.digest(),row.resourceTenantId(),row.documentId(),row.bindingId(),row.bindingVersion(),row.bindingDigest(),actor,row.policyRevision(),row.createdAt())))return Mono.error(unavailable());
                var expected=EvidenceDocumentMetadata.expected(row.documentId(),row.fileName(),row.mediaType(),row.contentLength(),row.contentDigest(),row.actorPlatformTenantId(),row.createdAt());
                var event=new AuditEvent(row.auditEventId(),new AuditStream(row.resourceTenantId(),"core-document-management","document",row.documentId()),actor,"document.evidence.upload",AuditEvent.Outcome.COMPLETED,row.actionId(),row.actionId(),null,row.createdAt(),row.effectDigest(),row.policyRevision());
                var verified=new EvidenceContentRead(SpringDataEvidenceUploadStoreImpl.receipt(row,true),content.content());
                return template.selectOne(Query.query(Criteria.where("id").is(row.documentId())),EvidenceDocumentMetadata.class).filter(expected::equals).switchIfEmpty(Mono.error(unavailable())).thenReturn(new Snapshot(row,event,verified));
            });
    });}
    private static boolean valid(UUID value){return value!=null&&!value.equals(new UUID(0,0));}
    private static boolean digest(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Exact immutable stored evidence readback required");}
}
