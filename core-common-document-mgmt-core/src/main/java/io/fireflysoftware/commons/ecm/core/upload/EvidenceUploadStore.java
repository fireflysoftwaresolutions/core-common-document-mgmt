/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import io.fireflysoftware.commons.ecm.interfaces.upload.*;
import reactor.core.publisher.Mono;

public interface EvidenceUploadStore {
    Mono<EvidenceUploadReceipt> store(EvidenceUploadRequest request);
}
