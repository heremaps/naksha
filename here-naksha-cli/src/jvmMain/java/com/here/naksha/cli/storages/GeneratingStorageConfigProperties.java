package com.here.naksha.cli.storages;

import naksha.model.objects.NakshaProperties;

public final class GeneratingStorageConfigProperties extends NakshaProperties {
    private static final String COUNT_KEY = "count";

    public int getCount() {
        return (int) getRaw(COUNT_KEY);
    }

    public void setCount(int count) {
        setRaw(COUNT_KEY, count);
    }
}