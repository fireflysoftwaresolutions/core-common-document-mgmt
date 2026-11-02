package io.fireflysoftware.commons.ecm.core.services.generation;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.DocumentCurrentSnapshotServiceImpl;

class DocumentIngressBudgetTest extends DocumentSnapshotFixtures {
    @Test void stalledOwnerReadCannotWaitBeyondInitialFiveSeconds() {
        deadline(20,5001);
    }
    @Test void earlierOriginalDeadlineIsNeverExtendedToFiveSeconds() {
        deadline(2,2001);
    }
    private void deadline(int originalSeconds,int elapsedMillis) {
        var original=R2dbcOperationBudget.observed(clock.instant(),clock.instant().plusSeconds(originalSeconds),
                ticks.get(),Duration.ofSeconds(25),clock,nanos);
        var request=selection(values());
        StepVerifier.withVirtualTime(()->new DocumentCurrentSnapshotServiceImpl((selection,budget)->{
                    org.junit.jupiter.api.Assertions.assertSame(original,budget);
                    return Mono.never();
                },clock,nanos).observe(request,original))
                .thenAwait(Duration.ofMillis(elapsedMillis)).expectError(TimeoutException.class).verify(Duration.ofMillis(500));
    }
}
