/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Planned effect identity is paired with the canonical completed append before any local insert. */
public final class EvidenceUploadEffect {
    private EvidenceUploadEffect(){}
    public static String digest(UploadedEvidenceIntent intent,EvidenceUploadAdmission.Permit permit,UUID documentId,Instant storedAt){
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            text(out,"FIREFLY_DOCUMENT_EVIDENCE_EFFECT_1");text(out,intent.digest());
            uuid(out,permit.resourceTenantId());uuid(out,documentId);uuid(out,permit.bindingId());out.writeLong(permit.bindingVersion());text(out,permit.bindingDigest());
            for(String value:new String[]{permit.actor().issuer(),permit.actor().subject(),permit.actor().clientId(),permit.actor().platformTenantId(),permit.policyRevision()})text(out,value);
            out.writeLong(storedAt.getEpochSecond());out.writeInt(storedAt.getNano());out.flush();return UploadedEvidenceIntent.hash(bytes.toByteArray());
        } catch(IOException impossible){throw new IllegalStateException("Owner evidence encoding unavailable",impossible);}
    }
    private static void uuid(DataOutputStream out,UUID value)throws IOException{out.writeLong(value.getMostSignificantBits());out.writeLong(value.getLeastSignificantBits());}
    private static void text(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
}
