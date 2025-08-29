package com.here.naksha.cli.validations.exceptions;

public class FieldValidationException extends Exception {
    public FieldValidationException(String fieldName, ValidationException validationException) {
        super("Invalid `%s` field.%n".formatted(fieldName) + validationException.computeFullMessage().indent(1));
    }
}
