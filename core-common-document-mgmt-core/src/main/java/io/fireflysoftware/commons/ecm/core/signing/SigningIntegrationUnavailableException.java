/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** No trusted dispatch capability has adopted the legacy signing association. */
public final class SigningIntegrationUnavailableException extends ResponseStatusException {
    private static final long serialVersionUID = 1L;
    public SigningIntegrationUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Trusted signing dispatch bridge unavailable");
    }
}
