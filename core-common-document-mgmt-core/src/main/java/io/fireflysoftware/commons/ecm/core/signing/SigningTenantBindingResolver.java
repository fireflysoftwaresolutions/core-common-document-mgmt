/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import reactor.core.publisher.Mono;
/** Required observer of existing authority; no default mapping, grant or renewal is supplied. */
@FunctionalInterface
public interface SigningTenantBindingResolver{Mono<SigningTenantBinding> resolve(SecurityPrincipal principal,String resourceTenant);}
