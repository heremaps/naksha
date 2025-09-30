package com.here.naksha.cli.copy.resolvers;

import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

public final class GeneralStorageUriResolver implements StorageUriResolver {
    private final StorageUriResolver jdbcResolver = new JdbcStorageUriResolver();
    private final StorageUriResolver generatingResolver = new GeneratingStorageUriResolver();

    @Override
    public @NotNull NakshaStorage resolve(@NotNull URI uri) {
        String protocol = uri.getScheme();
        if (protocol == null) {
            throw new StorageUriResolverException("Protocol should be provided!", uri);
        }
        StorageUriResolver resolver = switch (protocol) {
            case "jdbc" -> jdbcResolver;
            case "gen" -> generatingResolver;
            default -> throw new StorageUriResolverException("Unexpected protocol!", uri);
        };
        return resolver.resolve(uri);
    }
}
