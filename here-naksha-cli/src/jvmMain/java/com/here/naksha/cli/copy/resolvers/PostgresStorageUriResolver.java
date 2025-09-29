package com.here.naksha.cli.copy.resolvers;

import naksha.psql.PgConfig;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

class PostgresStorageUriResolver implements StorageUriResolver {
    @Override
    public @NotNull PgConfig resolve(@NotNull URI uri) {
        try {
            PgConfig pgConfig = new PgConfig("psql_storage")
                    .withMasterUri(uri.toString());
            pgConfig.withCreate(true);
            return pgConfig;
        } catch (Exception e) {
            throw new StorageUriResolverException("An error occurred!", uri, e);
        }
    }
}
