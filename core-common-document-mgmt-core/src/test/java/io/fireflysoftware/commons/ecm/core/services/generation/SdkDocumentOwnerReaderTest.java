package io.fireflysoftware.commons.ecm.core.services.generation;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import io.fireflysoftware.common.reference.master.data.sdk.api.*;
import io.fireflysoftware.common.config.sdk.api.DocumentIssuerApi;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.SdkDocumentOwnerReader;

/** In-process ExchangeFunction only; these synthetic DTOs are not owner admission. */
class SdkDocumentOwnerReaderTest extends DocumentSnapshotFixtures {
    @Test void genuineSdkCallsExactOwnersWithOneBearerHeader() {
        var v=values();var calls=new ArrayList<String>();
        ExchangeFunction exchange=request->{
            assertEquals(List.of("Bearer unit-token"),request.headers().getOrEmpty(HttpHeaders.AUTHORIZATION));
            String path=request.url().getPath();calls.add(request.method()+" "+path);
            Object body=path.contains("document-reference-current-observations")?v.current():path.contains("document-layout")?v.layout():path.contains("document-template-revisions")?v.source():v.issuer();
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(serialized(body)).build());
        };
        var reader=reader(exchange,(owner,selection,original)->Mono.just(new DocumentOwnerTokenProvider.AuthorizedToken("https://issuer.invalid","unit-subject",selection.product().tenantId(),clock.instant().plusSeconds(10),"unit-token")));
        var result=reader.read(selection(v),budget()).block();assertNotNull(result);assertEquals(v.source().getRevisionId(),result.source().getRevisionId());
        assertEquals(4,calls.size());assertEquals("POST /api/v1/document-reference-current-observations",calls.get(0));
        assertTrue(calls.get(1).endsWith(layout.toString()));assertTrue(calls.get(2).endsWith(source.toString()));
        assertTrue(calls.get(3).contains(tenant.toString()));
    }
    private SdkDocumentOwnerReader reader(ExchangeFunction exchange,DocumentOwnerTokenProvider provider) {
        return new SdkDocumentOwnerReader(provider,java.net.URI.create("https://masters.invalid"),java.net.URI.create("https://config.invalid"),
                "https://issuer.invalid","https://issuer.invalid",WebClient.builder().exchangeFunction(exchange).build(),clock);
    }
    @Test void expiredOriginalRefusesBeforeGeneratedTransport() {
        var count=new AtomicInteger();ExchangeFunction exchange=r->{count.incrementAndGet();return Mono.error(new AssertionError("No call allowed"));};
        var reader=reader(exchange,(owner,selection,original)->Mono.error(new AssertionError("No delegation allowed")));
        var v=values();var original=budget();ticks.set(3_000_000_000L);assertThrows(RuntimeException.class,()->reader.read(selection(v),original).block());assertEquals(0,count.get());
    }
    @Test void wrongIssuerTenantAndExpiredDelegationsNeverReachTransport() {
        var count=new AtomicInteger();ExchangeFunction exchange=r->{count.incrementAndGet();return Mono.error(new AssertionError("No owner call allowed"));};
        var v=values();
        for(var token:List.of(
                new DocumentOwnerTokenProvider.AuthorizedToken("https://other.invalid","subject",tenant,clock.instant().plusSeconds(2),"unit-token"),
                new DocumentOwnerTokenProvider.AuthorizedToken("https://issuer.invalid","subject",UUID.randomUUID(),clock.instant().plusSeconds(2),"unit-token"),
                new DocumentOwnerTokenProvider.AuthorizedToken("https://issuer.invalid","subject",tenant,clock.instant(),"unit-token"))) {
            assertThrows(RuntimeException.class,()->reader(exchange,(owner,x,b)->Mono.just(token)).read(selection(v),budget()).block());
        }
        assertEquals(0,count.get());
    }
    @Test void exactHttpsOriginRequired() {
        for(String uri:List.of("http://masters.invalid","https://user:secret@masters.invalid","https://masters.invalid/other","https://masters.invalid?token=x","https://masters.invalid/#fragment"))
            assertThrows(IllegalArgumentException.class,()->new SdkDocumentOwnerReader((o,s,b)->Mono.empty(),java.net.URI.create(uri),java.net.URI.create("https://config.invalid"),"issuer","issuer",WebClient.create(),clock));
    }
    @Test void existingReactiveTransactionRefusesBeforeDelegation() {
        var count=new AtomicInteger();var v=values();
        var reader=reader(r->Mono.error(new AssertionError("No HTTP")),(owner,s,b)->{count.incrementAndGet();return Mono.empty();});
        assertThrows(RuntimeException.class,()->reader.read(selection(v),budget())
                .contextWrite(org.springframework.transaction.reactive.TransactionContextManager.createTransactionContext()).block());
        assertEquals(0,count.get());
    }
    @Test void callsUseFreshClientsAndTokensRatherThanSharedMutableAuthentication() {
        var v=values();var auth=new ArrayList<String>();var issued=new AtomicInteger();
        ExchangeFunction exchange=request->{
            auth.add(request.headers().getFirst(HttpHeaders.AUTHORIZATION));String path=request.url().getPath();
            Object body=path.contains("document-reference-current-observations")?v.current():path.contains("document-layout")?v.layout():path.contains("document-template-revisions")?v.source():v.issuer();
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(serialized(body)).build());
        };
        var reader=reader(exchange,(owner,s,b)->Mono.just(new DocumentOwnerTokenProvider.AuthorizedToken("https://issuer.invalid","subject",tenant,clock.instant().plusSeconds(2),"token-"+((issued.getAndIncrement()/2)+1))));
        reader.read(selection(v),budget()).block();reader.read(selection(v),budget()).block();
        assertEquals(Collections.nCopies(4,"Bearer token-1"),auth.subList(0,4));
        assertEquals(Collections.nCopies(4,"Bearer token-2"),auth.subList(4,8));
    }
}
