/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.upload;

import io.fireflysoftware.commons.ecm.interfaces.upload.EvidenceUploadRequest;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Owner protocol; consumers transport the published digest without reproducing this encoding. */
public final class UploadedEvidenceIntent {
    public static final int MAX_CONTENT_BYTES=2*1024*1024;
    private final EvidenceUploadRequest request;
    private final byte[] content;
    private final String contentDigest,digest;
    private UploadedEvidenceIntent(EvidenceUploadRequest request) {
        this.request=Objects.requireNonNull(request);this.content=request.content();
        for(UUID id:new UUID[]{request.actionId(),request.applicationId(),request.partyId()})
            if(id==null||id.equals(new UUID(0,0)))throw invalid();
        validText(request.fileName(),255);validText(request.mediaType(),100);validText(request.documentTypeCode(),50);
        if(request.fileName().contains("/")||request.fileName().contains("\\")||Set.of(".","..").contains(request.fileName())
                ||!request.mediaType().matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
                ||!request.documentTypeCode().matches("[A-Z][A-Z0-9_]{0,49}")
                ||content==null||content.length==0||content.length>MAX_CONTENT_BYTES)throw invalid();
        contentDigest=hash(content);
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            text(out,"FIREFLY_DOCUMENT_EVIDENCE_UPLOAD_1");
            for(UUID id:new UUID[]{request.actionId(),request.applicationId(),request.partyId()}) {
                out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());
            }
            text(out,request.fileName());text(out,request.mediaType());text(out,request.documentTypeCode());
            out.writeInt(content.length);text(out,contentDigest);out.flush();digest=hash(bytes.toByteArray());
        } catch(IOException impossible){throw new IllegalStateException("Owner evidence encoding unavailable",impossible);}
    }
    public static UploadedEvidenceIntent capture(EvidenceUploadRequest request){if(request==null)throw invalid();return new UploadedEvidenceIntent(request);}
    public UUID actionId(){return request.actionId();}public UUID applicationId(){return request.applicationId();}public UUID partyId(){return request.partyId();}
    public String fileName(){return request.fileName();}public String mediaType(){return request.mediaType();}public String documentTypeCode(){return request.documentTypeCode();}
    public byte[] content(){return content.clone();}public int length(){return content.length;}public String contentDigest(){return contentDigest;}public String digest(){return digest;}
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("Required content digest unavailable",impossible);}}
    private static void text(DataOutputStream out,String value)throws IOException {byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static void validText(String text,int limit){
        if(text==null||text.isBlank()||!text.equals(text.strip())||text.length()>limit||text.codePoints().anyMatch(c->Character.isISOControl(c)))throw invalid();
        try{StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));}
        catch(CharacterCodingException invalid){throw invalid();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Exact bounded evidence content and identity required");}
    @Override public String toString(){return "UploadedEvidenceIntent[redacted]";}
}
