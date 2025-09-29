package com.here.naksha.cli.copy.resolvers;

import naksha.psql.PgConfig;
import naksha.psql.PgInstanceConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresStorageUriResolverTest {
    private final PostgresStorageUriResolver resolver = new PostgresStorageUriResolver();
    private final String host = "localhost";
    private final int port = 100;
    private final String db = "db";
    private final String user = "user";
    private final String password = "pass";

    @Test
    void shouldParse() throws URISyntaxException {
        // Given
        URI uri = new URI("jdbc:postgresql://%s:%s/%s?user=%s&password=%s".formatted(host, port, db, user, password));

        // When
        PgConfig config = resolver.resolve(uri);
        PgInstanceConfig instanceConfig = config.getMaster();

        // Then
        assertTrue(config.getCreate());
        assertEquals(host, instanceConfig.getHost());
        assertEquals(port, instanceConfig.getPort());
        assertEquals(db, instanceConfig.getDb());
        assertEquals(user, instanceConfig.getUser());
        assertEquals(password, instanceConfig.getPassword());
    }
}