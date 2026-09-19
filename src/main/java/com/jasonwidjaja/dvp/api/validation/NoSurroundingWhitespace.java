package com.jasonwidjaja.dvp.api.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoSurroundingWhitespaceValidator.class)
public @interface NoSurroundingWhitespace {

    String message() default "must not have leading or trailing whitespace";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
