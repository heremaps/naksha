package com.here.naksha.cli.copy.service;

import naksha.model.objects.NakshaCatalog;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaExceptionKt.illegalArg;
import static naksha.base.NakshaExceptionKt.illegalState;

public final class CopyElement {
    private final @NotNull NakshaStorage nakshaStorage;
    private final @NotNull String mapId;
    private final @NotNull String collectionId;
    NakshaCatalog catalog;
    NakshaCollection collection;

    public @NotNull NakshaCatalog catalog() {
      if (catalog == null) throw illegalState("The target has no catalog");
      return catalog;
    }

    public @NotNull NakshaCollection collection() {
      if (collection == null) throw illegalState("The target has no collection");
      return collection;
    }

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

    @NotNull
    public String getMapId() {
        return mapId;
    }

    @NotNull
    public String getCollectionId() {
        return collectionId;
    }

    public static final class Builder {
        private final @NotNull NakshaStorage nakshaStorage;
        private @Nullable String mapId;
        private @Nullable String collectionId;

        public Builder(@NotNull NakshaStorage nakshaStorage) {
            this.nakshaStorage = nakshaStorage;
        }

        @NotNull
        public CopyElement build() {
            return new CopyElement(this);
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

    private CopyElement(@NotNull Builder builder) {
        this.nakshaStorage = builder.nakshaStorage;
        final var mapId = builder.mapId;
        if (mapId == null) throw illegalArg("The builder has no map-id");
        final var collectionId = builder.collectionId;
        if (collectionId == null) throw illegalArg("The builder has no collection-id");
        this.mapId = mapId;
        this.collectionId = collectionId;
    }
}
