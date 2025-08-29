package com.here.naksha.cli.results;

/**
 * @param <T> type of {@link SuccessResult}'s payload
 * @param <S> type of {@link FailureResult}'s payload
 */
public sealed interface Result<T, S> permits SuccessResult, FailureResult {
}