package com.here.naksha.cli.results;

public sealed interface IResult<T, S> permits SuccessResult, ErrorResult {
}