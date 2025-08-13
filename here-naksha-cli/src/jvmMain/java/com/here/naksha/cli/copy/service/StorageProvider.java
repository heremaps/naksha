package com.here.naksha.cli.copy.service;

import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

public final class StorageProvider {
    @NotNull
    public IStorage useStorage(@NotNull NakshaStorage nakshaStorage) {
        return Naksha.useStorage(nakshaStorage);
    }
}
