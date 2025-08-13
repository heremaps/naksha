package com.here.naksha.cli.copy.service;

import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;

public final class CopyServiceFactory {
    @NotNull
    public CopyService create(
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions
    ) {
        return new CopyService(
                storageProvider,
                sessionOptions
        );
    }
}
