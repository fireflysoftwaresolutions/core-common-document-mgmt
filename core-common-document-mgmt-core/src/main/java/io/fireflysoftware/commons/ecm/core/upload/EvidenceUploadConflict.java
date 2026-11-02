/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import java.util.*;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;

public final class EvidenceUploadConflict extends BusinessException {
    private final UUID action;
    public EvidenceUploadConflict(UUID action){super(HttpStatus.CONFLICT,"DOCUMENT_EVIDENCE_ACTION_CONFLICT","The original action is bound to different evidence");this.action=Objects.requireNonNull(action);}
    public UUID actionId(){return action;}
}
