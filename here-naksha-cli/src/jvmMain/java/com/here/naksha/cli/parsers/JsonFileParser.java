package com.here.naksha.cli.parsers;

import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public final class JsonFileParser {
    public <T> T parse(Path path, Class<T> clazz) throws JsonFileParserException {
        if (!Files.exists(path)) {
            throw new JsonFileParserException("File does not exist!", path);
        }

        if (!Files.isRegularFile(path)) {
            throw new JsonFileParserException("It is not a file!", path);
        }

        String json;

        try {
            json = Files.readString(path);
        } catch (Exception e) {
            throw new JsonFileParserException("Problem with reading!", path, e);
        }

        Object raw;

        try {
            raw = Platform.fromJSON(json);
        } catch (Exception e) {
            throw new JsonFileParserException("Problem with json parsing!", path, e);
        }

        try {
            return requireNonNull(JvmBoxingUtil.box(raw, clazz));
        } catch (Exception e) {
            throw new JsonFileParserException("Cannot be boxed!", path, e);
        }
    }
}
