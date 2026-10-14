package com.here.naksha.cli.stream;

import naksha.model.IStorageProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

/** The storage providers registered in {@code META-INF/services/naksha.model.IStorageProvider}, by name. */
public final class StorageProviders {
    private final Map<String, IStorageProvider> byName = new TreeMap<>();

    public StorageProviders(@NotNull Iterable<IStorageProvider> providers) {
        for (IStorageProvider p : providers) {
            IStorageProvider existing = byName.putIfAbsent(p.getName(), p);
            if (existing != null && existing.getClass() != p.getClass()) {
                throw new IllegalStateException("Two storage providers use the name '" + p.getName() + "': "
                        + existing.getClass().getName() + " and " + p.getClass().getName());
            }
        }
    }

    public static @NotNull StorageProviders load() {
        return new StorageProviders(ServiceLoader.load(IStorageProvider.class));
    }

    public @NotNull Collection<IStorageProvider> all() {
        return byName.values();
    }

    public @NotNull IStorageProvider get(@NotNull String name) {
        IStorageProvider p = byName.get(name);
        if (p == null) {
            throw new IllegalArgumentException("Unknown storage provider '" + name + "', available: " + String.join(", ", byName.keySet()));
        }
        return p;
    }
}
