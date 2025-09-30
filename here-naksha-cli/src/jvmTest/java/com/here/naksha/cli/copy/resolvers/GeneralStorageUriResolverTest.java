package com.here.naksha.cli.copy.resolvers;

import com.here.naksha.cli.storages.GeneratingStorageConfig;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneralStorageUriResolverTest {
    private final GeneralStorageUriResolver resolver = new GeneralStorageUriResolver();

    @ParameterizedTest
    @MethodSource("uriArgs")
    void shouldResolvePostgres(String rawUri, Class<? extends NakshaStorage> clazz) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUri);

        // When
        NakshaStorage nakshaStorage = resolver.resolve(uri);

        // Then
        assertInstanceOf(clazz, nakshaStorage);
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

    private static Stream<Arguments> uriArgs() {
        return Stream.of(
                // uri, CONFIG_CLASS
                Arguments.of(
                        "gen://100:pref?tileIds=012,33210",
                        GeneratingStorageConfig.class
                ),
                Arguments.of(
                        "jdbc:postgresql://localhost:100/db?user=u&password=p",
                        PgConfig.class
                )
        );
    }
}