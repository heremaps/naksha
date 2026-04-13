package com.here.naksha.cli.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class JsonParserException extends Exception {
    JsonParserException(@NotNull String message, @NotNull Path path, @Nullable Throwable cause) {
        super(message + " file: " + path, cause);
    }

    JsonParserException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    JsonParserException(@NotNull JsonParserException e, @NotNull Path path) {
        this(e.getMessage(), path, e.getCause());
    }

    JsonParserException(@NotNull String message, @NotNull Path path) {
        this(message, path, null);
    }
}
