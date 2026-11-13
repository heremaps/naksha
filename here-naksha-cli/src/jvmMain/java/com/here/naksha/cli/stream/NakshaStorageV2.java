package com.here.naksha.cli.stream;

import com.here.naksha.cli.stream.providers.StreamOnlyStorage;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * Providing compatible storage for Naksha V2, used for {@link naksha.model.streaming.Stream} (data migration,...).
 * <p>
 * Implemented in Java to avoid a Kotlin-Java-Kotlin inheritance clash.
 * {@link StreamOnlyStorage#getHardCap()} overrides the getter for
 * {@code hardCap} in {@link naksha.model.AbstractStorage}.
 * A Kotlin subclass inherits conflicting declarations with the same
 * JVM signature: {@code getHardCap()I}.
 */
public final class NakshaStorageV2 extends StreamOnlyStorage {
    @Override
    public @NotNull IStreamSession newStreamSession(
            @Nullable SessionOptions options) {
        SessionOptions opts = options != null
                ? options
                : SessionOptions.from(NakshaContext.currentContext());

        return new StreamReadSessionV2(
                this,
                opts.socketTimeout,
                opts.stmtTimeout,
                opts.lockTimeout,
                opts);
    }
}