package com.here.naksha.cli.stream;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

public final class StreamProviders {
    private final Map<String, StreamProvider> byName = new TreeMap<>();

    public StreamProviders(@NotNull Iterable<StreamProvider> providers) {
        for (StreamProvider p : providers) {
            StreamProvider existing = byName.putIfAbsent(p.name(), p);
            if (existing != null && existing.getClass() != p.getClass()) {
                throw new IllegalStateException("Two stream providers use the name '" + p.name() + "': "
                        + existing.getClass().getName() + " and " + p.getClass().getName());
            }
        }
    }

    public static @NotNull StreamProviders load() {
        return new StreamProviders(ServiceLoader.load(StreamProvider.class));
    }

    public @NotNull Collection<StreamProvider> all() {
        return byName.values();
    }

    public @NotNull StreamProvider get(@NotNull String name) {
        StreamProvider p = byName.get(name);
        if (p == null) {
            throw new IllegalArgumentException("Unknown stream provider '" + name + "', available: " + String.join(", ", byName.keySet()));
        }
        return p;
    }
}
