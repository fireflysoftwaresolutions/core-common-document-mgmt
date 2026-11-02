package io.fireflysoftware.commons.ecm.web.controllers;
import io.fireflysoftware.commons.ecm.core.signing.SigningIntegrationUnavailableException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.fireflyframework.web.error.handler.GlobalExceptionHandler;
import org.fireflyframework.web.error.config.ErrorHandlingProperties;
import org.fireflyframework.web.error.converter.ExceptionConverterService;
import org.fireflyframework.web.error.models.ErrorResponse;
import org.fireflyframework.web.error.service.ErrorResponseNegotiator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SigningUnavailableHttpTest {
 @Test void actualSharedAdvicePreserves503AndSafeBodyWithoutARealServer()throws Exception{
  var converter=mock(ExceptionConverterService.class);when(converter.convertException(any())).thenThrow(new IllegalStateException("controlled metadata converter unavailable"));
  var negotiation=mock(ErrorResponseNegotiator.class);when(negotiation.determineContentType(any())).thenReturn(MediaType.APPLICATION_JSON);
  var json=JsonMapper.builder().addModule(new JavaTimeModule()).build();
  when(negotiation.negotiate(any(),any())).thenAnswer(call->json.writeValueAsBytes(call.<ErrorResponse>getArgument(1)));
  var handler=new GlobalExceptionHandler(converter,Optional.empty(),new ErrorHandlingProperties(),Optional.empty(),Optional.empty(),new MockEnvironment(),json,negotiation,Optional.empty());
  var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/signature-requests/controlled/send").build());
  handler.handle(exchange,new SigningIntegrationUnavailableException()).block(Duration.ofSeconds(1));
  assertEquals(503,exchange.getResponse().getStatusCode().value());
  var body=json.readTree(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(1)));assertEquals(503,body.get("status").asInt());
  assertEquals("Trusted signing dispatch bridge unavailable",body.get("message").asText());assertEquals("HTTP_STATUS_ERROR",body.get("code").asText());
 }
 @Test void bothExistingProducerOperationsDeclareUnavailableWithoutAddingRoutes()throws Exception{
  for(String method:new String[]{"sendSignatureRequest","sendSignatureRequestReminder"}){
   var declared=SignatureRequestController.class.getMethod(method,UUID.class).getAnnotation(ApiResponses.class);
   assertTrue(java.util.Arrays.stream(declared.value()).anyMatch(response->response.responseCode().equals("503")));
  }
 }
}
