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

import io.fireflysoftware.commons.ecm.core.mappers.SignatureRequestMapper;
import io.fireflysoftware.commons.ecm.models.repositories.SignatureRequestRepository;
import org.fireflyframework.ecm.port.esignature.SignatureRequestPort;
import org.fireflyframework.ecm.service.EcmPortProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignatureRequestServiceImplTest {

    @Mock
    private SignatureRequestRepository repository;

    @Mock
    private SignatureRequestMapper mapper;

    @Mock
    private EcmPortProvider ecmPortProvider;

    @Mock
    private SignatureRequestPort signatureRequestPort;

    @InjectMocks
    private SignatureRequestServiceImpl service;

    @Test
    void sendNotification_WithoutTrustedDispatch_RefusesBeforeProviderOrLocalUpdates() {
        UUID id = UUID.randomUUID();

        StepVerifier.create(service.sendNotification(id))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(
                            io.fireflysoftware.commons.ecm.core.signing.SigningIntegrationUnavailableException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals(503,
                            ((org.springframework.web.server.ResponseStatusException) error).getStatusCode().value());
                })
                .verify();

        verifyNoInteractions(repository, mapper, ecmPortProvider, signatureRequestPort);
    }
}
