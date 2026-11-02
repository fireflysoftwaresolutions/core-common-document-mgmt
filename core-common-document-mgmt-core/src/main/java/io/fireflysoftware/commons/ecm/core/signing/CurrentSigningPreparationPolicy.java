/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.fireflyframework.security.api.domain.PolicyEvaluation;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyEvaluationPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import reactor.core.publisher.Mono;

/** Requires real same-response provenance and supported obligations; no legacy permissive policy fallback. */
public final class CurrentSigningPreparationPolicy implements SigningPreparationPolicyPort {
    private final SecurityContextPort principals;
    private final PolicyEvaluationPort policy;
    private final Clock clock;
    public CurrentSigningPreparationPolicy(SecurityContextPort principals,PolicyEvaluationPort policy,Clock clock) {
        this.principals=Objects.requireNonNull(principals);this.policy=Objects.requireNonNull(policy);this.clock=Objects.requireNonNull(clock);
    }
    @Override public Mono<PolicyEvaluation> requireCurrent(SecurityPrincipal expected,SigningDraftIntent intent,
            SigningAccountScope account,SigningTenantBindingObservationPort.Observation binding,Instant originalUntil) {
        return Mono.defer(()->{
            Instant started=clock.instant();
            if(expected==null||intent==null||account==null||binding==null||originalUntil==null||!started.isBefore(originalUntil))return Mono.error(denied());
            return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(denied())).flatMap(principal->{
                Instant expiry=principal.claim("exp",Instant.class);String client=principal.claim("client_id",String.class);
                var actor=binding.actor();
                if(!expected.equals(principal)||expiry==null||!clock.instant().isBefore(expiry)
                        ||!Objects.equals(principal.issuer(),actor.issuer())||!Objects.equals(principal.subject(),actor.subject())
                        ||!Objects.equals(principal.tenantId(),actor.platformTenantId())||!Objects.equals(client,actor.clientId())
                        ||!binding.resourceTenantId().toString().equals(intent.tenantId())||!account.tenantId().equals(intent.tenantId())
                        ||!account.providerId().equals(intent.providerId()))return Mono.error(denied());
                Instant until=Collections.min(List.of(originalUntil,binding.notAfter(),expiry));
                if(!clock.instant().isBefore(until))return Mono.error(denied());
                Map<String,Object> facts=Map.ofEntries(
                        Map.entry("resourceTenantId",binding.resourceTenantId()),Map.entry("configurationBindingId",binding.bindingId()),
                        Map.entry("configurationBindingVersion",binding.bindingVersion()),Map.entry("configurationBindingDigest",binding.definitionDigest()),
                        Map.entry("documentVersionId",intent.documentVersionId()),Map.entry("signatureId",intent.signatureId()),
                        Map.entry("providerId",account.providerId()),Map.entry("accountBindingId",account.bindingId()),
                        Map.entry("accountBindingVersion",account.bindingVersion()),Map.entry("accountId",account.descriptor().accountId()),
                        Map.entry("endpoint",account.descriptor().endpoint()),
                        Map.entry("intentDigest",SigningDocumentSnapshot.sha256(intent.canonicalJson().getBytes(StandardCharsets.UTF_8))));
                return Mono.defer(()->policy.evaluate(principal,"document.signing.prepare","document:"+intent.documentId(),facts))
                        .switchIfEmpty(Mono.error(denied())).flatMap(evaluation->{
                            if(!evaluation.decision().granted()||!evaluation.decision().obligations().isEmpty()
                                    ||evaluation.provenance().isEmpty())return Mono.error(denied());
                            var provenance=evaluation.provenance().orElseThrow();
                            if(!provenance.policyRevision().matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"))return Mono.error(denied());
                            return Mono.defer(principals::currentPrincipal).filter(principal::equals).switchIfEmpty(Mono.error(denied()))
                                    .map(current->{if(!clock.instant().isBefore(until))throw denied();return evaluation;});
                        }).timeout(Duration.between(clock.instant(),until));
            }).timeout(Duration.between(started,originalUntil));
        }).onErrorMap(error->denied());
    }
    private static SecurityException denied(){return new SecurityException("Current signing preparation policy unavailable");}
}
