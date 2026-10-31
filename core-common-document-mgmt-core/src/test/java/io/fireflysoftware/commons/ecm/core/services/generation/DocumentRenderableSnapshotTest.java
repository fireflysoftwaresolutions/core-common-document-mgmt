package io.fireflysoftware.commons.ecm.core.services.generation;

import java.util.List;
import java.util.UUID;
import io.fireflysoftware.common.reference.master.data.sdk.model.*;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.DocumentCurrentSnapshotServiceImpl;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class DocumentRenderableSnapshotTest extends DocumentSnapshotFixtures {
    @Test void missingIssuerPresentationCannotBecomeRenderableSnapshot() {
        var values=values();values.issuer().getDefinition().setDisplayName(null);
        assertThrows(IllegalStateException.class,()->new DocumentCurrentSnapshotServiceImpl(
                (selection,original)->Mono.just(values),clock,nanos).observe(selection(values),budget()).block());
    }
    @Test void declaredNoticeWithoutItsExactImmutableBodyRefuses() {
        var values=values();
        var notice=new DocumentLayoutNoticeReference().role(DocumentLayoutNoticeReference.RoleEnum.GENERAL_DISCLOSURE)
                .placement(DocumentLayoutNoticeReference.PlacementEnum.FOOTER)
                .masterReference(new DocumentLayoutMasterReference().revisionId(UUID.randomUUID())
                        .templateId(UUID.randomUUID()).templateCode("UNIT_NOTICE").version("A.1").localeCode("en-US")
                        .mediaType(DocumentLayoutMasterReference.MediaTypeEnum.TEXT_HTML)
                        .canonicalizationVersion(DocumentLayoutMasterReference.CanonicalizationVersionEnum.MASTERS_JSON_V1)
                        .sourceDigest("c".repeat(64)).contentDigest("d".repeat(64)).schemaDigest("e".repeat(64)).styleDigest("f".repeat(64)));
        values.layout().getDefinition().getSemanticDefinition().setLegalNotices(List.of(notice));
        String canonical=serialized(values.layout().getDefinition());
        String digest=sha("MASTERS_DOCUMENT_LAYOUT_V1\n"+canonical);
        values.layout().setCanonicalJson(canonical);values.layout().setDefinitionDigest(digest);
        values.current().getTarget().getLayout().setDigest(digest);
        assertThrows(IllegalStateException.class,()->new DocumentCurrentSnapshotServiceImpl(
                (selection,original)->Mono.just(values),clock,nanos).observe(selection(values),budget()).block());
    }
    @Test void ownerAttestedTextAndOrderedNoticeBytesAreImmutable() {
        var base=values();var first=notice("FIRST","<p>Owner disclosure</p>");var second=notice("SECOND","<p>Owner privacy</p>");
        var value=withNotices(base,List.of(first,second));
        var snapshot=new DocumentCurrentSnapshotServiceImpl((s,b)->Mono.just(value),clock,nanos).observe(selection(value),budget()).block();
        assertEquals("Owner Fixture",snapshot.issuerPresentation().displayName());assertEquals("Owner footer",snapshot.issuerPresentation().footerText());
        assertEquals(DocumentCurrentSnapshot.IssuerProvenance.OWNER_ATTESTED,snapshot.issuerPresentation().provenance());
        assertEquals(List.of(first.getTemplateContent(),second.getTemplateContent()),snapshot.notices().stream().map(n->n.content()).toList());
        first.setTemplateContent("changed owner DTO");base.issuer().getDefinition().setDisplayName("changed DTO");
        assertEquals("<p>Owner disclosure</p>",snapshot.notices().getFirst().content());assertEquals("Owner Fixture",snapshot.issuerPresentation().displayName());
        assertThrows(UnsupportedOperationException.class,()->snapshot.notices().clear());
    }
    @Test void retiredAlteredOrWrongLocaleNoticeCannotRender() {
        for(String mutation:List.of("retired","content","schema","style","locale","order")) {
            var base=values();var first=notice("FIRST","<p>Disclosure</p>");var second=notice("SECOND","<p>Privacy</p>");
            var value=withNotices(base,List.of(first,second));
            switch(mutation){case "retired"->first.setLifecycle("RETIRED");case "content"->first.setTemplateContent("altered");
                case "schema"->first.setInputSchemaCanonicalJson("{}");case "style"->first.setStyleContent("altered");case "locale"->first.setLocaleCode("es-ES");
                case "order"->value=new DocumentOwnerReader.Observations(value.current(),value.layout(),value.source(),value.issuer(),value.delegationUntil(),List.of(second,first));}
            var actual=value;
            assertThrows(IllegalStateException.class,()->new DocumentCurrentSnapshotServiceImpl((s,b)->Mono.just(actual),clock,nanos).observe(selection(actual),budget()).block(),mutation);
        }
    }
    private DocumentTemplateRevisionDTO notice(String code,String body) {
        var input=java.util.Map.of("type","object","properties",java.util.Map.of(),"additionalProperties",false);
        String schema=serialized(input);String raw=serialized(java.util.Map.of("templateContent",body,"styleContent","","localeCode","en-US","version","A.1","mediaType","text/html","inputSchema",input));
        return new DocumentTemplateRevisionDTO().revisionId(UUID.randomUUID()).templateId(UUID.randomUUID()).templateCode(code)
                .version("A.1").localeCode("en-US").mediaType("text/html").canonicalizationVersion("MASTERS_JSON_V1").lifecycle("PUBLISHED")
                .templateContent(body).styleContent("").inputSchemaCanonicalJson(schema).sourceSnapshotCanonicalJson(raw)
                .contentDigest(sha(body)).schemaDigest(sha(schema)).styleDigest(sha("")).sourceDigest(sha(raw));
    }
    private DocumentOwnerReader.Observations withNotices(DocumentOwnerReader.Observations base,List<DocumentTemplateRevisionDTO> notices) {
        var refs=new java.util.ArrayList<DocumentLayoutNoticeReference>();
        for(int i=0;i<notices.size();i++){
            var n=notices.get(i);refs.add(new DocumentLayoutNoticeReference()
                .role(i==0?DocumentLayoutNoticeReference.RoleEnum.GENERAL_DISCLOSURE:DocumentLayoutNoticeReference.RoleEnum.PRIVACY_NOTICE)
                .placement(i==0?DocumentLayoutNoticeReference.PlacementEnum.FOOTER:DocumentLayoutNoticeReference.PlacementEnum.BODY_NOTE)
                .masterReference(new DocumentLayoutMasterReference().revisionId(n.getRevisionId()).templateId(n.getTemplateId()).templateCode(n.getTemplateCode())
                    .version(n.getVersion()).localeCode(n.getLocaleCode()).mediaType(DocumentLayoutMasterReference.MediaTypeEnum.TEXT_HTML)
                    .canonicalizationVersion(DocumentLayoutMasterReference.CanonicalizationVersionEnum.MASTERS_JSON_V1)
                    .sourceDigest(n.getSourceDigest()).contentDigest(n.getContentDigest()).schemaDigest(n.getSchemaDigest()).styleDigest(n.getStyleDigest())));
        }
        base.layout().getDefinition().getSemanticDefinition().setLegalNotices(refs);
        String canonical=serialized(base.layout().getDefinition()),digest=sha("MASTERS_DOCUMENT_LAYOUT_V1\n"+canonical);
        base.layout().setCanonicalJson(canonical);base.layout().setDefinitionDigest(digest);base.current().getTarget().getLayout().setDigest(digest);
        return new DocumentOwnerReader.Observations(base.current(),base.layout(),base.source(),base.issuer(),base.delegationUntil(),notices);
    }

}
