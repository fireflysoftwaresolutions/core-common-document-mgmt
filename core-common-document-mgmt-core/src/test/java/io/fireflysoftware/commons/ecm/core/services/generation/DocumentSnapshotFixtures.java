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

class DocumentSnapshotFixtures {
    final Clock clock=Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"),ZoneOffset.UTC);
    final AtomicLong ticks=new AtomicLong();
    final java.util.function.LongSupplier nanos=ticks::get;
    final com.fasterxml.jackson.databind.ObjectMapper json=JsonMapper.builder().build();
    final UUID tenant=UUID.randomUUID(),source=UUID.randomUUID(),layout=UUID.randomUUID(),issuer=UUID.randomUUID();
    final String content="<html><body>Unit fixture only</body></html>", schema="{\"type\":\"object\"}", rawSource="{\"inputSchema\":{\"type\":\"object\"},\"localeCode\":\"en-US\",\"mediaType\":\"text/html\",\"styleContent\":\"\",\"templateContent\":\"<html><body>Unit fixture only</body></html>\",\"version\":\"A.1\"}";
    static String sha(String s) {try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
    <T> T dto(String s,Class<T> type){try{return json.readValue(s,type);}catch(Exception e){throw new AssertionError(e);}}
    String serialized(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new AssertionError(e);}}
    DocumentOwnerReader.Observations values() {
        var ref=new DocumentLayoutMasterReference().revisionId(source).templateId(UUID.randomUUID()).templateCode("UNIT_ONLY").version("A.1").localeCode("en-US").sourceDigest(sha(rawSource)).contentDigest(sha(content)).schemaDigest(sha(schema)).styleDigest(sha("")).mediaType(DocumentLayoutMasterReference.MediaTypeEnum.fromValue("text/html")).canonicalizationVersion(DocumentLayoutMasterReference.CanonicalizationVersionEnum.fromValue("MASTERS_JSON_V1"));
        var definition=new DocumentLayoutDefinition().sourceReference(ref)
                .profileMetadata(dto("{\"reference\":{\"code\":\"UNIT_ONLY\",\"version\":\"test\",\"mediaType\":\"text/html\",\"manifestDigest\":\""+"a".repeat(64)+"\"},\"maxSections\":1,\"maxContentBytes\":262144}",DocumentLayoutOutputProfileMetadata.class))
                .semanticDefinition(new DocumentLayoutSemanticDefinition());
        String canonical=serialized(definition);String digest=sha("MASTERS_DOCUMENT_LAYOUT_V1\n"+canonical);
        var product=new DocumentReferenceProductScope().tenantId(tenant).rootId(UUID.randomUUID()).productId(UUID.randomUUID()).revisionId(UUID.randomUUID()).publicationId(UUID.randomUUID()).definitionDigest("a".repeat(64));
        var template=dto("{\"owner\":\"core-common-reference-master-data\",\"kind\":\"DOCUMENT_TEMPLATE\",\"id\":\""+source+"\",\"versionFormat\":\"OPAQUE\",\"versionText\":\"A.1\",\"digest\":\""+sha(rawSource)+"\"}",DocumentReferenceExact.class);
        var layoutRef=dto("{\"owner\":\"core-common-reference-master-data\",\"kind\":\"DOCUMENT_LAYOUT\",\"id\":\""+layout+"\",\"versionFormat\":\"OPAQUE\",\"versionText\":\"A.1\",\"digest\":\""+digest+"\"}",DocumentReferenceExact.class);
        var target=new DocumentReferenceCurrentTarget().product(product).purpose(DocumentReferenceCurrentTarget.PurposeEnum.DEPOSIT_ACCOUNT_ENROLLMENT).documentType("CONTRACT").locale("en-US").template(template).layout(layoutRef);
        var now=clock.millis();
        var current=new DocumentReferenceCurrent().target(target).definition(definition).observedAtEpochMillis(now).effectiveUntilEpochMillis(now+4000);
        var published=new DocumentLayoutPublicationDTO().templateId(ref.getTemplateId()).templateCode("UNIT_ONLY").layoutRevisionId(layout).sourceRevisionId(source).version("A.1").localeCode("en-US").canonicalizationVersion("MASTERS_DOCUMENT_LAYOUT_V1").canonicalJson(canonical).definitionDigest(digest).definition(definition).lifecycle("PUBLISHED");
        var revision=new DocumentTemplateRevisionDTO().templateId(ref.getTemplateId()).templateCode("UNIT_ONLY").revisionId(source).version("A.1").localeCode("en-US").mediaType("text/html").templateContent(content).styleContent("").inputSchemaCanonicalJson(schema).sourceSnapshotCanonicalJson(rawSource).canonicalizationVersion("MASTERS_JSON_V1").contentDigest(sha(content)).schemaDigest(sha(schema)).styleDigest(sha("")).sourceDigest(sha(rawSource)).lifecycle("PUBLISHED");
        var use=new DocumentIssuerUse().purpose(DocumentIssuerUse.PurposeEnum.DEPOSIT_ACCOUNT_ENROLLMENT).documentType("CONTRACT").locale("en-US");
        var issuerRef=new DocumentIssuerReference().tenantId(tenant.toString()).publicationId(issuer.toString()).issuerId(UUID.randomUUID().toString()).revision(1L).definitionDigest("b".repeat(64)).use(use);
        var issuerDef=new DocumentIssuerDefinition().issuerId(issuerRef.getIssuerId()).revision(1L).uses(List.of(use)).effectiveFromEpochMillis(now-1000).effectiveUntilEpochMillis(now+5000);
        var issuerObservation=new DocumentIssuerObservation().reference(issuerRef).definition(issuerDef).generation(1L).sourceGeneration(1L).observedAtEpochMillis(now).nativeUntilEpochMillis(now+4500).effectiveUntilEpochMillis(now+4000);
        return new DocumentOwnerReader.Observations(current,published,revision,issuerObservation,clock.instant().plusSeconds(2));
    }
    DocumentSelection selection(DocumentOwnerReader.Observations v) {
        var p=v.current().getTarget().getProduct();return new DocumentSelection(new DocumentSelection.Product(p.getTenantId(),p.getRootId(),p.getProductId(),p.getRevisionId(),p.getPublicationId(),p.getDefinitionDigest()),"DEPOSIT_ACCOUNT_ENROLLMENT","CONTRACT","en-US",new DocumentSelection.MasterReference(source,"A.1",sha(rawSource)),new DocumentSelection.MasterReference(layout,"A.1",v.layout().getDefinitionDigest()),new DocumentSelection.IssuerReference(issuer,1,"b".repeat(64)));
    }
    R2dbcOperationBudget budget(){return R2dbcOperationBudget.observed(clock.instant(),clock.instant().plusSeconds(3),ticks.get(),Duration.ofSeconds(5),clock,nanos);}
    void rejects(Consumer<DocumentOwnerReader.Observations> mutate){var v=values();var s=selection(v);mutate.accept(v);assertThrows(RuntimeException.class,()->new DocumentCurrentSnapshotServiceImpl((x,b)->Mono.just(v),clock,nanos).observe(s,budget()).block());}
}
