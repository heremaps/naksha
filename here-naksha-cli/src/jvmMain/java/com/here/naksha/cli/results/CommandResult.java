package com.here.naksha.cli.results;

/**
 * @param <T> type of {@link CommandSuccess}'s payload
 * @param <S> type of {@link CommandFailure}'s payload
 */
public sealed interface CommandResult<T, S> permits CommandSuccess, CommandFailure {
}