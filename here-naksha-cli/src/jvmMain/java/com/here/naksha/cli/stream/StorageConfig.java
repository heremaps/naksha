package com.here.naksha.cli.stream;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A configured storage, stored as {@code {"provider": "...", "config": {...}}}.
 * @param config the configuration as JSON, as passed to {@link naksha.model.IStorageProvider#getStorage}.
 */
public record StorageConfig(@NotNull String name, @NotNull String provider, @NotNull String config) {

    static @NotNull Map<String, Object> toJson(@NotNull StorageConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", c.provider());
        m.put("config", Json.parseObject(c.config()));
        return m;
    }

    static @NotNull StorageConfig fromJson(@NotNull String name, @NotNull Map<String, Object> m) {
        if (!(m.get("provider") instanceof String provider)) throw new IllegalArgumentException("Missing provider");
        Object config = m.get("config");
        return new StorageConfig(name, provider, config == null ? "{}" : Json.write(config).trim());
    }
}
