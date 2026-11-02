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


package io.fireflysoftware.commons.ecm.core.services.impl;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;

import io.fireflysoftware.commons.ecm.core.mappers.DocumentMapper;
import io.fireflysoftware.commons.ecm.core.mappers.EcmDomainMapper;
import io.fireflysoftware.commons.ecm.core.services.DocumentService;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentDTO;
import io.fireflysoftware.commons.ecm.models.entities.Document;
import io.fireflysoftware.commons.ecm.models.repositories.DocumentRepository;
import org.fireflyframework.ecm.service.EcmPortProvider;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.document.DocumentVersionPort;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/**
 * Implementation of the DocumentService interface.
 * Provides comprehensive document management with ECM port integration.
 */
@Service
@Transactional
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    public static final int MAX_UPLOAD_CONTENT_BYTES = 10 * 1024 * 1024;

    @Autowired
    private DocumentRepository repository;

    @Autowired
    private DocumentMapper mapper;

    @Autowired
    private EcmPortProvider ecmPortProvider;

    @Autowired
    private EcmDomainMapper ecmDomainMapper;

    @Override
    public Mono<DocumentDTO> getById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<DocumentDTO>> filter(FilterRequest<DocumentDTO> filterRequest) {
        return FilterUtils.createFilter(
                Document.class,
                mapper::toDTO
        ).filter(filterRequest);
    }

    @Override
    public Mono<DocumentDTO> update(DocumentDTO document) {
        if (document.getId() == null) {
            return Mono.error(new IllegalArgumentException("ID cannot be null for update operation"));
        }

        return repository.findById(document.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + document.getId())))
                .flatMap(existingEntity -> {
                    Document entityToUpdate = mapper.toEntity(document);
                    // Preserve created info
                    entityToUpdate.setCreatedAt(existingEntity.getCreatedAt());
                    entityToUpdate.setCreatedBy(existingEntity.getCreatedBy());
                    return repository.save(entityToUpdate);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<DocumentDTO> create(DocumentDTO document) {
        log.debug("Creating new document: {}", document.getName());

        // Ensure ID is null for create operation
        document.setId(null);

        Document entity = mapper.toEntity(document);
        return repository.save(entity)
                .doOnSuccess(savedEntity -> log.info("Document created successfully with ID: {}", savedEntity.getId()))
                .doOnError(error -> log.error("Failed to create document: {}", error.getMessage(), error))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> delete(UUID id) {
        log.debug("Deleting document with ID: {}", id);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + id)))
                .flatMap(entity -> {
                    log.info("Deleting document: {} (ID: {})", entity.getName(), entity.getId());

                    // Delete content from ECM storage if available
                    return ecmPortProvider.getDocumentContentPort()
                            .map(port -> {
                                log.debug("Deleting document content from ECM storage for document ID: {}", entity.getId());
                                return port.deleteContent(java.util.UUID.fromString(entity.getId().toString()))
                                        .doOnSuccess(result -> log.debug("Document content deleted from ECM storage"))
                                        .doOnError(error -> log.warn("Failed to delete document content from ECM storage: {}", error.getMessage()))
                                        .then(repository.delete(entity));
                            })
                            .orElse(repository.delete(entity))
                            .then(Mono.defer(() ->
                                    ecmPortProvider.getDocumentSearchPort()
                                            .map(searchPort -> searchPort.removeFromIndex(java.util.UUID.fromString(entity.getId().toString()))
                                                    .onErrorResume(err -> Mono.empty()))
                                            .orElse(Mono.empty())
                            ))
                            .doOnSuccess(result -> log.info("Document deleted successfully: {}", entity.getId()))
                            .doOnError(error -> log.error("Failed to delete document {}: {}", entity.getId(), error.getMessage(), error));
                });
    }

    // ECM Port Operations Implementation

    @Override
    public Mono<DocumentDTO> uploadContent(UUID documentId, FilePart filePart) {
        log.debug("Uploading content for document ID: {} with filename: {}", documentId, filePart.filename());

        return repository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + documentId)))
                .flatMap(document -> {
                    log.info("Uploading content for document: {} (ID: {}), filename: {}",
                            document.getName(), document.getId(), filePart.filename());

                    // Upload content using ECM port if available
                    java.util.UUID documentUuid = java.util.UUID.fromString(document.getId().toString());

                    return ecmPortProvider.getDocumentContentPort()
                            .map(port -> {
                                log.debug("Using ECM DocumentContentPort to store content");
                                return DataBufferUtils.join(filePart.content(), MAX_UPLOAD_CONTENT_BYTES)
                                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Nonempty document content required")))
                                        .map(buffer -> {
                                            try {
                                                byte[] bytes = new byte[buffer.readableByteCount()];
                                                if (bytes.length == 0) throw new IllegalArgumentException("Nonempty document content required");
                                                buffer.read(bytes);
                                                return bytes;
                                            } finally {
                                                DataBufferUtils.release(buffer);
                                            }
                                        })
                                        .flatMap(contentBytes -> {
                                            String mimeType = filePart.headers().getContentType() != null
                                                    ? filePart.headers().getContentType().toString()
                                                    : "application/octet-stream";
                                            String checksum = contentDigest(contentBytes);
                                            long contentLength = contentBytes.length;
                                            return port.storeContent(documentUuid, contentBytes, mimeType)
                                                    .switchIfEmpty(Mono.error(new IllegalStateException("Stored content reference required")))
                                                    .flatMap(storagePath -> {
                                                        if (storagePath.isBlank()) {
                                                            return Mono.error(new IllegalStateException("Stored content reference required"));
                                                        }
                                                        document.setFileName(filePart.filename());
                                                        document.setStoragePath(storagePath);
                                                        document.setMimeType(mimeType);
                                                        document.setFileSize(contentLength);
                                                        document.setChecksum(checksum);
                                                        return repository.save(document)
                                                                .flatMap(savedDoc -> ecmPortProvider.getDocumentSearchPort()
                                                                        .map(searchPort -> searchPort.indexDocument(ecmDomainMapper.toEcmDocument(savedDoc))
                                                                                .onErrorResume(err -> {
                                                                                    log.warn("Indexing failed for document {}: {}", savedDoc.getId(), err.getMessage());
                                                                                    return Mono.empty();
                                                                                })
                                                                                .thenReturn(savedDoc))
                                                                        .orElse(Mono.just(savedDoc)));
                                                    });
                                        })
                                .doOnError(error -> {
                                    log.error("Failed to upload content for document ID {}: {}", documentId, error.getMessage(), error);
                                });
                            })
                            .orElseThrow(() -> {
                                log.warn("Document content upload not available - ECM DocumentContentPort not configured");
                                return new RuntimeException("Document content upload requires ECM DocumentContentPort to be configured");
                            });
                })
                // Note: Search indexing would be handled by ECM search port if available
                .map(savedDocument -> savedDocument)
                .map(mapper::toDTO);
    }

    private static String contentDigest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required content digest unavailable", impossible);
        }
    }

    @Override
    public Flux<DataBuffer> downloadContent(UUID documentId) {
        log.debug("Downloading content for document ID: {}", documentId);

        return repository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + documentId)))
                .flatMapMany(document -> {
                    log.info("Downloading content for document: {} (ID: {})", document.getName(), document.getId());

                    // Download content using ECM port if available
                    java.util.UUID documentUuid = java.util.UUID.fromString(document.getId().toString());

                    return ecmPortProvider.getDocumentContentPort()
                            .map(port -> {
                                log.debug("Using ECM DocumentContentPort to download content");
                                return port.getContentStream(documentUuid)
                                        .doOnNext(buffer -> log.trace("Downloaded content buffer of size: {}", buffer.readableByteCount()))
                                        .doOnComplete(() -> log.debug("Content download completed for document ID: {}", documentId))
                                        .doOnError(error -> {
                                            log.error("Failed to download content for document ID {}: {}", documentId, error.getMessage(), error);
                                        });
                            })
                            .orElseThrow(() -> {
                                log.warn("Document content not available - ECM DocumentContentPort not configured");
                                return new RuntimeException("Document content download requires ECM DocumentContentPort to be configured");
                            });
                });
    }

    @Override
    public Mono<DocumentDTO> createVersion(UUID documentId, FilePart filePart, String versionComment) {
        log.debug("Creating new version for document ID: {} with comment: {}", documentId, versionComment);
        
        return repository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + documentId)))
                .flatMap(document -> {
                    log.info("Creating version for document: {} (ID: {}), current version: {}",
                            document.getName(), document.getId(), document.getVersion());
                    
                    java.util.UUID documentUuid = java.util.UUID.fromString(document.getId().toString());
                    Integer nextVersionNumber = (document.getVersion() == null ? 0 : document.getVersion().intValue()) + 1;
                    
                    // Use ECM port for version management if available
                    return ecmPortProvider.getDocumentVersionPort()
                            .map(port -> {
                                log.debug("Using ECM DocumentVersionPort to create version");
                                
                                // Convert FilePart content to byte array and create version through ECM port
                                return filePart.content()
                                        .collectList()
                                        .map(dataBuffers -> {
                                            // Convert DataBuffer list to byte array
                                            int totalSize = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
                                            byte[] bytes = new byte[totalSize];
                                            int offset = 0;
                                            for (DataBuffer buffer : dataBuffers) {
                                                int length = buffer.readableByteCount();
                                                buffer.read(bytes, offset, length);
                                                offset += length;
                                            }
                                            return bytes;
                                        })
                                        .flatMap(contentBytes -> {
                                            // Create ECM DocumentVersion domain object
                                            String mimeType = filePart.headers().getContentType() != null
                                                    ? filePart.headers().getContentType().toString()
                                                    : "application/octet-stream";
                                            
                                            org.fireflyframework.ecm.domain.model.document.DocumentVersion ecmVersion = 
                                                org.fireflyframework.ecm.domain.model.document.DocumentVersion.builder()
                                                    .id(java.util.UUID.randomUUID())
                                                    .documentId(documentUuid)
                                                    .versionNumber(nextVersionNumber)
                                                    .versionLabel(String.format("v%d", nextVersionNumber))
                                                    .comment(versionComment != null ? versionComment : "Version created")
                                                    .size((long) contentBytes.length)
                                                    .mimeType(mimeType)
                                                    .createdAt(java.time.Instant.now())
                                                    .current(true)
                                                    .majorVersion(false)
                                                    .status(org.fireflyframework.ecm.domain.enums.document.VersionStatus.CURRENT)
                                                    .versionType(org.fireflyframework.ecm.domain.enums.document.VersionType.MANUAL)
                                                    .changesSummary(versionComment)
                                                    .build();
                                            
                                            // Create version in ECM with content
                                            return port.createVersion(ecmVersion, contentBytes)
                                                    .doOnSuccess(createdVersion -> 
                                                        log.debug("ECM version created with ID: {}, storage path: {}", 
                                                                createdVersion.getId(), createdVersion.getStoragePath()))
                                                    .flatMap(createdVersion -> {
                                                        // Update document with new version information
                                                        document.setVersion(nextVersionNumber.longValue());
                                                        document.setFileName(filePart.filename());
                                                        document.setMimeType(mimeType);
                                                        document.setStoragePath(createdVersion.getStoragePath());
                                                        
                                                        return repository.save(document)
                                                                .flatMap(savedDoc -> {
                                                                    // Index updated document in search if available
                                                                    return ecmPortProvider.getDocumentSearchPort()
                                                                            .map(searchPort -> searchPort.indexDocument(ecmDomainMapper.toEcmDocument(savedDoc))
                                                                                    .onErrorResume(err -> {
                                                                                        log.warn("Indexing failed for document {}: {}", savedDoc.getId(), err.getMessage());
                                                                                        return Mono.empty();
                                                                                    })
                                                                                    .thenReturn(savedDoc))
                                                                            .orElse(Mono.just(savedDoc));
                                                                })
                                                                .doOnSuccess(savedDoc -> 
                                                                    log.info("Document version {} created successfully for document ID: {}", 
                                                                            nextVersionNumber, savedDoc.getId()));
                                                    });
                                        })
                                        .doOnError(error -> {
                                            log.error("Failed to create version for document ID {}: {}", 
                                                    documentId, error.getMessage(), error);
                                        });
                            })
                            .orElse(
                                // Fallback: simple version increment without ECM versioning
                                Mono.defer(() -> {
                                    log.warn("Creating version without ECM - DocumentVersionPort not available");
                                    document.setVersion(nextVersionNumber.longValue());
                                    document.setFileName(filePart.filename());
                                    document.setMimeType(filePart.headers().getContentType() != null ? 
                                            filePart.headers().getContentType().toString() : null);
                                    return repository.save(document)
                                            .flatMap(savedDoc -> ecmPortProvider.getDocumentSearchPort()
                                                    .map(searchPort -> searchPort.indexDocument(ecmDomainMapper.toEcmDocument(savedDoc))
                                                            .onErrorResume(err -> Mono.empty())
                                                            .thenReturn(savedDoc))
                                                    .orElse(Mono.just(savedDoc)))
                                            .doOnSuccess(savedDoc -> 
                                                log.debug("Document version incremented locally (no ECM) for ID: {}", savedDoc.getId()));
                                })
                            );
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<DocumentDTO> getContentMetadata(UUID documentId) {
        return repository.findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found with ID: " + documentId)))
                .map(mapper::toDTO);
    }
}
