package io.fireflysoftware.commons.ecm.core.signing.impl;
import java.time.Clock;
import io.fireflysoftware.commons.ecm.core.signing.SigningAuditedPhase;
import io.fireflysoftware.commons.ecm.models.repositories.signing.*;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
/** Test-only package access; public production construction builds all repositories from one verified template. */
public final class SigningStoreTestAccess {
 private SigningStoreTestAccess() { }
 public static SpringDataSigningPreparationStore create(SigningAccountBindingRepository accounts,SigningEnvelopeRepository envelopes,
       SigningOperationRepository operations,SigningRequestBindingRepository requests,SigningDocumentRepository documents,
       SigningDocumentVersionRepository versions,SigningProviderRepository providers,SigningSignatureRepository signatures,
       R2dbcEntityTemplate template,SigningAuditedPhase audit,Clock clock) {
  return new SpringDataSigningPreparationStore(accounts,envelopes,operations,requests,documents,versions,providers,signatures,template,audit,clock);
 }
}
