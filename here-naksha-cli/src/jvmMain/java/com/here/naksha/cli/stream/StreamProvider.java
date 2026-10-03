package com.here.naksha.cli.stream;

import naksha.model.IStreamSession;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/** An import or export implementation, registered in {@code META-INF/services} and selected by {@link #name()}. */
public interface StreamProvider {
    @NotNull String name();

    @NotNull String description();

    @NotNull IStreamSession open(@Nullable Path config, @NotNull SessionOptions options);
}
