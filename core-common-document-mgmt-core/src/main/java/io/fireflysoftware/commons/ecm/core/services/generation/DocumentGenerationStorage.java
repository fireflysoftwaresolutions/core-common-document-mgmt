/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import java.util.UUID;
import org.fireflyframework.core.transaction.*;
import org.fireflyframework.kernel.audit.AuditEvent;
import reactor.core.publisher.Mono;

/**
 * A future adapter must verify current admission and append this exact audit event in the
 * same owning root as mapped document, immutable metadata and issuance state inserts.
 * No external provider effect can be made atomic by this SQL contract.
 */
public interface DocumentGenerationStorage {
    record Request(UUID resourceTenantId,UUID documentId,DocumentCurrentSnapshot snapshot,
            AuditEvent event,byte[] renderedContent) {
        public Request { renderedContent=renderedContent==null?null:renderedContent.clone(); }
        @Override public byte[] renderedContent(){return renderedContent==null?null:renderedContent.clone();}
        @Override public String toString(){return "DocumentGenerationStorage.Request";}
    }
    record StoredDocument(UUID resourceTenantId,UUID documentId,String contentDigest) { }
    Mono<StoredDocument> store(Request request,RootTransactionScope owningScope,R2dbcOperationBudget original);
}
