package com.here.naksha.cli.copy.service;

import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.mockito.Mockito.mock;

public class TestCopyElement {
    private final CopyElement copyElement;

    private TestCopyElement(
            Builder builder
    ) {
        NakshaStorage storage = mock();
        copyElement = new CopyElement.Builder(storage, builder.collectionId)
                .setMapId(builder.mapId)
                .build();
    }

    public CopyElement getCopyElement() {
        return copyElement;
    }

    public NakshaStorage getStorage() {
        return copyElement.getNakshaStorage();
    }

    @Nullable
    public String getMapId() {
        return copyElement.getMapId();
    }

    @NotNull
    public String getCollectionId() {
        return copyElement.getCollectionId();
    }

    public static class Builder {
        private @Nullable String mapId;
        private final @NotNull String collectionId;

        public Builder(
                @NotNull String collectionId
        ) {
            this.collectionId = collectionId;
        }

        public TestCopyElement build() {
            return new TestCopyElement(
                    this
            );
        }

        public Builder setMapId(@Nullable String mapId) {
            this.mapId = mapId;
            return this;
        }
    }
}
