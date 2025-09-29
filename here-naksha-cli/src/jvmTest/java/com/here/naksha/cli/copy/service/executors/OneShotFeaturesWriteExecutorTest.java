package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.request.FeatureTupleList;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.here.naksha.cli.copy.service.CopyServiceTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OneShotFeaturesWriteExecutorTest extends FeaturesWriteExecutorTest {
    @ParameterizedTest
    @ValueSource(ints = {256, 299, 10_000})
    void shouldCopyInOneBatch(int numOfTuples) throws FeaturesWriteExecutorException {
        // Given
        FeaturesWriteExecutor parallelFeaturesWriteExecutor = createFeaturesWriteExecutor();

        // And
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(storage, sessionOptions);

        // And
        FeatureTupleList featureTuples = generateFeatureTuples(numOfTuples);

        // When
        parallelFeaturesWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        );

        // And
        List<WriteRequest> writeRequests = captureRequestsOfType(writeSession, WriteRequest.class);

        // Then: should copy in the one batch
        assertEquals(1, writeRequests.size());
    }

    @Override
    protected FeaturesWriteExecutor createFeaturesWriteExecutor() {
        return new OneShotFeaturesWriteExecutor();
    }
}