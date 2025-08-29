package com.here.naksha.cli.validations;

import com.here.naksha.cli.results.FailureResult;
import com.here.naksha.cli.results.Result;
import com.here.naksha.cli.validations.exceptions.FieldValidationException;
import com.here.naksha.cli.validations.exceptions.ValidationException;
import naksha.base.AnyMap;
import org.jetbrains.annotations.NotNull;

public final class ValidatorUtils {
    private ValidatorUtils() {
    }

    public static void requireValidField(
            @NotNull String key, @NotNull AnyMap map, @NotNull Validator<Object, ?> validator
    ) throws FieldValidationException {
        Object object = map.get(key);
        Result<?, ValidationException> result = validator.validate(object);
        handleFieldValidationResult(result, key);
    }

    public static void requireValidArgument(@NotNull Result<?, ValidationException> result) {
        if (result instanceof FailureResult(ValidationException exception)) {
            throw new IllegalArgumentException(exception.getMessage());
        }
    }

    private static void handleFieldValidationResult(
            Result<?, ValidationException> result, String fieldName
    ) throws FieldValidationException {
        if (result instanceof FailureResult(ValidationException exception)) {
            throw new FieldValidationException(fieldName, exception);
        }
    }
}
