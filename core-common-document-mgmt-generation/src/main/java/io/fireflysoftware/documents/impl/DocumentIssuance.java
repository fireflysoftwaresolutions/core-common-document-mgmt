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

package io.fireflysoftware.documents.impl;

import io.fireflysoftware.documents.*;
import io.fireflysoftware.commons.ecm.core.services.generation.*;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

import io.fireflysoftware.documents.render.DocumentRenderPort;
import io.fireflysoftware.documents.render.RenderedDocument;
import io.fireflysoftware.documents.storage.DocumentStoragePort;
import io.fireflysoftware.documents.storage.StoredDocument;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves a template from {@link DocumentTemplateCatalog}, renders it, and stores it
 * through {@link DocumentStoragePort}.
 */
public final class DocumentIssuance implements DocumentPort {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private final DocumentTemplateCatalog catalog;
    private final DocumentRenderPort renderer;
    private final DocumentStoragePort storage;
    private final DocumentCurrentSnapshotService snapshots;
    private final DocumentRenderProfiles profiles;
    private final DocumentGenerationEventSource events;
    private final DocumentGenerationCommitService commits;
    private final java.time.Clock clock;
    private final java.util.function.LongSupplier nanos;

    public DocumentIssuance(DocumentTemplateCatalog catalog, DocumentRenderPort renderer, DocumentStoragePort storage) {
        this.catalog = catalog;
        this.renderer = renderer;
        this.storage = storage;
        this.snapshots=null;this.profiles=null;this.events=null;this.commits=null;this.clock=null;this.nanos=null;
    }

    public DocumentIssuance(io.fireflysoftware.commons.ecm.core.services.generation.DocumentCurrentSnapshotService snapshots,
            DocumentRenderPort renderer,DocumentRenderProfiles profiles,DocumentGenerationEventSource events,
            io.fireflysoftware.commons.ecm.core.services.generation.DocumentGenerationCommitService commits,
            java.time.Clock clock,java.util.function.LongSupplier nanos) {
        this.catalog=null;this.storage=null;this.renderer=java.util.Objects.requireNonNull(renderer);
        this.snapshots=java.util.Objects.requireNonNull(snapshots);this.profiles=java.util.Objects.requireNonNull(profiles);
        this.events=java.util.Objects.requireNonNull(events);this.commits=java.util.Objects.requireNonNull(commits);
        this.clock=java.util.Objects.requireNonNull(clock);this.nanos=java.util.Objects.requireNonNull(nanos);
    }

    @Override public Mono<DocumentGenerationCommitService.LocalCommit> issue(GovernedIssueDocument command,R2dbcOperationBudget original) {
        return Mono.defer(()->{
            if(snapshots==null)throw new IllegalStateException("Governed issuance capability unavailable");
            java.util.Objects.requireNonNull(command);java.util.Objects.requireNonNull(original);original.remaining();
            var started=clock.instant();var envelope=original.intersect(R2dbcOperationBudget.observed(started,
                    started.plusSeconds(5),nanos.getAsLong(),java.time.Duration.ofSeconds(5),clock,nanos));
            return snapshots.observe(command.selection(),original).switchIfEmpty(Mono.error(unavailable())).flatMap(snapshot->{
                envelope.remaining();
                if(!command.selection().equals(snapshot.selection()))throw unavailable();snapshot.budget().remaining();
                var rendered=renderer.render(snapshot,command.factsJson(),profiles.require(snapshot));
                if(!snapshot.mediaType().equals(rendered.mediaType()))throw unavailable();
                byte[] bytes=rendered.html().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                envelope.remaining();snapshot.budget().remaining();
                return events.bind(command.documentId(),snapshot,bytes.clone(),original).switchIfEmpty(Mono.error(unavailable()))
                        .timeout(envelope.remaining()).flatMap(event->{
                    envelope.remaining();snapshot.budget().remaining();
                    var request=new DocumentGenerationStorage.Request(command.selection().product().tenantId(),command.documentId(),snapshot,event,bytes);
                    String digest=DocumentStorageIntent.digest(request);
                    if(!digest.equals(event.evidenceDigest()))throw unavailable();
                    return commits.store(request,original).switchIfEmpty(Mono.error(unavailable())).map(committed->{
                        envelope.remaining();snapshot.budget().remaining();
                        if(committed.local()==null||!request.resourceTenantId().equals(committed.local().resourceTenantId())
                                ||!request.documentId().equals(committed.local().documentId())||!event.eventId().equals(committed.eventId())
                                ||!digest.equals(committed.requestDigest())||!DocumentStorageIntent.contentDigest(bytes).equals(committed.local().contentDigest())
                                ||committed.providerState()!=DocumentGenerationCommitService.ProviderState.PENDING_PROVIDER)throw unavailable();
                        return committed;
                    });
                });
            }).timeout(envelope.remaining());
        });
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("Exact admitted audited governed issuance required");}

    @Override
    public IssuedDocument issue(IssueDocument command) {
        if(catalog==null||storage==null)throw new IllegalStateException("Legacy catalog/storage capability unavailable");
        validate(command);
        DocumentTemplate template = resolve(command);
        RenderedDocument rendered = renderer.render(template.code(), template.kitKind(), template.label(), command);
        if (rendered.html() == null || rendered.html().isBlank()) {
            throw new IllegalArgumentException("Template " + command.templateCode() + " rendered an empty document");
        }
        String name = template.label() + " for " + command.party().name();
        try {
            StoredDocument stored = storage.store(new StoredDocument(null, template.code(), rendered.mediaType(),
                    rendered.html(), name));
            return toIssued(stored);
        } catch (DocumentStoreException ex) {
            if (ex.documentId() != null) {
                storage.remove(ex.documentId());
            }
            throw ex;
        }
    }

    @Override
    public IssuedDocument read(UUID documentId) {
        if(storage==null)throw new IllegalStateException("Legacy catalog/storage capability unavailable");
        if (documentId == null) {
            throw new IllegalArgumentException("Document id is required");
        }
        return storage.read(documentId).map(DocumentIssuance::toIssued)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    private DocumentTemplate resolve(IssueDocument command) {
        DocumentTemplate template = catalog.find(command.templateCode());
        if (template == null || !template.active()) {
            throw unknown(command);
        }
        boolean sameLanguage = template.language() == command.language();
        boolean sameFamily = template.productFamily() == command.productFamily();
        boolean sameRegulated = template.regulated() == command.regulated();
        boolean sameTemplate = command.templateCode().equals(template.code());
        if (!sameLanguage || !sameFamily || !sameRegulated || !sameTemplate) {
            throw unknown(command);
        }
        return template;
    }

    private static UnknownDocumentTypeException unknown(IssueDocument command) {
        return new UnknownDocumentTypeException("No document template is catalogued for type '"
                + command.templateCode() + "' with language " + command.language().code()
                + ", product family " + command.productFamily().name()
                + ", and regulated " + command.regulated() + ". Nothing was stored.");
    }

    private static void validate(IssueDocument command) {
        if (command == null) {
            throw new IllegalArgumentException("Document request is required");
        }
        if (command.templateCode() == null || command.templateCode().isBlank()) {
            throw new IllegalArgumentException("Document type is required");
        }
        if (command.language() == null) {
            throw new IllegalArgumentException("Document language is required");
        }
        if (command.productFamily() == null) {
            throw new IllegalArgumentException("Product family is required");
        }
        if (command.party() == null || command.party().id() == null || command.party().name() == null
                || command.party().name().isBlank() || command.party().givenName() == null
                || command.party().givenName().isBlank() || command.party().role() == null
                || command.party().role().isBlank()) {
            throw new IllegalArgumentException("Party id, given name, name, and role are required");
        }
        if (command.contract() == null || command.contract().id() == null || command.contract().status() == null
                || command.contract().status().isBlank()) {
            throw new IllegalArgumentException("Agreement id and status are required");
        }
        if (command.product() == null || command.product().id() == null || command.product().name() == null
                || command.product().name().isBlank() || command.product().family() == null) {
            throw new IllegalArgumentException("Product id, name, and family are required");
        }
        if (command.product().family() != command.productFamily()) {
            throw new IllegalArgumentException("Product family does not match the document request");
        }
        if (command.currency() == null || !CURRENCY.matcher(command.currency()).matches()) {
            throw new IllegalArgumentException("Currency must be a three-letter code");
        }
        if (command.amount() == null || command.amount().signum() < 0) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("At least one contract line is required");
        }
        for (ContractLine line : command.lines()) {
            if (line == null || line.id() == null || line.productId() == null || line.role() == null
                    || line.role().isBlank() || line.quantity() == null) {
                throw new IllegalArgumentException("Each contract line needs an id, a product, a role, and a quantity");
            }
        }
        if (command.issuedOn() == null || command.effectiveOn() == null || command.endsOn() == null) {
            throw new IllegalArgumentException("Issued, effective, and end dates are required");
        }
        boolean linked = "amendment".equals(command.templateCode()) || "renewal".equals(command.templateCode());
        if (linked && command.predecessorId() == null) {
            throw new IllegalArgumentException("An amendment or a renewal names the predecessor contract");
        }
        if ("amendment".equals(command.templateCode())
                && (command.change() == null || command.change().isBlank())) {
            throw new IllegalArgumentException("An amendment states what changed");
        }
    }

    private static IssuedDocument toIssued(StoredDocument stored) {
        return new IssuedDocument(stored.id(), stored.templateCode(), stored.mediaType(), stored.html(), stored.name());
    }
}
