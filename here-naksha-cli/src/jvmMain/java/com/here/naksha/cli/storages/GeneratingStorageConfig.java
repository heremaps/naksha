package com.here.naksha.cli.storages;

import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.objects.NakshaStorage;

import java.util.concurrent.atomic.AtomicBoolean;

public final class GeneratingStorageConfig extends NakshaStorage {
    private GeneratingStorageConfigProperties generatingStorageConfigProperties;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    void init() {
        if(isInitialized.compareAndSet(false, true)) {
            generatingStorageConfigProperties = new GeneratingStorageConfigProperties(getProperties());
        }
    }

    GeneratingStorageConfigProperties getGeneratingStorageConfigProperties() {
        if(!isInitialized.get()) {
            throw new NakshaException(NakshaError.UNINITIALIZED, "Run init() before!");
        }

        return generatingStorageConfigProperties;
    }
}