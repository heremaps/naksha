package com.here.naksha.cli.validations;

import com.here.naksha.cli.results.FailureResult;
import com.here.naksha.cli.results.Result;
import com.here.naksha.cli.results.SuccessResult;
import com.here.naksha.cli.validations.exceptions.OrValidationException;
import com.here.naksha.cli.validations.exceptions.ValidationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@FunctionalInterface
public interface Validator<T, S> {
    @NotNull
    Result<S, ValidationException> validate(@Nullable T toValidate);

    @NotNull
    default <U> Validator<T, U> and(@NotNull Validator<S, U> other) {
        return obj -> {
            Result<S, ValidationException> result = validate(obj);
            return switch (result) {
                case FailureResult(ValidationException exception) -> new FailureResult<>(exception);
                case SuccessResult(S payload) -> other.validate(payload);
            };
        };
    }

    @NotNull
    default Validator<T, S> or(@NotNull Validator<T, S> other) {
        return obj -> {
            Result<S, ValidationException> result = validate(obj);
            return switch (result) {
                case FailureResult(ValidationException exception) -> other.orValidate(obj, exception);
                case SuccessResult<S, ValidationException> successResult -> successResult;
            };
        };
    }

    private Result<S, ValidationException> orValidate(@Nullable T toValidate, ValidationException prevException) {
        Result<S, ValidationException> result = validate(toValidate);
        return switch (result) {
            case FailureResult(ValidationException exception) -> new FailureResult<>(
                    new OrValidationException(List.of(prevException, exception))
            );
            case SuccessResult<S, ValidationException> successResult -> successResult;
        };
    }
}
