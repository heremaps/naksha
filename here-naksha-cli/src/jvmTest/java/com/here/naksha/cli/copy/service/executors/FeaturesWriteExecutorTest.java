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
import naksha.model.objects.NakshaStorage;
import naksha.model.request.FeatureTupleList;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.here.naksha.cli.copy.service.CopyServiceTestUtils.*;
import static naksha.model.RandomFeatures.randomFeatures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

abstract class FeaturesWriteExecutorTest {
    private final FeaturesWriteExecutor featuresWriteExecutor = createFeaturesWriteExecutor();
    private final NakshaStorage targetNakshaStorage = new NakshaStorage("target", "targetclassname");
    protected final CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage)
            .setMapId("targetmap")
            .setCollectionId("targetcol")
            .build();
    protected static SessionOptions sessionOptions;

    @BeforeAll
    static void beforeAll() {
        NakshaContext nakshaContext = NakshaContext.currentContext().withAppId("testAppId");
        sessionOptions = SessionOptions.from(nakshaContext);
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 299, 10_000})
    final void shouldWrite(int numOfFeatures) throws FeaturesWriteExecutorException {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(storage, sessionOptions);

        // And: features
        List<NakshaFeature> nakshaFeatures = randomFeatures(numOfFeatures);
        FeatureTupleList featureTuples = nakshaFeatureListToFeatureTupleList(nakshaFeatures);

        // When:
        FeaturesWriteExecutorInfo info = featuresWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        );

        // And
        List<WriteRequest> writeRequests = captureRequestsOfType(writeSession, WriteRequest.class);
        List<Write> writes = writeRequestsToWrites(writeRequests);

        // Then:
        assertEquals(numOfFeatures, info.numberOfWrittenElements());
        assertCreateFeaturesWrites(writes, nakshaFeatures, targetCopyElement);
        verify(writeSession, times(writeRequests.size())).commit();
    }

    @Test
    final void shouldThrowWhenErrorResponse() {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningErrorResponse(storage, sessionOptions);

        // And: features
        List<NakshaFeature> nakshaFeatures = randomFeatures(10_000);
        FeatureTupleList featureTuples = nakshaFeatureListToFeatureTupleList(nakshaFeatures);

        // When:
        assertThrows(FeaturesWriteExecutorException.class, () -> featuresWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        ));

        // Then
        List<WriteRequest> writeRequests = captureRequestsOfType(writeSession, WriteRequest.class);
        verify(writeSession, times(writeRequests.size())).rollback();
    }

    @Test
    final void shouldThrowWhenUnexpectedResponse() {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningUnexpectedResponse(storage, sessionOptions);

        // And: features
        List<NakshaFeature> nakshaFeatures = randomFeatures(10_000);
        FeatureTupleList featureTuples = nakshaFeatureListToFeatureTupleList(nakshaFeatures);

        // When:
        assertThrows(FeaturesWriteExecutorException.class, () -> featuresWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        ));

        // Then
        List<WriteRequest> writeRequests = captureRequestsOfType(writeSession, WriteRequest.class);
        verify(writeSession, times(writeRequests.size())).rollback();
    }

    @Test
    final void shouldThrowWhenWriteSessionThrows() {
        // Given:
        IStorage storage = createTargetStorage(sessionOptions);
        createThrowingWriteSessionForStorage(storage, sessionOptions);

        // And: features
        List<NakshaFeature> nakshaFeatures = randomFeatures(10_000);
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

    protected FeatureTupleList generateFeatureTuples(int numberOfTuples) {
        return nakshaFeatureListToFeatureTupleList(randomFeatures(numberOfTuples));
    }
}