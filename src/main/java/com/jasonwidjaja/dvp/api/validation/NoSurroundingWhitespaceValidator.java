package com.jasonwidjaja.dvp.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoSurroundingWhitespaceValidator implements ConstraintValidator<NoSurroundingWhitespace, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.equals(value.strip());
    }
}
