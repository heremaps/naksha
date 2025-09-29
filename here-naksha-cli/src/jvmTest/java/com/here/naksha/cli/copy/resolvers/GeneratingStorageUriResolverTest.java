package com.here.naksha.cli.copy.resolvers;

import com.here.naksha.cli.storages.GeneratingStorageConfig;
import com.here.naksha.cli.storages.GeneratingStorageConfigProperties;
import naksha.base.StringList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GeneratingStorageUriResolverTest {
    private final GeneratingStorageUriResolver resolver = new GeneratingStorageUriResolver();

    @ParameterizedTest
    @MethodSource
    void shouldResolve(
            URI uri, int expectedCount, String expectedIdsPrefix, StringList expectedTileIds
    ) {
        // When
        GeneratingStorageConfig config = resolver.resolve(uri);
        GeneratingStorageConfigProperties properties = config.getProperties();

        // Then
        assertEquals(expectedCount, properties.getCount());
        assertEquals(expectedIdsPrefix, properties.getIdsPrefix());
        assertIterableEquals(expectedTileIds, properties.getTileIds());
    }

    @ParameterizedTest
    @MethodSource
    void shouldResolveWithoutIdsPrefix(
            URI uri, int expectedCount, StringList expectedTileIds
    ) {
        // When
        GeneratingStorageConfig config = resolver.resolve(uri);
        GeneratingStorageConfigProperties properties = config.getProperties();

        // Then
        assertEquals(expectedCount, properties.getCount());
        assertIterableEquals(expectedTileIds, properties.getTileIds());
        assertNull(properties.getIdsPrefix());
    }

    @Test
    void shouldThrowWhenCountIsNotInteger() throws URISyntaxException {
        // Given
        URI uri = new URI("gen://count:test?tileIds=0132102,230");

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gen://:pref?tileIds=0132102,230", "gen://?tileIds=0132102,230"})
    void shouldThrowWhenCountIsNotProvided(String rawUri) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUri);

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gen://-10:pref?tileIds=0132102,230", "gen://0?tileIds=0132102,230"})
    void shouldThrowWhenCountIsNotPositiveInteger(String rawUri) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUri);

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gen://100:pref:pref?tileIds=0132102,230"})
    void shouldThrowWhenInvalidAuthority(String rawUri) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUri);

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gen://100:pref", "gen://100:pref?tileIds", "gen://100:pref?tileIds="})
    void shouldThrowWhenTileIdsAbsent(String rawUri) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUri);

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gen://100:pref?tileIds=021324", "gen://100:pref?tileIds=tileId", "gen://100:pref?tileIds=4,5"})
    void shouldThrowWhenTileIdsAreNotQuadKeys(String rawUri) throws URISyntaxException {
        // Given
        URI uri = new URI(rawUri);

        // When & Then
        assertThrows(StorageUriResolverException.class, () -> resolver.resolve(uri));
    }

    private static Stream<Arguments> shouldResolve() throws URISyntaxException {
        return Stream.of(
                // uri, expectedCount, expectedIdsPrefix, expectedTileIds
                Arguments.of(
                        new URI("gen://100:test?tileIds=0132102,230"),
                        100,
                        "test",
                        StringList.of("0132102", "230")
                ),
                Arguments.of(
                        new URI("gen://2137:genPref?tileIds=0132102,230,3213210102030"),
                        2137,
                        "genPref",
                        StringList.of("0132102", "230", "3213210102030")
                )
        );
    }

    private static Stream<Arguments> shouldResolveWithoutIdsPrefix() throws URISyntaxException {
        return Stream.of(
                // uri, expectedCount, expectedTileIds
                Arguments.of(
                        new URI("gen://100?tileIds=0132102,230"),
                        100,
                        StringList.of("0132102", "230")
                ),
                Arguments.of(
                        new URI("gen://2137?tileIds=0132102,230,3213210102030"),
                        2137,
                        StringList.of("0132102", "230", "3213210102030")
                )
        );
    }
}