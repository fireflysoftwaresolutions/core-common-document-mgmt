/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import org.fireflyframework.ecm.port.document.DocumentContentPort;
import org.fireflyframework.ecm.port.esignature.SignatureEnvelopePort;
import reactor.core.publisher.Mono;

/** Explicit owner composition from registered server scope; does not install schema or activate HTTP routes. */
public interface SigningPreparationRuntimeFactory {
    Mono<SigningDraftWorkflow> create(SigningAccountScope serverAccount,DocumentContentPort content,SignatureEnvelopePort envelopes);
    default Mono<SigningDraftWorkflow> createPolicyCustody(SigningAccountScope serverAccount,DocumentContentPort content,SignatureEnvelopePort envelopes) {
        return Mono.error(new SigningIntegrationUnavailableException());
    }
}
