/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing.impl;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.fireflysoftware.commons.ecm.core.signing.*;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureType;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureFormat;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureStatus;
import io.fireflysoftware.commons.ecm.models.entities.Document;
import io.fireflysoftware.commons.ecm.models.entities.DocumentVersion;
import io.fireflysoftware.commons.ecm.models.entities.DocumentSignature;
import io.fireflysoftware.commons.ecm.models.entities.SignatureProvider;
import io.fireflysoftware.commons.ecm.models.entities.signing.*;
import io.fireflysoftware.commons.ecm.models.repositories.signing.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;

/** All business reads/writes use mapped Spring Data; optional schema installation is external. */
public final class SpringDataSigningPreparationStore implements SigningPreparationStore {
 private final SigningAccountBindingRepository accounts;
 private final SigningEnvelopeRepository envelopes;
 private final SigningOperationRepository operations;
 private final SigningRequestBindingRepository requests;
 private final SigningDocumentRepository documents;
 private final SigningDocumentVersionRepository versions;
 private final SigningProviderRepository providers;
 private final SigningSignatureRepository signatures;
 private final R2dbcEntityTemplate template;
 private final SigningAuditedPhase audit;
 private final Clock clock;
 private final boolean policyCustody;
 /** Compatibility constructor cannot authorize new business writes without mandatory audit resources. */
 public SpringDataSigningPreparationStore(SigningAccountBindingRepository accounts,SigningEnvelopeRepository envelopes,
       SigningOperationRepository operations,SigningRequestBindingRepository requests,SigningDocumentRepository documents,
       SigningDocumentVersionRepository versions,SigningProviderRepository providers,SigningSignatureRepository signatures,
       R2dbcEntityTemplate template,SigningTransactions transactions,Clock clock){
  this(accounts,envelopes,operations,requests,documents,versions,providers,signatures,template,(SigningAuditedPhase)null,clock);
  Objects.requireNonNull(transactions);
 }
 public SpringDataSigningPreparationStore(org.fireflyframework.core.transaction.BoundedR2dbcResources resources,
       org.springframework.r2dbc.core.DatabaseClient client,R2dbcEntityTemplate template,
       org.springframework.r2dbc.connection.R2dbcTransactionManager manager,org.fireflyframework.core.transaction.CleanupPolicy cleanup,
       org.fireflyframework.core.audit.AuditAppendAuthority appendAuthority,SigningAuditAdmission admission,Clock clock,java.util.function.LongSupplier nanos){
  this(new org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory(template),template,
      new SigningAuditedPhase(resources,client,template,manager,cleanup,appendAuthority,admission,clock,nanos),clock);
 }
 private SpringDataSigningPreparationStore(org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory factory,
       R2dbcEntityTemplate template,SigningAuditedPhase audit,Clock clock){
  this(factory.getRepository(SigningAccountBindingRepository.class),factory.getRepository(SigningEnvelopeRepository.class),
       factory.getRepository(SigningOperationRepository.class),factory.getRepository(SigningRequestBindingRepository.class),
       factory.getRepository(SigningDocumentRepository.class),factory.getRepository(SigningDocumentVersionRepository.class),
       factory.getRepository(SigningProviderRepository.class),factory.getRepository(SigningSignatureRepository.class),template,audit,clock);
 }
 SpringDataSigningPreparationStore(SigningAccountBindingRepository accounts,SigningEnvelopeRepository envelopes,
       SigningOperationRepository operations,SigningRequestBindingRepository requests,SigningDocumentRepository documents,
       SigningDocumentVersionRepository versions,SigningProviderRepository providers,SigningSignatureRepository signatures,
       R2dbcEntityTemplate template,SigningAuditedPhase audit,Clock clock){
  this(accounts,envelopes,operations,requests,documents,versions,providers,signatures,template,audit,clock,false);
 }
 private SpringDataSigningPreparationStore(SigningAccountBindingRepository accounts,SigningEnvelopeRepository envelopes,
       SigningOperationRepository operations,SigningRequestBindingRepository requests,SigningDocumentRepository documents,
       SigningDocumentVersionRepository versions,SigningProviderRepository providers,SigningSignatureRepository signatures,
       R2dbcEntityTemplate template,SigningAuditedPhase audit,Clock clock,boolean policyCustody){
  this.policyCustody=policyCustody;
  this.accounts=Objects.requireNonNull(accounts);this.envelopes=Objects.requireNonNull(envelopes);this.operations=Objects.requireNonNull(operations);this.requests=Objects.requireNonNull(requests);
  this.documents=Objects.requireNonNull(documents);this.versions=Objects.requireNonNull(versions);this.providers=Objects.requireNonNull(providers);this.signatures=Objects.requireNonNull(signatures);
  this.template=Objects.requireNonNull(template);this.audit=audit;this.clock=Objects.requireNonNull(clock);
  if(audit!=null)audit.requireTemplate(template);
 }
 public static SpringDataSigningPreparationStore policyCustody(org.fireflyframework.core.transaction.BoundedR2dbcResources resources,
       org.springframework.r2dbc.core.DatabaseClient client,R2dbcEntityTemplate template,
       org.springframework.r2dbc.connection.R2dbcTransactionManager manager,org.fireflyframework.core.transaction.CleanupPolicy cleanup,
       org.fireflyframework.core.audit.AuditAppendAuthority appendAuthority,SigningAuditAdmission admission,Clock clock,java.util.function.LongSupplier nanos){
  if((Object)appendAuthority!=admission||!(admission instanceof SigningPolicyTracePort))throw new IllegalArgumentException("One trusted signing admission trace and append runtime required");
  var audit=new SigningAuditedPhase(resources,client,template,manager,cleanup,appendAuthority,admission,clock,nanos);
  var factory=new org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory(template);
  return new SpringDataSigningPreparationStore(factory.getRepository(SigningAccountBindingRepository.class),factory.getRepository(SigningEnvelopeRepository.class),
       factory.getRepository(SigningOperationRepository.class),factory.getRepository(SigningRequestBindingRepository.class),factory.getRepository(SigningDocumentRepository.class),
       factory.getRepository(SigningDocumentVersionRepository.class),factory.getRepository(SigningProviderRepository.class),factory.getRepository(SigningSignatureRepository.class),template,audit,clock,true);
 }
 private void requireAudit(){if(audit==null)throw new IllegalStateException("Paired durable signing audit resources required");}
 @Override public Mono<Ticket> begin(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority){
  if(policyCustody)return beginPolicy(intent,account,authority);
  return Mono.defer(()->{
   requireAudit();requireAuthority(intent,account,authority);authority.requireLive(clock);
   UUID operation=UUID.randomUUID(),envelope=UUID.randomUUID(),request=UUID.randomUUID(),event=UUID.randomUUID();
   return audit.execute(intent,account,authority,()->selection(intent,account).map(stamp->{
    String capturedAuthorityJson=authorityJson(authority);
    String capturedAuthorityDigest=SigningDigest.sha256(capturedAuthorityJson.getBytes(StandardCharsets.UTF_8));
    var evidence=new SigningAuditEvidence(operation,envelope,intent,account,stamp.fingerprint(),capturedAuthorityDigest,null,event,null);
    return new SigningAuditedPhase.Plan<>(evidence,receipt->Mono.defer(()->{
     authority.requireLive(clock);var created=clock.instant();var recorded=receipt.record().event();
     String json=intent.canonicalJson(),digest=SigningDigest.sha256(json.getBytes(StandardCharsets.UTF_8)),authorityJson=capturedAuthorityJson;
     var envelopeRow=new SigningEnvelope(envelope,intent.tenantId(),intent.providerId(),account.bindingId(),account.bindingVersion(),account.descriptor().accountId(),account.descriptor().endpoint(),
       intent.signatureId(),intent.documentId(),intent.documentVersionId(),"PREPARING",null,created,null);
     var operationRow=new SigningOperation(operation,envelope,intent.tenantId(),intent.providerId(),account.bindingId(),account.bindingVersion(),intent.signatureId(),intent.documentId(),intent.documentVersionId(),
       "PREPARING",0,stamp.fingerprint(),json,digest,authorityJson,SigningDigest.sha256(authorityJson.getBytes(StandardCharsets.UTF_8)),authority.notAfter(),stamp.expectedLength(),stamp.mediaType(),null,null,null,created,null,
       recorded.stream().resourceTenantId(),recorded.processId(),recorded.correlationId(),recorded.policyRevision(),recorded.eventId(),null);
     var requestRow=new SigningRequestBinding(request,envelope,operation,intent.tenantId(),intent.providerId(),account.bindingId(),account.bindingVersion(),intent.signatureId(),intent.documentId(),intent.documentVersionId(),null,1,null,created,null);
     return envelopes.save(envelopeRow).flatMap(saved->Mono.defer(()->{authority.requireLive(clock);return operations.save(operationRow);}))
       .flatMap(saved->Mono.defer(()->{authority.requireLive(clock);return requests.save(requestRow);}))
       .map(saved->{authority.requireLive(clock);return new Ticket(operation,envelope,request,0,intent,account,authority,stamp,receipt.record());});
    }));
   }));
  }).onErrorMap(SpringDataSigningPreparationStore::safeFailure);
 }
 @Override public Mono<PreparedSigningDraft> complete(Ticket ticket,SigningDocumentSnapshot snapshot,SigningAuthority current){
  if(policyCustody)return completePolicy(ticket,snapshot,current);
  return Mono.defer(()->{
   requireAudit();if(ticket.reservationAudit()==null)throw new SecurityException("Exact audited reservation required");
   var intent=ticket.intent();var account=ticket.account();requireAuthority(intent,account,current);current.requireLive(clock);ticket.authority().requireLive(clock);
   if(!ticket.authority().actor().equals(current.actor())||!ticket.authority().binding().bindingId().equals(current.binding().bindingId())
      ||ticket.authority().binding().version()!=current.binding().version()||!ticket.authority().binding().definitionDigest().equals(current.binding().definitionDigest())
      ||current.notAfter().isAfter(ticket.authority().notAfter())||!intent.documentId().equals(snapshot.documentId())||!intent.documentVersionId().equals(snapshot.documentVersionId())
      ||snapshot.contentLength()!=ticket.source().expectedLength()||!snapshot.fileName().equals(ticket.source().fileName())||!snapshot.mediaType().equals(ticket.source().mediaType()))throw stale();
   UUID event=UUID.randomUUID();
   return audit.execute(intent,account,current,()->selection(intent,account).flatMap(stamp->{
    if(!stamp.equals(ticket.source()))return Mono.error(stale());
    return requestBinding(ticket).map(persisted->{
    String initialAuthorityJson=authorityJson(ticket.authority());
    String capturedAuthorityDigest=SigningDigest.sha256(initialAuthorityJson.getBytes(StandardCharsets.UTF_8));
    var evidence=new SigningAuditEvidence(ticket.operationId(),ticket.envelopeId(),intent,account,stamp.fingerprint(),capturedAuthorityDigest,snapshot.sha256(),event,ticket.reservationAudit().event());
    return new SigningAuditedPhase.Plan<>(evidence,receipt->Mono.defer(()->{
    current.requireLive(clock);ticket.authority().requireLive(clock);
    var previous=ticket.reservationAudit().event();
    Criteria fence=Criteria.where("id").is(ticket.operationId()).and("envelopeId").is(ticket.envelopeId()).and("tenantId").is(intent.tenantId())
      .and("providerId").is(intent.providerId()).and("documentSignatureId").is(intent.signatureId()).and("documentId").is(intent.documentId()).and("documentVersionId").is(intent.documentVersionId())
      .and("accountBindingId").is(account.bindingId()).and("accountBindingVersion").is(account.bindingVersion()).and("state").is("PREPARING").and("generation").is(ticket.generation())
      .and("sourceFingerprint").is(stamp.fingerprint()).and("intentDigest").is(SigningDigest.sha256(intent.canonicalJson().getBytes(StandardCharsets.UTF_8)))
      .and("authorityDigest").is(SigningDigest.sha256(initialAuthorityJson.getBytes(StandardCharsets.UTF_8))).and("authorityUntil").is(ticket.authority().notAfter())
      .and("auditResourceTenantId").is(previous.stream().resourceTenantId()).and("auditProcessId").is(previous.processId()).and("auditCorrelationId").is(previous.correlationId())
      .and("auditPolicyRevision").is(previous.policyRevision()).and("reservationAuditEventId").is(previous.eventId()).and("preparedAuditEventId").isNull();
    Update snapshotWrite=Update.update("state","PREPARED").set("generation",ticket.generation()+1).set("content",snapshot.content()).set("contentDigest",snapshot.sha256()).set("contentLength",(long)snapshot.contentLength()).set("preparedAuditEventId",receipt.record().event().eventId());
    return template.update(Query.query(fence),snapshotWrite,SigningOperation.class).flatMap(count->{
     if(count!=1)return Mono.error(stale());current.requireLive(clock);
     Criteria envelopeFence=Criteria.where("id").is(ticket.envelopeId()).and("tenantId").is(intent.tenantId()).and("providerId").is(intent.providerId())
       .and("accountBindingId").is(account.bindingId()).and("accountBindingVersion").is(account.bindingVersion()).and("documentSignatureId").is(intent.signatureId())
       .and("documentId").is(intent.documentId()).and("documentVersionId").is(intent.documentVersionId()).and("state").is("PREPARING");
     return template.update(Query.query(envelopeFence),Update.update("state","PREPARED"),SigningEnvelope.class).flatMap(envelopesChanged->{
      if(envelopesChanged!=1)return Mono.error(stale());current.requireLive(clock);
      return Mono.just(new PreparedSigningDraft(ticket.operationId(),ticket.envelopeId(),persisted.id(),ticket.generation()+1,"PREPARED",snapshot.sha256()));
     });
    });
    }));
    });
   }));
  }).onErrorMap(SpringDataSigningPreparationStore::safeFailure);
 }
 private Mono<Ticket> beginPolicy(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority){
  return Mono.defer(()->{
   requireAudit();requireAuthority(intent,account,authority);authority.requireLive(clock);
   UUID operation=UUID.randomUUID(),envelope=UUID.randomUUID(),request=UUID.randomUUID(),event=UUID.randomUUID();
   return audit.executeV3(intent,account,authority,()->selection(intent,account).map(stamp->{
    String capturedAuthorityJson=authorityJson(authority);
    String capturedAuthorityDigest=SigningDigest.sha256(capturedAuthorityJson.getBytes(StandardCharsets.UTF_8));
    var evidence=new SigningAuditEvidence(operation,envelope,intent,account,stamp.fingerprint(),capturedAuthorityDigest,null,event,null);
    return new SigningAuditedPhase.PlanV3<>(evidence,receipt->Mono.defer(()->{
     authority.requireLive(clock);var created=clock.instant();var recorded=receipt.record().event();
     String json=intent.canonicalJson(),digest=SigningDigest.sha256(json.getBytes(StandardCharsets.UTF_8)),authorityJson=capturedAuthorityJson;
     var envelopeRow=new SigningEnvelope(envelope,intent.tenantId(),intent.providerId(),account.bindingId(),account.bindingVersion(),account.descriptor().accountId(),account.descriptor().endpoint(),
       intent.signatureId(),intent.documentId(),intent.documentVersionId(),"PREPARING",null,created,null);
     var operationRow=new SigningOperation(operation,envelope,intent.tenantId(),intent.providerId(),account.bindingId(),account.bindingVersion(),intent.signatureId(),intent.documentId(),intent.documentVersionId(),
       "PREPARING",0,stamp.fingerprint(),json,digest,authorityJson,SigningDigest.sha256(authorityJson.getBytes(StandardCharsets.UTF_8)),authority.notAfter(),stamp.expectedLength(),stamp.mediaType(),null,null,null,created,0L,
       recorded.stream().resourceTenantId(),recorded.processId(),recorded.correlationId(),recorded.policyRevision(),recorded.eventId(),null);
     var requestRow=new SigningRequestBinding(request,envelope,operation,intent.tenantId(),intent.providerId(),account.bindingId(),account.bindingVersion(),intent.signatureId(),intent.documentId(),intent.documentVersionId(),null,1,null,created,null);
     return envelopes.save(envelopeRow).flatMap(saved->Mono.defer(()->{authority.requireLive(clock);return insertPolicyOperation(operationRow,receipt.trace());}))
       .flatMap(saved->Mono.defer(()->{authority.requireLive(clock);return requests.save(requestRow);}))
       .map(saved->{authority.requireLive(clock);return new Ticket(operation,envelope,request,0,intent,account,authority,stamp,receipt.record());});
    }));
   }));
  }).onErrorMap(SpringDataSigningPreparationStore::safeFailure);
 }
 private Mono<PreparedSigningDraft> completePolicy(Ticket ticket,SigningDocumentSnapshot snapshot,SigningAuthority current){
  return Mono.defer(()->{
   requireAudit();if(ticket.reservationAudit()==null)throw new SecurityException("Exact audited reservation required");
   var intent=ticket.intent();var account=ticket.account();requireAuthority(intent,account,current);current.requireLive(clock);ticket.authority().requireLive(clock);
   if(!ticket.authority().actor().equals(current.actor())||!ticket.authority().binding().bindingId().equals(current.binding().bindingId())
      ||ticket.authority().binding().version()!=current.binding().version()||!ticket.authority().binding().definitionDigest().equals(current.binding().definitionDigest())
      ||current.notAfter().isAfter(ticket.authority().notAfter())||!intent.documentId().equals(snapshot.documentId())||!intent.documentVersionId().equals(snapshot.documentVersionId())
      ||snapshot.contentLength()!=ticket.source().expectedLength()||!snapshot.fileName().equals(ticket.source().fileName())||!snapshot.mediaType().equals(ticket.source().mediaType()))throw stale();
   UUID event=UUID.randomUUID();
   return audit.executeV3(intent,account,current,()->selection(intent,account).flatMap(stamp->{
    if(!stamp.equals(ticket.source()))return Mono.error(stale());
    return requestBinding(ticket).flatMap(persisted->reservationPolicy(ticket).map(reserved->{
    String initialAuthorityJson=authorityJson(ticket.authority());
    String capturedAuthorityDigest=SigningDigest.sha256(initialAuthorityJson.getBytes(StandardCharsets.UTF_8));
    var evidence=new SigningAuditEvidence(ticket.operationId(),ticket.envelopeId(),intent,account,stamp.fingerprint(),capturedAuthorityDigest,snapshot.sha256(),event,ticket.reservationAudit().event());
    return new SigningAuditedPhase.PlanV3<>(evidence,receipt->Mono.defer(()->{
    current.requireLive(clock);ticket.authority().requireLive(clock);
    var previous=ticket.reservationAudit().event();
    Criteria fence=Criteria.where("id").is(ticket.operationId()).and("envelopeId").is(ticket.envelopeId()).and("tenantId").is(intent.tenantId())
      .and("providerId").is(intent.providerId()).and("documentSignatureId").is(intent.signatureId()).and("documentId").is(intent.documentId()).and("documentVersionId").is(intent.documentVersionId())
      .and("accountBindingId").is(account.bindingId()).and("accountBindingVersion").is(account.bindingVersion()).and("state").is("PREPARING").and("generation").is(ticket.generation())
      .and("sourceFingerprint").is(stamp.fingerprint()).and("intentDigest").is(SigningDigest.sha256(intent.canonicalJson().getBytes(StandardCharsets.UTF_8)))
      .and("authorityDigest").is(SigningDigest.sha256(initialAuthorityJson.getBytes(StandardCharsets.UTF_8))).and("authorityUntil").is(ticket.authority().notAfter())
      .and("auditResourceTenantId").is(previous.stream().resourceTenantId()).and("auditProcessId").is(previous.processId()).and("auditCorrelationId").is(previous.correlationId())
      .and(policyColumn("evidenceProtocol")).is(3).and(policyColumn("reservationTraceDigest")).is(reserved.traceDigest()).and(policyColumn("preparedTraceDigest")).isNull()
      .and("auditPolicyRevision").is(previous.policyRevision()).and("reservationAuditEventId").is(previous.eventId()).and("preparedAuditEventId").isNull();
    Update snapshotWrite=Update.update("state","PREPARED").set("generation",ticket.generation()+1).set("content",snapshot.content()).set("contentDigest",snapshot.sha256()).set("contentLength",(long)snapshot.contentLength()).set("preparedAuditEventId",receipt.record().event().eventId()).set(policyColumn("preparedTraceDigest"),receipt.trace().digest());
    return template.update(Query.query(fence),snapshotWrite,SigningOperation.class).flatMap(count->{
     if(count!=1)return Mono.error(stale());current.requireLive(clock);
     Criteria envelopeFence=Criteria.where("id").is(ticket.envelopeId()).and("tenantId").is(intent.tenantId()).and("providerId").is(intent.providerId())
       .and("accountBindingId").is(account.bindingId()).and("accountBindingVersion").is(account.bindingVersion()).and("documentSignatureId").is(intent.signatureId())
       .and("documentId").is(intent.documentId()).and("documentVersionId").is(intent.documentVersionId()).and("state").is("PREPARING");
     return template.update(Query.query(envelopeFence),Update.update("state","PREPARED"),SigningEnvelope.class).flatMap(envelopesChanged->{
      if(envelopesChanged!=1)return Mono.error(stale());current.requireLive(clock);
      return Mono.just(new PreparedSigningDraft(ticket.operationId(),ticket.envelopeId(),persisted.id(),ticket.generation()+1,"PREPARED",snapshot.sha256()));
     });
    });
    }));
    }));
   }));
  }).onErrorMap(SpringDataSigningPreparationStore::safeFailure);
 }
 private Mono<SigningOperation> insertPolicyOperation(SigningOperation row,SigningPolicyTraceV1 trace){
  if(trace.phase()!=SigningPolicyTraceV1.Phase.RESERVE)throw stale();
  var projection=new SigningOperationPolicy(row.id(),row.tenantId(),row.auditResourceTenantId(),3,trace.digest(),null);
  var strategy=template.getDataAccessStrategy();
  var columns=strategy.getOutboundRow(row);var policyColumns=strategy.getOutboundRow(projection);
  for(var column:policyColumns.keySet()){
   if(columns.containsKey(column)){
    if(!Objects.equals(columns.get(column),policyColumns.get(column)))throw stale();
   }else columns.put(column,policyColumns.get(column));
  }
  var mapper=strategy.getStatementMapper().forType(SigningOperation.class);
  org.springframework.data.r2dbc.core.StatementMapper.InsertSpec insert=mapper.createInsert(strategy.getTableName(SigningOperation.class));
  for(var column:columns.keySet())insert=insert.withColumn(column,columns.get(column));
  return template.getDatabaseClient().sql(mapper.getMappedObject(insert)).fetch().rowsUpdated()
    .switchIfEmpty(Mono.error(stale())).flatMap(count->count==1?Mono.just(row):Mono.error(stale()));
 }
 private String policyColumn(String property){
  return template.getConverter().getMappingContext().getRequiredPersistentEntity(SigningOperationPolicy.class).getRequiredPersistentProperty(property).getColumnName().getReference();
 }
 private Mono<SigningPolicyCustody> reservationPolicy(Ticket ticket){
  var previous=ticket.reservationAudit();var event=previous.event();
  if(!org.fireflyframework.kernel.audit.AuditRecordHasher.record(new org.fireflyframework.kernel.audit.AuditCheckpoint(event.stream(),previous.sequence()-1,previous.previousHash()),event,previous.recordedAt()).equals(previous))return Mono.error(stale());
  var query=Query.query(Criteria.where("id").is(ticket.operationId()).and("tenantId").is(ticket.intent().tenantId()).and("resourceTenantId").is(event.stream().resourceTenantId()));
  var repository=new org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory(template).getRepository(SigningPolicyCustodyRepository.class);
  return template.select(SigningOperationPolicy.class).matching(query).one().switchIfEmpty(Mono.error(stale())).flatMap(policy->{
   if(!policy.id().equals(ticket.operationId())||!policy.tenantId().equals(ticket.intent().tenantId())
      ||!policy.resourceTenantId().equals(event.stream().resourceTenantId())||policy.evidenceProtocol()!=3||policy.preparedTraceDigest()!=null)return Mono.error(stale());
   return repository.findByTenantIdAndResourceTenantIdAndOperationIdAndEventIdAndPhase(ticket.intent().tenantId(),event.stream().resourceTenantId(),ticket.operationId(),event.eventId(),"RESERVE")
    .filter(row->row.tenantId().equals(ticket.intent().tenantId())&&row.resourceTenantId().equals(event.stream().resourceTenantId())
      &&row.operationId().equals(ticket.operationId())&&row.eventId().equals(event.eventId())&&row.phase().equals("RESERVE")
      &&row.traceDigest().equals(policy.reservationTraceDigest()))
    .flatMap(row->{
     var trace=new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.RESERVE,
       new org.fireflyframework.security.api.domain.PolicyEvaluationProvenance(row.authorizationRevision(),row.authorizationDecisionId()),
       new org.fireflyframework.security.api.domain.PolicyEvaluationProvenance(row.admissionRevision(),row.admissionDecisionId()));
     var original=new SigningAuditEvidence(ticket.operationId(),ticket.envelopeId(),ticket.intent(),ticket.account(),ticket.source().fingerprint(),
       SigningDigest.sha256(authorityJson(ticket.authority()).getBytes(StandardCharsets.UTF_8)),null,event.eventId(),null);
     if(!row.businessDigest().equals(original.digest(event.stream().resourceTenantId()))||!trace.admission().policyRevision().equals(event.policyRevision())
        ||!new SigningAuditEvidenceV3(original,trace).digest(event.stream().resourceTenantId()).equals(event.evidenceDigest()))return Mono.error(stale());
     return Mono.just(row);
    }).switchIfEmpty(Mono.error(stale()));
  });
 }
 private Mono<SigningRequestBinding> requestBinding(Ticket ticket){
  if(ticket.requestId()==null)return Mono.error(stale());
  var intent=ticket.intent();var account=ticket.account();
  return requests.findByIdAndOperationIdAndEnvelopeIdAndTenantId(ticket.requestId(),ticket.operationId(),ticket.envelopeId(),intent.tenantId())
    .filter(row->Objects.equals(row.id(),ticket.requestId())&&Objects.equals(row.operationId(),ticket.operationId())
      &&Objects.equals(row.envelopeId(),ticket.envelopeId())&&Objects.equals(row.tenantId(),intent.tenantId())
      &&Objects.equals(row.providerId(),intent.providerId())&&Objects.equals(row.accountBindingId(),account.bindingId())
      &&row.accountBindingVersion()==account.bindingVersion()&&Objects.equals(row.documentSignatureId(),intent.signatureId())
      &&Objects.equals(row.documentId(),intent.documentId())&&Objects.equals(row.documentVersionId(),intent.documentVersionId())
      &&row.plannedSlot()==1&&row.legacyRequestId()==null&&row.externalRecipientId()==null)
    .switchIfEmpty(Mono.error(stale()));
 }
 private void requireAuthority(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority){
  var binding=authority.binding();var actor=authority.actor();
  if(!intent.tenantId().equals(account.tenantId())||!intent.providerId().equals(account.providerId())||!intent.tenantId().equals(binding.resourceTenant())
    ||!actor.issuer().equals(binding.issuer())||!actor.subject().equals(binding.subject())||!actor.platformTenantId().equals(binding.platformTenant()))throw new SecurityException("Exact signing scope required");
 }
 private Mono<SourceStamp> selection(SigningDraftIntent intent,SigningAccountScope scope){
  return accounts.findByIdAndTenantIdAndBindingVersion(scope.bindingId(),intent.tenantId(),scope.bindingVersion()).switchIfEmpty(Mono.error(new SecurityException("Registered signing account unavailable")))
   .flatMap(account->{
    if(!account.id().equals(scope.bindingId())||account.bindingVersion()!=scope.bindingVersion()||!account.tenantId().equals(intent.tenantId())||!Boolean.TRUE.equals(account.active())||!account.providerId().equals(scope.providerId())||!"DOCUSIGN".equals(account.providerCode())
       ||!account.accountId().equals(scope.descriptor().accountId())||!account.endpoint().equals(scope.descriptor().endpoint()))return Mono.error(new SecurityException("Registered signing account changed"));
    return providers.findByIdAndTenantId(intent.providerId(),intent.tenantId()).switchIfEmpty(Mono.error(stale())).flatMap(provider->{
     if(!Boolean.TRUE.equals(provider.getIsActive())||!"DOCUSIGN".equalsIgnoreCase(provider.getProviderCode()))return Mono.error(stale());
     return documents.findByIdAndTenantId(intent.documentId(),intent.tenantId()).switchIfEmpty(Mono.error(stale())).flatMap(document->
       versions.findByIdAndDocumentIdAndTenantId(intent.documentVersionId(),intent.documentId(),intent.tenantId()).switchIfEmpty(Mono.error(stale())).flatMap(version->
        signatures.findByIdAndTenantId(intent.signatureId(),intent.tenantId()).switchIfEmpty(Mono.error(stale())).map(signature->stamp(intent,account,provider,document,version,signature))));
    });
   });
 }
 private SourceStamp stamp(SigningDraftIntent intent,SigningAccountBinding account,SignatureProvider provider,Document document,DocumentVersion version,DocumentSignature signature){
  if(!Objects.equals(document.getId(),intent.documentId())||!Objects.equals(document.getTenantId(),intent.tenantId())||!Objects.equals(version.getId(),intent.documentVersionId())||!Objects.equals(version.getTenantId(),intent.tenantId())
    ||!Objects.equals(provider.getId(),intent.providerId())||!Objects.equals(provider.getTenantId(),intent.tenantId())||!Objects.equals(signature.getId(),intent.signatureId())||!Objects.equals(signature.getTenantId(),intent.tenantId())
    ||!Objects.equals(version.getDocumentId(),intent.documentId())||!Objects.equals(signature.getDocumentId(),intent.documentId())||!Objects.equals(signature.getDocumentVersionId(),intent.documentVersionId())
    ||!Objects.equals(signature.getSignatureProviderId(),intent.providerId())||!Objects.equals(signature.getSignerPartyId(),intent.signerPartyId())||!Objects.equals(signature.getSignerName(),intent.signerName())||!Objects.equals(signature.getSignerEmail(),intent.signerEmail())
    ||signature.getSignatureType()!=SignatureType.BASIC||signature.getSignatureFormat()!=SignatureFormat.PDF_VISIBLE||signature.getSignatureStatus()!=SignatureStatus.PENDING
    ||!Objects.equals(signature.getSignaturePositionX(),intent.x())||!Objects.equals(signature.getSignaturePositionY(),intent.y())||!Objects.equals(signature.getSignaturePage(),intent.page())
    ||!Objects.equals(signature.getSignatureWidth(),intent.width())||!Objects.equals(signature.getSignatureHeight(),intent.height())||signature.getSignatureReason()!=null||signature.getSignatureLocation()!=null||signature.getSignatureContactInfo()!=null||signature.getSignatureData()!=null||signature.getSignatureCertificate()!=null||signature.getSignedAt()!=null||signature.getExpirationDate()!=null
    ||version.getFileSize()==null||version.getFileSize()<1||version.getFileSize()>SigningDocumentSnapshot.MAX_BYTES||!"application/pdf".equals(version.getMimeType())||!"pdf".equalsIgnoreCase(version.getFileExtension())||!Boolean.FALSE.equals(version.getIsEncrypted())||version.getStorageType()==null||version.getStoragePath()==null||version.getStoragePath().isBlank())throw stale();
  Map<String,Object> fields=new TreeMap<>();fields.put("protocol","CORE_SIGNING_SOURCE_V1");fields.put("account",account);fields.put("provider",provider);fields.put("document",document);fields.put("documentVersion",version);fields.put("signature",signature);
  String fingerprint=SigningDigest.sha256(json(fields).getBytes(StandardCharsets.UTF_8));return new SourceStamp(fingerprint,version.getFileName(),version.getMimeType(),version.getFileSize());
 }
 private static String authorityJson(SigningAuthority authority){
  Map<String,Object> values=new TreeMap<>();values.put("protocol","CORE_SIGNING_AUTHORITY_V1");values.put("actor",authority.actor());values.put("binding",authority.binding());values.put("notAfter",authority.notAfter());return json(values);
 }
 private static String json(Object value){
  try{return JsonMapper.builder().addModule(new JavaTimeModule()).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build().writeValueAsString(value);}
  catch(java.io.IOException error){throw new IllegalArgumentException("Exact signing provenance serialization required");}
 }
 private static RuntimeException safeFailure(Throwable failure){
  if(failure instanceof SecurityException)return new SecurityException("Signing preparation authority unavailable");
  if(failure instanceof OptimisticLockingFailureException)return stale();
  return new IllegalStateException("Signing preparation persistence failed");
 }
 private static OptimisticLockingFailureException stale(){return new OptimisticLockingFailureException("Signing source or operation scope changed");}
}
