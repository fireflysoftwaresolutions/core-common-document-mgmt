/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import reactor.core.publisher.Mono;
@FunctionalInterface
public interface SigningDraftWorkflow{
 Mono<PreparedSigningDraft> prepare(SigningDraftIntent intent);
 default Mono<Void> dispatch(PreparedSigningDraft prepared){return Mono.error(new SigningIntegrationUnavailableException());}
}
