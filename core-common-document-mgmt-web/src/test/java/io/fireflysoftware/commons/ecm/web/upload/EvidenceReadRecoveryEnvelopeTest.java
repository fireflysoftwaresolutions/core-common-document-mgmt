/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;
import io.fireflysoftware.commons.ecm.core.upload.*;
import io.fireflysoftware.commons.ecm.web.controllers.EvidenceReadController;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceReadAdmissionImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.mockito.Mockito.*;

class EvidenceReadRecoveryEnvelopeTest {
    @Test void providerResolutionFailureIsTypedUnavailableWithoutAnyReadDispatch(){
        var fixture=new AuthenticatedEvidenceReadAdmissionTest();
        for(boolean authorityFails:new boolean[]{true,false}){
            @SuppressWarnings("unchecked") ObjectProvider<AuthenticatedEvidenceReadAdmissionImpl> authorities=mock(ObjectProvider.class);
            @SuppressWarnings("unchecked") ObjectProvider<EvidenceReadStore> owners=mock(ObjectProvider.class);
            var owner=mock(EvidenceReadStore.class);
            if(authorityFails){when(authorities.getIfUnique()).thenThrow(new IllegalStateException("fixture-authority-provider-unavailable"));when(owners.getIfUnique()).thenReturn(owner);}
            else{when(authorities.getIfUnique()).thenReturn(fixture.authority);when(owners.getIfUnique()).thenThrow(new IllegalStateException("fixture-owner-provider-unavailable"));}
            var client=WebTestClient.bindToController(new EvidenceReadController(authorities,owners)).configureClient().build();
            for(String suffix:new String[]{"","/content"})client.get().uri("/api/v1/managed-document-evidence/applications/"+fixture.app+"/actions/"+fixture.action+suffix).exchange()
                .expectStatus().isEqualTo(503).expectHeader().valueEquals("Cache-Control","no-store").expectBody()
                .jsonPath("$.actionId").isEqualTo(fixture.action.toString()).jsonPath("$.outcome").isEqualTo("OBSERVATION_UNAVAILABLE")
                .jsonPath("$.recovery").isEqualTo("SAME_ACTION_OBSERVE_OR_EXACT_REPLAY").jsonPath("$.documentId").doesNotExist();
            verifyNoInteractions(owner);
        }
    }
}
