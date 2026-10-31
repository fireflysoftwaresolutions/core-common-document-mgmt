package io.fireflysoftware.commons.ecm.core.signing;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class SequentialPhaseHandoffTest {
 @Test void completedFirstPhaseAllowsImmediateNextPhase(){
  var f=new TrustedSigningRuntimeTest.Fixture();
  var result=f.invoke((authority,first)->f.runtime.withinPhase(first,()->Mono.just(f.marker()))
   .flatMap(done->f.runtime.requireCurrent(f.intent,f.account,authority)
    .flatMap(second->f.runtime.withinPhase(second,()->Mono.just(f.marker()))))).block(Duration.ofSeconds(3));
  assertNotNull(result);assertEquals(3,f.transport.sdkCalls.get());assertEquals(2,f.policies.get());
 }
 @Test void completedFirstPhaseAllowsAsynchronousNextPhase(){
  var f=new TrustedSigningRuntimeTest.Fixture();
  var result=f.invoke((authority,first)->f.runtime.withinPhase(first,()->Mono.just(f.marker()))
   .delayElement(Duration.ofMillis(20))
   .flatMap(done->f.runtime.requireCurrent(f.intent,f.account,authority)
    .flatMap(second->f.runtime.withinPhase(second,()->Mono.just(f.marker()))))).block(Duration.ofSeconds(3));
  assertNotNull(result);assertEquals(3,f.transport.sdkCalls.get());assertEquals(2,f.policies.get());
 }
}
