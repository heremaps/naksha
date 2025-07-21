package com.here.naksha.cli;

import picocli.CommandLine;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

public final class TestUtils {
    public static final int SUCCESS_EXIT_CODE = CommandLine.ExitCode.OK;
    public static final int INVALID_INPUT_EXIT_CODE = CommandLine.ExitCode.USAGE;
    public static final int EXECUTION_EXCEPTION_EXIT_CODE = CommandLine.ExitCode.SOFTWARE;

    public static String getAbsolutePathOfResource(String resourcePath) throws IOException {
        URL resourceUrl = TestUtils.class.getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        try {
            return Paths.get(resourceUrl.toURI()).toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI syntax for resource: " + resourcePath, e);
        }
    }

    private TestUtils() {
    }
}