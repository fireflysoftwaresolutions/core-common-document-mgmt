/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Objects;
import org.fireflyframework.security.api.domain.PolicyEvaluation;
import org.fireflyframework.security.api.domain.PolicyEvaluationProvenance;

/** Immutable observed metadata; neither authority nor a durable custody receipt. */
public record SigningPolicyTraceV1(Phase phase, PolicyEvaluationProvenance authorization,
        PolicyEvaluationProvenance admission) {
    public enum Phase { RESERVE, PREPARED }
    public SigningPolicyTraceV1 {
        Objects.requireNonNull(phase);validate(authorization);validate(admission);
    }
    public static SigningPolicyTraceV1 checked(Phase phase,PolicyEvaluation authorization,PolicyEvaluation admission) {
        return new SigningPolicyTraceV1(phase,provenance(authorization),provenance(admission));
    }
    public byte[] canonicalBytes() {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            text(out,"CORE_SIGNING_POLICY_TRACE");out.writeInt(1);text(out,phase.name());
            evaluation(out,phase==Phase.RESERVE?"INITIAL_AUTHORIZATION":"AFTER_STORAGE_REVALIDATION",authorization);
            evaluation(out,phase==Phase.RESERVE?"RESERVE_ADMISSION":"PREPARED_ADMISSION",admission);
            out.flush();return bytes.toByteArray();
        } catch(IOException error) { throw new IllegalStateException("Canonical signing policy evidence unavailable"); }
    }
    public String digest() { return SigningDocumentSnapshot.sha256(canonicalBytes()); }
    private static void evaluation(DataOutputStream out,String role,PolicyEvaluationProvenance value)throws IOException {
        text(out,role);text(out,"PERMIT");out.writeInt(0);text(out,value.policyRevision());text(out,value.decisionId());
    }
    private static PolicyEvaluationProvenance provenance(PolicyEvaluation value) {
        if(value==null||!value.decision().granted()||!value.decision().obligations().isEmpty()||value.provenance().isEmpty())throw invalid();
        return value.provenance().orElseThrow();
    }
    private static void validate(PolicyEvaluationProvenance value) {
        if(value==null||!value.policyRevision().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                ||value.decisionId()==null||value.decisionId().isBlank()||value.decisionId().length()>256
                ||value.decisionId().codePoints().anyMatch(Character::isISOControl))throw invalid();
        utf8(value.decisionId());
    }
    private static byte[] utf8(String value) {
        try {
            var encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            byte[] result=new byte[encoded.remaining()];encoded.get(result);return result;
        } catch(CharacterCodingException error) { throw invalid(); }
    }
    private static void text(DataOutputStream out,String value)throws IOException {
        byte[] encoded=utf8(value);out.writeInt(encoded.length);out.write(encoded);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Exact checked signing policy trace required"); }
    @Override @com.fasterxml.jackson.annotation.JsonValue public String toString() { return "SigningPolicyTraceV1[redacted]"; }
}
