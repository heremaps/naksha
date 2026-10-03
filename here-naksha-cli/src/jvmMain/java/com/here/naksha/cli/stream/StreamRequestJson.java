package com.here.naksha.cli.stream;

import naksha.base.Id;
import naksha.base.Platform;
import naksha.model.streaming.StreamRequest;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DRAFT: JSON round-trip for recovery requests; {@link Id} values are written as their text.
 */
public final class StreamRequestJson {
    private StreamRequestJson() {}

    public static @NotNull String toJson(@NotNull StreamRequest request) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : request.entrySet()) {
            Object v = e.getValue();
            copy.put(e.getKey(), v instanceof Id id ? id.text() : v);
        }
        return Platform.toJSON(copy);
    }

    public static @NotNull StreamRequest fromJson(@NotNull String json) {
        Object raw = Platform.fromJSON(json);
        return Objects.requireNonNull(Platform.javaProxy(raw, StreamRequest.class), "Not a JSON object");
    }
}
