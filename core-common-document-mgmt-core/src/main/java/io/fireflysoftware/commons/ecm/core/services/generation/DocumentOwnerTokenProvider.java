/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.services.generation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.fireflyframework.core.transaction.R2dbcOperationBudget;
import reactor.core.publisher.Mono;

/** A trusted delegation adapter must verify actor, issuer, audience and resource-tenant authority. */
public interface DocumentOwnerTokenProvider {
    enum Owner { MASTERS, CONFIGURATION }
    record AuthorizedToken(String issuer,String subject,UUID resourceTenant,Instant until,String token) {
        public AuthorizedToken {
            if(issuer==null||issuer.isBlank()||subject==null||subject.isBlank()||resourceTenant==null
                    ||until==null||token==null||token.isBlank()||token.contains("\r")||token.contains("\n"))
                throw new IllegalArgumentException("Verified owner delegation required");
        }
        @Override public String toString(){return "AuthorizedToken";}
    }
    Mono<AuthorizedToken> authorize(Owner owner,DocumentSelection selection,R2dbcOperationBudget original);
}
