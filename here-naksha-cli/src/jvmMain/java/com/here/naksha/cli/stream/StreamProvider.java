package com.here.naksha.cli.stream;

import naksha.model.IStreamSession;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * An import or export implementation, discovered using {@link java.util.ServiceLoader} and selected by {@link #name()}.
 * Implementations are registered in {@code META-INF/services/com.here.naksha.cli.stream.StreamProvider}.
 */
public interface StreamProvider {
    /** The name used in the CLI arguments, for example {@code random} or {@code null}. */
    @NotNull String name();

    /** A short description, printed by {@code stream-copy --list}. */
    @NotNull String description();

    /**
     * Opens a stream session.
     *
     * @param config the configuration file, {@code null} if none was given.
     * @param options the session options.
     */
    @NotNull IStreamSession open(@Nullable Path config, @NotNull SessionOptions options);
}
