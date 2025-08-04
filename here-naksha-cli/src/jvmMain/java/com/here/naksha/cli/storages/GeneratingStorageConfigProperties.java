package com.here.naksha.cli.storages;

import naksha.model.objects.NakshaProperties;

final class GeneratingStorageConfigProperties {
    private final int count;

    GeneratingStorageConfigProperties(NakshaProperties nakshaProperties) {
        count = nakshaProperties.getOr("count", 0);
    }

    int getCount() {
        return count;
    }
}
