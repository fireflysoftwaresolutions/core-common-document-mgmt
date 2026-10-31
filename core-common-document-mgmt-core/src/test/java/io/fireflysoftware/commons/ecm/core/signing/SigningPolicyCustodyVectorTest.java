package io.fireflysoftware.commons.ecm.core.signing;
import io.fireflysoftware.commons.ecm.models.entities.signing.SigningPolicyCustody;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.fireflyframework.security.api.domain.PolicyEvaluationProvenance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SigningPolicyCustodyVectorTest {
 @Test void independentPythonFramesBindTheActualMappedConstructorAndCoreTrace()throws Exception{
  var file=Path.of(System.getProperty("core.signing.source")).resolve("core-common-document-mgmt-core/src/test/resources/signing-policy-vectors-v1.json");
  var vectors=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(file)).get("vectors");assertEquals(38,vectors.size());
  for(var v:vectors){char[] units=new char[v.get("authorizationIdUtf16").size()];for(int i=0;i<units.length;i++)units[i]=(char)v.get("authorizationIdUtf16").get(i).asInt();String id=new String(units);
   java.util.function.Supplier<SigningPolicyCustody> row=()->new SigningPolicyCustody(new UUID(1,1),"opaque tenant",new UUID(2,2),new UUID(3,3),new UUID(4,4),v.get("phase").asText(),3,v.get("authorizationRevision").asText(),id,v.get("admissionRevision").asText(),v.get("admissionId").asText(),"a".repeat(64),v.get("traceSha256").asText());
   if(v.get("accepted").asBoolean()){
    var value=assertDoesNotThrow(row::get,v.get("name").asText());var trace=new SigningPolicyTraceV1(SigningPolicyTraceV1.Phase.valueOf(value.phase()),new PolicyEvaluationProvenance(value.authorizationRevision(),value.authorizationDecisionId()),new PolicyEvaluationProvenance(value.admissionRevision(),value.admissionDecisionId()));
    assertArrayEquals(Base64.getDecoder().decode(v.get("traceBase64").asText()),trace.canonicalBytes());assertEquals(value.traceDigest(),trace.digest());assertEquals(id,value.authorizationDecisionId());
   }else assertThrows(IllegalArgumentException.class,row::get,v.get("name").asText());
  }
 }
 @Test void sqlWhitespaceProfileMatchesActualManagedJdkAcrossAllCodePoints()throws Exception{
  var path=Path.of(System.getProperty("core.signing.source")).resolve("core-common-document-mgmt-models/src/main/resources/db/optional/document-signing/V9__Signing_Policy_Custody.sql");
  var matcher=Pattern.compile("codepoint NOT IN\\(([^)]+)\\)").matcher(Files.readString(path));assertTrue(matcher.find());var configured=new HashSet<Integer>();for(String item:matcher.group(1).split(","))configured.add(Integer.valueOf(item));
  for(int cp=0;cp<=Character.MAX_CODE_POINT;cp++)assertEquals(Character.isWhitespace(cp)&&!Character.isISOControl(cp),configured.contains(cp),"Code point "+cp);
 }
 @Test void genericJsonNeverPublishesOriginalEngineIdentifiers()throws Exception{var row=SigningPolicyCustodyValueTest.row("PRIVATE_ENGINE_DECISION");String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(row);assertFalse(json.contains("PRIVATE_ENGINE_DECISION"));assertFalse(json.contains("authorizationDecisionId"));assertFalse(row.toString().contains("PRIVATE_ENGINE_DECISION"));}
}
