package com.here.naksha.cli.results;

public record ErrorResult<T, S>(S payload) implements IResult<T, S> {
}
