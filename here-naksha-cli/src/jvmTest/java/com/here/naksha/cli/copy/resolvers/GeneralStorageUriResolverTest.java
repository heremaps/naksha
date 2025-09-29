package com.here.naksha.cli.copy.resolvers;

import com.here.naksha.cli.storages.GeneratingStorageConfig;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneralStorageUriResolverTest {
    private final GeneralStorageUriResolver resolver = new GeneralStorageUriResolver();

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://localhost:100/db?user=u&password=p"})
    void shouldResolvePostgres(String rawUrl) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUrl);

        // When
        NakshaStorage nakshaStorage = resolver.resolve(uri);

        // Then
        assertInstanceOf(PgConfig.class, nakshaStorage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gen://100:pref?tileIds=012,33210"})
    void shouldResolveGenerating(String rawUrl) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUrl);

        // When
        NakshaStorage nakshaStorage = resolver.resolve(uri);

        // Then
        assertInstanceOf(GeneratingStorageConfig.class, nakshaStorage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://here.com", "jdbc:https://here.com"})
    void shouldThrowWhenUnexpectedProtocol(String rawUrl) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUrl);

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"//localhost:100/db?user=u&password=p", "gen//100:pref?tileIds=012,33210"})
    void shouldThrowWhenProtocolAbsent(String rawUrl) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUrl);

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }
}