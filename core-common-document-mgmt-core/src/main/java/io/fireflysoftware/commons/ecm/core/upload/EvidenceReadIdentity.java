/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;
import java.util.UUID;

/** Lookup identity only; native current authority must independently bind every value. */
public record EvidenceReadIdentity(UUID actionId,UUID applicationId,UUID partyId) {
    public EvidenceReadIdentity{for(UUID value:new UUID[]{actionId,applicationId,partyId})if(value==null||value.equals(new UUID(0,0)))throw new IllegalArgumentException("Exact original evidence lookup identity required");}
    @Override public String toString(){return "EvidenceReadIdentity[redacted]";}
}
