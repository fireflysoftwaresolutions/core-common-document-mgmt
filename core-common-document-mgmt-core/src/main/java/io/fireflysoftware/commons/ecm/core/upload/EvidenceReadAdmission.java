/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import java.time.*;
import java.util.*;
import org.fireflyframework.kernel.audit.AuditActor;
import reactor.core.publisher.Mono;

/** No audit append authority: this port authorizes observation of an existing immutable custody graph. */
public interface EvidenceReadAdmission {
    record Permit(EvidenceReadIdentity identity,UUID resourceTenantId,AuditActor actor,UUID bindingId,long bindingVersion,String bindingDigest,String policyRevision,Instant observedAt,long observedNanos,Instant notAfter){
        public Permit{
            Objects.requireNonNull(identity);Objects.requireNonNull(actor);Objects.requireNonNull(observedAt);Objects.requireNonNull(notAfter);
            for(UUID value:new UUID[]{resourceTenantId,bindingId})if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact current read resource binding required");
            if(actor.staffRealm()!=null||actor.staffId()!=null||bindingVersion<1||bindingDigest==null||!bindingDigest.matches("[a-f0-9]{64}")||policyRevision==null||!policyRevision.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
                ||!observedAt.isBefore(notAfter)||Duration.between(observedAt,notAfter).compareTo(Duration.ofSeconds(5))>0)throw new IllegalArgumentException("Finite current HUMAN read binding required");
        }
        @Override public String toString(){return "EvidenceReadPermit[redacted]";}
    }
    Mono<Permit> requireCurrent(EvidenceReadIdentity identity);
    Mono<Void> revalidate(Permit original);
}
