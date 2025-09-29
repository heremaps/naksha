package com.here.naksha.cli.copy.resolvers;

import java.net.URI;

public class StorageUriResolverException extends RuntimeException {
    public StorageUriResolverException(String message, URI uri, Throwable cause) {
        super(message + " URI: " + uri, cause);
    }

    public StorageUriResolverException(String message, URI uri) {
        super(message + " URI: " + uri);
    }

    public StorageUriResolverException(String message, URI uri, String expectedUriFormat) {
        super(message + " URI: " + uri + " Expected format: " + expectedUriFormat);
    }
}
