package com.here.naksha.cli.results;

public record SuccessResult<T, S>(T payload) implements IResult<T, S> {
}
