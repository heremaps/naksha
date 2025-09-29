package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorInfo;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.FeatureTupleList;
import naksha.model.request.Write;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.here.naksha.cli.copy.service.CopyServiceTestUtlis.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

abstract class FeaturesWriteExecutorsCommonTest {
    private final FeaturesWriteExecutor featuresWriteExecutor = createFeaturesWriteExecutor();
    private final NakshaStorage targetNakshaStorage = new NakshaStorage("target", "targetclassname");
    private final CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage)
            .setMapId("targetmap")
            .setCollectionId("targetcol")
            .build();
    private static SessionOptions sessionOptions;

    @BeforeAll
    static void beforeAll() {
        NakshaContext nakshaContext = NakshaContext.currentContext().withAppId("testAppId");
        sessionOptions = SessionOptions.from(nakshaContext);
    }

    @Test
    final void shouldWrite() throws FeaturesWriteExecutorException {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(storage, sessionOptions);

        // And: features
        NakshaFeatureList nakshaFeatures = sampleNakshaFeatures();
        int expectedNumOfFeatures = nakshaFeatures.size();
        FeatureTupleList featureTuples = nakshaFeatureListToFeatureTupleList(nakshaFeatures);

        // When:
        FeaturesWriteExecutorInfo info = featuresWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        );

        // Then:
        assertEquals(expectedNumOfFeatures, info.numberOfWrittenElements());
        List<Write> writes = captureWrites(writeSession);
        assertCreateFeaturesWrites(writes, nakshaFeatures, targetCopyElement);
        verify(writeSession).commit();
    }

    @Test
    final void shouldThrowWhenErrorResponse() {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningErrorResponse(storage, sessionOptions);

        // And: features
        NakshaFeatureList nakshaFeatures = sampleNakshaFeatures();
        FeatureTupleList featureTuples = nakshaFeatureListToFeatureTupleList(nakshaFeatures);

        // When & Then:
        assertThrows(FeaturesWriteExecutorException.class, () -> featuresWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        ));
        verify(writeSession).rollback();
    }

    @Test
    final void shouldThrowWhenUnexpectedResponse() {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningUnexpectedResponse(storage, sessionOptions);

        // And: features
        NakshaFeatureList nakshaFeatures = sampleNakshaFeatures();
        FeatureTupleList featureTuples = nakshaFeatureListToFeatureTupleList(nakshaFeatures);

        // When & Then:
        assertThrows(FeaturesWriteExecutorException.class, () -> featuresWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        ));
        verify(writeSession).rollback();
    }

    @Test
    final void shouldThrowWhenWriteSessionThrows() {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        createThrowingWriteSessionForStorage(storage, sessionOptions);

        // And: features
        NakshaFeatureList nakshaFeatures = sampleNakshaFeatures();
        FeatureTupleList featureTuples = nakshaFeatureListToFeatureTupleList(nakshaFeatures);

        // When & Then:
        assertThrows(FeaturesWriteExecutorException.class, () -> featuresWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        ));
    }

    protected abstract FeaturesWriteExecutor createFeaturesWriteExecutor();

    private NakshaFeatureList sampleNakshaFeatures() {
        return NakshaFeatureList.of(
                new NakshaFeature("1"),
                new NakshaFeature("2"),
                new NakshaFeature("3")
        );
    }
}