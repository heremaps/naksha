package com.here.naksha.cli.results;

public record CommandSuccess<T, S>(T payload) implements CommandResult<T, S> {
}
