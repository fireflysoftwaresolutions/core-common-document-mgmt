package io.fireflysoftware.commons.ecm.core.signing;

import io.fireflysoftware.commons.ecm.core.services.impl.DocumentSignatureServiceImpl;
import io.fireflysoftware.commons.ecm.core.services.impl.SignatureRequestServiceImpl;
import io.fireflysoftware.commons.ecm.core.mappers.DocumentSignatureMapper;
import io.fireflysoftware.commons.ecm.core.mappers.SignatureRequestMapper;
import io.fireflysoftware.commons.ecm.core.mappers.EcmDomainMapper;
import io.fireflysoftware.commons.ecm.core.config.EcmIntegrationProperties;
import io.fireflysoftware.commons.ecm.core.validation.EcmParameterValidator;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentSignatureDTO;
import io.fireflysoftware.commons.ecm.interfaces.dtos.SignatureRequestDTO;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureStatus;
import io.fireflysoftware.commons.ecm.models.entities.DocumentSignature;
import io.fireflysoftware.commons.ecm.models.entities.SignatureRequest;
import io.fireflysoftware.commons.ecm.models.repositories.DocumentSignatureRepository;
import io.fireflysoftware.commons.ecm.models.repositories.SignatureRequestRepository;
import org.fireflyframework.ecm.service.EcmPortProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacySigningUnavailableTest {
 private final UUID id=new UUID(1,1);
 private final DocumentSignatureServiceImpl signatures=new DocumentSignatureServiceImpl();
 private final SignatureRequestServiceImpl requests=new SignatureRequestServiceImpl();
 private final DocumentSignatureRepository signatureRows=mock(DocumentSignatureRepository.class);
 private final SignatureRequestRepository requestRows=mock(SignatureRequestRepository.class);
 private final EcmPortProvider ports=mock(EcmPortProvider.class);
 @BeforeEach void setup() {
  var row=DocumentSignature.builder().id(id).documentId(new UUID(1,2)).tenantId("tenant").signatureStatus(SignatureStatus.PENDING).build();
  var sm=mock(DocumentSignatureMapper.class);var rm=mock(SignatureRequestMapper.class);var validator=mock(EcmParameterValidator.class);
  ReflectionTestUtils.setField(signatures,"repository",signatureRows);ReflectionTestUtils.setField(signatures,"mapper",sm);
  ReflectionTestUtils.setField(signatures,"ecmPortProvider",ports);ReflectionTestUtils.setField(signatures,"ecmParameterValidator",validator);
  ReflectionTestUtils.setField(signatures,"ecmDomainMapper",new EcmDomainMapper(new EcmIntegrationProperties()));
  ReflectionTestUtils.setField(requests,"repository",requestRows);ReflectionTestUtils.setField(requests,"mapper",rm);ReflectionTestUtils.setField(requests,"ecmPortProvider",ports);
  when(validator.validateDocumentSignatureEcmParameters(any())).thenReturn(List.of());when(sm.toEntity(any())).thenReturn(row);
  when(signatureRows.save(any())).thenReturn(Mono.just(row));when(signatureRows.findById(id)).thenReturn(Mono.just(row));
  when(sm.toDTO(any())).thenReturn(DocumentSignatureDTO.builder().id(id).documentId(row.getDocumentId()).tenantId("tenant").build());
  var request=SignatureRequest.builder().id(id).documentSignatureId(id).requestReference("legacy-unadopted").tenantId("tenant").build();
  when(requestRows.findById(id)).thenReturn(Mono.just(request));when(requestRows.save(any())).thenReturn(Mono.just(request));
  when(rm.toDTO(any())).thenReturn(SignatureRequestDTO.builder().id(id).documentSignatureId(id).tenantId("tenant").build());
  when(ports.getSignatureRequestPort()).thenReturn(Optional.empty());
 }
 private void unavailable(Mono<?> operation) {
  var failure=assertThrows(ResponseStatusException.class,()->operation.block(Duration.ofSeconds(1)));
  assertEquals(503,failure.getStatusCode().value());
  verifyNoInteractions(signatureRows,requestRows,ports);
 }
 @Test void initiationCannotCreateLocalSuccessWithoutTrustedDispatch() {
  unavailable(signatures.initiateSigningProcess(DocumentSignatureDTO.builder().documentId(id).build()));
 }
 @Test void unadoptedCancellationCannotMutateRowsOrPretendProviderDeleteIsVoid() { unavailable(signatures.cancelSignature(id)); }
 @Test void unadoptedNotificationCannotSetASentFlag() { unavailable(requests.sendNotification(id)); }
 @Test void unadoptedReminderCannotSetASentFlag() { unavailable(requests.sendReminder(id)); }
}
