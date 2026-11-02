/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.kernel.audit.AuditActor;
import reactor.core.publisher.Mono;

/** Server-observed mapping of an authenticated opaque namespace; this value grants no write authority. */
public interface SigningTenantBindingObservationPort {
    record Observation(UUID resourceTenantId,UUID bindingId,long bindingVersion,String definitionDigest,
            AuditActor actor,Instant notAfter) {
        public Observation {
            Objects.requireNonNull(resourceTenantId);Objects.requireNonNull(bindingId);
            Objects.requireNonNull(actor);Objects.requireNonNull(notAfter);
            if(resourceTenantId.equals(new UUID(0,0))||bindingId.equals(new UUID(0,0))||bindingVersion<1
                    ||bindingVersion==Long.MAX_VALUE||definitionDigest==null||!definitionDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Exact server-observed signing binding required");
            }
        }
        @Override @com.fasterxml.jackson.annotation.JsonValue public String toString(){return "SigningBindingObservation[redacted]";}
    }
    Mono<Observation> observe(Instant originalNotAfter);
}
