/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.documents;

import io.fireflysoftware.commons.ecm.core.services.generation.DocumentCurrentSnapshotService;
import io.fireflysoftware.commons.ecm.core.services.generation.impl.SpringDataDocumentGenerationCommitService;
import io.fireflysoftware.documents.impl.DocumentIssuance;
import io.fireflysoftware.documents.render.FreeMarkerDocumentRenderAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly opt-in and requires real owner delegation, event authority and audited native storage.
 * This configuration never imports the legacy sandbox catalog/storage configuration.
 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="firefly.documents.governed",name="enabled",havingValue="true")
public class GovernedDocumentConfiguration {
    @Bean("governedDocumentPort")
    public DocumentPort governedDocumentPort(DocumentCurrentSnapshotService snapshots,DocumentRenderProfiles profiles,
            DocumentGenerationEventSource events,SpringDataDocumentGenerationCommitService commits,
            @Qualifier("documentGenerationClock") java.time.Clock clock,
            @Qualifier("documentGenerationNanos") java.util.function.LongSupplier nanos) {
        return new DocumentIssuance(snapshots,new FreeMarkerDocumentRenderAdapter(),profiles,events,commits,clock,nanos);
    }
}
