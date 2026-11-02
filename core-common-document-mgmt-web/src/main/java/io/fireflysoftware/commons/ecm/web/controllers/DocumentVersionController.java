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
import io.fireflysoftware.commons.ecm.core.services.DocumentVersionService;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentVersionDTO;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * REST controller for managing Document Version resources.
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/versions")
@RequiredArgsConstructor
@Tag(name = "Document Version Controller", description = "API for managing document versions")
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;

    @GetMapping
    @Operation(summary = "List all versions of a document", description = "Returns a paginated list of versions for a specific document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved document versions",
                    content = @Content(schema = @Schema(implementation = PaginationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public Mono<PaginationResponse<DocumentVersionDTO>> listDocumentVersions(
            @Parameter(description = "ID of the document") @PathVariable UUID documentId,
            @Parameter(description = "Filter request for document versions") @ParameterObject @ModelAttribute FilterRequest<DocumentVersionDTO> filterRequest) {
        // We'll use the service's filter method directly
        // The service implementation should handle filtering by document ID
        return documentVersionService.filter(filterRequest != null ? filterRequest : new FilterRequest<>());
    }

    @GetMapping("/{versionId}")
    @Operation(summary = "Get specific version of a document", description = "Returns a specific version of a document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved document version",
                    content = @Content(schema = @Schema(implementation = DocumentVersionDTO.class))),
            @ApiResponse(responseCode = "404", description = "Document version not found")
    })
    public Mono<DocumentVersionDTO> getDocumentVersion(
            @Parameter(description = "ID of the document") @PathVariable UUID documentId,
            @Parameter(description = "ID of the version to retrieve") @PathVariable UUID versionId) {
        return documentVersionService.getById(versionId)
                .filter(version -> version.getDocumentId().equals(documentId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new document version", description = "Creates a new version for a document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document version created successfully",
                    content = @Content(schema = @Schema(implementation = DocumentVersionDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid document version data"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public Mono<DocumentVersionDTO> createDocumentVersion(
            @Parameter(description = "ID of the document") @PathVariable UUID documentId,
            @Parameter(description = "Document version data to create") @RequestBody DocumentVersionDTO versionDTO) {
        versionDTO.setDocumentId(documentId);
        return documentVersionService.create(versionDTO);
    }

    @DeleteMapping("/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document version", description = "Deletes a specific version of a document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Document version deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Document version not found")
    })
    public Mono<Void> deleteDocumentVersion(
            @Parameter(description = "ID of the document") @PathVariable UUID documentId,
            @Parameter(description = "ID of the version to delete") @PathVariable UUID versionId) {
        return documentVersionService.getById(versionId)
                .filter(version -> version.getDocumentId().equals(documentId))
                .flatMap(version -> documentVersionService.delete(versionId));
    }

    // ECM Operations for Version Content

    @PostMapping(value = "/{versionId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload content for a document version", description = "Uploads file content for a specific document version")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Version content uploaded successfully",
                    content = @Content(schema = @Schema(implementation = DocumentVersionDTO.class))),
            @ApiResponse(responseCode = "404", description = "Document version not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file upload")
    })
    public Mono<DocumentVersionDTO> uploadVersionContent(
            @Parameter(description = "ID of the document") @PathVariable UUID documentId,
            @Parameter(description = "ID of the version to upload content for") @PathVariable UUID versionId,
            @Parameter(description = "File to upload") @RequestPart("file") FilePart filePart) {
        return documentVersionService.getById(versionId)
                .filter(version -> version.getDocumentId().equals(documentId))
                .flatMap(version -> documentVersionService.uploadVersionContent(versionId, filePart));
    }

    @GetMapping("/{versionId}/download")
    @Operation(summary = "Download document version content", description = "Downloads the content of a specific document version")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Version content downloaded successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Document version not found"),
            @ApiResponse(responseCode = "404", description = "Version content not available")
    })
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadVersionContent(
            @Parameter(description = "ID of the document") @PathVariable UUID documentId,
            @Parameter(description = "ID of the version to download") @PathVariable UUID versionId) {
        return documentVersionService.getById(versionId)
                .filter(version -> version.getDocumentId().equals(documentId))
                .map(version -> {
                    Flux<DataBuffer> content = documentVersionService.downloadVersionContent(versionId);
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + version.getFileName() + "\"");
                    if (version.getMimeType() != null) {
                        headers.add(HttpHeaders.CONTENT_TYPE, version.getMimeType());
                    }
                    
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(content);
                })
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/{versionId}/metadata")
    @Operation(summary = "Get document version content metadata", description = "Retrieves metadata about document version content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Version metadata retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DocumentVersionDTO.class))),
            @ApiResponse(responseCode = "404", description = "Document version not found")
    })
    public Mono<DocumentVersionDTO> getVersionContentMetadata(
            @Parameter(description = "ID of the document") @PathVariable UUID documentId,
            @Parameter(description = "ID of the version") @PathVariable UUID versionId) {
        return documentVersionService.getById(versionId)
                .filter(version -> version.getDocumentId().equals(documentId))
                .flatMap(version -> documentVersionService.getVersionContentMetadata(versionId));
    }
}
