package com.here.naksha.cli.validations;

import com.here.naksha.cli.results.FailureResult;
import com.here.naksha.cli.results.SuccessResult;
import com.here.naksha.cli.validations.exceptions.ValidationException;
import naksha.base.JvmBoxingUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

public final class Validators {
    private Validators() {
    }

    @NotNull
    public static <T> Validator<T, T> isNotNull() {
        return obj -> {
            if (obj == null) {
                return new FailureResult<>(new ValidationException("Cannot be null."));
            }
            return new SuccessResult<>(obj);
        };
    }

    @NotNull
    public static <T, S> Validator<T, S> isNull() {
        return obj -> {
            if (obj != null) {
                return new FailureResult<>(new ValidationException("Should be null."));
            }
            return new SuccessResult<>((S) null);
        };
    }

    @NotNull
    public static <T, S> Validator<T, S> isInstanceOf(@NotNull Class<S> clazz) {
        return obj -> {
            requireNonNull(obj);
            if (clazz.isInstance(obj)) {
                return new SuccessResult<>(clazz.cast(obj));
            }
            return new FailureResult<>(
                    new ValidationException(
                            "Should be %s. Received %s.".formatted(
                                    clazz.getSimpleName(),
                                    obj.getClass().getSimpleName()
                            )
                    )
            );
        };
    }

    @NotNull
    public static <T> Validator<T, T> fulfillPredicate(@NotNull Predicate<T> predicate, @NotNull String exceptionMessage) {
        return obj -> {
            requireNonNull(obj);
            if (predicate.test(obj)) {
                return new SuccessResult<>(obj);
            }
            return new FailureResult<>(new ValidationException(exceptionMessage));
        };
    }

    @NotNull
    public static <T> Validator<T, T> fulfillPredicate(
            @NotNull Predicate<T> predicate, @NotNull Function<T, String> exceptionMessage
    ) {
        return obj -> fulfillPredicate(predicate, exceptionMessage.apply(obj)).validate(obj);
    }

    @NotNull
    public static <T extends Iterable<E>, S, E> Validator<T, T> allElements(@NotNull Validator<E, S> elementValidator) {
        return iter -> {
            requireNonNull(iter);
            for (E element : iter) {
                if (elementValidator.validate(element) instanceof FailureResult(ValidationException e)) {
                    return new FailureResult<>(new ValidationException("All elements of the iterable should pass the validation.", e));
                }
            }
            return new SuccessResult<>(iter);
        };
    }

    @NotNull
    public static <T, S> Validator<T, S> canBeBoxed(@NotNull Class<S> clazz) {
        return obj -> {
            requireNonNull(obj);
            try {
                S boxed = requireNonNull(JvmBoxingUtil.box(obj, clazz));
                return new SuccessResult<>(boxed);
            } catch (Exception exception) {
                return new FailureResult<>(
                        new ValidationException(
                                "The instance of the class %s should be able to be boxed into %s."
                                        .formatted(obj.getClass().getSimpleName(), clazz.getSimpleName()),
                                exception
                        )
                );
            }
        };
    }
}
