package com.here.naksha.cli.results;

public record FailureResult<T, S>(S payload) implements Result<T, S> {
}
