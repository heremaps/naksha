package com.here.naksha.cli.copy;

import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NakshaStorageParserTest {
    @Test
    void shouldFailWithFileDoesNotExist(@TempDir Path dir) {
        // Given
        Path pathToFile = dir.resolve("NoExist");
        File file = pathToFile.toFile();
        NakshaStorageParser nakshaStorageParser = new NakshaStorageParser();

        // When & Then
        NakshaStorageParserException exception = assertThrows(NakshaStorageParserException.class, () -> {
            nakshaStorageParser.get(file);
        });
        assertEquals("File does not exist! file: %s".formatted(file.getPath()), exception.getMessage());
    }

    @Test
    void shouldFailWithNoReadable(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, "{}");
        File file = pathToFile.toFile();
        assertTrue(file.setReadable(false), "Can not set file as unreadable!");
        NakshaStorageParser nakshaStorageParser = new NakshaStorageParser();

        // When & Then
        NakshaStorageParserException exception = assertThrows(NakshaStorageParserException.class, () -> {
            nakshaStorageParser.get(file);
        });
        assertEquals("Problem with reading! file: %s".formatted(file.getPath()), exception.getMessage());
    }

    @Test
    void shouldFailWithItIsNoFile(@TempDir Path dir) {
        // Given
        File file = dir.toFile();
        NakshaStorageParser nakshaStorageParser = new NakshaStorageParser();

        // When & Then
        NakshaStorageParserException exception = assertThrows(NakshaStorageParserException.class, () -> {
            nakshaStorageParser.get(file);
        });
        assertEquals("It is not a file! file: %s".formatted(file.getPath()), exception.getMessage());
    }

    @Test
    void shouldFailWithJsonParsing(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, """
                {
                    : "test"
                }
                """);
        File file = pathToFile.toFile();
        NakshaStorageParser nakshaStorageParser = new NakshaStorageParser();

        // When & Then
        NakshaStorageParserException exception = assertThrows(NakshaStorageParserException.class, () -> {
            nakshaStorageParser.get(file);
        });
        assertEquals("Problem with json parsing! file: %s".formatted(file.getPath()), exception.getMessage());
    }

    @Test
    void shouldProvideStorage(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, """
                {
                    "key": "value"
                }
                """);
        File file = pathToFile.toFile();
        NakshaStorageParser nakshaStorageParser = new NakshaStorageParser();

        // When
        NakshaStorage nakshaStorage = assertDoesNotThrow(() -> nakshaStorageParser.get(file));

        // Then
        assertNotNull(nakshaStorage);
    }
}