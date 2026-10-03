package com.here.naksha.cli.stream.providers;

import com.here.naksha.cli.stream.StreamProvider;
import naksha.model.IStreamSession;
import naksha.model.SessionOptions;
import naksha.model.streaming.AbstractStreamSession;
import naksha.model.streaming.StreamChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A target that acknowledges every chunk and throws the data away, like {@code /dev/null}. Needs no configuration.
 */
public final class NullStreamProvider implements StreamProvider {
    @Override
    public @NotNull String name() {
        return "null";
    }

    @Override
    public @NotNull String description() {
        return "A target that acknowledges every chunk and throws the data away.";
    }

    @Override
    public @NotNull IStreamSession open(@Nullable Path config, @NotNull SessionOptions options) {
        return new NullStreamSession(options);
    }

    static final class NullStreamSession extends AbstractStreamSession {
        NullStreamSession(SessionOptions options) {
            super(null, options);
        }

        @Override
        public boolean getMayWrite() {
            return true;
        }

        @Override
        public boolean write(@NotNull StreamChunk chunk) {
            chunk.acknowledge();
            return true;
        }
    }
}
