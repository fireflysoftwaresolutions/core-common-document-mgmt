/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;

public final class EvidenceReadFailure extends BusinessException {
    private final UUID action;
    public EvidenceReadFailure(UUID action){super(HttpStatus.SERVICE_UNAVAILABLE,"DOCUMENT_EVIDENCE_OBSERVATION_UNAVAILABLE","Current document evidence observation unavailable",Map.of("actionId",Objects.requireNonNull(action).toString(),"outcome","OBSERVATION_UNAVAILABLE","recovery","SAME_ACTION_OBSERVE_OR_EXACT_REPLAY"));this.action=action;}
    public UUID actionId(){return action;}
}
