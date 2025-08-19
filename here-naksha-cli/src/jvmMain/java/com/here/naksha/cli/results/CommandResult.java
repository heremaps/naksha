package com.here.naksha.cli.results;

public sealed interface CommandResult<T, S> permits CommandSuccess, CommandFailure {
}