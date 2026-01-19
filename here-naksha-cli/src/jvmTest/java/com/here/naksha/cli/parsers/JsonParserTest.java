package com.here.naksha.cli.parsers;

import com.here.naksha.cli.utils.JsonParser;
import com.here.naksha.cli.utils.JsonParserException;
import naksha.base.AnyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JsonParserTest {
    @Test
    void shouldFailWithFileDoesNotExist(@TempDir Path dir) {
        // Given
        Path pathToFile = dir.resolve("NoExist");
        JsonParser jsonParser = new JsonParser();

        // When & Then
        assertThrows(
                JsonParserException.class,
                () -> jsonParser.readAndParse(pathToFile, AnyObject.class)
        );
    }

    @Test
    void shouldFailWithNoReadable(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, "{}");
        File file = pathToFile.toFile();
        assumeTrue(file.setReadable(false), "Can not set file as unreadable!");
        JsonParser jsonParser = new JsonParser();

        // When & Then
        assertThrows(
                JsonParserException.class,
                () -> jsonParser.readAndParse(pathToFile, AnyObject.class)
        );
    }

    @Test
    void shouldFailWithItIsNoFile(@TempDir Path dir) {
        // Given
        JsonParser jsonParser = new JsonParser();

        // When & Then
        assertThrows(
                JsonParserException.class,
                () -> jsonParser.readAndParse(dir, AnyObject.class)
        );
    }

    @Test
    void shouldFailWithJsonParsingProblem(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, """
                {
                    : "test"
                }
                """);
        JsonParser jsonParser = new JsonParser();

        // When & Then
        assertThrows(
                JsonParserException.class,
                () -> jsonParser.readAndParse(pathToFile, AnyObject.class)
        );
    }

    @Test
    void shouldFailWithCannotBeBoxed(@TempDir Path dir) throws IOException {
        // Given: file with PlatformList
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, "[1, 2]");
        JsonParser jsonParser = new JsonParser();

        // When: parsing to proxy for PlatformMap & Then: throw
        JsonParserException exception = assertThrows(
                JsonParserException.class,
                () -> jsonParser.readAndParse(pathToFile, AnyObject.class)
        );
        assertEquals("Cannot be boxed! file: %s".formatted(pathToFile), exception.getMessage());
    }

    @Test
    void shouldProvideObject(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, """
                {
                    "key": "value"
                }
                """);
        JsonParser jsonParser = new JsonParser();

        // When
        AnyObject object = assertDoesNotThrow(() -> jsonParser.readAndParse(pathToFile, AnyObject.class));

        // Then: key and value are present
        assertNotNull(object);
        assertEquals("value", object.get("key"));
    }
}