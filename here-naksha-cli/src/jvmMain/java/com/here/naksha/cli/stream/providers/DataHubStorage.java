package com.here.naksha.cli.stream.providers;

import naksha.model.IStreamSession;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** In Java, because a Kotlin subclass of {@link StreamOnlyStorage} clashes on {@code getHardCap()}. */
public final class DataHubStorage extends StreamOnlyStorage {
    private volatile DataHubConfig config;
    private volatile DataHubClient client;

    @Override
    protected void init(@NotNull NakshaStorage storage) {
        config = DataHubConfig.from(storage);
        client = new DataHubClient(config);
    }

    @Override
    public @NotNull IStreamSession newStreamSession(@Nullable SessionOptions options) {
        return new DataHubStreamSession(this, client, config, orDefault(options));
    }
}
