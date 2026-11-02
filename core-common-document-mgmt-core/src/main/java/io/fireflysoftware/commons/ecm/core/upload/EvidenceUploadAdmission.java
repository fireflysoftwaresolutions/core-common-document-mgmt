/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.kernel.audit.*;
import reactor.core.publisher.Mono;

/** Trusted current native caller/policy/binding bridge, never a request-body grant. */
public interface EvidenceUploadAdmission extends AuditAppendAuthority {
    record Permit(UUID actionId,UUID applicationId,UUID partyId,String intentDigest,UUID resourceTenantId,
            AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,String policyRevision,
            Instant observedAt,long observedNanos,Instant notAfter) {
        public Permit {
            for(UUID id:new UUID[]{actionId,applicationId,partyId,resourceTenantId,bindingId})
                if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact evidence authority required");
            Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);
            if(actor.staffRealm()!=null||actor.staffId()!=null||actor.platformTenantId().length()>100)throw new IllegalArgumentException("Exact representable HUMAN namespace required");
            if(bindingVersion<1||intentDigest==null||!intentDigest.matches("[0-9a-f]{64}")||bindingDigest==null||!bindingDigest.matches("[0-9a-f]{64}")
                    ||policyRevision==null||!policyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                    ||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)
                throw new IllegalArgumentException("Finite current evidence authority required");
        }
        @Override public String toString(){return "EvidenceUploadPermit[redacted]";}
    }
    Mono<Permit> requireCurrent(UploadedEvidenceIntent intent);
    Mono<Void> revalidate(Permit permit);
    <T> Mono<T> withinExpectedAppend(Permit permit,AuditEvent event,Supplier<Mono<T>> append);
}
