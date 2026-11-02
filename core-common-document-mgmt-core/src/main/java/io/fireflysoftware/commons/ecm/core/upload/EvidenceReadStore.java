/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import reactor.core.publisher.Mono;

public interface EvidenceReadStore {
    Mono<EvidenceContentRead> read(EvidenceReadIdentity identity);
}
