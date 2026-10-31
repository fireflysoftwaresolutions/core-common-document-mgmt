/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.util.UUID;
import reactor.core.publisher.Mono;
public interface SigningPreparationStore{
 record SourceStamp(String fingerprint,String fileName,String mediaType,long expectedLength){
  public SourceStamp{if(fingerprint==null||!fingerprint.matches("[0-9a-f]{64}")||!"application/pdf".equals(mediaType)||expectedLength<1||expectedLength>SigningDocumentSnapshot.MAX_BYTES)throw new IllegalArgumentException("Exact signing source stamp required");SigningDraftIntent.text(fileName,255);}
  @Override public String toString(){return "SigningSourceStamp[redacted]";}}
 record Ticket(UUID operationId,UUID envelopeId,UUID requestId,long generation,SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority,SourceStamp source,org.fireflyframework.kernel.audit.AuditRecord reservationAudit){
  public Ticket(UUID operationId,UUID envelopeId,UUID requestId,long generation,SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority,SourceStamp source){this(operationId,envelopeId,requestId,generation,intent,account,authority,source,null);}
  @Override public String toString(){return "SigningPreparationTicket[redacted]";}}
 Mono<Ticket> begin(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority);
 Mono<PreparedSigningDraft> complete(Ticket ticket,SigningDocumentSnapshot snapshot,SigningAuthority currentAuthority);
}
