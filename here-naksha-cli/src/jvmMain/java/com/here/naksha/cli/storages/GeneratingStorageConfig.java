package com.here.naksha.cli.storages;

import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public final class GeneratingStorageConfig extends NakshaStorage {
    private GeneratingStorageConfigProperties properties;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    @Override
    public @NotNull GeneratingStorageConfigProperties getProperties() {
        if(!isInitialized.get()) {
            throw new NakshaException(NakshaError.UNINITIALIZED, "Run init() before!");
        }

        return properties;
    }

    void init(GeneratingStorageConfigProperties properties) {
        if(isInitialized.compareAndSet(false, true)) {
            this.properties = properties;
        }
    }

    void init() {
        if(isInitialized.compareAndSet(false, true)) {
            properties = new GeneratingStorageConfigProperties(super.getProperties());
        }
    }
}