package io.fireflysoftware.commons.ecm.web.controllers;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.fireflysoftware.commons.ecm.core.services.DocumentService;
import io.fireflysoftware.commons.ecm.core.services.DocumentVersionService;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentDTO;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentVersionDTO;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.*;

/** Controlled service boundary; real WebFlux byte encoding, no provider, socket or native authority. */
class BinaryDownloadContractTest {
    private static final byte[] BYTES={0,1,2,13,10,65,127,(byte)128,(byte)255};
    private static final UUID DOCUMENT=UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID VERSION=UUID.fromString("10000000-0000-4000-8000-000000000002");
    @Test void documentDownloadPreservesEveryBinaryByteAndAttachment(){
        var service=mock(DocumentService.class);var dto=new DocumentDTO();dto.setId(DOCUMENT);dto.setFileName("document.bin");dto.setMimeType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        when(service.getById(DOCUMENT)).thenReturn(Mono.just(dto));when(service.downloadContent(DOCUMENT)).thenReturn(content());
        var client=WebTestClient.bindToController(new DocumentController(service)).build();
        client.get().uri("/api/v1/documents/{id}/download",DOCUMENT).exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_OCTET_STREAM).expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"document.bin\"")
                .expectBody(byte[].class).isEqualTo(BYTES);
    }
    @Test void versionDownloadPreservesEveryBinaryByteAndAttachment(){
        var service=mock(DocumentVersionService.class);var dto=new DocumentVersionDTO();dto.setId(VERSION);dto.setDocumentId(DOCUMENT);dto.setFileName("version.bin");dto.setMimeType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        when(service.getById(VERSION)).thenReturn(Mono.just(dto));when(service.downloadVersionContent(VERSION)).thenReturn(content());
        var client=WebTestClient.bindToController(new DocumentVersionController(service)).build();
        client.get().uri("/api/v1/documents/{document}/versions/{version}/download",DOCUMENT,VERSION).exchange().expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_OCTET_STREAM).expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"version.bin\"")
                .expectBody(byte[].class).isEqualTo(BYTES);
    }
    @Test void wrongDocumentVersionAssociationRemainsNotFound(){
        var service=mock(DocumentVersionService.class);var dto=new DocumentVersionDTO();dto.setId(VERSION);dto.setDocumentId(UUID.randomUUID());when(service.getById(VERSION)).thenReturn(Mono.just(dto));
        WebTestClient.bindToController(new DocumentVersionController(service)).build().get()
                .uri("/api/v1/documents/{document}/versions/{version}/download",DOCUMENT,VERSION).exchange().expectStatus().isNotFound();
        verify(service,never()).downloadVersionContent(any());
    }
    @Test void documentResponseDeclaresBinaryRatherThanOpaqueDataBuffer()throws Exception{binary(DocumentController.class.getMethod("downloadContent",UUID.class).getAnnotation(ApiResponses.class));}
    @Test void versionResponseDeclaresBinaryRatherThanOpaqueDataBuffer()throws Exception{binary(DocumentVersionController.class.getMethod("downloadVersionContent",UUID.class,UUID.class).getAnnotation(ApiResponses.class));}
    private static Flux<org.springframework.core.io.buffer.DataBuffer> content(){
        var buffers=DefaultDataBufferFactory.sharedInstance;return Flux.just(buffers.wrap(Arrays.copyOfRange(BYTES,0,4)),buffers.wrap(Arrays.copyOfRange(BYTES,4,BYTES.length)));
    }
    private static void binary(ApiResponses responses){
        var success=Arrays.stream(responses.value()).filter(r->r.responseCode().equals("200")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(success.content()).anyMatch(c->c.mediaType().equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                &&c.schema().type().equals("string")&&c.schema().format().equals("binary")),"Actual streamed download needs explicit binary OpenAPI metadata");
    }
}
