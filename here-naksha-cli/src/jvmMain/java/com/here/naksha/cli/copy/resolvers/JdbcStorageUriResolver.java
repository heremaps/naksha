package com.here.naksha.cli.copy.resolvers;

import naksha.psql.PgConfig;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;

final class JdbcStorageUriResolver implements StorageUriResolver {
    @Override
    public @NotNull PgConfig resolve(@NotNull URI uri) {
        requirePostgres(uri);
        try {
            PgConfig pgConfig = new PgConfig("psql_storage")
                    .withMasterUri(uri.toString());
            pgConfig.withCreate(true);
            return pgConfig;
        } catch (Exception e) {
            throw new StorageUriResolverException("An error occurred!", uri, e);
        }
    }

    private void requirePostgres(URI uri) {
        uri = cutScheme(uri);
        String protocol = uri.getScheme();
        if (!"postgresql".equals(protocol)) {
            throw new StorageUriResolverException("Unexpected protocol!", uri);
        }
    }

    private URI cutScheme(URI uri) {
        String schemeSpecificPart = uri.getSchemeSpecificPart();
        try {
            return new URI(schemeSpecificPart);
        } catch (URISyntaxException e) {
            throw new StorageUriResolverException("An error occurred when resolving URI!", uri, e);
        }
    }
}
