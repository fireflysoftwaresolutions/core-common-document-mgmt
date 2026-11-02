/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;

import io.fireflysoftware.commons.ecm.web.controllers.EvidenceUploadController;
import io.fireflysoftware.commons.ecm.core.upload.EvidenceUploadStore;
import io.fireflysoftware.commons.ecm.web.upload.impl.AuthenticatedEvidenceUploadAdmissionImpl;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Actual JSON/base64 and validation boundary; disabled owner yields no fabricated content receipt. */
class EvidenceUploadBoundaryTest {
    final UUID application=UUID.randomUUID(),action=UUID.randomUUID(),party=UUID.randomUUID();
    WebTestClient client(){
        var empty=new StaticListableBeanFactory();
        var controller=new EvidenceUploadController(empty.getBeanProvider(AuthenticatedEvidenceUploadAdmissionImpl.class),empty.getBeanProvider(EvidenceUploadStore.class));
        return WebTestClient.bindToController(controller)
            .httpMessageCodecs(new EvidenceUploadConfiguration()::configureHttpMessageCodecs)
            .configureClient().codecs(codecs->codecs.defaultCodecs().maxInMemorySize(3*1024*1024)).build();
    }
    String body(byte[] bytes){return "{\"actionId\":\""+action+"\",\"applicationId\":\""+application+"\",\"partyId\":\""+party+"\",\"fileName\":\"proof.pdf\",\"mediaType\":\"application/pdf\",\"documentTypeCode\":\"IDENTITY\",\"content\":\""+Base64.getEncoder().encodeToString(bytes)+"\"}";}
    void send(String body,int status){client().post().uri("/api/v1/managed-document-evidence/applications/"+application+"/actions/"+action)
        .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isEqualTo(status);}
    @Test void exactlyTwoMiBReachesDisabledOwnerGateInsteadOfDecoderRejection(){send(body(new byte[2*1024*1024]),503);}
    @Test void oneAdditionalDecodedByteIsRefusedBeforeOwnerEvenWithSameBase64Length(){send(body(new byte[2*1024*1024+1]),400);}
    @Test void oversizedJsonEnvelopeIsRefusedBeforeOwner(){send(body(new byte[3*1024*1024]),413);}
    @Test void actionAndApplicationPathMismatchCannotReachOwner(){send(body(new byte[]{1,2,3}).replace(application.toString(),UUID.randomUUID().toString()),400);}
}
