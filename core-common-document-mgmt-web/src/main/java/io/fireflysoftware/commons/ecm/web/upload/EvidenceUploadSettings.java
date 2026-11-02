/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.web.upload;
import java.time.Instant;
import java.util.UUID;

/** Explicit server-owned document resource binding; never derived from caller request data. */
public record EvidenceUploadSettings(String issuer,String audience,String allowedClientId,String platformTenant,
        UUID resourceTenantId,UUID bindingId,long bindingVersion,String bindingDigest,Instant notAfter){
    public EvidenceUploadSettings{
        for(String value:new String[]{issuer,audience,allowedClientId,platformTenant})
            if(value==null || value.isBlank() || value.length()>512 || value.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Explicit evidence upload deployment binding required");
        for(UUID id:new UUID[]{resourceTenantId,bindingId})if(id==null || id.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact evidence upload dataset required");
        if(bindingVersion<1 || bindingDigest==null || !bindingDigest.matches("[0-9a-f]{64}") || notAfter==null)throw new IllegalArgumentException("Versioned evidence upload binding required");
    }
    public void requireCurrent(Instant now){if(!now.isBefore(notAfter))throw new IllegalStateException("Current evidence upload binding required");}
    @Override public String toString(){return "EvidenceUploadSettings[redacted]";}
}
