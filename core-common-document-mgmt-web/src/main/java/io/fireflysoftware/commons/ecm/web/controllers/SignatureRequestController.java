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
import io.fireflysoftware.commons.ecm.core.services.SignatureRequestService;
import io.fireflysoftware.commons.ecm.interfaces.dtos.SignatureRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * REST controller for managing Signature Request resources.
 */
@RestController
@RequestMapping("/api/v1/signature-requests")
@RequiredArgsConstructor
@Tag(name = "Signature Request Controller", description = "API for managing signature requests")
public class SignatureRequestController {

    private final SignatureRequestService signatureRequestService;

    @GetMapping
    @Operation(summary = "List all signature requests", description = "Returns a paginated list of signature requests with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved signature requests",
                    content = @Content(schema = @Schema(implementation = PaginationResponse.class)))
    })
    public Mono<PaginationResponse<SignatureRequestDTO>> listSignatureRequests(
            @Parameter(description = "Filter request for signature requests") @ParameterObject @ModelAttribute FilterRequest<SignatureRequestDTO> filterRequest) {
        return signatureRequestService.filter(filterRequest != null ? filterRequest : new FilterRequest<>());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get signature request by ID", description = "Returns a signature request by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved signature request",
                    content = @Content(schema = @Schema(implementation = SignatureRequestDTO.class))),
            @ApiResponse(responseCode = "404", description = "Signature request not found")
    })
    public Mono<SignatureRequestDTO> getSignatureRequestById(
            @Parameter(description = "ID of the signature request to retrieve") @PathVariable UUID id) {
        return signatureRequestService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new signature request", description = "Creates a new signature request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Signature request created successfully",
                    content = @Content(schema = @Schema(implementation = SignatureRequestDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid signature request data")
    })
    public Mono<SignatureRequestDTO> createSignatureRequest(
            @Parameter(description = "Signature request data to create") @RequestBody SignatureRequestDTO requestDTO) {
        return signatureRequestService.create(requestDTO);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing signature request", description = "Updates an existing signature request by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signature request updated successfully",
                    content = @Content(schema = @Schema(implementation = SignatureRequestDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid signature request data"),
            @ApiResponse(responseCode = "404", description = "Signature request not found")
    })
    public Mono<SignatureRequestDTO> updateSignatureRequest(
            @Parameter(description = "ID of the signature request to update") @PathVariable UUID id,
            @Parameter(description = "Updated signature request data") @RequestBody SignatureRequestDTO requestDTO) {
        requestDTO.setId(id);
        return signatureRequestService.update(requestDTO);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a signature request", description = "Deletes a signature request by its ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Signature request deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Signature request not found")
    })
    public Mono<Void> deleteSignatureRequest(
            @Parameter(description = "ID of the signature request to delete") @PathVariable UUID id) {
        return signatureRequestService.delete(id);
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send a signature request", description = "Sends a notification for a signature request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signature request notification sent successfully",
                    content = @Content(schema = @Schema(implementation = SignatureRequestDTO.class))),
            @ApiResponse(responseCode = "404", description = "Signature request not found"),
            @ApiResponse(responseCode = "503", description = "Trusted signing dispatch bridge unavailable; legacy association is unadopted")
    })
    public Mono<SignatureRequestDTO> sendSignatureRequest(
            @Parameter(description = "ID of the signature request to send") @PathVariable UUID id) {
        return signatureRequestService.sendNotification(id);
    }

    @PostMapping("/{id}/remind")
    @Operation(summary = "Send a reminder for a signature request", description = "Sends a reminder for a signature request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signature request reminder sent successfully",
                    content = @Content(schema = @Schema(implementation = SignatureRequestDTO.class))),
            @ApiResponse(responseCode = "404", description = "Signature request not found"),
            @ApiResponse(responseCode = "503", description = "Trusted signing dispatch bridge unavailable; legacy association is unadopted")
    })
    public Mono<SignatureRequestDTO> sendSignatureRequestReminder(
            @Parameter(description = "ID of the signature request to remind") @PathVariable UUID id) {
        return signatureRequestService.sendReminder(id);
    }
}
