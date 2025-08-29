package com.here.naksha.cli.parsers;

import naksha.base.AnyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JsonFileParserTest {
    @Test
    void shouldFailWithFileDoesNotExist(@TempDir Path dir) {
        // Given
        Path pathToFile = dir.resolve("NoExist");
        JsonFileParser jsonFileParser = new JsonFileParser();

        // When & Then
        JsonFileParserException exception = assertThrows(
                JsonFileParserException.class,
                () -> jsonFileParser.parse(pathToFile, AnyObject.class)
        );
        assertEquals("Problem with reading! NoSuchFileException file: %s".formatted(pathToFile), exception.getMessage());
    }

    @Test
    void shouldFailWithNoReadable(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, "{}");
        File file = pathToFile.toFile();
        assumeTrue(file.setReadable(false), "Can not set file as unreadable!");
        JsonFileParser jsonFileParser = new JsonFileParser();

        // When & Then
        JsonFileParserException exception = assertThrows(
                JsonFileParserException.class,
                () -> jsonFileParser.parse(pathToFile, AnyObject.class)
        );
        assertEquals("Problem with reading! AccessDeniedException file: %s".formatted(pathToFile), exception.getMessage());
    }

    @Test
    void shouldFailWithItIsNoFile(@TempDir Path dir) {
        // Given
        JsonFileParser jsonFileParser = new JsonFileParser();

        // When & Then
        JsonFileParserException exception = assertThrows(
                JsonFileParserException.class,
                () -> jsonFileParser.parse(dir, AnyObject.class)
        );
        assertEquals("Problem with reading! Is a directory file: %s".formatted(dir), exception.getMessage());
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
        JsonFileParser jsonFileParser = new JsonFileParser();

        // When & Then
        JsonFileParserException exception = assertThrows(
                JsonFileParserException.class,
                () -> jsonFileParser.parse(pathToFile, AnyObject.class)
        );
        assertEquals("Problem with json parsing! file: %s".formatted(pathToFile), exception.getMessage());
    }

    @Test
    void shouldFailWithCannotBeBoxed(@TempDir Path dir) throws IOException {
        // Given: file with PlatformList
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, "[1, 2]");
        JsonFileParser jsonFileParser = new JsonFileParser();

        // When: parsing to proxy for PlatformMap & Then: throw
        JsonFileParserException exception = assertThrows(
                JsonFileParserException.class,
                () -> jsonFileParser.parse(pathToFile, AnyObject.class)
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
        JsonFileParser jsonFileParser = new JsonFileParser();

        // When
        AnyObject object = assertDoesNotThrow(() -> jsonFileParser.parse(pathToFile, AnyObject.class));

        // Then: key and value are present
        assertNotNull(object);
        assertEquals("value", object.get("key"));
    }
}