package com.here.naksha.cli.storages;

import naksha.base.JvmBoxingUtil;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

public final class GeneratingStorageConfig extends NakshaStorage {
    @Override
    public @NotNull GeneratingStorageConfigProperties getProperties() {
        return JvmBoxingUtil.box(super.getProperties(), GeneratingStorageConfigProperties.class);
    }
}