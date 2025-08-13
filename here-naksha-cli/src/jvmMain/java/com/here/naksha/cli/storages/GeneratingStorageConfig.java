package com.here.naksha.cli.storages;

import naksha.base.PlatformType;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

import static naksha.base.Platform.forClass;

public final class GeneratingStorageConfig extends NakshaStorage {
    public static final PlatformType<GeneratingStorageConfig> TYPE = forClass(GeneratingStorageConfig.class);

    @Override
    public @NotNull GeneratingStorageConfigProperties getProperties() {
        return getProperties(GeneratingStorageConfigProperties.TYPE);
    }
}
