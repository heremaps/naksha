package com.here.naksha.cli;

import picocli.CommandLine;

public final class TestUtils {
    public static final int SUCCESS_EXIT_CODE = CommandLine.ExitCode.OK;
    public static final int INVALID_INPUT_EXIT_CODE = CommandLine.ExitCode.USAGE;
    public static final int EXECUTION_EXCEPTION_EXIT_CODE = CommandLine.ExitCode.SOFTWARE;

    private TestUtils() {
    }
}

