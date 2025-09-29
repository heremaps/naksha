package com.here.naksha.cli.copy.resolvers;

import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;

public class GeneralStorageUriResolver implements StorageUriResolver {
    @Override
    public @NotNull NakshaStorage resolve(@NotNull URI uri) {
        String protocol = uri.getScheme();
        if (protocol == null) {
            throw new StorageUriResolverException("Protocol should be provided!", uri);
        }
        StorageUriResolver resolver = switch (protocol) {
            case "jdbc" -> resolveJdbc(uri);
            case "gen" -> new GeneratingStorageUriResolver();
            default -> throw new StorageUriResolverException("Unexpected protocol!", uri);
        };
        return resolver.resolve(uri);
    }

    private StorageUriResolver resolveJdbc(URI uri) {
        uri = cutScheme(uri);
        String protocol = uri.getScheme();
        if ("postgresql".equals(protocol)) {
            return new PostgresStorageUriResolver();
        } else {
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
