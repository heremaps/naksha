package com.here.naksha.cli.storages;

import naksha.model.objects.NakshaStorage;

public final class GeneratingStorageConfig extends NakshaStorage {
    public int getCount() {
        return getProperties().getOr("count", 0);
    }
}