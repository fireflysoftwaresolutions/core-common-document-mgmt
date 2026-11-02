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
import io.fireflysoftware.commons.ecm.core.mappers.SignatureRequestMapper;
import io.fireflysoftware.commons.ecm.core.services.SignatureRequestService;
import io.fireflysoftware.commons.ecm.interfaces.dtos.SignatureRequestDTO;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureStatus;
import io.fireflysoftware.commons.ecm.models.entities.SignatureRequest;
import io.fireflysoftware.commons.ecm.models.repositories.SignatureRequestRepository;
import org.fireflyframework.ecm.service.EcmPortProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * Implementation of the SignatureRequestService interface.
 */
@Service
@Transactional
public class SignatureRequestServiceImpl implements SignatureRequestService {

    @Autowired
    private SignatureRequestRepository repository;

    @Autowired
    private SignatureRequestMapper mapper;

    @Autowired
    private EcmPortProvider ecmPortProvider;

    @Override
    public Mono<SignatureRequestDTO> getById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PaginationResponse<SignatureRequestDTO>> filter(FilterRequest<SignatureRequestDTO> filterRequest) {
        return FilterUtils.createFilter(
                SignatureRequest.class,
                mapper::toDTO
        ).filter(filterRequest);
    }

    @Override
    public Mono<SignatureRequestDTO> update(SignatureRequestDTO signatureRequest) {
        if (signatureRequest.getId() == null) {
            return Mono.error(new IllegalArgumentException("ID cannot be null for update operation"));
        }

        return repository.findById(signatureRequest.getId())
                .switchIfEmpty(Mono.error(new RuntimeException("Signature request not found with ID: " + signatureRequest.getId())))
                .flatMap(existingEntity -> {
                    SignatureRequest entityToUpdate = mapper.toEntity(signatureRequest);
                    // Preserve created info
                    entityToUpdate.setCreatedAt(existingEntity.getCreatedAt());
                    entityToUpdate.setCreatedBy(existingEntity.getCreatedBy());
                    return repository.save(entityToUpdate);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<SignatureRequestDTO> create(SignatureRequestDTO signatureRequest) {
        // Ensure ID is null for create operation
        signatureRequest.setId(null);

        // Set default values if not provided
        if (signatureRequest.getRequestStatus() == null) {
            signatureRequest.setRequestStatus(SignatureStatus.PENDING);
        }
        if (signatureRequest.getNotificationSent() == null) {
            signatureRequest.setNotificationSent(false);
        }
        if (signatureRequest.getReminderSent() == null) {
            signatureRequest.setReminderSent(false);
        }

        SignatureRequest entity = mapper.toEntity(signatureRequest);
        return repository.save(entity)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Signature request not found with ID: " + id)))
                .flatMap(entity -> repository.delete(entity));
    }

    @Override
    public Flux<SignatureRequestDTO> getByDocumentSignatureId(UUID documentSignatureId) {
        return repository.findByDocumentSignatureId(documentSignatureId)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<SignatureRequestDTO> getByRequestReference(String requestReference) {
        return repository.findByRequestReference(requestReference)
                .map(mapper::toDTO);
    }

    @Override
    public Flux<SignatureRequestDTO> getByRequestStatus(SignatureStatus requestStatus) {
        return repository.findByRequestStatus(requestStatus)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Mono<SignatureRequestDTO> sendNotification(UUID id) {
        return Mono.error(new io.fireflysoftware.commons.ecm.core.signing.SigningIntegrationUnavailableException());
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Mono<SignatureRequestDTO> sendReminder(UUID id) {
        return Mono.error(new io.fireflysoftware.commons.ecm.core.signing.SigningIntegrationUnavailableException());
    }

    @Override
    public Flux<SignatureRequestDTO> processExpiredRequests() {
        LocalDateTime now = LocalDateTime.now();

        // Find all pending requests that have expired
        return repository.findByExpirationDateBeforeAndRequestStatus(now, SignatureStatus.PENDING)
                .flatMap(entity -> {
                    // Mark the request as expired
                    entity.setRequestStatus(SignatureStatus.EXPIRED);
                    return repository.save(entity);
                })
                .map(mapper::toDTO);
    }
}
