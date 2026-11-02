/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.interfaces.upload.validation.impl;
import io.fireflysoftware.commons.ecm.interfaces.upload.validation.BoundedEvidenceContent;
import jakarta.validation.*;

public final class BoundedEvidenceContentValidatorImpl implements ConstraintValidator<BoundedEvidenceContent,byte[]> {
    @Override public boolean isValid(byte[] value,ConstraintValidatorContext context){return value!=null&&value.length>=1&&value.length<=BoundedEvidenceContent.MAX_BYTES;}
}
