/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.documents;
import java.util.UUID;
import io.fireflysoftware.commons.ecm.core.services.generation.DocumentSelection;
/** Exact references and facts are request data, never actor, admission, policy or provider authority. */
public record GovernedIssueDocument(UUID documentId,DocumentSelection selection,String factsJson) {
    public GovernedIssueDocument {
        if(documentId==null||documentId.equals(new UUID(0,0))||selection==null||factsJson==null)
            throw new IllegalArgumentException("Exact governed document request required");
    }
    @Override public String toString(){return "GovernedIssueDocument";}
}
