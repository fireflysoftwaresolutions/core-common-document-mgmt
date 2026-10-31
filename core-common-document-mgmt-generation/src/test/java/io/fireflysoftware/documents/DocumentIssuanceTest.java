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

import io.fireflysoftware.documents.render.DocumentRenderPort;
import io.fireflysoftware.documents.render.FreeMarkerDocumentRenderAdapter;
import io.fireflysoftware.documents.storage.DocumentStoragePort;
import io.fireflysoftware.documents.storage.SandboxDocumentStorageAdapter;
import io.fireflysoftware.documents.storage.StoredDocument;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIssuanceTest {

    private static final String[] BANNED = {
            "lorem", "Nombre Apellido", "[Client]", "[x]", "TODO", "FIXME", "STUB", "placeholder"
    };

    @Test
    void everyKitTemplateIsStoredAndReadBack() throws IOException {
        SandboxDocumentStorageAdapter storage = new SandboxDocumentStorageAdapter();
        DocumentPort port = port(storage);
        Path examples = examplesDirectory();
        Files.createDirectories(examples);

        for (DocumentTypeCode type : DocumentTypeCode.values()) {
            IssuedDocument issued = port.issue(SandboxEngagement.issue(type));
            IssuedDocument read = port.read(issued.id());

            assertThat(read.id()).isEqualTo(issued.id());
            assertThat(read.templateCode()).isEqualTo(type.code());
            assertThat(read.mediaType()).isEqualTo(FreeMarkerDocumentRenderAdapter.MEDIA_TYPE);
            assertThat(read.html()).isEqualTo(issued.html());
            assertThat(read.html()).contains(SandboxEngagement.PARTY_NAME);
            assertThat(read.html()).contains(SandboxEngagement.CONTRACT_ID.toString());
            assertThat(read.html()).contains(SandboxEngagement.PRODUCT_ID.toString());
            assertThat(read.html()).contains(SandboxEngagement.PRODUCT_NAME);
            assertThat(read.html()).contains(SandboxEngagement.CURRENCY);
            assertThat(read.html()).contains("firefly:template\" content=\"" + type.code() + "\"");
            assertThat(read.html()).contains("firefly:kind\" content=\"" + type.kitKind() + "\"");
            for (String banned : BANNED) {
                assertThat(read.html()).doesNotContain(banned);
            }

            Path sample = examples.resolve(type.code() + ".html");
            Files.writeString(sample, read.html());
            assertThat(Files.readString(sample)).isEqualTo(read.html());
            assertThat(read.html()).contains("Firefly documents: A4 print stylesheet");
        }
        assertThat(Files.readString(examples.resolve("contract.html")))
                .contains(SandboxEngagement.PARTY_NAME)
                .contains(SandboxEngagement.CONTRACT_ID.toString())
                .contains("250,000.00")
                .contains("Contract lines")
                .contains(SandboxEngagement.LINE_ID.toString())
                .contains(SandboxEngagement.PROTECTION_LINE_ID.toString())
                .contains("November 1, 2031")
                .contains("@font-face")
                .contains("min-height: 297mm")
                .doesNotContain("0.1 · Draft");

        assertThat(port.read(port.issue(SandboxEngagement.issue(DocumentTypeCode.LETTER)).id()).templateCode())
                .isEqualTo("letter");
    }

    @Test
    void missingTypeStoresNothing() {
        CountingStorage storage = new CountingStorage();
        DocumentPort port = port(storage);

        assertThatThrownBy(() -> port.issue(SandboxEngagement.issue("notice")))
                .isInstanceOf(UnknownDocumentTypeException.class)
                .hasMessageContaining("notice")
                .hasMessageContaining("Nothing was stored");
        assertThat(storage.stores.get()).isZero();
        assertThat(storage.rows).isEmpty();
    }

    @Test
    void languageMismatchStoresNothing() {
        CountingStorage storage = new CountingStorage();
        DocumentPort port = port(storage);
        IssueDocument spanish = new IssueDocument("letter", DocumentLanguage.ES, ProductFamilyCode.LENDING, true,
                new Party(SandboxEngagement.PARTY_ID, SandboxEngagement.GIVEN_NAME, SandboxEngagement.PARTY_NAME,
                        SandboxEngagement.PARTY_ROLE),
                new Agreement(SandboxEngagement.CONTRACT_ID, SandboxEngagement.STATUS),
                new Product(SandboxEngagement.PRODUCT_ID, SandboxEngagement.PRODUCT_NAME, ProductFamilyCode.LENDING),
                SandboxEngagement.CURRENCY, SandboxEngagement.ISSUED_ON, SandboxEngagement.EFFECTIVE_ON,
                SandboxEngagement.ENDS_ON, SandboxEngagement.AMOUNT, SandboxEngagement.issue("letter").lines(),
                null, null);

        assertThatThrownBy(() -> port.issue(spanish))
                .isInstanceOf(UnknownDocumentTypeException.class)
                .hasMessageContaining("Nothing was stored");
        assertThat(storage.stores.get()).isZero();
    }

    @Test
    void invalidCurrencyStoresNothing() {
        CountingStorage storage = new CountingStorage();
        DocumentPort port = port(storage);
        IssueDocument command = new IssueDocument("letter", DocumentLanguage.EN, ProductFamilyCode.LENDING, true,
                new Party(SandboxEngagement.PARTY_ID, SandboxEngagement.GIVEN_NAME, SandboxEngagement.PARTY_NAME,
                        SandboxEngagement.PARTY_ROLE),
                new Agreement(SandboxEngagement.CONTRACT_ID, SandboxEngagement.STATUS),
                new Product(SandboxEngagement.PRODUCT_ID, SandboxEngagement.PRODUCT_NAME, ProductFamilyCode.LENDING),
                "eu", SandboxEngagement.ISSUED_ON, SandboxEngagement.EFFECTIVE_ON, SandboxEngagement.ENDS_ON,
                SandboxEngagement.AMOUNT, SandboxEngagement.issue("letter").lines(), null, null);

        assertThatThrownBy(() -> port.issue(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("three-letter");
        assertThat(storage.stores.get()).isZero();
    }

    @Test
    void failedStoreLeavesNothingReadable() {
        PartialStorage storage = new PartialStorage();
        DocumentPort port = port(storage);

        DocumentStoreException failure = org.junit.jupiter.api.Assertions.assertThrows(DocumentStoreException.class,
                () -> port.issue(SandboxEngagement.issue(DocumentTypeCode.CONTRACT)));

        assertThat(failure.documentId()).isNotNull();
        assertThat(storage.read(failure.documentId())).isEmpty();
        assertThatThrownBy(() -> port.read(failure.documentId()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void renderFailureStoresNothing() {
        CountingStorage storage = new CountingStorage();
        DocumentRenderPort broken = (templateCode, kitKind, label, command) -> {
            throw new IllegalArgumentException("Template " + templateCode + " could not be rendered");
        };
        DocumentPort port = new DocumentIssuance(new SandboxDocumentTemplateCatalog(), broken, storage);

        assertThatThrownBy(() -> port.issue(SandboxEngagement.issue(DocumentTypeCode.CONTRACT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contract");
        assertThat(storage.stores.get()).isZero();
    }

    @Test
    void sandboxIsTheDefaultAndCoreStorageIsSelectable() {
        new ApplicationContextRunner()
                .withUserConfiguration(DocumentAdapterConfiguration.class)
                .run(context -> {
                    assertThat(context.getBean(DocumentStoragePort.class)).isInstanceOf(SandboxDocumentStorageAdapter.class);
                    assertThat(context.getBean(DocumentRenderPort.class)).isInstanceOf(FreeMarkerDocumentRenderAdapter.class);
                    assertThat(context.getBean(DocumentTemplateCatalog.class)).isInstanceOf(SandboxDocumentTemplateCatalog.class);
                    assertThat(context.getBean(DocumentTemplateCatalog.class).find("contract")).isNotNull();
                    assertThat(context.getBean(DocumentTemplateCatalog.class).find("sow")).isNull();
                    assertThat(context.getBean(DocumentTemplateCatalog.class).find("amendment").code()).isEqualTo("amendment");
                    assertThat(context.getBean(DocumentTemplateCatalog.class).find("renewal").code()).isEqualTo("renewal");
                    assertThat(context.getBean(DocumentTemplateCatalog.class).find("letter").code()).isEqualTo("letter");
                    assertThat(context.getBean(DocumentTemplateCatalog.class).find("welcome").code()).isEqualTo("welcome");
                });
    }

    @Test
    void coreTemplatesStayInsideThisModule() {
        new ApplicationContextRunner()
                .withUserConfiguration(DocumentAdapterConfiguration.class)
                .withPropertyValues("firefly.documents.templates=core")
                .run(context -> {
                    DocumentTemplateCatalog catalog = context.getBean(DocumentTemplateCatalog.class);
                    assertThat(catalog).isInstanceOf(SandboxDocumentTemplateCatalog.class);
                    assertThat(catalog.find("welcome").code()).isEqualTo("welcome");
                    assertThat(catalog.find("contract").code()).isEqualTo("contract");
                });
    }

    private static DocumentPort port(DocumentStoragePort storage) {
        return new DocumentIssuance(new SandboxDocumentTemplateCatalog(), new FreeMarkerDocumentRenderAdapter(), storage);
    }

    private static Path examplesDirectory() {
        Path here = Path.of("").toAbsolutePath();
        if ("core-common-document-mgmt-generation".equals(String.valueOf(here.getFileName()))) {
            return here.resolve("examples");
        }
        return here.resolve("core-common-document-mgmt-generation/examples");
    }

    private static final class CountingStorage implements DocumentStoragePort {
        private final AtomicInteger stores = new AtomicInteger();
        private final java.util.Map<UUID, StoredDocument> rows = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public StoredDocument store(StoredDocument draft) {
            stores.incrementAndGet();
            StoredDocument stored = draft.withId(draft.id() == null ? UUID.randomUUID() : draft.id());
            rows.put(stored.id(), stored);
            return stored;
        }

        @Override
        public Optional<StoredDocument> read(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void remove(UUID id) {
            rows.remove(id);
        }
    }

    /** Writes the row, then fails, so issuance must compensate. */
    private static final class PartialStorage implements DocumentStoragePort {
        private final java.util.Map<UUID, StoredDocument> rows = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public StoredDocument store(StoredDocument draft) {
            UUID id = UUID.randomUUID();
            rows.put(id, draft.withId(id));
            throw new DocumentStoreException("store failed", id, new IllegalStateException("store failed"));
        }

        @Override
        public Optional<StoredDocument> read(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void remove(UUID id) {
            rows.remove(id);
        }
    }
}
