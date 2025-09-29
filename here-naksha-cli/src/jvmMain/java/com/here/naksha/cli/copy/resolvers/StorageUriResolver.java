package com.here.naksha.cli.copy.resolvers;

import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

interface StorageUriResolver {
    @NotNull
    NakshaStorage resolve(@NotNull URI uri);
}
