package com.here.naksha.cli.parsers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class JsonFileParserException extends Exception {
    JsonFileParserException(@NotNull String message, @NotNull Path path, @Nullable Throwable cause) {
        super(message + " file: " + path, cause);
    }

    JsonFileParserException(@NotNull String message, @NotNull Path path) {
        super(message + " file: " + path);
    }

    JsonFileParserException(@NotNull String message) {
        super(message);
    }

    JsonFileParserException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
