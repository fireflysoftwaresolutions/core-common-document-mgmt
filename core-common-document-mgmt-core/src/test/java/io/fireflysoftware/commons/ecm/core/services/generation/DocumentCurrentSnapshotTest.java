package io.fireflysoftware.commons.ecm.core.services.generation;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.common.config.sdk.model.*;
import io.fireflysoftware.common.reference.master.data.sdk.model.*;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.DocumentCurrentSnapshotServiceImpl;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class DocumentCurrentSnapshotTest extends DocumentSnapshotFixtures {
    @Test void wrongTenantRefuses(){rejects(v->v.current().getTarget().getProduct().setTenantId(UUID.randomUUID()));}
    @Test void wrongProductRevisionRefuses(){rejects(v->v.current().getTarget().getProduct().setRevisionId(UUID.randomUUID()));}
    @Test void missingOpaqueMasterVersionRefuses(){rejects(v->v.current().getTarget().getTemplate().setVersionFormat(null));}
    @Test void retiredSourceRefuses(){rejects(v->v.source().setLifecycle("RETIRED"));}
    @Test void alteredCanonicalSourceBytesRefuse(){rejects(v->v.source().setSourceSnapshotCanonicalJson(rawSource+" "));}
    @Test void alteredLayoutCanonicalBytesRefuse(){rejects(v->v.layout().setCanonicalJson(v.layout().getCanonicalJson()+" "));}
    @Test void wrongIssuerTenantRefuses(){rejects(v->v.issuer().getReference().setTenantId(UUID.randomUUID().toString()));}
    @Test void expiredObservationRefuses(){rejects(v->v.current().setEffectiveUntilEpochMillis(clock.millis()));}
    @Test void ownerWindowOverFiveSecondsRefuses(){rejects(v->v.current().setEffectiveUntilEpochMillis(clock.millis()+5001));}
    @Test void nativeCannotBeInventedFromMastersEffectiveCap(){var v=values();var snapshot=new DocumentCurrentSnapshotServiceImpl((x,b)->Mono.just(v),clock,nanos).observe(selection(v),budget()).block();assertEquals(DocumentCurrentSnapshot.MastersProvenance.EFFECTIVE_ONLY,snapshot.mastersProvenance());assertEquals(Instant.ofEpochMilli(v.issuer().getNativeUntilEpochMillis()),snapshot.issuerNativeUntil());}
    @Test void emptyOwnerResultRefuses(){var v=values();assertThrows(RuntimeException.class,()->new DocumentCurrentSnapshotServiceImpl((x,b)->Mono.empty(),clock,nanos).observe(selection(v),budget()).blockOptional().orElseThrow());}
    @Test void originalBudgetMustRemainLiveAfterOwnerWait(){var v=values();var original=budget();assertThrows(RuntimeException.class,()->new DocumentCurrentSnapshotServiceImpl((x,b)->Mono.fromSupplier(()->{ticks.set(3_000_000_000L);return v;}),clock,nanos).observe(selection(v),original).block());}
    @Test void deliveredCapCannotExceedDelegationOrOriginalBudget(){
        var v=values();var original=budget();var result=new DocumentCurrentSnapshotServiceImpl((x,b)->Mono.just(v),clock,nanos).observe(selection(v),original).block();
        assertEquals(v.delegationUntil(),result.effectiveUntil());assertTrue(result.budget().remaining().compareTo(original.remaining())<=0);
    }
    @Test void contentAndSchemaBytesAreRetainedUnmodified(){
        var v=values();var result=new DocumentCurrentSnapshotServiceImpl((x,b)->Mono.just(v),clock,nanos).observe(selection(v),budget()).block();
        assertEquals(content,result.content());assertEquals(rawSource,result.sourceCanonicalJson());assertEquals(schema,result.schemaCanonicalJson());assertEquals(v.layout().getCanonicalJson(),result.layoutCanonicalJson());
        v.source().setTemplateContent("mutated DTO");v.layout().setCanonicalJson("mutated DTO");assertEquals(content,result.content());assertEquals(v.current().getTarget().getTemplate().getId(),result.selection().template().revisionId());
    }
    @Test void immutableSourceTemplateIdentityMustMatchLayout(){rejects(v->v.source().setTemplateId(UUID.randomUUID()));}
    @Test void immutableSourceMediaMustMatchLayoutReference(){rejects(v->{v.source().setMediaType("text/plain");v.source().setSourceSnapshotCanonicalJson(rawSource.replace("text/html","text/plain"));v.source().setSourceDigest(sha(v.source().getSourceSnapshotCanonicalJson()));});}
}
