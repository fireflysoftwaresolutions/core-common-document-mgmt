/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.documents;

import java.util.UUID;

/**
 * Port a journey or a domain calls to issue a communication and read it back.
 * FreeMarker is the current renderer behind {@link io.fireflysoftware.documents.render.DocumentRenderPort}.
 * A later engine replaces that adapter without changing this port.
 */
public interface DocumentPort {

    IssuedDocument issue(IssueDocument command);

    IssuedDocument read(UUID documentId);
    default reactor.core.publisher.Mono<io.fireflysoftware.commons.ecm.core.services.generation.DocumentGenerationCommitService.LocalCommit> issue(
            GovernedIssueDocument command,org.fireflyframework.core.transaction.R2dbcOperationBudget original) {
        return reactor.core.publisher.Mono.error(new IllegalStateException("Governed issuance capability unavailable"));
    }
}
