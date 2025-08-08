package com.here.naksha.cli.storages;

import naksha.base.JvmBoxingUtil;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public final class GeneratingStorageConfig extends NakshaStorage {
    @Override
    public @NotNull GeneratingStorageConfigProperties getProperties() {
        return requireNonNull(JvmBoxingUtil.box(super.getProperties(), GeneratingStorageConfigProperties.class));
    }
}