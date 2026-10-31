/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;
import java.util.UUID;
/** Local preparation identity and digest only; no provider success or content is exposed. */
public record PreparedSigningDraft(UUID operationId,UUID envelopeId,UUID requestId,long generation,String state,String contentDigest){
 public PreparedSigningDraft{if(operationId==null||envelopeId==null||requestId==null||generation<1||!"PREPARED".equals(state)||contentDigest==null||!contentDigest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact prepared signing result required");}
 @Override public String toString(){return "PreparedSigningDraft[redacted]";}
}
