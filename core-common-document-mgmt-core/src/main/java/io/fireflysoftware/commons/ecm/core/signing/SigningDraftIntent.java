/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.core.signing;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fireflysoftware.commons.ecm.interfaces.dtos.DocumentSignatureDTO;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureType;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureFormat;
import io.fireflysoftware.commons.ecm.interfaces.enums.SignatureStatus;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Exact supported local intent; this value does not authorize or dispatch a signature. */
public record SigningDraftIntent(UUID signatureId, UUID documentId, UUID documentVersionId, UUID providerId,
        String tenantId, UUID signerPartyId, String signerName, String signerEmail,
        int x, int y, int page, int width, int height, String language, String role, String message,
        String authenticationMethod) {
    public SigningDraftIntent {
        if (signatureId == null || documentId == null || documentVersionId == null || providerId == null
                || x < 0 || y < 0 || x > 10000 || y > 10000 || page < 1 || page > 1000
                || width < 1 || width > 10000 || height < 1 || height > 10000) throw invalid();
        text(tenantId, 100); text(signerName, 100); text(signerEmail, 254);
        if (!signerEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || !"en".equals(language)
                || !"Signer".equals(role) || !("EMAIL".equals(authenticationMethod) || "NONE".equals(authenticationMethod))) throw invalid();
        if (message != null) text(message, 1000);
    }
    public static SigningDraftIntent from(DocumentSignatureDTO value) {
        if (value == null || value.getSignatureType() != SignatureType.BASIC || value.getSignatureFormat() != SignatureFormat.PDF_VISIBLE
                || value.getSignaturePositionX() == null || value.getSignaturePositionY() == null || value.getSignaturePage() == null
                || value.getSignatureWidth() == null || value.getSignatureHeight() == null || !Integer.valueOf(1).equals(value.getSigningOrder())
                || !Boolean.TRUE.equals(value.getSignatureRequired()) || value.getSignerTimeZone() != null
                || value.getExpirationDate() != null || value.getSignedAt() != null || value.getSignatureData() != null
                || value.getSignatureCertificate() != null || value.getSignatureReason() != null || value.getSignatureLocation() != null
                || value.getSignatureContactInfo() != null || value.getExternalSignerId() != null || value.getSigningUrl() != null
                || value.getEcmMetadata() != null || value.getCreatedBy() != null || value.getCreatedAt() != null
                || value.getUpdatedBy() != null || value.getUpdatedAt() != null || value.getVersion() != null
                || value.getSignatureStatus() != null && value.getSignatureStatus() != SignatureStatus.PENDING) throw invalid();
        return new SigningDraftIntent(value.getId(), value.getDocumentId(), value.getDocumentVersionId(), value.getSignatureProviderId(),
                value.getTenantId(), value.getSignerPartyId(), value.getSignerName(), value.getSignerEmail(), value.getSignaturePositionX(),
                value.getSignaturePositionY(), value.getSignaturePage(), value.getSignatureWidth(), value.getSignatureHeight(),
                value.getSignerLanguage(), value.getSignerRole(), value.getCustomSignatureMessage(), value.getAuthenticationMethod());
    }
    public String canonicalJson() {
        Map<String,Object> values = new TreeMap<>();
        values.put("protocol", "CORE_SIGNING_PREPARATION_V1"); values.put("signatureId", signatureId); values.put("documentId", documentId);
        values.put("documentVersionId", documentVersionId); values.put("providerId", providerId); values.put("tenantId", tenantId);
        values.put("signerPartyId", signerPartyId); values.put("signerName", signerName); values.put("signerEmail", signerEmail);
        values.put("x", x); values.put("y", y); values.put("page", page); values.put("width", width); values.put("height", height);
        values.put("coordinateContract", "POINTS_72_DPI_TOP_LEFT_PAGE_ONE_BASED"); values.put("language", language); values.put("role", role);
        values.put("message", message); values.put("authenticationMethod", authenticationMethod); values.put("signingOrder", 1);
        values.put("signatureRequired", true); values.put("signatureType", "BASIC"); values.put("signatureFormat", "PDF_VISIBLE");
        try { return JsonMapper.builder().build().writeValueAsString(values); }
        catch (java.io.IOException error) { throw invalid(); }
    }
    static String text(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.strip())
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c >= 0xD800 && c <= 0xDFFF)) throw invalid();
        return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Exact supported signing preparation intent required"); }
    @Override @com.fasterxml.jackson.annotation.JsonValue public String toString() { return "SigningDraftIntent[redacted]"; }
}
