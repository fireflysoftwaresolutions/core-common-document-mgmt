/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.controllers;

import io.fireflysoftware.commons.ecm.core.upload.*;
import org.fireflyframework.web.idempotency.annotation.DisableIdempotency;
import io.fireflysoftware.commons.ecm.interfaces.upload.*;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceUploadAdmissionImpl;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/managed-document-evidence")
@SecurityScheme(name="documentEvidenceBearer",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
public class EvidenceUploadController {
    private final ObjectProvider<AuthenticatedEvidenceUploadAdmissionImpl> authorities;
    private final ObjectProvider<EvidenceUploadStore> owners;
    public EvidenceUploadController(ObjectProvider<AuthenticatedEvidenceUploadAdmissionImpl> authorities,ObjectProvider<EvidenceUploadStore> owners){this.authorities=authorities;this.owners=owners;}
    @DisableIdempotency
    @PostMapping(path="/applications/{applicationId}/actions/{actionId}",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="storeManagedDocumentEvidence",summary="Store bounded original evidence content under current application and party authority")
    @SecurityRequirement(name="documentEvidenceBearer")
    @ApiResponses({@ApiResponse(responseCode="201",description="Original content and pending custody stored",content=@Content(schema=@Schema(implementation=EvidenceUploadReceipt.class))),
        @ApiResponse(responseCode="200",description="Exact original action and content replayed",content=@Content(schema=@Schema(implementation=EvidenceUploadReceipt.class))),
        @ApiResponse(responseCode="400",description="Invalid path binding, metadata or decoded content bound",content={}),
        @ApiResponse(responseCode="401",description="Native bearer authentication refused",content={}),
        @ApiResponse(responseCode="403",description="Current signed party or owner authorization refused",content={}),
        @ApiResponse(responseCode="413",description="Bounded JSON envelope exceeded",content={}),
        @ApiResponse(responseCode="409",description="Original action conflicts with different content or metadata; retained custody is unchanged",content=@Content(schema=@Schema(implementation=EvidenceUploadError.class))),
        @ApiResponse(responseCode="503",description="REFUSED after proven rollback, OUTCOME_UNKNOWN after uncertain completion, or COMMITTED_RESPONSE_UNAVAILABLE; recover only the same original action",content=@Content(schema=@Schema(implementation=EvidenceUploadError.class)))})
    public Mono<ResponseEntity<EvidenceUploadReceipt>> store(@PathVariable UUID applicationId,@PathVariable UUID actionId,@Valid @RequestBody EvidenceUploadRequest request){return Mono.defer(()->{
        if(request==null||!applicationId.equals(request.applicationId())||!actionId.equals(request.actionId()))return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exact original application and action required"));
        var authority=authorities.getIfUnique();var owner=owners.getIfUnique();
        if(authority==null||owner==null)return Mono.error(new EvidenceUploadFailure(actionId,EvidenceUploadFailure.Outcome.REFUSED));
        return authority.invoke(request,owner).map(receipt->ResponseEntity.status(receipt.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(receipt))
            .onErrorMap(ResponseStatusException.class,error->error.getStatusCode().value()==503?new EvidenceUploadFailure(actionId,EvidenceUploadFailure.Outcome.OUTCOME_UNKNOWN):error);
    });}
    @ExceptionHandler(EvidenceUploadFailure.class)
    public ResponseEntity<EvidenceUploadError> unavailable(EvidenceUploadFailure error){return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new EvidenceUploadError(error.actionId(),error.getCode(),EvidenceUploadError.Outcome.valueOf(error.outcome().name()),"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
    @ExceptionHandler(EvidenceUploadConflict.class)
    public ResponseEntity<EvidenceUploadError> conflict(EvidenceUploadConflict error){return ResponseEntity.status(HttpStatus.CONFLICT).body(new EvidenceUploadError(error.actionId(),error.getCode(),EvidenceUploadError.Outcome.CONFLICT,"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
}
