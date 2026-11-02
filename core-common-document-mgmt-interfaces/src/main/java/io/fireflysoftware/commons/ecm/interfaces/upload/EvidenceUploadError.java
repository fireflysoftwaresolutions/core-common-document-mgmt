/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.interfaces.upload;
import java.util.UUID;

/** Operation-owned recovery envelope; never includes credentials, content, actor or guessed document identity. */
public record EvidenceUploadError(UUID actionId,String code,Outcome outcome,String recovery) {
    public enum Outcome { REFUSED,CONFLICT,OUTCOME_UNKNOWN,COMMITTED_RESPONSE_UNAVAILABLE,OBSERVATION_UNAVAILABLE }
}
