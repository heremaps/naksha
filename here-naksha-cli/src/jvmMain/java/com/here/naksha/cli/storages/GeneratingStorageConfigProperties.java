package com.here.naksha.cli.storages;

import naksha.model.objects.NakshaProperties;

public final class GeneratingStorageConfigProperties extends NakshaProperties {
    private static final String COUNT = "count";

    int getCount() {
        return (int) getRaw(COUNT);
    }

    void setCount(int count) {
        setRaw(COUNT, count);
    }
}