/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.util.UUID;
import org.fireflyframework.ecm.domain.model.esignature.SignatureEnvelopeAccountBinding;
/** Explicit server registration; an incoming request cannot choose an account. */
public record SigningAccountScope(UUID bindingId,long bindingVersion,UUID providerId,String tenantId,SignatureEnvelopeAccountBinding descriptor){
 public SigningAccountScope{if(bindingId==null||bindingVersion<1||providerId==null||descriptor==null||descriptor.provider()!=org.fireflyframework.ecm.domain.enums.esignature.SignatureProvider.DOCUSIGN)throw new IllegalArgumentException("Registered signing account required");SigningDraftIntent.text(tenantId,100);}
 public void requireSame(SignatureEnvelopeAccountBinding actual){if(!descriptor.equals(actual))throw new SecurityException("Current signing account differs from server registration");}
 @Override public String toString(){return "SigningAccountScope[redacted]";}
}
