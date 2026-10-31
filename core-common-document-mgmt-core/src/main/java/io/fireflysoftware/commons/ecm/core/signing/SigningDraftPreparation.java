/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.util.Objects;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;
/** Internal, explicitly composed preparation; no transaction spans storage and no provider write exists. */
public final class SigningDraftPreparation implements SigningDraftWorkflow{
 private final SigningAuthorityAdapter authority;private final SigningPreparationStore store;private final SigningAccountScope account;private final SignatureEnvelopePort envelopes;private final DocumentContentPort content;
 public SigningDraftPreparation(SigningAuthorityAdapter authority,SigningPreparationStore store,SigningAccountScope account,SignatureEnvelopePort envelopes,DocumentContentPort content){
  this.authority=Objects.requireNonNull(authority);this.store=Objects.requireNonNull(store);this.account=Objects.requireNonNull(account);this.envelopes=Objects.requireNonNull(envelopes);this.content=Objects.requireNonNull(content);
 }
 private Mono<Void> outsideTransaction(){return TransactionSynchronizationManager.forCurrentTransaction().map(TransactionSynchronizationManager::isActualTransactionActive)
   .onErrorResume(NoTransactionException.class,error->Mono.just(false)).flatMap(active->active?Mono.error(new IllegalStateException("Signing preparation refuses an ambient transaction")):Mono.empty());}
 @Override public Mono<PreparedSigningDraft> prepare(SigningDraftIntent intent){
  return Mono.defer(()->outsideTransaction().then(Mono.defer(envelopes::accountBinding)).switchIfEmpty(Mono.error(new SigningIntegrationUnavailableException()))
   .flatMap(binding->{account.requireSame(binding);return authority.authorize(intent,account);})
   .flatMap(original->authority.withinOriginalWindow(original,()->store.begin(intent,account,original)
     .flatMap(ticket->capture(intent,ticket).flatMap(snapshot->finish(original,intent,ticket,snapshot)))))
   .switchIfEmpty(Mono.error(new IllegalStateException("Signing preparation produced no result"))));
 }
 private Mono<SigningDocumentSnapshot> capture(SigningDraftIntent intent,SigningPreparationStore.Ticket ticket){
  return outsideTransaction().then(Mono.defer(()->content.getContent(intent.documentVersionId())).onErrorMap(error->new IllegalStateException("Selected signing version content unavailable")))
   .switchIfEmpty(Mono.error(new IllegalStateException("Selected signing version content unavailable")))
   .map(bytes->new SigningDocumentSnapshot(intent.documentId(),intent.documentVersionId(),ticket.source().fileName(),ticket.source().mediaType(),bytes));
 }
 private Mono<PreparedSigningDraft> finish(SigningAuthority original,SigningDraftIntent intent,SigningPreparationStore.Ticket ticket,SigningDocumentSnapshot snapshot){
  if(snapshot.contentLength()!=ticket.source().expectedLength())return Mono.error(new IllegalStateException("Signing version length changed"));
  return outsideTransaction().then(Mono.defer(envelopes::accountBinding)).switchIfEmpty(Mono.error(new SigningIntegrationUnavailableException()))
   .flatMap(binding->{account.requireSame(binding);return authority.revalidate(original,intent,account);})
   .flatMap(current->authority.withinOriginalWindow(current,()->store.complete(ticket,snapshot,current)))
   .map(prepared->{
    if(!prepared.operationId().equals(ticket.operationId())||!prepared.envelopeId().equals(ticket.envelopeId())||!prepared.requestId().equals(ticket.requestId())
      ||prepared.generation()!=ticket.generation()+1||!"PREPARED".equals(prepared.state())||!prepared.contentDigest().equals(snapshot.sha256()))
     throw new IllegalStateException("Signing preparation returned a foreign association");
    return prepared;
   });
 }
}
