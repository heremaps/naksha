package com.here.naksha.cli.copy;

import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NakshaStorageProviderTest {
    @Test
    void shouldFailWithFileDoesNotExist(@TempDir Path dir) {
        // Given
        Path pathToFile = dir.resolve("NoExist");
        File file = pathToFile.toFile();
        NakshaStorageProvider nakshaStorageProvider = new NakshaStorageProvider();

        // When & Then
        assertThatThrownBy(() -> nakshaStorageProvider.get(file))
                .isInstanceOf(NakshaStorageProviderException.class)
                .hasMessage("File does not exist! file: %s".formatted(file.getPath()));
    }

    @Test
    void shouldFailWithNoReadable(@TempDir Path dir) throws IOException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, "{}");
        File file = pathToFile.toFile();
        assertTrue(file.setReadable(false), "Can not set file as unreadable!");
        NakshaStorageProvider nakshaStorageProvider = new NakshaStorageProvider();

        // When & Then
        assertThatThrownBy(() -> nakshaStorageProvider.get(file))
                .isInstanceOf(NakshaStorageProviderException.class)
                .hasMessage("Problem with reading! file: %s".formatted(file.getPath()));
    }

    @Test
    void shouldFailWithItIsNoFile(@TempDir Path dir) throws IOException {
        // Given
        File file = dir.toFile();
        NakshaStorageProvider nakshaStorageProvider = new NakshaStorageProvider();

        // When & Then
        assertThatThrownBy(() -> nakshaStorageProvider.get(file))
                .isInstanceOf(NakshaStorageProviderException.class)
                .hasMessage("It is not a file! file: %s".formatted(file.getPath()));
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
        NakshaStorageProvider nakshaStorageProvider = new NakshaStorageProvider();

        // When & Then
        assertThatThrownBy(() -> nakshaStorageProvider.get(file))
                .isInstanceOf(NakshaStorageProviderException.class)
                .hasMessage("Problem with json parsing! file: %s".formatted(file.getPath()));
    }

    @Test
    void shouldWork(@TempDir Path dir) throws IOException, NakshaStorageProviderException {
        // Given
        Path pathToFile = dir.resolve("file");
        Files.writeString(pathToFile, """
                {
                    "key": "value"
                }
                """);
        File file = pathToFile.toFile();
        NakshaStorageProvider nakshaStorageProvider = new NakshaStorageProvider();

        // When
        NakshaStorage nakshaStorage = nakshaStorageProvider.get(file);

        // Then
        assertThat(nakshaStorage)
                .isNotNull();
    }
}