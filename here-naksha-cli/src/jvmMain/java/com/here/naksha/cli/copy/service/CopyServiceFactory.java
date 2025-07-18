package com.here.naksha.cli.copy.service;

import naksha.model.SessionOptions;

public final class CopyServiceFactory {
    public CopyService create(
            StorageProvider storageProvider,
            SessionOptions sessionOptions
    ) {
        return new CopyService(
                storageProvider,
                sessionOptions
        );
    }
}
