package io.fireflysoftware.commons.ecm.core.services.generation;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.fireflyframework.kernel.audit.*;
import io.fireflysoftware.commons.ecm.models.entities.generation.GeneratedDocument;

class DocumentStorageFixtures extends DocumentSnapshotFixtures {
    DocumentGenerationStorage.Request request(byte[] bytes){
        var values=values();var snapshot=new io.fireflysoftware.commons.ecm.core.services.generation.impl.DocumentCurrentSnapshotServiceImpl((s,b)->reactor.core.publisher.Mono.just(values),clock,nanos).observe(selection(values),budget()).block();
        var actor=new AuditActor("https://issuer.invalid","unit-subject","unit-client",tenant.toString(),null,null);
        var event=new AuditEvent(UUID.fromString("00000001-0000-4000-8000-000000000001"),new AuditStream(tenant,"core-common-document-mgmt","document-generation",source),actor,"DOCUMENT_LOCAL_STORAGE_RECORDED",AuditEvent.Outcome.COMPLETED,source,layout,null,clock.instant(),"0".repeat(64),"UNIT_POLICY");
        return new DocumentGenerationStorage.Request(tenant,source,snapshot,event,bytes);
    }
}
