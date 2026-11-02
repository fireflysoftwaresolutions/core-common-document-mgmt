/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.fireflyframework.kernel.audit.AuditActor;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import reactor.core.publisher.Mono;
/** Application composition must provide genuine authentication, policy and resource-binding adapters. */
public final class SigningAuthorityAdapter{
 private final SecurityContextPort principals;private final PolicyDecisionPort policy;private final SigningTenantBindingResolver tenants;private final Clock clock;
 public SigningAuthorityAdapter(SecurityContextPort principals,PolicyDecisionPort policy,SigningTenantBindingResolver tenants,Clock clock){
  this.principals=Objects.requireNonNull(principals);this.policy=Objects.requireNonNull(policy);this.tenants=Objects.requireNonNull(tenants);this.clock=Objects.requireNonNull(clock);
 }
 public Mono<SigningAuthority> authorize(SigningDraftIntent intent,SigningAccountScope account){
  return Mono.defer(principals::currentPrincipal).switchIfEmpty(Mono.error(denied())).flatMap(principal->{
   String client=principal.claim("client_id",String.class);Instant expiry=principal.claim("exp",Instant.class);
   if(principal.issuer()==null||principal.subject()==null||principal.tenantId()==null||client==null||expiry==null||!clock.instant().isBefore(expiry))return Mono.error(denied());
   var actor=new AuditActor(principal.issuer(),principal.subject(),client,principal.tenantId(),null,null);
   if(!account.tenantId().equals(intent.tenantId())||!account.providerId().equals(intent.providerId()))return Mono.error(denied());
   return Mono.defer(()->tenants.resolve(principal,intent.tenantId())).switchIfEmpty(Mono.error(denied())).flatMap(binding->{
    if(!binding.issuer().equals(actor.issuer())||!binding.subject().equals(actor.subject())||!binding.platformTenant().equals(actor.platformTenantId())||!binding.resourceTenant().equals(intent.tenantId()))return Mono.error(denied());
    Instant until=binding.notAfter().isBefore(expiry)?binding.notAfter():expiry;var authority=new SigningAuthority(actor,binding,until);authority.requireLive(clock);
    Map<String,Object> facts=Map.of("resourceTenant",intent.tenantId(),"documentVersionId",intent.documentVersionId(),"providerId",account.providerId(),"accountBindingId",account.bindingId(),
      "accountBindingVersion",account.bindingVersion(),"accountId",account.descriptor().accountId(),"endpoint",account.descriptor().endpoint(),"intentDigest",SigningDocumentSnapshot.sha256(intent.canonicalJson().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    return Mono.defer(()->policy.authorize(principal,"document.signing.prepare","document:"+intent.documentId(),facts)).switchIfEmpty(Mono.error(denied())).flatMap(decision->{
     if(!decision.granted()||!decision.obligations().isEmpty())return Mono.error(denied());authority.requireLive(clock);return Mono.just(authority);
    });
   });
  }).onErrorMap(error->denied());
 }
 public Mono<SigningAuthority> revalidate(SigningAuthority original,SigningDraftIntent intent,SigningAccountScope account){
  return Mono.defer(()->{original.requireLive(clock);return authorize(intent,account);}).flatMap(current->{
   var first=original.binding();var live=current.binding();
   if(!original.actor().equals(current.actor())||!first.bindingId().equals(live.bindingId())||first.version()!=live.version()||!first.definitionDigest().equals(live.definitionDigest())||!first.resourceTenant().equals(live.resourceTenant()))return Mono.error(denied());
   Instant until=original.notAfter().isBefore(current.notAfter())?original.notAfter():current.notAfter();
   var bounded=new SigningAuthority(original.actor(),original.binding(),until);bounded.requireLive(clock);return Mono.just(bounded);
  });
 }
 public <T> Mono<T> withinOriginalWindow(SigningAuthority original,java.util.function.Supplier<Mono<T>> work){
  return Mono.defer(()->{
   Instant now=clock.instant();if(!now.isBefore(original.notAfter()))return Mono.error(denied());
   return Mono.defer(work).timeout(java.time.Duration.between(now,original.notAfter()),Mono.error(denied()))
    .doOnNext(result->original.requireLive(clock));
  });
 }
 private static SecurityException denied(){return new SecurityException("Current scoped signing authority unavailable");}
}
