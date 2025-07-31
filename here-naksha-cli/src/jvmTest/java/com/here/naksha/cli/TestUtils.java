package com.here.naksha.cli;

import org.junit.jupiter.api.Assertions;
import picocli.CommandLine;

import java.net.URL;
import java.nio.file.Paths;

public final class TestUtils {
    public static final int SUCCESS_EXIT_CODE = CommandLine.ExitCode.OK;
    public static final int INVALID_INPUT_EXIT_CODE = CommandLine.ExitCode.USAGE;
    public static final int EXECUTION_EXCEPTION_EXIT_CODE = CommandLine.ExitCode.SOFTWARE;

    public static String getAbsolutePathOfResource(String resourcePath) {
        URL resourceUrl = TestUtils.class.getClassLoader().getResource(resourcePath);
        Assertions.assertNotNull(resourceUrl);
        return Assertions.assertDoesNotThrow(() -> Paths.get(resourceUrl.toURI()).toAbsolutePath().toString());
    }

    private TestUtils() {
    }
}