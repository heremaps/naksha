package com.here.naksha.cli.stream;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/** Named entities, like storage configurations or streams. The CLI keeps them in files, Naksha-Hub could keep them in its admin database. */
public interface Store<T> {
    /** The names of all entities, sorted. */
    @NotNull List<String> names();

    @NotNull Optional<T> get(@NotNull String name);

    void save(@NotNull String name, @NotNull T value);

    boolean delete(@NotNull String name);
}
