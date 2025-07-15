package com.here.naksha.cli.copy.service;

import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CopyElement {
    private final NakshaStorage nakshaStorage;
    private final String mapId;
    private final String collectionId;

    private CopyElement(
            Builder builder
    ) {
        this.nakshaStorage = builder.nakshaStorage;
        this.collectionId = builder.collectionId;
        this.mapId = builder.mapId;
    }

    public NakshaStorage getNakshaStorage() {
        return nakshaStorage;
    }

    public String getMapId() {
        return mapId;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public static class Builder {
        private final NakshaStorage nakshaStorage;
        private String mapId;
        private final String collectionId;

        public Builder(
                @NotNull NakshaStorage nakshaStorage,
                @NotNull String collectionId
        ) {
            this.nakshaStorage = nakshaStorage;
            this.collectionId = collectionId;
        }

        public CopyElement build() {
            return new CopyElement(
                    this
            );
        }

        public Builder setMapId(@Nullable String mapId) {
            this.mapId = mapId;
            return this;
        }
    }
}
