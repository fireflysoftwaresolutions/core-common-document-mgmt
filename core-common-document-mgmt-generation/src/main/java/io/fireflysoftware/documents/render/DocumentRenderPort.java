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

package io.fireflysoftware.documents.render;

import io.fireflysoftware.documents.IssueDocument;

/** Renders a catalog template. A future engine implements this same port in place of FreeMarker. */
public interface DocumentRenderPort {

    RenderedDocument render(String templateCode, String kitKind, String label, IssueDocument command);
    default RenderedDocument render(io.fireflysoftware.commons.ecm.core.services.generation.DocumentCurrentSnapshot snapshot,
            String factsJson,GovernedRenderProfile profile) {
        throw new IllegalStateException("Governed rendering capability unavailable");
    }
}
