package com.here.naksha.cli.copy;

import com.here.naksha.cli.copy.service.CopyService;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.request.ReadFeatures;
import org.jetbrains.annotations.Nullable;

import static org.mockito.Mockito.mock;

class TestCopyServiceBuilder {
    private @Nullable String srcMapId;
    private @Nullable String targetMapId;
    private @Nullable String srcCollectionId;
    private @Nullable String targetCollectionId;
    private final IStorage mockedSrcStorage = mock();
    private final IStorage mockedTargetStorage = mock();
    private final SessionOptions mockedSessionOptions = mock();
    private final ReadFeatures readFeatures = new ReadFeatures();

    public CopyService build() throws CopyServiceException {
        CopyService.Builder builder = new CopyService.Builder(
                mockedSrcStorage,
                mockedTargetStorage,
                mockedSessionOptions
        )
            .setSrcCollectionId(srcCollectionId)
            .setSrcMapId(srcMapId)
            .setTargetCollectionId(targetCollectionId)
            .setTargetMapId(targetMapId);

        return builder.build();
    }

    public TestCopyServiceBuilder setSrcMapId(@Nullable String srcMapId) {
        this.srcMapId = srcMapId;
        readFeatures.setMapId(srcMapId);
        return this;
    }

    public TestCopyServiceBuilder setTargetMapId(@Nullable String targetMapId) {
        this.targetMapId = targetMapId;
        return this;
    }

    public TestCopyServiceBuilder setSrcCollectionId(@Nullable String srcCollectionId) {
        this.srcCollectionId = srcCollectionId;
        if(srcCollectionId != null) {
            readFeatures.setCollectionIds(StringList.of(srcCollectionId));
        }
        return this;
    }

    public TestCopyServiceBuilder setTargetCollectionId(@Nullable String targetCollectionId) {
        this.targetCollectionId = targetCollectionId;
        return this;
    }

    public IStorage getMockedSrcStorage() {
        return mockedSrcStorage;
    }

    public IStorage getMockedTargetStorage() {
        return mockedTargetStorage;
    }


    public SessionOptions getMockedSessionOptions() {
        return mockedSessionOptions;
    }

    public ReadFeatures getReadFeatures() {
        return readFeatures;
    }

    @Nullable
    public String getTargetMapId() {
        return targetMapId;
    }

    @Nullable
    public String getTargetCollectionId() {
        return targetCollectionId;
    }
}
