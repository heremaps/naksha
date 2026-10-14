package com.here.naksha.cli.stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** What the provider, config, stream and sync commands share. */
public final class CliContext {
    public final @NotNull StorageProviders providers;
    public final @NotNull Store<StorageConfig> configs;
    public final @NotNull Store<StreamDefinition> streams;
    private final @NotNull BufferedReader in;
    private final @Nullable Console console;

    public CliContext(@NotNull StorageProviders providers, @NotNull Path home, @NotNull BufferedReader in, @Nullable Console console) {
        this.providers = providers;
        this.configs = new JsonFileStore<>(home.resolve("config"), StorageConfig::toJson, StorageConfig::fromJson);
        this.streams = new JsonFileStore<>(home.resolve("stream"), StreamDefinition::toJson, StreamDefinition::fromJson);
        this.in = in;
        this.console = console;
    }

    /** Uses {@code ~/.config/naksha/cli}, which can be changed with the environment variable {@code NAKSHA_CLI_HOME}. */
    public static @NotNull CliContext load() {
        String home = System.getenv("NAKSHA_CLI_HOME");
        Path path = home != null && !home.isBlank()
                ? Path.of(home)
                : Path.of(System.getProperty("user.home"), ".config", "naksha", "cli");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return new CliContext(StorageProviders.load(), path, in, System.console());
    }

    @NotNull Questions questions(@NotNull PrintWriter out) {
        return new Questions(in, out, console);
    }
}
