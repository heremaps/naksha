package com.here.naksha.cli.copy.service;

import naksha.model.SessionOptions;

public final class CopyServiceFactory {
    public CopyService create(
            NakshaProvider nakshaProvider,
            SessionOptions sessionOptions
    ) {
        return new CopyService(
                nakshaProvider,
                sessionOptions
        );
    }
}
