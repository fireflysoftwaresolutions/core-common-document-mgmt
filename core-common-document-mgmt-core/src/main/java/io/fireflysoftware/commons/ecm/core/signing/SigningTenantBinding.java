/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.time.Instant;
import java.util.UUID;
/** Existing finite binding observed by a trusted adapter; this constructor grants nothing. */
public record SigningTenantBinding(UUID bindingId,long version,String definitionDigest,String issuer,String subject,String platformTenant,String resourceTenant,Instant notAfter){
 public SigningTenantBinding{
  if(bindingId==null||version<1||definitionDigest==null||!definitionDigest.matches("[0-9a-f]{64}")||notAfter==null)throw new IllegalArgumentException("Finite trusted signing tenant binding required");
  for(String value:new String[]{issuer,subject,platformTenant,resourceTenant})if(value==null||value.isBlank()||value.length()>500||value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Exact trusted signing identity required");
 }
 @Override public String toString(){return "SigningTenantBinding[redacted]";}
}
