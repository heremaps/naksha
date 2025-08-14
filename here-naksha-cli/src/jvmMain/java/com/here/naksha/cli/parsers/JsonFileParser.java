package com.here.naksha.cli.parsers;

import naksha.base.Platform;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;
import static naksha.base.Platform.forClass;

public final class JsonFileParser {
    @NotNull
    public <T> T parse(@NotNull Path path, @NotNull Class<T> clazz) throws JsonFileParserException {
        requireFileExists(path);
        requireIsRegularFile(path);
        String json = readFile(path);
        return requireNonNull(Platform.fromJson(json, forClass(clazz)));
    }

    private void requireFileExists(Path path) throws JsonFileParserException {
        if (!Files.exists(path)) {
            throw new JsonFileParserException("File does not exist!", path);
        }
    }

    private void requireIsRegularFile(Path path) throws JsonFileParserException {
        if (!Files.isRegularFile(path)) {
            throw new JsonFileParserException("It is not a file!", path);
        }
    }

    private String readFile(Path path) throws JsonFileParserException {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new JsonFileParserException("Problem with reading!", path, e);
        }
    }
}
