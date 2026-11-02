/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.commons.ecm.interfaces.upload.validation;
import io.fireflysoftware.commons.ecm.interfaces.upload.validation.impl.BoundedEvidenceContentValidatorImpl;
import jakarta.validation.*;
import java.lang.annotation.*;

/** Decoded byte count is separate from the published base64 string length. */
@Documented
@Constraint(validatedBy=BoundedEvidenceContentValidatorImpl.class)
@Target({ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER,ElementType.RECORD_COMPONENT,ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BoundedEvidenceContent {
    int MAX_BYTES=2097152;
    String message() default "Content must contain 1 through 2097152 decoded bytes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
