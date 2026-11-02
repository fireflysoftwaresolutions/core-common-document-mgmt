/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.impl;

import io.fireflysoftware.commons.ecm.core.mappers.DocumentMapper;
import io.fireflysoftware.commons.ecm.models.entities.Document;
import io.fireflysoftware.commons.ecm.models.repositories.DocumentRepository;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.service.EcmPortProvider;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

/** Controlled owner-unit regressions; storage mocks establish no native/provider acceptance. */
class UploadedDocumentCustodyContractTest {
    static final UUID DOCUMENT=UUID.fromString("ca70a199-a06e-45c7-8fb7-65cba47a23c9");
    final DocumentRepository repository=mock(DocumentRepository.class);
    final DocumentContentPort content=mock(DocumentContentPort.class);
    final EcmPortProvider ports=mock(EcmPortProvider.class);
    final DocumentServiceImpl service=new DocumentServiceImpl();
    UploadedDocumentCustodyContractTest(){
        ReflectionTestUtils.setField(service,"repository",repository);ReflectionTestUtils.setField(service,"mapper",Mappers.getMapper(DocumentMapper.class));ReflectionTestUtils.setField(service,"ecmPortProvider",ports);
        when(repository.findById(DOCUMENT)).thenReturn(Mono.just(Document.builder().id(DOCUMENT).name("evidence.pdf").build()));
        when(ports.getDocumentContentPort()).thenReturn(Optional.of(content));when(ports.getDocumentSearchPort()).thenReturn(Optional.empty());
    }
    @Test void successfulContentUploadPersistsItsActualDigestAndLength()throws Exception{
        byte[] bytes={1,2,3};var file=mock(FilePart.class);var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_PDF);
        when(file.filename()).thenReturn("evidence.pdf");when(file.headers()).thenReturn(headers);when(file.content()).thenReturn(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes.clone())));
        when(content.storeContent(eq(DOCUMENT),any(byte[].class),eq("application/pdf"))).thenReturn(Mono.just("controlled-storage-reference"));
        when(repository.save(any(Document.class))).thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        StepVerifier.create(service.uploadContent(DOCUMENT,file)).assertNext(receipt->{assertThat(receipt.getChecksum()).isEqualTo(expected);assertThat(receipt.getFileSize()).isEqualTo(3L);}).verifyComplete();
        verify(content).storeContent(eq(DOCUMENT),aryEq(bytes),eq("application/pdf"));
    }
    @Test void failedContentDeletionPreservesMetadataForCustodyRecovery(){
        when(content.deleteContent(DOCUMENT)).thenReturn(Mono.error(new IOException("controlled deletion unavailable")));
        var deletions=new java.util.concurrent.atomic.AtomicInteger();
        when(repository.delete(any(Document.class))).thenReturn(Mono.defer(()->{deletions.incrementAndGet();return Mono.empty();}));
        StepVerifier.create(service.delete(DOCUMENT)).expectError(IOException.class).verify();
        assertThat(deletions.get()).isZero();
    }
}
