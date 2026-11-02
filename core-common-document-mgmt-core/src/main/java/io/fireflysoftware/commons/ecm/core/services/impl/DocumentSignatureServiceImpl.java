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

import io.fireflysoftware.commons.ecm.core.mappers.DocumentSignatureMapper;
import io.fireflysoftware.commons.ecm.core.mappers.EcmDomainMapper;
import io.fireflysoftware.commons.ecm.core.services.DocumentSignatureService;
import io.fireflysoftware.commons.ecm.core.validation.EcmParameterValidator;
import io.fireflysoftware.commons.ecm.core.services.SignatureRequestService;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentSignatureDTO;
import io.fireflysoftware.commons.ecm.interfaces.dtos.SignatureRequestDTO;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureStatus;
import io.fireflysoftware.commons.ecm.models.entities.DocumentSignature;
import io.fireflysoftware.commons.ecm.models.repositories.DocumentSignatureRepository;
import org.fireflyframework.ecm.service.EcmPortProvider;
import org.fireflyframework.ecm.port.esignature.SignatureRequestPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.ecm.domain.model.esignature.SignatureRequest;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureRequestStatus;
import org.fireflyframework.ecm.domain.enums.esignature.SignatureRequestType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
/**
 * Implementation of the DocumentSignatureService interface.
 * Provides comprehensive document signature management with ECM port integration.
 */
@Service
@Transactional
@Slf4j
public class DocumentSignatureServiceImpl implements DocumentSignatureService {

    @Autowired
    private DocumentSignatureRepository repository;

    @Autowired
    private DocumentSignatureMapper mapper;

    @Autowired
    private EcmPortProvider ecmPortProvider;

    @Autowired
    private SignatureRequestService signatureRequestService;

    @Autowired
    private EcmDomainMapper ecmDomainMapper;

    @Autowired
    private EcmParameterValidator ecmParameterValidator;

    @Override
    public Mono<DocumentSignatureDTO> getById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<DocumentSignatureDTO>> filter(FilterRequest<DocumentSignatureDTO> filterRequest) {
        return FilterUtils.createFilter(
                DocumentSignature.class,
                mapper::toDTO
        ).filter(filterRequest);
    }

    @Override
    public Mono<DocumentSignatureDTO> update(DocumentSignatureDTO documentSignature) {
        if (documentSignature.getId() == null) {
            return Mono.error(new IllegalArgumentException("ID cannot be null for update operation"));
        }

        return repository.findById(documentSignature.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("Document signature not found with ID: " + documentSignature.getId())))
                .flatMap(existingEntity -> {
                    DocumentSignature entityToUpdate = mapper.toEntity(documentSignature);
                    // Preserve created info
                    entityToUpdate.setCreatedAt(existingEntity.getCreatedAt());
                    entityToUpdate.setCreatedBy(existingEntity.getCreatedBy());
                    return repository.save(entityToUpdate);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<DocumentSignatureDTO> create(DocumentSignatureDTO documentSignature) {
        // Ensure ID is null for create operation
        documentSignature.setId(null);

        DocumentSignature entity = mapper.toEntity(documentSignature);
        return repository.save(entity)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Document signature not found with ID: " + id)))
                .flatMap(entity -> repository.delete(entity));
    }

    @Override
    public Flux<DocumentSignatureDTO> getByDocumentId(UUID documentId) {
        return repository.findByDocumentId(documentId)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<DocumentSignatureDTO> getByDocumentVersionId(UUID documentVersionId) {
        return repository.findByDocumentVersionId(documentVersionId)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<DocumentSignatureDTO> getBySignerPartyId(UUID signerPartyId) {
        return repository.findBySignerPartyId(signerPartyId)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<DocumentSignatureDTO> getBySignatureStatus(SignatureStatus signatureStatus) {
        return repository.findBySignatureStatus(signatureStatus)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Mono<DocumentSignatureDTO> initiateSigningProcess(DocumentSignatureDTO documentSignature) {
        return Mono.error(new io.fireflysoftware.commons.ecm.core.signing.SigningIntegrationUnavailableException());
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Mono<DocumentSignatureDTO> cancelSignature(UUID id) {
        return Mono.error(new io.fireflysoftware.commons.ecm.core.signing.SigningIntegrationUnavailableException());
    }

    @Override
    public Mono<Boolean> isDocumentFullySigned(UUID documentId) {
        // Check if there are any pending signatures for the document
        return repository.findByDocumentIdAndSignatureStatus(documentId, SignatureStatus.PENDING)
                .count()
                .map(count -> count == 0)
                .flatMap(noMorePendingSignatures -> {
                    if (noMorePendingSignatures) {
                        // If there are no pending signatures, check if there are any signed signatures
                        return repository.findByDocumentIdAndSignatureStatus(documentId, SignatureStatus.SIGNED)
                                .count()
                                .map(count -> count > 0);
                    } else {
                        // If there are pending signatures, the document is not fully signed
                        return Mono.just(false);
                    }
                });
    }
}
