package com.here.naksha.cli.validations.exceptions;

public class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public String computeFullMessage() {
        StringBuilder fullMessage = new StringBuilder();
        Throwable cause = this.getCause();
        while (cause != null) {
            fullMessage.append("\n");
            if (cause instanceof ValidationException validationException) {
                fullMessage.append(validationException.computeFullMessage());
            } else {
                fullMessage.append(cause.getMessage());
            }
            cause = cause.getCause();
        }
        return getMessage() + fullMessage.toString().indent(1);
    }
}
