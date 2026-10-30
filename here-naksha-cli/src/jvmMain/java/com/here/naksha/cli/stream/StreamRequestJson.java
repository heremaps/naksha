package com.here.naksha.cli.stream;

import naksha.base.Platform;
import naksha.model.streaming.StreamRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** JSON round-trip for recovery requests. */
public final class StreamRequestJson {
    private StreamRequestJson() {}

    public static @NotNull String toJson(@NotNull StreamRequest request) {
        return Platform.toJSON(request);
    }

    public static @NotNull StreamRequest fromJson(@NotNull String json) {
        Object raw = Platform.fromJSON(json);
        return Objects.requireNonNull(Platform.javaProxy(raw, StreamRequest.class), "Not a JSON object");
    }
}
