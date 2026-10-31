/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;
/** Results are withheld until a new transaction's actual commit publisher completes. */
public final class SigningTransactions{
 private final TransactionalOperator transactions;
 public SigningTransactions(TransactionalOperator transactions){this.transactions=Objects.requireNonNull(transactions);}
 private Mono<Void> noAmbient(){return TransactionSynchronizationManager.forCurrentTransaction().map(TransactionSynchronizationManager::isActualTransactionActive)
  .onErrorResume(NoTransactionException.class,error->Mono.just(false)).flatMap(active->active?Mono.error(new IllegalStateException("Signing phase refuses an ambient transaction")):Mono.empty());}
 public <T> Mono<T> committed(Supplier<Mono<T>> work){
  return Mono.defer(()->noAmbient().then(Mono.defer(()->{
   var captured=new AtomicReference<T>();
   return transactions.execute(status->{
    if(!status.isNewTransaction())return Mono.error(new IllegalStateException("Signing phase requires a new transaction"));
    return Mono.defer(work).switchIfEmpty(Mono.error(new IllegalStateException("Signing transaction produced no row"))).doOnNext(captured::set).then();
   }).then(Mono.defer(()->Mono.justOrEmpty(captured.get()).switchIfEmpty(Mono.error(new IllegalStateException("Signing transaction produced no committed result")))));
  })));
 }
}
