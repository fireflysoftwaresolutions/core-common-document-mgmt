package io.fireflysoftware.documents.render;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;

/** Synthetic local renderer vectors; no owner HTTP, admission or persistence proof. */
public class GovernedFreeMarkerTest {
    public static final String DIGEST="a".repeat(64);
    public static final Instant NOW=Instant.parse("2026-10-29T00:00:00Z");
    public static final Clock CLOCK=Clock.fixed(NOW,ZoneOffset.UTC);
    public static final java.util.function.LongSupplier NANOS=()->0L;
    public static DocumentCurrentSnapshot snapshot(String content) {
        var tenant=UUID.randomUUID();var selection=new DocumentSelection(new DocumentSelection.Product(tenant,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),DIGEST),
                "DEPOSIT_ACCOUNT_ENROLLMENT","ACCOUNT_CONTRACT","en-US",new DocumentSelection.MasterReference(UUID.randomUUID(),"A.1",DIGEST),
                new DocumentSelection.MasterReference(UUID.randomUUID(),"A.1",DIGEST),new DocumentSelection.IssuerReference(UUID.randomUUID(),1,DIGEST));
        String schema="{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"properties\":{\"names\":{\"type\":\"array\",\"maxItems\":5,\"items\":{\"type\":\"string\",\"maxLength\":80}}},\"required\":[\"names\"],\"additionalProperties\":false}";
        String layout="{\"profileMetadata\":{\"reference\":{\"code\":\"DEPLOYED_A4\",\"version\":\"1\",\"mediaType\":\"text/html\",\"manifestDigest\":\""+DIGEST+"\"},\"maxContentBytes\":8192}}";
        var notice=new DocumentCurrentSnapshot.Notice("GENERAL_DISCLOSURE","FOOTER",new DocumentSelection.MasterReference(UUID.randomUUID(),"A.1",DIGEST),
                UUID.randomUUID(),"DISCLOSURE","en-US","text/plain","Owner disclosure","{}","{}","",DIGEST,DIGEST,DIGEST);
        return new DocumentCurrentSnapshot(selection,content,"text/html","{}",schema,layout,
                new DocumentCurrentSnapshot.IssuerCurrentEvidence(1,1,NOW,NOW.plusSeconds(2)),NOW,NOW.plusSeconds(2),
                DocumentCurrentSnapshot.MastersProvenance.EFFECTIVE_ONLY,NOW.plusSeconds(2),
                R2dbcOperationBudget.observed(NOW,NOW.plusSeconds(2),0,Duration.ofSeconds(2),CLOCK,NANOS),
                new DocumentCurrentSnapshot.IssuerPresentation("Actual owner","contact@example.test","Owner tenant",null,"Actual footer","https://unfetched.invalid/logo", "branding",1,DocumentCurrentSnapshot.IssuerProvenance.OWNER_ATTESTED),List.of(notice));
    }
    public static GovernedRenderProfile profile(DocumentCurrentSnapshot s){return new GovernedRenderProfile("DEPLOYED_A4","1","text/html",DIGEST,8192,32768,500,false,
            s.selection().template(),s.selection().layout(),GovernedRenderProfile.ExecutionScope.REVIEWED_OWNER_SOURCE_AND_LAYOUT_INLINE);}
    @Test void realFreeMarkerListsConditionsAndOwnerTextRenderWithoutStationeryFallback(){
        var s=snapshot("<h1>${issuer.displayName}</h1><#if facts.names?size gt 0><#list facts.names as name><p>${name}</p></#list></#if><footer>${issuer.footerText}<#list notices as notice>${notice.content}</#list></footer>");
        var out=new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[\"A & B\",\"Second\"]}",profile(s));
        assertTrue(out.html().contains("<h1>Actual owner</h1>"));assertTrue(out.html().contains("A &amp; B"));assertTrue(out.html().contains("Owner disclosure"));
        assertTrue(out.html().contains("Actual footer"));assertFalse(out.html().contains("unfetched.invalid"));assertFalse(out.html().contains("KitStationery"));
    }
    @Test void schemaMismatchRefuses(){var s=snapshot("${issuer.displayName}");assertThrows(RuntimeException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":42}",profile(s)));}
    @Test void absentOrDifferentDeploymentProfileRefuses(){
        var s=snapshot("Owner text");assertThrows(RuntimeException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[]}",null));
        assertThrows(RuntimeException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[]}",new GovernedRenderProfile("OTHER","1","text/html",DIGEST,8192,32768,500,false,s.selection().template(),s.selection().layout(),GovernedRenderProfile.ExecutionScope.REVIEWED_OWNER_SOURCE_AND_LAYOUT_INLINE)));
    }
    @Test void logoRequirementRefusesWithoutGenuineAssetCapability(){var s=snapshot("Owner text");assertThrows(RuntimeException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[]}",new GovernedRenderProfile("DEPLOYED_A4","1","text/html",DIGEST,8192,32768,500,true,s.selection().template(),s.selection().layout(),GovernedRenderProfile.ExecutionScope.REVIEWED_OWNER_SOURCE_AND_LAYOUT_INLINE)));}
    @Test void apiClassConstructionAndIncludesCannotEscape(){
        for(String body:List.of("${issuer?api}","${'java.lang.System'?new()}","<#include 'contract.ftl'>")) {
            var s=snapshot(body);assertThrows(RuntimeException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[]}",profile(s)));}
    }
    @Test void repeatedOutputAndInstructionsRefuseAtExplicitCaps(){
        var s=snapshot("<#list 1..10000 as n>x</#list>");
        assertThrows(RuntimeException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[]}",profile(s)));
    }
    @Test void ownerContentByteCeilingRemainsOperativeBelowDeploymentCeiling()throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String content:List.of("Owner","éééé","Owner text","ééééé")){
            var original=snapshot(content);var tree=json.readTree(original.layoutCanonicalJson());
            ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("profileMetadata")).put("maxContentBytes",8);
            String canonical=json.writeValueAsString(tree);
            String digest=DocumentStorageIntent.contentDigest(("MASTERS_DOCUMENT_LAYOUT_V1\n"+canonical).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var prior=original.selection();var selection=new DocumentSelection(prior.product(),prior.purpose(),prior.documentType(),prior.locale(),prior.template(),new DocumentSelection.MasterReference(prior.layout().revisionId(),prior.layout().versionText(),digest),prior.issuer());
            var s=new DocumentCurrentSnapshot(selection,original.content(),original.mediaType(),original.sourceCanonicalJson(),original.schemaCanonicalJson(),canonical,original.issuerEvidence(),original.observedAt(),original.effectiveUntil(),original.mastersProvenance(),original.issuerNativeUntil(),original.budget(),original.issuerPresentation(),original.notices());
            if(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=8)
                assertEquals(content,new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[]}",profile(s)).html());
            else assertThrows(IllegalArgumentException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"names\":[]}",profile(s)),content);
        }
    }

    private DocumentCurrentSnapshot withNumberSchema(DocumentCurrentSnapshot s,String schema){
        return new DocumentCurrentSnapshot(s.selection(),s.content(),s.mediaType(),s.sourceCanonicalJson(),schema,s.layoutCanonicalJson(),s.issuerEvidence(),s.observedAt(),s.effectiveUntil(),s.mastersProvenance(),s.issuerNativeUntil(),s.budget(),s.issuerPresentation(),s.notices());
    }
    @Test void exactDecimalAndLargeIntegerFactsRemainLosslessThroughFreeMarker(){
        String schema="{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"},\"quantity\":{\"type\":\"integer\"}},\"required\":[\"amount\",\"quantity\"],\"additionalProperties\":false}";
        var s=withNumberSchema(snapshot("${facts.amount?string[\"0.00\"]}|${facts.quantity?c}"),schema);
        String integer="1234567890123456789012345678901234567890";
        for(String amount:List.of("12345678901234567890.12","-12345678901234567890.12","0.01")){
            String actual=new FreeMarkerDocumentRenderAdapter().render(s,"{\"amount\":"+amount+",\"quantity\":"+integer+"}",profile(s)).html();
            assertArrayEquals((amount+"|"+integer).getBytes(java.nio.charset.StandardCharsets.UTF_8),actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Test void oneCentSchemaBoundaryIsExactBeforeRendering(){
        String schema="{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\",\"minimum\":0.01,\"maximum\":0.01}},\"required\":[\"amount\"],\"additionalProperties\":false}";
        var s=withNumberSchema(snapshot("${facts.amount?string[\"0.00\"]}"),schema);
        assertEquals("0.01",new FreeMarkerDocumentRenderAdapter().render(s,"{\"amount\":0.01}",profile(s)).html());
        for(String amount:List.of("0.0099999999999999999999999999999999999","0.0100000000000000000000000000000000001"))
            assertThrows(IllegalArgumentException.class,()->new FreeMarkerDocumentRenderAdapter().render(s,"{\"amount\":"+amount+"}",profile(s)));
    }

}
