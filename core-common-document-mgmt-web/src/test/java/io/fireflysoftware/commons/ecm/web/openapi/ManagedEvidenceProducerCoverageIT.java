/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.openapi;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ManagedEvidenceProducerCoverageIT {
    static final Set<String> VERBS=Set.of("get","post","put","patch","delete","head","options","trace");
    static final List<String> UPLOAD=List.of("/api/v1/managed-document-evidence/applications/{applicationId}/actions/{actionId}","post","storeManagedDocumentEvidence");
    @Test void originalWholeProducerRetainsEveryExistingOperationAndPublishesRealCustodyAndRecoveryContract()throws Exception {
        var mapper=new ObjectMapper(new YAMLFactory());var original=mapper.readTree(read("document.evidence.producer.original"));var candidate=mapper.readTree(read("document.evidence.producer.candidate"));
        var before=operations(original);var after=operations(candidate);assertThat(before).hasSize(68).contains(UPLOAD);
        var required=new TreeSet<List<String>>(Comparator.comparing(Object::toString));required.addAll(before);required.add(List.of(UPLOAD.getFirst(),"get","observeManagedDocumentEvidence"));required.add(List.of(UPLOAD.getFirst()+"/content","get","readManagedDocumentEvidenceContent"));
        assertThat(after).containsExactlyInAnyOrderElementsOf(required);
        var operation=candidate.path("paths").path(UPLOAD.getFirst()).path("post");
        for(String status:List.of("200","201"))assertThat(operation.path("responses").path(status).path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceUploadReceipt");
        for(String status:List.of("409","503"))assertThat(operation.path("responses").path(status).path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceUploadError");
        assertThat(operation.path("security").findValues("documentEvidenceBearer")).hasSize(1);
        for(JsonNode parameter:operation.path("parameters"))assertThat(parameter.path("name").asText()).isNotEqualToIgnoringCase("X-Idempotency-Key");
        var schemas=candidate.path("components").path("schemas");var content=schemas.path("EvidenceUploadRequest").path("properties").path("content");
        assertThat(content.path("type").asText()).isEqualTo("string");assertThat(content.path("format").asText()).isEqualTo("byte");assertThat(content.path("writeOnly").asBoolean()).isFalse();
        assertThat(content.path("maxLength").asInt()).isEqualTo(2796204);
        assertThat(schemas.path("EvidenceUploadRequest").path("properties").has("documentId")).isFalse();
        assertThat(schemas.path("EvidenceUploadReceipt").path("properties").has("content")).isFalse();
        for(String suffix:List.of("","/content")){
            var read=candidate.path("paths").path(UPLOAD.getFirst()+suffix).path("get");
            assertThat(read.path("security").findValues("documentEvidenceBearer")).hasSize(1);
            assertThat(read.path("responses").path("503").path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceUploadError");
            assertThat(read.path("responses").has("401")).isTrue();assertThat(read.path("responses").has("403")).isTrue();
        }
        assertThat(candidate.path("paths").path(UPLOAD.getFirst()).path("get").path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/EvidenceUploadReceipt");
        var binary=candidate.path("paths").path(UPLOAD.getFirst()+"/content").path("get").path("responses").path("200").path("content").path("application/octet-stream").path("schema");
        assertThat(binary.path("type").asText()).isEqualTo("string");assertThat(binary.path("format").asText()).isEqualTo("binary");
        assertThat(schemas.path("EvidenceUploadReceipt").path("properties").path("storedAt").path("$ref").asText()).isEqualTo("#/components/schemas/FireflyInstant");assertThat(schemas.path("FireflyInstant").path("type").asText()).isEqualTo("string");assertThat(schemas.path("FireflyInstant").path("format").asText()).isEqualTo("date-time");
        var current=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(read("document.evidence.producer.current"));var expected=current.deepCopy();expected.withObject("/components/schemas/EvidenceUploadReceipt/properties").set("storedAt",mapper.createObjectNode().put("$ref","#/components/schemas/FireflyInstant"));expected.withObject("/components/schemas").set("FireflyInstant",mapper.createObjectNode().put("type","string").put("format","date-time"));expected.remove("servers");var exact=(com.fasterxml.jackson.databind.node.ObjectNode)candidate.deepCopy();exact.remove("servers");assertThat(exact).isEqualTo(expected);
        var summary=Map.of("originalOperations",before,"producedOperations",after,"nativeYamlSemanticComparison",true,"ownerSuccessStatuses",List.of(200,201),"ownerErrorStatuses",List.of(409,503));
        Files.writeString(Path.of(System.getProperty("document.evidence.producer.coverage")),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(summary)+"\n");
    }
    static Set<List<String>> operations(JsonNode document){
        assertThat(document.path("openapi").asText()).startsWith("3.");assertThat(document.path("paths").isObject()).isTrue();var result=new TreeSet<List<String>>(Comparator.comparing(Object::toString));
        document.path("paths").properties().forEach(path->path.getValue().properties().forEach(operation->{if(VERBS.contains(operation.getKey())){
            String id=operation.getValue().path("operationId").asText();assertThat(id).isNotBlank();assertThat(result.add(List.of(path.getKey(),operation.getKey(),id))).isTrue();
        }}));return result;
    }
    static byte[] read(String key)throws Exception{var path=Path.of(Objects.requireNonNull(System.getProperty(key)));assertThat(path).isAbsolute();assertThat(Files.isRegularFile(path)&&!Files.isSymbolicLink(path)).isTrue();byte[] bytes=Files.readAllBytes(path);assertThat(bytes.length).isBetween(1,2097152);return bytes;}
}
