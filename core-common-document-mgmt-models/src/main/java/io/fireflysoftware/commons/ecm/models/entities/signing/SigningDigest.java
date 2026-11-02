/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.models.entities.signing;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
/** Local signing-data consistency only; not an authorization or canonical audit hash. */
public final class SigningDigest{
 public static final int MAX_BYTES=10*1024*1024;
 private SigningDigest(){ }
 public static String sha256(byte[] bytes){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
  catch(NoSuchAlgorithmException error){throw new IllegalStateException("SHA-256 unavailable",error);}
 }
}
