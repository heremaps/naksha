package com.here.naksha.cli.stream;

import com.here.naksha.cli.stream.providers.StreamOnlyStorage;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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