package com.here.naksha.cli.stream.providers;

import naksha.model.IStorage;
import naksha.model.IStorageConfigBuilder;
import naksha.model.IStorageProvider;
import naksha.model.IStreamSession;
import naksha.model.SessionOptions;
import naksha.model.streaming.AbstractStreamSession;
import naksha.model.streaming.StreamChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NullStorageProvider implements IStorageProvider {
    @Override
    public @NotNull String getName() {
        return "null";
    }

    @Override
    public @NotNull String getDescription() {
        return "A target that throws the data away.";
    }

    @Override
    public @NotNull IStorageConfigBuilder newConfigBuilder() {
        return new NoQuestions();
    }

    @Override
    public @NotNull IStorage getStorage(@NotNull String config) {
        return StreamOnlyStorage.use(getName(), NullStorage.class, config);
    }

    public static final class NullStorage extends StreamOnlyStorage {
        @Override
        public @NotNull IStreamSession newStreamSession(@Nullable SessionOptions options) {
            return new NullStreamSession(this, orDefault(options));
        }
    }

    static final class NullStreamSession extends AbstractStreamSession {
        NullStreamSession(IStorage storage, SessionOptions options) {
            super(storage, options);
        }

        @Override
        public boolean getMayWrite() {
            return true;
        }

        @Override
        public void write(@NotNull StreamChunk chunk) {
            chunk.acknowledge();
        }
    }
}
