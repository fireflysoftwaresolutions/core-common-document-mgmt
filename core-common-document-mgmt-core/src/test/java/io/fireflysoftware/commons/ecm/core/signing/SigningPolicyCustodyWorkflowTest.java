package io.fireflysoftware.commons.ecm.core.signing;
import io.fireflysoftware.commons.ecm.core.signing.impl.SpringDataSigningPreparationStore;
import io.fireflysoftware.commons.ecm.models.entities.*;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningAccountBinding;
import io.fireflysoftware.commons.ecm.interfaces.enums.*;
import java.time.*;
import java.util.Map;
import org.fireflyframework.core.transaction.CleanupPolicy;
import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningPolicyCustodyWorkflowTest {
 @Test void fourSameResponseManagedEvaluationsBecomeTwoMappedRowsInTheActualOwnerWorkflow()throws Exception{
  var f=new TrustedSigningRuntimeTest.Fixture();var db=new org.fireflyframework.core.audit.SigningPolicyCustodyDriver();
  Instant now=Instant.now();Clock clock=Clock.systemUTC();
  f.transport.principal.set(SecurityPrincipal.builder().issuer(GeneratedConfigSigningTenantBindingResolverTest.ISSUER).subject("opaque:user:42")
    .tenantId(GeneratedConfigSigningTenantBindingResolverTest.PLATFORM).claims(Map.of("client_id","actual-user-client","exp",now.plusSeconds(60))).build());
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var response=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(f.transport.response.get());
  ((com.fasterxml.jackson.databind.node.ObjectNode)response.get("definition")).put("notAfterEpochMillis",now.plusSeconds(120).toEpochMilli());
  f.transport.atResponse.set(()->{try{Instant at=clock.instant();response.put("observedAtEpochMillis",at.toEpochMilli());response.put("validUntilEpochMillis",at.plusSeconds(5).toEpochMilli());f.transport.response.set(mapper.writeValueAsString(response));}catch(java.io.IOException error){throw new AssertionError(error);}});
  var calls=new java.util.concurrent.atomic.AtomicInteger();var evaluations=new java.util.ArrayList<org.fireflyframework.security.api.domain.PolicyEvaluation>();
  var engine=new org.fireflyframework.security.adapter.opa.OpaPolicyEvaluationAdapter(org.springframework.web.reactive.function.client.WebClient.builder().baseUrl("https://policy.example.test").exchangeFunction(request->{
   int ordinal=calls.incrementAndGet();return Mono.just(org.springframework.web.reactive.function.client.ClientResponse.create(org.springframework.http.HttpStatus.OK)
    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,"application/json").body("{\"result\":{\"allow\":true,\"policy_revision\":\"policy-actual-7\"},\"decision_id\":\"actual-evaluation-"+ordinal+": \u2028\ud83d\udd12\"}").build());
  }).build(),"core/signing/prepare");
  var resolver=new GeneratedConfigSigningTenantBindingResolver(()->Mono.just(f.transport.principal.get()),f.transport.manager,f.transport.configClient,GeneratedConfigSigningTenantBindingResolverTest.CONFIG,"configuration-current",GeneratedConfigSigningTenantBindingResolverTest.ISSUER,GeneratedConfigSigningTenantBindingResolverTest.PLATFORM,clock);
  var runtime=new TrustedSigningRuntime(()->Mono.just(f.transport.principal.get()),resolver,new CurrentSigningPreparationPolicy(()->Mono.just(f.transport.principal.get()),(principal,action,resource,facts)->engine.evaluate(principal,action,resource,facts).doOnNext(evaluations::add),clock),clock,java.util.UUID::randomUUID);
  var intent=f.intent;var account=f.account;byte[] bytes=SigningDraftWorkflowTest.PDF.clone();
  db.seed("account_binding",new SigningAccountBinding(account.bindingId(),intent.tenantId(),intent.providerId(),account.bindingVersion(),"DOCUSIGN",account.descriptor().accountId(),account.descriptor().endpoint(),true,now,0L));
  db.seed("signature_providers",SignatureProvider.builder().id(intent.providerId()).tenantId(intent.tenantId()).providerCode("DOCUSIGN").isActive(true).version(0L).build());
  db.seed("documents",Document.builder().id(intent.documentId()).tenantId(intent.tenantId()).version(0L).build());
  db.seed("document_versions",DocumentVersion.builder().id(intent.documentVersionId()).documentId(intent.documentId()).tenantId(intent.tenantId()).versionNumber(1).fileName("agreement.pdf").fileExtension("pdf").mimeType("application/pdf").fileSize((long)bytes.length).isEncrypted(false).storageType(StorageType.DATABASE).storagePath("controlled-version").build());
  db.seed("document_signatures",DocumentSignature.builder().id(intent.signatureId()).tenantId(intent.tenantId()).documentId(intent.documentId()).documentVersionId(intent.documentVersionId()).signatureProviderId(intent.providerId()).signerPartyId(intent.signerPartyId()).signerName(intent.signerName()).signerEmail(intent.signerEmail()).signatureType(SignatureType.BASIC).signatureFormat(SignatureFormat.PDF_VISIBLE).signatureStatus(SignatureStatus.PENDING).signaturePositionX(intent.x()).signaturePositionY(intent.y()).signaturePage(intent.page()).signatureWidth(intent.width()).signatureHeight(intent.height()).version(0L).build());
  var store=SpringDataSigningPreparationStore.policyCustody(db.resources(),db.client(),db.template(),db.manager(),new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),runtime,runtime,clock,System::nanoTime);
  var envelopes=mock(SignatureEnvelopePort.class);when(envelopes.accountBinding()).thenReturn(Mono.just(account.descriptor()));
  var content=mock(DocumentContentPort.class);when(content.getContent(intent.documentVersionId())).thenAnswer(call->{assertEquals(1,db.commits());assertEquals(1,db.rows("policy_custody").size());assertEquals("PREPARING",db.rows("operation").getFirst().get("state"));return Mono.just(bytes);});
  var authority=new SigningAuthorityAdapter(()->Mono.just(f.transport.principal.get()),runtime.decisions(account),runtime,clock);
  var result=runtime.scoped(new SigningDraftPreparation(authority,store,account,envelopes,content)).prepare(intent).onErrorMap(error->new AssertionError("Controlled policies="+calls.get()+";sdk="+f.transport.sdkCalls.get()+";commits="+db.commits()+";tables="+db.rows("policy_custody").size()+";statements="+db.statements(),error)).block(Duration.ofSeconds(3));
  assertNotNull(result);assertEquals("PREPARED",result.state());assertEquals(4,calls.get());assertEquals(4,f.transport.sdkCalls.get());assertEquals(2,db.commits());assertEquals(2,db.rows("policy_custody").size());
  for(int i=0;i<2;i++){var row=db.rows("policy_custody").get(i);assertEquals(i==0?"RESERVE":"PREPARED",row.get("phase"));assertEquals(evaluations.get(i*2).provenance().orElseThrow().decisionId(),row.get("authorization_decision_id"));assertEquals(evaluations.get(i*2+1).provenance().orElseThrow().decisionId(),row.get("admission_decision_id"));assertEquals(evaluations.get(i*2).provenance().orElseThrow().policyRevision(),row.get("authorization_revision"));}
  verify(content).getContent(intent.documentVersionId());verifyNoMoreInteractions(content);verify(envelopes,times(2)).accountBinding();verifyNoMoreInteractions(envelopes);
 }
}
