/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.impl;

import io.fireflysoftware.commons.ecm.core.mappers.DocumentMapper;
import io.fireflysoftware.commons.ecm.models.entities.Document;
import io.fireflysoftware.commons.ecm.models.repositories.DocumentRepository;
import io.netty.buffer.PooledByteBufAllocator;
import java.util.*;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.service.EcmPortProvider;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadedDocumentBufferSafetyTest {
    final UUID documentId=UUID.fromString("cd62718f-16cd-47bf-8ac4-294c75e99968");
    final DocumentRepository repository=mock(DocumentRepository.class);
    final DocumentContentPort content=mock(DocumentContentPort.class);
    final EcmPortProvider ports=mock(EcmPortProvider.class);
    final DocumentServiceImpl service=new DocumentServiceImpl();
    final NettyDataBufferFactory buffers=new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
    UploadedDocumentBufferSafetyTest(){
        ReflectionTestUtils.setField(service,"repository",repository);ReflectionTestUtils.setField(service,"mapper",Mappers.getMapper(DocumentMapper.class));ReflectionTestUtils.setField(service,"ecmPortProvider",ports);
        when(repository.findById(documentId)).thenReturn(Mono.just(Document.builder().id(documentId).name("evidence.pdf").build()));
        when(ports.getDocumentContentPort()).thenReturn(Optional.of(content));when(ports.getDocumentSearchPort()).thenReturn(Optional.empty());
    }
    FilePart file(Flux<DataBuffer> body){
        var part=mock(FilePart.class);var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_PDF);
        when(part.filename()).thenReturn("evidence.pdf");when(part.headers()).thenReturn(headers);when(part.content()).thenReturn(body);return part;
    }
    @Test void successfulUploadReleasesAllPooledInputBuffers(){
        var first=buffers.allocateBuffer(2).write(new byte[]{1,2});var second=buffers.allocateBuffer(1).write(new byte[]{3});
        when(content.storeContent(eq(documentId),any(byte[].class),eq("application/pdf"))).thenReturn(Mono.just("controlled-reference"));
        when(repository.save(any(Document.class))).thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        StepVerifier.create(service.uploadContent(documentId,file(Flux.just(first,second)))).expectNextCount(1).verifyComplete();
        assertThat(first.getNativeBuffer().refCnt()).isZero();assertThat(second.getNativeBuffer().refCnt()).isZero();
    }
    @Test void oversizeUploadReleasesBuffersBeforeStorageOrMetadataMutation(){
        var oversized=buffers.allocateBuffer(DocumentServiceImpl.MAX_UPLOAD_CONTENT_BYTES+1).write(new byte[DocumentServiceImpl.MAX_UPLOAD_CONTENT_BYTES+1]);
        StepVerifier.create(service.uploadContent(documentId,file(Flux.just(oversized)))).expectError(DataBufferLimitException.class).verify();
        assertThat(oversized.getNativeBuffer().refCnt()).isZero();verifyNoInteractions(content);verify(repository,never()).save(any(Document.class));
    }
    @Test void emptyUploadCannotBecomeStoredMetadata(){
        StepVerifier.create(service.uploadContent(documentId,file(Flux.empty()))).expectError(IllegalArgumentException.class).verify();
        var empty=buffers.allocateBuffer(1);
        StepVerifier.create(service.uploadContent(documentId,file(Flux.just(empty)))).expectError(IllegalArgumentException.class).verify();
        assertThat(empty.getNativeBuffer().refCnt()).isZero();verifyNoInteractions(content);verify(repository,never()).save(any(Document.class));
    }
    @Test void blankStorageReferenceCannotBecomeSuccessfulMetadata(){
        var body=buffers.allocateBuffer(1).write(new byte[]{1});
        when(content.storeContent(eq(documentId),any(byte[].class),eq("application/pdf"))).thenReturn(Mono.just(" "));
        StepVerifier.create(service.uploadContent(documentId,file(Flux.just(body)))).expectError(IllegalStateException.class).verify();
        assertThat(body.getNativeBuffer().refCnt()).isZero();verify(repository,never()).save(any(Document.class));
    }
}
