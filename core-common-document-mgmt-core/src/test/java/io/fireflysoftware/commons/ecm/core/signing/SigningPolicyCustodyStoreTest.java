package io.fireflysoftware.commons.ecm.core.signing;
import io.fireflysoftware.commons.ecm.core.signing.impl.SpringDataSigningPreparationStore;
import java.time.Duration;
import org.fireflyframework.core.audit.*;
import org.fireflyframework.core.transaction.CleanupPolicy;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SigningPolicyCustodyStoreTest {
    final SpringDataSigningPreparationStoreTest metadata=new SpringDataSigningPreparationStoreTest();
    final org.fireflyframework.core.audit.SigningPolicyCustodyDriver db=new org.fireflyframework.core.audit.SigningPolicyCustodyDriver();
    final SigningPolicyCustodyFixture values=new SigningPolicyCustodyFixture(db.clock());
    final SigningAuditAdmission admission=mock(SigningAuditAdmission.class,withSettings().extraInterfaces(SigningPolicyTracePort.class,AuditAppendAuthority.class).defaultAnswer(CALLS_REAL_METHODS));
    final SigningPolicyTracePort traces=(SigningPolicyTracePort)admission;
    SigningPolicyCustodyStoreTest(){
        metadata.setup();
        db.seed("account_binding",metadata.accounts.findByIdAndTenantIdAndBindingVersion(values.account().bindingId(),values.intent().tenantId(),7).block());
        db.seed("signature_providers",metadata.providers.findByIdAndTenantId(values.intent().providerId(),values.intent().tenantId()).block());
        db.seed("documents",metadata.documents.findByIdAndTenantId(values.intent().documentId(),values.intent().tenantId()).block());
        db.seed("document_versions",metadata.version);
        db.seed("document_signatures",metadata.signatures.findByIdAndTenantId(values.intent().signatureId(),values.intent().tenantId()).block());
        when(admission.requireCurrent(any(),any(),any())).thenAnswer(call->Mono.just(values.permit()));
        when(traces.requirePolicyCustody()).thenReturn(Mono.empty());
        when(traces.currentTrace(any())).thenAnswer(call->{var t=values.trace();return Mono.just(db.rows("operation").isEmpty()?t:new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.PREPARED,t.authorization(),t.admission()));});
        doAnswer(call->{java.util.function.Supplier<?> work=call.getArgument(3);return work.get();}).when(traces).withinExpectedAppendV3(any(),any(),any(),any());
        when(((AuditAppendAuthority)admission).requireCurrent(any())).thenAnswer(call->{org.fireflyframework.kernel.audit.AuditEvent e=call.getArgument(0);return Mono.just(new AuditAppendAuthority.Permit(e.stream(),e.actor(),values.authority().notAfter()));});
    }
    SpringDataSigningPreparationStore store(){return SpringDataSigningPreparationStore.policyCustody(db.resources(),db.client(),db.template(),db.manager(),new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),(AuditAppendAuthority)admission,admission,db.clock(),System::nanoTime);}
    SigningPreparationStore.Ticket begin(){return store().begin(values.intent(),values.account(),values.authority()).block(Duration.ofSeconds(2));}
    @Test void bothCommittedMappedPhasesRetainOriginalTraceAndUnchangedOperationProjection(){var ticket=begin();assertNotNull(ticket);assertEquals(1,db.rows("policy_custody").size());assertEquals(3,db.rows("operation").getFirst().get("evidence_protocol"));assertNotNull(db.rows("operation").getFirst().get("reservation_trace_digest"));var result=store().complete(ticket,metadata.snapshot(),values.authority()).block(Duration.ofSeconds(2));assertEquals("PREPARED",result.state());assertEquals(2,db.rows("policy_custody").size());assertEquals(2,db.commits());assertEquals(0,db.rollbacks());assertNotNull(db.rows("operation").getFirst().get("prepared_trace_digest"));assertEquals(30,io.fireflysoftware.commons.ecm.models.entities.signing.SigningOperation.class.getRecordComponents().length);for(var row:db.rows("policy_custody"))assertEquals(values.trace().authorization().decisionId(),row.get("authorization_decision_id"));assertTrue(db.statements().stream().anyMatch(sql->sql.startsWith("UPDATE")&&sql.contains("evidence_protocol")&&sql.contains("reservation_trace_digest")));}
    @Test void missingPhysicalProtocolCannotSilentlyResumeAsV3(){var ticket=begin();db.rows("operation").getFirst().put("evidence_protocol",null);int before=db.writes();assertThrows(RuntimeException.class,()->store().complete(ticket,metadata.snapshot(),values.authority()).block());assertEquals(before,db.writes());assertEquals(1,db.commits());assertEquals(1,db.rows("policy_custody").size());assertEquals("PREPARING",db.rows("operation").getFirst().get("state"));}
    @Test void zeroAssignedOperationInsertRollsBackAuditCustodyAndEnvelope(){db.countAt(6,0);assertThrows(RuntimeException.class,this::begin);assertEquals(0,db.commits());assertEquals(1,db.rollbacks());assertTrue(db.rows("policy_custody").isEmpty());assertTrue(db.rows("envelope").isEmpty());assertTrue(db.rows("audit_event").isEmpty());}
    @Test void mixedAuthorityAdaptersCannotConstructV3Store(){var other=mock(AuditAppendAuthority.class);assertThrows(IllegalArgumentException.class,()->SpringDataSigningPreparationStore.policyCustody(db.resources(),db.client(),db.template(),db.manager(),new CleanupPolicy(Duration.ofSeconds(1),Duration.ofSeconds(1)),other,admission,db.clock(),System::nanoTime));assertEquals(0,db.creates());}
    @Test void preparedTicketCannotAppendFreshAdmissionAsOriginalCustodyAgain(){var ticket=begin();assertNotNull(store().complete(ticket,metadata.snapshot(),values.authority()).block());int writes=db.writes();assertThrows(RuntimeException.class,()->store().complete(ticket,metadata.snapshot(),values.authority()).block());assertEquals(writes,db.writes());assertEquals(2,db.rows("policy_custody").size());assertEquals(2,db.commits());}
    @Test void selfConsistentChangedStoredTraceStillMustMatchOriginalCanonicalReservation(){var ticket=begin();var old=values.trace();var changed=new SigningPolicyTraceV1(old.phase(),new org.fireflyframework.security.api.domain.PolicyEvaluationProvenance(old.authorization().policyRevision(),"changed-history"),old.admission());var row=db.rows("policy_custody").getFirst();row.put("authorization_decision_id","changed-history");row.put("trace_digest",changed.digest());db.rows("operation").getFirst().put("reservation_trace_digest",changed.digest());int writes=db.writes();assertThrows(RuntimeException.class,()->store().complete(ticket,metadata.snapshot(),values.authority()).block());assertEquals(writes,db.writes());assertEquals(1,db.rows("audit_event").size());assertEquals(1,db.commits());assertEquals("PREPARING",db.rows("operation").getFirst().get("state"));}

}
