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


package io.fireflysoftware.commons.ecm.web.controllers;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.commons.ecm.core.services.DocumentService;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ModelAttribute;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * REST controller for managing Document resources.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Document Controller", description = "API for managing documents")
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    @Operation(summary = "List all documents", description = "Returns a paginated list of documents with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved documents",
                    content = @Content(schema = @Schema(implementation = PaginationResponse.class)))
    })
    public Mono<PaginationResponse<DocumentDTO>> listDocuments(
            @Parameter(description = "Filter request for documents") @ParameterObject @ModelAttribute FilterRequest<DocumentDTO> filterRequest) {
        return documentService.filter(filterRequest != null ? filterRequest : new FilterRequest<>());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID", description = "Returns a document by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved document",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public Mono<DocumentDTO> getDocumentById(
            @Parameter(description = "ID of the document to retrieve") @PathVariable UUID id) {
        return documentService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new document", description = "Creates a new document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document created successfully",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid document data")
    })
    public Mono<DocumentDTO> createDocument(
            @Parameter(description = "Document data to create") @RequestBody DocumentDTO documentDTO) {
        return documentService.create(documentDTO);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing document", description = "Updates an existing document by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document updated successfully",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid document data"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public Mono<DocumentDTO> updateDocument(
            @Parameter(description = "ID of the document to update") @PathVariable UUID id,
            @Parameter(description = "Updated document data") @RequestBody DocumentDTO documentDTO) {
        documentDTO.setId(id);
        return documentService.update(documentDTO);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document", description = "Deletes a document by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public Mono<Void> deleteDocument(
            @Parameter(description = "ID of the document to delete") @PathVariable UUID id) {
        return documentService.delete(id);
    }

    // ECM Operations

    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload document content", description = "Uploads file content for an existing document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document content uploaded successfully",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file upload")
    })
    public Mono<DocumentDTO> uploadContent(
            @Parameter(description = "ID of the document to upload content for") @PathVariable UUID id,
            @Parameter(description = "File to upload") @RequestPart("file") FilePart filePart) {
        return documentService.uploadContent(id, filePart);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download document content", description = "Downloads the content of a document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document content downloaded successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "404", description = "Document content not available")
    })
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadContent(
            @Parameter(description = "ID of the document to download") @PathVariable UUID id) {
        return documentService.getById(id)
                .map(document -> {
                    Flux<DataBuffer> content = documentService.downloadContent(id);
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + document.getFileName() + "\"");
                    if (document.getMimeType() != null) {
                        headers.add(HttpHeaders.CONTENT_TYPE, document.getMimeType());
                    }
                    
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(content);
                })
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create new document version", description = "Creates a new version of a document with new content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document version created successfully",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file upload")
    })
    public Mono<DocumentDTO> createVersion(
            @Parameter(description = "ID of the document to create version for") @PathVariable UUID id,
            @Parameter(description = "File for new version") @RequestPart("file") FilePart filePart,
            @Parameter(description = "Version comment") @RequestParam(required = false) String comment) {
        return documentService.createVersion(id, filePart, comment);
    }

    @GetMapping("/{id}/metadata")
    @Operation(summary = "Get document content metadata", description = "Retrieves metadata about document content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document metadata retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public Mono<DocumentDTO> getContentMetadata(
            @Parameter(description = "ID of the document") @PathVariable UUID id) {
        return documentService.getContentMetadata(id);
    }
}
