package com.here.naksha.cli.copy.service;

import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.objects.NakshaStorage;

public final class NakshaProvider {
    public IStorage useStorage(NakshaStorage nakshaStorage) {
        return Naksha.useStorage(nakshaStorage);
    }
}
