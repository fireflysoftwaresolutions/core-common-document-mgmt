package org.fireflyframework.core.audit;
import io.fireflysoftware.commons.ecm.core.signing.*;
import io.r2dbc.spi.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.concurrent.atomic.*;
import org.fireflyframework.core.transaction.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningPolicyCustodyLifecycleTest {
 static final class MovingClock extends Clock {
  final AtomicLong offset=new AtomicLong();
  @Override public ZoneId getZone(){return ZoneOffset.UTC;}
  @Override public Clock withZone(ZoneId zone){if(!ZoneOffset.UTC.equals(zone))throw new IllegalArgumentException();return this;}
  @Override public Instant instant(){return Instant.now().plusNanos(offset.get());}
 }
 static final class Fixture {
  final AuditTestFixture.Driver driver=new AuditTestFixture.Driver();final MovingClock clock=new MovingClock();
  final Sinks.Empty<Void> commit=Sinks.empty();final AtomicInteger writes=new AtomicInteger();boolean holdCommit,failClose,holdRead;final AtomicBoolean readWaiting=new AtomicBoolean();
  final SigningPolicyCustodyFixture values=new SigningPolicyCustodyFixture(clock);
  final SigningAuditAdmission admission=mock(SigningAuditAdmission.class,withSettings().extraInterfaces(SigningPolicyTracePort.class).defaultAnswer(CALLS_REAL_METHODS));
  final SigningPolicyTracePort trace=(SigningPolicyTracePort)admission;
  final ConnectionFactory delegate=new ConnectionFactory(){
   @Override public org.reactivestreams.Publisher<? extends Connection> create(){return Mono.from(driver.create()).map(original->(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
    if(method.getName().equals("createStatement")){
     String sql=(String)args[0];Statement statement=original.createStatement(sql);
     return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Statement.class},(held,operation,parameters)->{
      if(holdRead&&operation.getName().equals("execute")&&sql.startsWith("SELECT")&&sql.contains("policy_custody"))return Mono.defer(()->{readWaiting.set(true);return Mono.never();});
      Object value=operation.invoke(statement,parameters);return value==statement?held:value;
     });
    }
    if(method.getName().equals("commitTransaction")&&holdCommit)return commit.asMono().then(Mono.defer(()->Mono.from(original.commitTransaction())));
    if(method.getName().equals("close")&&failClose)return Mono.error(new IllegalStateException("PRIVATE_CONTROLLED_CLOSE_DETAIL"));
    return method.invoke(original,args);
   }));}
   @Override public ConnectionFactoryMetadata getMetadata(){return driver.getMetadata();}
  };
  final OwnedLeaseConnectionFactory factory=new OwnedLeaseConnectionFactory(delegate);
  final DatabaseClient client=DatabaseClient.create(factory);final R2dbcEntityTemplate template=new R2dbcEntityTemplate(client,PostgresDialect.INSTANCE);
  final R2dbcTransactionManager manager=new R2dbcTransactionManager(factory);final BoundedR2dbcResources resources=BoundedR2dbcResources.verify(factory,client,template,manager);
  Fixture(){when(admission.requireCurrent(any(),any(),any())).thenReturn(Mono.just(values.permit()));when(trace.requirePolicyCustody()).thenReturn(Mono.empty());when(trace.currentTrace(any())).thenReturn(Mono.just(values.trace()));doAnswer(call->{java.util.function.Supplier<?> work=call.getArgument(3);return work.get();}).when(trace).withinExpectedAppendV3(any(),any(),any(),any());}
  Mono<String> run(){var phase=new SigningAuditedPhase(resources,client,template,manager,new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),event->Mono.just(new AuditAppendAuthority.Permit(event.stream(),event.actor(),values.authority().notAfter())),admission,clock,()->System.nanoTime()+clock.offset.get());return phase.executeV3(values.intent(),values.account(),values.authority(),()->Mono.just(new SigningAuditedPhase.PlanV3<>(values.evidence(),receipt->Mono.fromSupplier(()->{writes.incrementAndGet();return "reserved";}))));}
 }
 @Test void resultCannotEscapeWhileCommitIsPendingAfterActualCustodyReadback(){var f=new Fixture();f.holdCommit=true;StepVerifier.create(f.run()).expectSubscription().then(()->{assertEquals(1,f.writes.get());assertEquals(1,f.driver.tables.get("policy_custody").size());assertEquals(0,f.driver.commits);assertEquals(0,f.driver.closes);}).then(()->assertEquals(Sinks.EmitResult.OK,f.commit.tryEmitEmpty())).expectNext("reserved").verifyComplete();assertEquals(1,f.driver.commits);assertEquals(1,f.driver.closes);}
 @Test void closeFailureAfterCommitWithholdsResultWithoutInventingRollback(){var f=new Fixture();f.failClose=true;var error=assertThrows(RootTransactionException.class,()->f.run().block());assertNotEquals(CleanupFact.COMPLETE,error.cleanupFact());assertEquals(1,f.driver.commits);assertEquals(0,f.driver.rollbacks);assertEquals(1,f.driver.tables.get("policy_custody").size());assertNull(error.getCause());assertFalse(error.toString().contains("PRIVATE_CONTROLLED_CLOSE_DETAIL"));}
 @Test void expiryDuringMappedCustodyReadbackCannotInvokeBusinessWrites(){var f=new Fixture();var changed=new AtomicBoolean();f.driver.beforeRead=()->{if(f.driver.tables.containsKey("policy_custody")&&changed.compareAndSet(false,true))f.clock.offset.addAndGet(Duration.ofSeconds(6).toNanos());};assertThrows(RuntimeException.class,()->f.run().block());assertTrue(changed.get());assertEquals(0,f.writes.get());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);assertTrue(f.driver.tables.isEmpty());}
 @Test void cancellationAtPendingMappedCustodyReadbackRollsBackBeforeBusiness(){var f=new Fixture();f.holdRead=true;StepVerifier.create(f.run()).expectSubscription().then(()->{assertTrue(f.readWaiting.get());assertEquals(1,f.driver.tables.get("policy_custody").size());assertEquals(0,f.writes.get());assertEquals(0,f.driver.commits);}).thenCancel().verify(Duration.ofSeconds(1));assertEquals(0,f.writes.get());assertEquals(0,f.driver.commits);assertEquals(1,f.driver.rollbacks);assertEquals(1,f.driver.closes);assertTrue(f.driver.tables.isEmpty());}

}
