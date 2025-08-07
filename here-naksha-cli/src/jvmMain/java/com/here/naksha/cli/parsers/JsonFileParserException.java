package com.here.naksha.cli.parsers;

import java.nio.file.Path;

public final class JsonFileParserException extends Exception {
    JsonFileParserException(String message, Path path, Throwable cause) {
        super(message + " file: " + path, cause);
    }

    JsonFileParserException(String message, Path path) {
        super(message + " file: " + path);
    }
}
