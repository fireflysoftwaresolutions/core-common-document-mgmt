/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.controllers;

import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.interfaces.upload.*;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceReadAdmissionImpl;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/managed-document-evidence")
@SecurityRequirement(name="documentEvidenceBearer")
public class EvidenceReadController {
    private final ObjectProvider<AuthenticatedEvidenceReadAdmissionImpl> authorities;private final ObjectProvider<EvidenceReadStore> owners;
    public EvidenceReadController(ObjectProvider<AuthenticatedEvidenceReadAdmissionImpl> authorities,ObjectProvider<EvidenceReadStore> owners){this.authorities=authorities;this.owners=owners;}
    @GetMapping(path="/applications/{applicationId}/actions/{actionId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId="observeManagedDocumentEvidence",summary="Observe the original immutable content receipt under current signed read authority")
    @ApiResponses({@ApiResponse(responseCode="200",description="Existing original content and canonical pair verified",content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EvidenceUploadReceipt.class))),
        @ApiResponse(responseCode="401",description="Native bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current read/application/party authority refused",content={}),
        @ApiResponse(responseCode="503",description="Current observation unavailable; no upload or replacement action was dispatched",content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EvidenceUploadError.class)))})
    public Mono<ResponseEntity<EvidenceUploadReceipt>> observe(@PathVariable UUID applicationId,@PathVariable UUID actionId){return read(applicationId,actionId).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value.receipt()));}
    @GetMapping(path="/applications/{applicationId}/actions/{actionId}/content",produces=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(operationId="readManagedDocumentEvidenceContent",summary="Read actual bounded stored bytes after current authority and canonical-pair verification")
    @ApiResponses({@ApiResponse(responseCode="200",description="Actual original PostgreSQL content; consumers must independently hash the returned bytes",content=@Content(mediaType=MediaType.APPLICATION_OCTET_STREAM_VALUE,schema=@Schema(type="string",format="binary")),headers=@Header(name="X-Evidence-Content-SHA256",description="SHA256 of actual returned content",schema=@Schema(type="string",pattern="[0-9a-f]{64}"))),
        @ApiResponse(responseCode="401",description="Native bearer authentication refused",content={}),@ApiResponse(responseCode="403",description="Current read/application/party authority refused",content={}),
        @ApiResponse(responseCode="503",description="Current bytes or canonical pair unavailable; no upload or replacement action was dispatched",content=@Content(mediaType=MediaType.APPLICATION_JSON_VALUE,schema=@Schema(implementation=EvidenceUploadError.class)))})
    public Mono<ResponseEntity<Resource>> content(@PathVariable UUID applicationId,@PathVariable UUID actionId){return read(applicationId,actionId).map(value->ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(value.receipt().contentLength()).header("X-Evidence-Content-SHA256",value.receipt().contentDigest())
        .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(value.receipt().fileName(),StandardCharsets.UTF_8).build().toString()).body((Resource)new ByteArrayResource(value.content())));}
    private Mono<EvidenceContentRead> read(UUID application,UUID action){return Mono.defer(()->{
        final AuthenticatedEvidenceReadAdmissionImpl authority;final EvidenceReadStore owner;
        try{authority=authorities.getIfUnique();owner=owners.getIfUnique();}catch(RuntimeException unavailable){return Mono.error(new EvidenceReadFailure(action));}
        if(authority==null||owner==null)return Mono.error(new EvidenceReadFailure(action));
        return authority.invoke(application,action,owner::read).onErrorMap(error->{if(error instanceof EvidenceReadFailure)return error;if(error instanceof ResponseStatusException status&&java.util.Set.of(401,403).contains(status.getStatusCode().value()))return error;return new EvidenceReadFailure(action);});
    });}
    @ExceptionHandler(EvidenceReadFailure.class)
    public ResponseEntity<EvidenceUploadError> unavailable(EvidenceReadFailure error){return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(new EvidenceUploadError(error.actionId(),error.getCode(),EvidenceUploadError.Outcome.OBSERVATION_UNAVAILABLE,"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));}
}
