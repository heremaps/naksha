package com.here.naksha.cli.utils;

import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class JsonParser {
    @NotNull
    public <T> T readAndParse(@NotNull Path path, @NotNull Class<T> clazz) throws JsonParserException {
        try {
            String json = readFile(path);
            return parse(json, clazz);
        } catch (JsonParserException e) {
            throw new JsonParserException(e, path);
        }
    }

    public <T> T parse(@NotNull String json, @NotNull Class<T> clazz) throws JsonParserException {
        Object raw = parseJsonToObject(json);
        return box(raw, clazz);
    }

    private String readFile(Path path) throws JsonParserException {
        try {
            return Files.readString(path);
        } catch (FileSystemException e) {
            throw new JsonParserException("Problem with reading! " + e.getClass().getSimpleName(), path);
        } catch (IOException e) {
            throw new JsonParserException("Problem with reading! " + e.getMessage(), path);
        } catch (Exception e) {
            throw new JsonParserException("Problem with reading!", path, e);
        }
    }

    private Object parseJsonToObject(String json) throws JsonParserException {
        try {
            return requireNonNull(Platform.fromJSON(json));
        } catch (Exception e) {
            throw new JsonParserException("Problem with json parsing!", e);
        }
    }

    private <T> T box(Object raw, Class<T> clazz) throws JsonParserException {
        try {
            return requireNonNull(JvmBoxingUtil.box(raw, clazz));
        } catch (Exception e) {
            throw new JsonParserException("Cannot be boxed!", e);
        }
    }
}
