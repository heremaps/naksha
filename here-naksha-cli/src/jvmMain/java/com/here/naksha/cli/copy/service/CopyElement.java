package com.here.naksha.cli.copy.service;

import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CopyElement {
    private final NakshaStorage nakshaStorage;
    private final String mapId;
    private final String collectionId;

    @Override
    public String toString() {
        return "CopyElement{storageId: \"%s\", mapId: \"%s\", collectionId: \"%s\"}".formatted(
                nakshaStorage.getId(),
                mapId,
                collectionId
        );
    }

    @NotNull
    public NakshaStorage getNakshaStorage() {
        return nakshaStorage;
    }

    @Nullable
    public String getMapId() {
        return mapId;
    }

    @Nullable
    public String getCollectionId() {
        return collectionId;
    }

    public static final class Builder {
        @NotNull
        private final NakshaStorage nakshaStorage;
        @Nullable
        private String mapId;
        @Nullable
        private String collectionId;

        public Builder(
                @NotNull NakshaStorage nakshaStorage
        ) {
            this.nakshaStorage = nakshaStorage;
        }

        @NotNull
        public CopyElement build() {
            return new CopyElement(
                    this
            );
        }

        @NotNull
        public Builder setMapId(@Nullable String mapId) {
            this.mapId = mapId;
            return this;
        }

        @NotNull
        public Builder setCollectionId(@Nullable String collectionId) {
            this.collectionId = collectionId;
            return this;
        }
    }

    private CopyElement(
            Builder builder
    ) {
        this.nakshaStorage = builder.nakshaStorage;
        this.collectionId = builder.collectionId;
        this.mapId = builder.mapId;
    }
}
