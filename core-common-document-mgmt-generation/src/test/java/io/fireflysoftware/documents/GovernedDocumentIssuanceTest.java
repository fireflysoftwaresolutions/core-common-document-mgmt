package io.fireflysoftware.documents;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import io.fireflysoftware.documents.impl.DocumentIssuance;
import io.fireflysoftware.documents.render.*;
import org.fireflyframework.kernel.audit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import reactor.core.publisher.Mono;

/** Controlled collaborators prove sequencing, not native admission/audit or physical SQL durability. */
class GovernedDocumentIssuanceTest {
    private final UUID id=UUID.randomUUID();
    private final DocumentCurrentSnapshot snapshot=GovernedFreeMarkerTest.snapshot("<h1>${issuer.displayName}</h1><#list facts.names as n>${n}</#list><#list notices as n>${n.content}</#list>");
    private final AtomicInteger stored=new AtomicInteger();private final List<String> calls=new ArrayList<>();
    private DocumentGenerationEventSource events=(document,s,bytes,b)->{
        calls.add("event");assertSame(snapshot,s);assertSame(snapshot.budget(),b);
        var event=new AuditEvent(UUID.randomUUID(),new AuditStream(s.selection().product().tenantId(),"core-common-document-mgmt","document-generation",document),
                new AuditActor("https://unit.invalid","unit-subject","unit-client",s.selection().product().tenantId().toString(),null,null),
                "DOCUMENT_LOCAL_STORAGE_RECORDED",AuditEvent.Outcome.COMPLETED,id,id,null,GovernedFreeMarkerTest.NOW,"0".repeat(64),"UNIT_ONLY");
        var raw=new DocumentGenerationStorage.Request(s.selection().product().tenantId(),document,s,event,bytes);
        return Mono.just(new AuditEvent(event.eventId(),event.stream(),event.actor(),event.action(),event.outcome(),event.processId(),event.correlationId(),event.causationId(),event.occurredAt(),DocumentStorageIntent.digest(raw),event.policyRevision()));
    };
    private DocumentGenerationCommitService commits=(request,b)->{
        calls.add("store");stored.incrementAndGet();assertSame(snapshot.budget(),b);assertSame(snapshot,request.snapshot());
        assertEquals(request.event().evidenceDigest(),DocumentStorageIntent.digest(request));
        String html=new String(request.renderedContent(),StandardCharsets.UTF_8);assertTrue(html.contains("Actual owner"));assertTrue(html.contains("A &amp; B"));assertTrue(html.contains("Owner disclosure"));
        return Mono.just(new DocumentGenerationCommitService.LocalCommit(new DocumentGenerationStorage.StoredDocument(request.resourceTenantId(),request.documentId(),DocumentStorageIntent.contentDigest(request.renderedContent())),request.event().eventId(),DocumentStorageIntent.digest(request),DocumentGenerationCommitService.ProviderState.PENDING_PROVIDER));
    };
    private DocumentIssuance service(DocumentGenerationEventSource eventSource,DocumentGenerationCommitService commit){
        return new DocumentIssuance((s,b)->{calls.add("snapshot");assertSame(snapshot.budget(),b);return Mono.just(snapshot);},new FreeMarkerDocumentRenderAdapter(),s->{calls.add("profile");return GovernedFreeMarkerTest.profile(s);},eventSource,commit,GovernedFreeMarkerTest.CLOCK,GovernedFreeMarkerTest.NANOS);
    }
    private GovernedIssueDocument request(){return new GovernedIssueDocument(id,snapshot.selection(),"{\"names\":[\"A & B\"]}");}
    @Test void exactRenderedBytesReachOnlyOwningCommitAndPendingProvider(){
        var result=service(events,commits).issue(request(),snapshot.budget()).block();
        assertEquals(List.of("snapshot","profile","event","store"),calls);assertEquals(1,stored.get());assertEquals(id,result.local().documentId());assertEquals(DocumentGenerationCommitService.ProviderState.PENDING_PROVIDER,result.providerState());
    }
    @Test void missingAuthorityCannotBecomeLocalSuccess(){assertThrows(RuntimeException.class,()->service((d,s,c,b)->Mono.empty(),commits).issue(request(),snapshot.budget()).block());assertEquals(0,stored.get());}
    @Test void schemaRefusalPrecedesEventAndStorage(){assertThrows(RuntimeException.class,()->service(events,commits).issue(new GovernedIssueDocument(id,snapshot.selection(),"{\"names\":1}"),snapshot.budget()).block());assertFalse(calls.contains("event"));assertEquals(0,stored.get());}
    @Test void returnedWrongCommitIdentityRefuses(){
        assertThrows(RuntimeException.class,()->service(events,(r,b)->Mono.just(new DocumentGenerationCommitService.LocalCommit(new DocumentGenerationStorage.StoredDocument(UUID.randomUUID(),id,"a".repeat(64)),r.event().eventId(),DocumentStorageIntent.digest(r),DocumentGenerationCommitService.ProviderState.PENDING_PROVIDER))).issue(request(),snapshot.budget()).block());
    }
    @Test void currentAdmissionRefusalPropagatesWithoutFallbackOrProvider(){assertThrows(RuntimeException.class,()->service(events,(r,b)->Mono.error(new IllegalStateException("Native admission unavailable"))).issue(request(),snapshot.budget()).block());assertEquals(List.of("snapshot","profile","event"),calls);}
    @Test void alteredAuditIntentRefusesBeforeCommit(){
        DocumentGenerationEventSource wrong=(d,s,c,b)->events.bind(d,s,new byte[]{1,2,3},b);
        assertThrows(RuntimeException.class,()->service(wrong,commits).issue(request(),snapshot.budget()).block());assertEquals(0,stored.get());
    }
    @Test void neverOwnerWaitStopsAtInitialFiveSecondsWithoutNewAuthority(){
        var original=org.fireflyframework.core.transaction.R2dbcOperationBudget.observed(GovernedFreeMarkerTest.NOW,GovernedFreeMarkerTest.NOW.plusSeconds(20),0,
                java.time.Duration.ofSeconds(20),GovernedFreeMarkerTest.CLOCK,GovernedFreeMarkerTest.NANOS);
        var service=new DocumentIssuance((s,b)->{assertSame(original,b);return Mono.never();},new FreeMarkerDocumentRenderAdapter(),
                s->GovernedFreeMarkerTest.profile(s),events,commits,GovernedFreeMarkerTest.CLOCK,GovernedFreeMarkerTest.NANOS);
        reactor.test.StepVerifier.withVirtualTime(()->service.issue(request(),original)).thenAwait(java.time.Duration.ofSeconds(5))
                .expectError(java.util.concurrent.TimeoutException.class).verify();
        assertTrue(calls.isEmpty());assertEquals(0,stored.get());
    }

}
