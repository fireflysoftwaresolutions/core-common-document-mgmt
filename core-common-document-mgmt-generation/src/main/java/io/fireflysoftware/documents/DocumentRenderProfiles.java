/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.documents;
import io.fireflysoftware.commons.ecm.core.services.generation.DocumentCurrentSnapshot;
import io.fireflysoftware.documents.render.GovernedRenderProfile;
/** Explicit deployment capabilities; no profile fallback or source-dependent registry mutation. */
@FunctionalInterface public interface DocumentRenderProfiles { GovernedRenderProfile require(DocumentCurrentSnapshot snapshot); }
