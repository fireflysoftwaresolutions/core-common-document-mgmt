/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.time.Clock;
import java.time.Instant;
import org.fireflyframework.kernel.audit.AuditActor;
public record SigningAuthority(AuditActor actor,SigningTenantBinding binding,Instant notAfter){
 public SigningAuthority{if(actor==null||binding==null||notAfter==null||notAfter.isAfter(binding.notAfter()))throw new IllegalArgumentException("Original finite signing authority required");}
 public void requireLive(Clock clock){if(!clock.instant().isBefore(notAfter))throw new SecurityException("Original signing authority expired");}
 @Override public String toString(){return "SigningAuthority[redacted]";}
}
