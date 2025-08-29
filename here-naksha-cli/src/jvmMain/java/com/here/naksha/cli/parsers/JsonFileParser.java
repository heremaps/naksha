package com.here.naksha.cli.parsers;

import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class JsonFileParser {
    @NotNull
    public <T> T parse(@NotNull Path path, @NotNull Class<T> clazz) throws JsonFileParserException {
        String json = readFile(path);
        Object raw = parseJsonToObject(json, path);
        return box(raw, clazz, path);
    }

    private String readFile(Path path) throws JsonFileParserException {
        try {
            return Files.readString(path);
        } catch (FileSystemException e) {
            throw new JsonFileParserException("Problem with reading! " + e.getClass().getSimpleName(), path);
        } catch (IOException e) {
            throw new JsonFileParserException("Problem with reading! " + e.getMessage(), path);
        } catch (Exception e) {
            throw new JsonFileParserException("Problem with reading!", path, e);
        }
    }

    private Object parseJsonToObject(String json, Path path) throws JsonFileParserException {
        try {
            return requireNonNull(Platform.fromJSON(json));
        } catch (Exception e) {
            throw new JsonFileParserException("Problem with json parsing!", path, e);
        }
    }

    private <T> T box(Object raw, Class<T> clazz, Path path) throws JsonFileParserException {
        try {
            return requireNonNull(JvmBoxingUtil.box(raw, clazz));
        } catch (Exception e) {
            throw new JsonFileParserException("Cannot be boxed!", path, e);
        }
    }
}
