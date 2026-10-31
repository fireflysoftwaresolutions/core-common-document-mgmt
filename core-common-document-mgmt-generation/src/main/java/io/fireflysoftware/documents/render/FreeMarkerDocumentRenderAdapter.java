/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.documents.render;

import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import io.fireflysoftware.documents.IssueDocument;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Renders consulting-kit document structures to HTML.
 * An installer replaces this class with another {@link DocumentRenderPort} when a different engine is selected.
 */
public final class FreeMarkerDocumentRenderAdapter implements DocumentRenderPort {

    public static final String MEDIA_TYPE = "text/html;charset=UTF-8";

    private final Configuration configuration;

    public FreeMarkerDocumentRenderAdapter() {
        Configuration configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        configuration.setClassLoaderForTemplateLoading(FreeMarkerDocumentRenderAdapter.class.getClassLoader(), "documents");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setOutputFormat(HTMLOutputFormat.INSTANCE);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setLocalizedLookup(false);
        this.configuration = configuration;
    }

    @Override
    public RenderedDocument render(String templateCode, String kitKind, String label, IssueDocument command) {
        try {
            Template template = configuration.getTemplate(templateCode + ".ftl");
            StringWriter writer = new StringWriter();
            template.process(DocumentModel.of(command, templateCode, kitKind, label), writer);
            return new RenderedDocument(templateCode, MEDIA_TYPE, writer.toString());
        } catch (IOException | TemplateException ex) {
            throw new IllegalArgumentException("Template " + templateCode + " could not be rendered", ex);
        }
    }
    /** Owner templates retain FreeMarker condition/list/macro semantics; no classpath or remote loader is available. */
    @Override public RenderedDocument render(io.fireflysoftware.commons.ecm.core.services.generation.DocumentCurrentSnapshot snapshot,
            String factsJson,GovernedRenderProfile profile) {
        try {
            java.util.Objects.requireNonNull(snapshot);java.util.Objects.requireNonNull(profile);
            snapshot.budget().remaining();
            if(!profile.reviewedSource().equals(snapshot.selection().template())||!profile.reviewedLayout().equals(snapshot.selection().layout()))
                throw new IllegalStateException("Reviewed exact owner source/layout execution capability required");
            if(profile.logoRequired())throw new IllegalStateException("Current-bound logo asset capability unavailable");
            if(snapshot.issuerPresentation()==null||snapshot.issuerPresentation().provenance()!=
                    io.fireflysoftware.commons.ecm.core.services.generation.DocumentCurrentSnapshot.IssuerProvenance.OWNER_ATTESTED
                    ||snapshot.notices().isEmpty()||!"text/html".equals(snapshot.mediaType()))throw new IllegalStateException("Renderable owner snapshot required");
            var json=com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
                    .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            var layout=json.readTree(snapshot.layoutCanonicalJson()).path("profileMetadata");var ref=layout.path("reference");
            if(!profile.code().equals(ref.path("code").asText())||!profile.version().equals(ref.path("version").asText())
                    ||!profile.mediaType().equals(ref.path("mediaType").asText())||!profile.manifestDigest().equals(ref.path("manifestDigest").asText())
                    ||layout.path("maxContentBytes").asInt()<1||layout.path("maxContentBytes").asInt()>profile.maxContentBytes())
                throw new IllegalStateException("Exact deployed owner profile capability required");
            if(snapshot.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>Math.min(layout.path("maxContentBytes").asInt(),profile.maxContentBytes())
                    ||factsJson==null||factsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)
                throw new IllegalArgumentException("Bounded render input required");
            var facts=json.readTree(factsJson);if(!facts.isObject())throw new IllegalArgumentException("Object facts required");
            boundedFacts(facts,0,new int[]{0});
            if(!new org.fireflyframework.validators.schema.OfflineJsonSchemaValidator()
                    .validateInstance(snapshot.schemaCanonicalJson(),factsJson).valid())throw new IllegalArgumentException("Owner input schema refused facts");
            Configuration restricted=new Configuration(Configuration.VERSION_2_3_35);
            restricted.setTemplateLoader(null);restricted.setDefaultEncoding("UTF-8");restricted.setOutputFormat(HTMLOutputFormat.INSTANCE);
            restricted.setAutoEscapingPolicy(Configuration.FORCE_AUTO_ESCAPING_POLICY);
            restricted.setObjectWrapper(new freemarker.template.SimpleObjectWrapper(Configuration.VERSION_2_3_35));
            restricted.setNewBuiltinClassResolver(freemarker.core.TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
            restricted.setAPIBuiltinEnabled(false);restricted.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            restricted.setLogTemplateExceptions(false);restricted.setWrapUncheckedExceptions(true);restricted.setLocalizedLookup(false);
            var locale=java.util.Locale.forLanguageTag(snapshot.selection().locale().replace('_','-'));
            if(locale.getLanguage().isEmpty())throw new IllegalArgumentException("Exact supported locale required");
            restricted.setLocale(locale);
            var issuer=snapshot.issuerPresentation();var presentation=new java.util.HashMap<String,Object>();
            presentation.put("displayName",issuer.displayName());
            if(issuer.contactEmail()!=null)presentation.put("contactEmail",issuer.contactEmail());
            if(issuer.tenantName()!=null)presentation.put("tenantName",issuer.tenantName());
            if(issuer.footerText()!=null)presentation.put("footerText",issuer.footerText());
            // URL metadata is deliberately not a renderable asset or a remote fetch capability.
            var notices=new java.util.ArrayList<java.util.Map<String,Object>>();
            for(var notice:snapshot.notices())notices.add(java.util.Map.of("role",notice.role(),"placement",notice.placement(),"content",notice.content()));
            var model=java.util.Map.of("facts",json.convertValue(facts,java.util.Map.class),"issuer",presentation,"notices",notices,
                    "layout",json.readValue(snapshot.layoutCanonicalJson(),java.util.Map.class),
                    "source",json.readValue(snapshot.sourceCanonicalJson(),java.util.Map.class));
            Template template=new Template(snapshot.selection().template().revisionId().toString(),new java.io.StringReader(snapshot.content()),restricted);
            var writer=new BoundedWriter(profile.maxOutputBytes());
            var env=template.createProcessingEnvironment(model,writer);int[] instructions={0};
            env.setTemplateProcessingTracer(new freemarker.core.TemplateProcessingTracer(){
                public void enterElement(freemarker.core.Environment environment,freemarker.core.TemplateProcessingTracer.TracedElement element){
                    snapshot.budget().remaining();if(++instructions[0]>profile.maxInstructions())throw new IllegalStateException("Render instruction capability exhausted");
                }
                public void exitElement(freemarker.core.Environment environment,freemarker.core.TemplateProcessingTracer.TracedElement element){ }
            });
            env.process();snapshot.budget().remaining();
            String output=writer.toString();if(output.isBlank()||output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>profile.maxOutputBytes())
                throw new IllegalArgumentException("Bounded nonempty rendered bytes required");
            return new RenderedDocument(snapshot.selection().template().revisionId().toString(),"text/html",output);
        }catch(IOException|TemplateException error){throw new IllegalArgumentException("Governed owner template could not be rendered",error);}
    }
    private static void boundedFacts(com.fasterxml.jackson.databind.JsonNode value,int depth,int[] count){
        if(depth>16||++count[0]>512||value.isTextual()&&value.textValue().length()>8192||value.size()>128)
            throw new IllegalArgumentException("Bounded primitive facts required");
        if(value.isContainerNode())value.elements().forEachRemaining(child->boundedFacts(child,depth+1,count));
    }
    private static final class BoundedWriter extends java.io.Writer {
        private final StringBuilder value=new StringBuilder();private final int limit;
        BoundedWriter(int limit){this.limit=limit;}
        public void write(char[] chars,int offset,int length)throws IOException{
            if(value.length()+length>limit)throw new IOException("Render output capability exhausted");value.append(chars,offset,length);
        }
        public void flush(){ }public void close(){ }public String toString(){return value.toString();}
    }

}
