package com.here.naksha.cli;

import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public final class TestUtils {
    public static final int SUCCESS_EXIT_CODE = CommandLine.ExitCode.OK;
    public static final int INVALID_INPUT_EXIT_CODE = CommandLine.ExitCode.USAGE;
    public static final int EXECUTION_EXCEPTION_EXIT_CODE = CommandLine.ExitCode.SOFTWARE;

    public static String readStringFromResource(String resourcePath) throws IOException {
        try (InputStream in = TestUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

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

