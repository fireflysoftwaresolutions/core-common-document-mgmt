/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.fireflyframework.core.audit.AuditAppendAuthority;
import org.fireflyframework.kernel.audit.*;
import org.fireflyframework.security.api.domain.*;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/** Trusted owner composition, per subscription; its private capabilities never come from request DTOs. */
public final class TrustedSigningRuntime implements SigningAuditAdmission,AuditAppendAuthority,SigningTenantBindingResolver,SigningPolicyTracePort {
    private final SecurityContextPort principals;
    private final SigningTenantBindingObservationPort bindings;
    private final SigningPreparationPolicyPort policy;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final Object executionKey=new Object(),appendKey=new Object();

    public TrustedSigningRuntime(SecurityContextPort principals,SigningTenantBindingObservationPort bindings,
            SigningPreparationPolicyPort policy,Clock clock,Supplier<UUID> ids) {
        this.principals=Objects.requireNonNull(principals);this.bindings=Objects.requireNonNull(bindings);
        this.policy=Objects.requireNonNull(policy);this.clock=Objects.requireNonNull(clock);this.ids=Objects.requireNonNull(ids);
    }
    public SigningDraftWorkflow scoped(SigningDraftWorkflow workflow) {
        Objects.requireNonNull(workflow);
        return intent->Mono.deferContextual(context->{
            if(context.hasKey(executionKey)||intent==null)return Mono.error(denied());
            Instant started=clock.instant();
            return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(denied())).flatMap(principal->{
                Instant expiry=principal.claim("exp",Instant.class);String client=principal.claim("client_id",String.class);
                if(expiry==null||client==null||client.isBlank()||principal.issuer()==null||principal.subject()==null||principal.tenantId()==null)return Mono.error(denied());
                Instant until=min(expiry,started.plusSeconds(5));if(!clock.instant().isBefore(until))return Mono.error(denied());
                var actor=new AuditActor(principal.issuer(),principal.subject(),client,principal.tenantId(),null,null);
                var execution=new Execution(intent,principal,actor,until,nonzero(),nonzero());
                if(execution.process.equals(execution.correlation))return Mono.error(denied());
                return Mono.defer(()->workflow.prepare(intent)).switchIfEmpty(Mono.error(denied()))
                        .doOnNext(value->requireLive(execution)).timeout(Duration.between(clock.instant(),until))
                        .doFinally(signal->{execution.active.set(false);var phase=execution.phase.getAndSet(null);if(phase!=null)phase.active.set(false);})
                        .contextWrite(current->current.put(executionKey,execution));
            }).timeout(Duration.between(started,started.plusSeconds(5)));
        });
    }
    public PolicyDecisionPort decisions(SigningAccountScope account) {
        Objects.requireNonNull(account);
        return (principal,action,resource,ignored)->Mono.deferContextual(context->{
            var execution=execution(context);var observed=execution.binding.get();
            if(observed==null||!execution.principal.equals(principal)||!"document.signing.prepare".equals(action)
                    ||!("document:"+execution.intent.documentId()).equals(resource))return Mono.error(denied());
            return currentPrincipal(execution).then(policy.requireCurrent(principal,execution.intent,account,observed,min(execution.until,observed.notAfter())))
                    .map(evaluation->{
                        requireLive(execution);
                        if(!evaluation.decision().granted()||!evaluation.decision().obligations().isEmpty()||evaluation.provenance().isEmpty())throw denied();
                        captureAuthorization(execution,evaluation);
                        return evaluation.decision();
                    });
        }).onErrorMap(error->denied());
    }
    @Override public Mono<SigningTenantBinding> resolve(SecurityPrincipal expected,String resourceTenant) {
        return Mono.deferContextual(context->{
            var execution=execution(context);if(!execution.principal.equals(expected)||!execution.intent.tenantId().equals(resourceTenant))return Mono.error(denied());
            return currentPrincipal(execution).then(observe(execution,execution.until)).flatMap(binding->{
                if(!binding.resourceTenantId().toString().equals(resourceTenant))return Mono.error(denied());
                var actor=binding.actor();return Mono.just(new SigningTenantBinding(binding.bindingId(),binding.bindingVersion(),binding.definitionDigest(),
                        actor.issuer(),actor.subject(),actor.platformTenantId(),resourceTenant,min(execution.until,binding.notAfter())));
            });
        }).onErrorMap(error->denied());
    }
    @Override public Mono<SigningAuditAdmission.Permit> requireCurrent(SigningDraftIntent intent,SigningAccountScope account,SigningAuthority authority) {
        return Mono.deferContextual(context->{
            var execution=execution(context);
            if(execution.phase.get()!=null||!execution.intent.equals(intent)||account==null||authority==null
                    ||!execution.actor.equals(authority.actor())||!account.tenantId().equals(intent.tenantId())
                    ||!account.providerId().equals(intent.providerId()))return Mono.error(denied());
            Instant until=min(execution.until,authority.notAfter());if(!clock.instant().isBefore(until))return Mono.error(denied());
            return currentPrincipal(execution).then(observe(execution,until)).flatMap(binding->{
                var original=authority.binding();
                if(!binding.bindingId().equals(original.bindingId())||binding.bindingVersion()!=original.version()
                        ||!binding.definitionDigest().equals(original.definitionDigest())||!binding.resourceTenantId().toString().equals(original.resourceTenant())
                        ||!execution.actor.issuer().equals(original.issuer())||!execution.actor.subject().equals(original.subject())
                        ||!execution.actor.platformTenantId().equals(original.platformTenant()))return Mono.error(denied());
                return policy.requireCurrent(execution.principal,intent,account,binding,until).map(evaluation->{
                    if(!evaluation.decision().granted()||!evaluation.decision().obligations().isEmpty()||evaluation.provenance().isEmpty())throw denied();
                    var provenance=evaluation.provenance().orElseThrow();
                    requireLive(execution);Instant validUntil=min(until,binding.notAfter());if(!clock.instant().isBefore(validUntil))throw denied();
                    var permit=new SigningAuditAdmission.Permit(binding.resourceTenantId(),binding.resourceTenantId().toString(),binding.bindingId(),binding.bindingVersion(),
                            binding.definitionDigest(),execution.actor,provenance.policyRevision(),execution.process,execution.correlation,validUntil);
                    var phase=new Phase(permit,account,provenance,clock.instant(),capturePhase(execution,evaluation));
                    if(!execution.phase.compareAndSet(null,phase))throw denied();return permit;
                });
            });
        }).onErrorMap(error->denied());
    }
    @Override public <T> Mono<T> withinPhase(SigningAuditAdmission.Permit permit,Supplier<Mono<T>> work) {
        return Mono.deferContextual(context->{
            var execution=execution(context);var phase=phase(execution,permit);
            if(!phase.entered.compareAndSet(false,true))return Mono.error(denied());
            return Mono.using(()->phase,held->Mono.defer(work),held->{held.active.set(false);execution.phase.compareAndSet(held,null);},true);
        });
    }
    @Override public Mono<SigningPolicyTraceV1> currentTrace(SigningAuditAdmission.Permit permit) {
        return Mono.deferContextual(context->{
            var execution=execution(context);var phase=phase(execution,permit);
            if(!phase.entered.get()||execution.traceInvalid.get()||phase.trace==null)return Mono.error(denied());
            return Mono.just(phase.trace);
        }).onErrorMap(error->denied());
    }
    @Override public Mono<Void> requirePolicyCustody() { return Mono.empty(); }
    @Override public <T> Mono<T> withinExpectedAppendV3(SigningAuditAdmission.Permit permit,SigningAuditEvidenceV3 evidence,AuditEvent event,Supplier<Mono<T>> work) {
        return Mono.deferContextual(context->{
            var execution=execution(context);var phase=phase(execution,permit);
            if(!phase.entered.get()||context.hasKey(appendKey)||execution.traceInvalid.get()||phase.trace==null
                    ||evidence==null||evidence.trace()!=phase.trace||event==null
                    ||!evidence.business().intent().equals(execution.intent)||!evidence.business().account().equals(phase.account)
                    ||!event.equals(evidence.event(permit,event.occurredAt()))||event.occurredAt().isBefore(phase.observed)
                    ||event.occurredAt().isAfter(clock.instant())||!phase.appendEntered.compareAndSet(false,true))return Mono.error(denied());
            var capability=new ExpectedAppend(execution,phase,event);
            return Mono.defer(work).contextWrite(current->current.put(appendKey,capability));
        });
    }
    @Override public <T> Mono<T> withinExpectedAppend(SigningAuditAdmission.Permit permit,SigningAuditEvidence evidence,AuditEvent event,Supplier<Mono<T>> work) {
        return Mono.deferContextual(context->{
            var execution=execution(context);var phase=phase(execution,permit);
            if(!phase.entered.get()||context.hasKey(appendKey)||evidence==null||event==null
                    ||!evidence.intent().equals(execution.intent)||!evidence.account().equals(phase.account)
                    ||!event.equals(evidence.event(permit,event.occurredAt()))||event.occurredAt().isBefore(phase.observed)
                    ||event.occurredAt().isAfter(clock.instant())||!phase.appendEntered.compareAndSet(false,true))return Mono.error(denied());
            var capability=new ExpectedAppend(execution,phase,event);
            return Mono.defer(work).contextWrite(current->current.put(appendKey,capability));
        });
    }
    @Override public Mono<AuditAppendAuthority.Permit> requireCurrent(AuditEvent event) {
        return Mono.deferContextual(context->{
            var execution=execution(context);Object value=context.getOrDefault(appendKey,null);
            if(!(value instanceof ExpectedAppend expected)||expected.execution!=execution||!expected.event.equals(event)
                    ||phase(execution,expected.phase.permit)!=expected.phase||!expected.phase.entered.get()
                    ||expected.checks.incrementAndGet()>2)return Mono.error(denied());
            return currentPrincipal(execution).map(principal->{
                phase(execution,expected.phase.permit);requireLive(execution);
                return new AuditAppendAuthority.Permit(event.stream(),execution.actor,min(execution.until,expected.phase.permit.notAfter()));
            });
        }).onErrorMap(error->denied());
    }
    private void captureAuthorization(Execution execution,PolicyEvaluation evaluation) {
        int index=execution.phases.get();
        if(index>1||execution.phase.get()!=null||!execution.authorizations.compareAndSet(index,index+1)
                ||!execution.authorization.compareAndSet(null,evaluation))execution.traceInvalid.set(true);
    }
    private SigningPolicyTraceV1 capturePhase(Execution execution,PolicyEvaluation evaluation) {
        int index=execution.phases.getAndUpdate(value->Math.min(value+1,2));
        var authorization=execution.authorization.getAndSet(null);
        if(execution.traceInvalid.get()||index>1||execution.authorizations.get()!=index+1||authorization==null
                ||index==1&&execution.reservationTrace.get()==null){execution.traceInvalid.set(true);return null;}
        try {
            var trace=SigningPolicyTraceV1.checked(index==0?SigningPolicyTraceV1.Phase.RESERVE:SigningPolicyTraceV1.Phase.PREPARED,authorization,evaluation);
            if(index==0)execution.reservationTrace.set(trace);return trace;
        } catch(IllegalArgumentException error) { execution.traceInvalid.set(true);return null; }
    }
    private Mono<SigningTenantBindingObservationPort.Observation> observe(Execution execution,Instant until) {
        requireLive(execution);
        var captured=execution.binding.get();Instant bounded=captured==null?until:min(until,captured.notAfter());
        if(!clock.instant().isBefore(bounded))return Mono.error(denied());
        return Mono.defer(()->bindings.observe(bounded)).switchIfEmpty(Mono.error(denied())).map(binding->{
            requireLive(execution);
            if(!execution.actor.equals(binding.actor())||!binding.resourceTenantId().toString().equals(execution.intent.tenantId())
                    ||!clock.instant().isBefore(binding.notAfter())||binding.notAfter().isAfter(bounded))throw denied();
            var first=execution.binding.get();if(first==null){if(!execution.binding.compareAndSet(null,binding))first=execution.binding.get();else first=binding;}
            if(!first.resourceTenantId().equals(binding.resourceTenantId())||!first.bindingId().equals(binding.bindingId())
                    ||first.bindingVersion()!=binding.bindingVersion()||!first.definitionDigest().equals(binding.definitionDigest())
                    ||!first.actor().equals(binding.actor()))throw denied();
            return binding;
        }).timeout(Duration.between(clock.instant(),bounded));
    }
    private Mono<SecurityPrincipal> currentPrincipal(Execution execution) {
        return Mono.defer(principals::currentPrincipal).filter(execution.principal::equals).switchIfEmpty(Mono.error(denied()))
                .map(principal->{requireLive(execution);return principal;});
    }
    private Execution execution(ContextView context) {
        Object value=context.getOrDefault(executionKey,null);if(!(value instanceof Execution execution))throw denied();requireLive(execution);return execution;
    }
    private Phase phase(Execution execution,SigningAuditAdmission.Permit permit) {
        requireLive(execution);var phase=execution.phase.get();
        if(phase==null||phase.permit!=permit||!phase.active.get()||!clock.instant().isBefore(permit.notAfter()))throw denied();return phase;
    }
    private void requireLive(Execution execution){
        Instant now=clock.instant();var first=execution.binding.get();
        if(!execution.active.get()||!now.isBefore(execution.until)||first!=null&&!now.isBefore(first.notAfter()))throw denied();
    }
    private UUID nonzero(){UUID id=Objects.requireNonNull(ids.get());if(id.equals(new UUID(0,0)))throw denied();return id;}
    private static Instant min(Instant first,Instant... rest){for(Instant value:rest)if(value.isBefore(first))first=value;return first;}
    private static SecurityException denied(){return new SecurityException("Current trusted signing runtime unavailable");}
    private static final class Execution {
        final SigningDraftIntent intent;final SecurityPrincipal principal;final AuditActor actor;final Instant until;final UUID process,correlation;
        final AtomicBoolean active=new AtomicBoolean(true);final AtomicReference<Phase> phase=new AtomicReference<>();
        final AtomicInteger authorizations=new AtomicInteger(),phases=new AtomicInteger();final AtomicBoolean traceInvalid=new AtomicBoolean();
        final AtomicReference<PolicyEvaluation> authorization=new AtomicReference<>();final AtomicReference<SigningPolicyTraceV1> reservationTrace=new AtomicReference<>();
        final AtomicReference<SigningTenantBindingObservationPort.Observation> binding=new AtomicReference<>();
        Execution(SigningDraftIntent intent,SecurityPrincipal principal,AuditActor actor,Instant until,UUID process,UUID correlation){this.intent=intent;this.principal=principal;this.actor=actor;this.until=until;this.process=process;this.correlation=correlation;}
    }
    private static final class Phase {
        final SigningAuditAdmission.Permit permit;final SigningAccountScope account;final PolicyEvaluationProvenance provenance;final Instant observed;
        final AtomicBoolean active=new AtomicBoolean(true),entered=new AtomicBoolean(),appendEntered=new AtomicBoolean();
        final SigningPolicyTraceV1 trace;
        Phase(SigningAuditAdmission.Permit permit,SigningAccountScope account,PolicyEvaluationProvenance provenance,Instant observed,SigningPolicyTraceV1 trace){this.permit=permit;this.account=account;this.provenance=provenance;this.observed=observed;this.trace=trace;}
    }
    private static final class ExpectedAppend {
        final Execution execution;final Phase phase;final AuditEvent event;final AtomicInteger checks=new AtomicInteger();
        ExpectedAppend(Execution execution,Phase phase,AuditEvent event){this.execution=execution;this.phase=phase;this.event=event;}
    }
}
