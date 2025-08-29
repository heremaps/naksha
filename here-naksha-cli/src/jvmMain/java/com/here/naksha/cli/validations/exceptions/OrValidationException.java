package com.here.naksha.cli.validations.exceptions;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OrValidationException extends ValidationException {
    List<ValidationException> validationExceptions;

    public OrValidationException(List<ValidationException> validationExceptions) {
        super(buildMessage(validationExceptions, ValidationException::getMessage));
        this.validationExceptions = validationExceptions;
    }

    private static String buildMessage(
            List<ValidationException> messages, Function<ValidationException,
                    String> exceptionMessageFunction
    ) {
        String header = "At least one of the following conditions must be satisfied:\n";
        String joinedMessages = messages.stream()
                .map(e -> "-" + exceptionMessageFunction.apply(e).indent(1))
                .collect(Collectors.joining("\n"));
        return header + joinedMessages;
    }

    @Override
    public String computeFullMessage() {
        return buildMessage(validationExceptions, ValidationException::computeFullMessage);
    }
}