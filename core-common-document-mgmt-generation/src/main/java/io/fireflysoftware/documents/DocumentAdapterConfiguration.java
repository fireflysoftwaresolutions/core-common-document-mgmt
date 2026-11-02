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

import io.fireflysoftware.documents.impl.DocumentIssuance;

import io.fireflysoftware.commons.ecm.models.repositories.DocumentMetadataRepository;
import io.fireflysoftware.commons.ecm.models.repositories.DocumentRepository;
import io.fireflysoftware.documents.render.DocumentRenderPort;
import io.fireflysoftware.documents.render.FreeMarkerDocumentRenderAdapter;
import io.fireflysoftware.documents.storage.DocumentCoreStorageAdapter;
import io.fireflysoftware.documents.storage.DocumentStoragePort;
import io.fireflysoftware.documents.storage.SandboxDocumentStorageAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sandbox storage, the in-module template catalog, and FreeMarker are the defaults.
 * None of them opens a network connection.
 * {@code firefly.documents.storage=core} selects the document-core adapter.
 * {@code firefly.documents.templates=core} uses the five template codes held in this module.
 * {@code firefly.documents.render=freemarker} selects FreeMarker. A future engine replaces that bean.
 */
@Configuration
@ConditionalOnProperty(prefix="firefly.documents.governed", name="enabled", havingValue="false", matchIfMissing=true)
public class DocumentAdapterConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "firefly.documents", name = "render", havingValue = "freemarker", matchIfMissing = true)
    public DocumentRenderPort freeMarkerDocumentRenderPort() {
        return new FreeMarkerDocumentRenderAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.documents", name = "storage", havingValue = "sandbox", matchIfMissing = true)
    public DocumentStoragePort sandboxDocumentStoragePort() {
        return new SandboxDocumentStorageAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.documents", name = "storage", havingValue = "core")
    public DocumentStoragePort documentCoreStoragePort(DocumentRepository documents, DocumentMetadataRepository metadata) {
        return new DocumentCoreStorageAdapter(documents, metadata);
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.documents", name = "templates", havingValue = "sandbox", matchIfMissing = true)
    public DocumentTemplateCatalog sandboxDocumentTemplateCatalog() {
        return new SandboxDocumentTemplateCatalog();
    }

    @Bean
    @ConditionalOnProperty(prefix = "firefly.documents", name = "templates", havingValue = "core")
    public DocumentTemplateCatalog coreDocumentTemplateCatalog() {
        return new SandboxDocumentTemplateCatalog();
    }

    @Bean
    public DocumentPort documentPort(DocumentTemplateCatalog catalog, DocumentRenderPort renderer, DocumentStoragePort storage) {
        return new DocumentIssuance(catalog, renderer, storage);
    }
}
