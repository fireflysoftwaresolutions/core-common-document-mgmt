/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import java.util.*;

public final class EvidenceUploadFailure extends BusinessException {
    public enum Outcome { REFUSED, OUTCOME_UNKNOWN, COMMITTED_RESPONSE_UNAVAILABLE }
    private final Outcome outcome;
    private final UUID action;
    public EvidenceUploadFailure(UUID action,Outcome outcome){super(HttpStatus.SERVICE_UNAVAILABLE,"DOCUMENT_EVIDENCE_"+outcome.name(),"Document evidence outcome unavailable",Map.of("actionId",Objects.requireNonNull(action).toString(),"outcome",outcome.name(),"recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));this.outcome=java.util.Objects.requireNonNull(outcome);this.action=action;}
    public Outcome outcome(){return outcome;}
    public UUID actionId(){return action;}
}
