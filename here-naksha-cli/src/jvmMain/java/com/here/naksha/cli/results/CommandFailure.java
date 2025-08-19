package com.here.naksha.cli.results;

public record CommandFailure<T, S>(S payload) implements CommandResult<T, S> {
}
