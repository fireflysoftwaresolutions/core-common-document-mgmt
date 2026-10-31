package io.fireflysoftware.commons.ecm.core.signing;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SigningTransactionsTest {
 private static ReactiveTransaction newTransaction(){return mock(ReactiveTransaction.class,call->call.getMethod().getName().equals("isNewTransaction")?true:org.mockito.Answers.RETURNS_DEFAULTS.answer(call));}
 @Test void storageCannotStartOnRowOnNextBeforeActualSpringCommitCompletes(){
  var manager=mock(ReactiveTransactionManager.class);when(manager.getReactiveTransaction(any())).thenReturn(Mono.just(newTransaction()));
  var commit=Sinks.<Void>one();when(manager.commit(any())).thenReturn(commit.asMono());when(manager.rollback(any())).thenReturn(Mono.empty());
  var phases=new SigningTransactions(TransactionalOperator.create(manager));var rows=new AtomicInteger();var storage=new AtomicInteger();var result=new AtomicReference<String>();
  var subscription=phases.committed(()->Mono.fromSupplier(()->{rows.incrementAndGet();return "row";}))
   .flatMap(row->Mono.fromSupplier(()->{storage.incrementAndGet();return row;})).subscribe(result::set);
  assertEquals(1,rows.get());assertEquals(0,storage.get());assertNull(result.get());
  commit.tryEmitEmpty();assertEquals(1,storage.get());assertEquals("row",result.get());subscription.dispose();
 }
 @Test void commitFailureCannotEmitPreparedResult(){
  var manager=mock(ReactiveTransactionManager.class);when(manager.getReactiveTransaction(any())).thenReturn(Mono.just(newTransaction()));
  var failure=new IllegalStateException("controlled commit failure");when(manager.commit(any())).thenReturn(Mono.error(failure));when(manager.rollback(any())).thenReturn(Mono.empty());
  var phases=new SigningTransactions(TransactionalOperator.create(manager));assertSame(failure,assertThrows(IllegalStateException.class,()->phases.committed(()->Mono.just("row")).block(Duration.ofSeconds(1))));
 }
 @Test void subscriptionsDoNotShareCapturedRows(){
  var manager=mock(ReactiveTransactionManager.class);when(manager.getReactiveTransaction(any())).thenAnswer(call->Mono.just(newTransaction()));
  when(manager.commit(any())).thenReturn(Mono.empty());when(manager.rollback(any())).thenReturn(Mono.empty());
  var count=new AtomicInteger();var phase=new SigningTransactions(TransactionalOperator.create(manager)).committed(()->Mono.fromSupplier(count::incrementAndGet));
  assertEquals(1,phase.block());assertEquals(2,phase.block());
 }
 @Test void participatingOrNontransactionalStatusCannotPretendToHaveCommittedANewPhase(){
  var manager=mock(ReactiveTransactionManager.class);when(manager.getReactiveTransaction(any())).thenReturn(Mono.just(mock(ReactiveTransaction.class)));
  when(manager.commit(any())).thenReturn(Mono.empty());when(manager.rollback(any())).thenReturn(Mono.empty());var calls=new AtomicInteger();
  assertThrows(IllegalStateException.class,()->new SigningTransactions(TransactionalOperator.create(manager)).committed(()->Mono.fromSupplier(calls::incrementAndGet)).block());assertEquals(0,calls.get());
 }
 @Test void ambientTransactionIsRefusedBeforeWork(){
  var manager=mock(ReactiveTransactionManager.class);when(manager.getReactiveTransaction(any())).thenReturn(Mono.just(newTransaction()));
  when(manager.commit(any())).thenReturn(Mono.empty());when(manager.rollback(any())).thenReturn(Mono.empty());var calls=new AtomicInteger();
  var value=new SigningTransactions(TransactionalOperator.create(manager)).committed(()->Mono.fromSupplier(calls::incrementAndGet))
   .contextWrite(ctx->{var tx=ctx.get(org.springframework.transaction.reactive.TransactionContext.class);new org.springframework.transaction.reactive.TransactionSynchronizationManager(tx).setActualTransactionActive(true);return ctx;})
   .contextWrite(org.springframework.transaction.reactive.TransactionContextManager.getOrCreateContext()).contextWrite(org.springframework.transaction.reactive.TransactionContextManager.getOrCreateContextHolder());
  assertThrows(IllegalStateException.class,value::block);assertEquals(0,calls.get());verify(manager,never()).getReactiveTransaction(any());
 }

}
