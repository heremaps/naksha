package com.here.naksha.cli.copy.service;

import naksha.model.IStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CopyElement {
    private final IStorage storage;
    private final String mapId;
    private final String collectionId;

    private CopyElement(
            Builder builder
    ) {
        this.storage = builder.storage;
        this.collectionId = builder.collectionId;
        this.mapId = builder.mapId;
    }

    public IStorage getStorage() {
        return storage;
    }

    public String getMapId() {
        return mapId;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public static class Builder {
        private final IStorage storage;
        private String mapId;
        private final String collectionId;

        public Builder(
                @NotNull IStorage storage,
                @NotNull String collectionId
        ) {
            this.storage = storage;
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
