package com.here.naksha.cli.parsers;

import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class JsonFileParser {
    @NotNull
    public <T> T parse(@NotNull Path path, @NotNull Class<T> clazz) throws JsonFileParserException {
        try {
            requireFileExists(path);
            requireIsRegularFile(path);
            String json = readFile(path);
            Object raw = parseJsonToObject(json);
            return box(raw, clazz);
        } catch (JsonFileParserException exception) {
            throw new JsonFileParserException(exception.getMessage(), path, exception.getCause());
        }
    }

    private void requireFileExists(Path path) throws JsonFileParserException {
        if (!Files.exists(path)) {
            throw new JsonFileParserException("File does not exist!");
        }
    }

    private void requireIsRegularFile(Path path) throws JsonFileParserException {
        if (!Files.isRegularFile(path)) {
            throw new JsonFileParserException("It is not a file!");
        }
    }

    private String readFile(Path path) throws JsonFileParserException {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new JsonFileParserException("Problem with reading!", e);
        }
    }

    private Object parseJsonToObject(String json) throws JsonFileParserException {
        try {
            return requireNonNull(Platform.fromJSON(json));
        } catch (Exception e) {
            throw new JsonFileParserException("Problem with json parsing!", e);
        }
    }

    private <T> T box(Object raw, Class<T> clazz) throws JsonFileParserException {
        try {
            return requireNonNull(JvmBoxingUtil.box(raw, clazz));
        } catch (Exception e) {
            throw new JsonFileParserException("Cannot be boxed!", e);
        }
    }
}
