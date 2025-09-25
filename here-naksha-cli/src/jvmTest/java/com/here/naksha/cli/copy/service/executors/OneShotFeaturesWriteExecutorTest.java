package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.request.FeatureTupleList;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.here.naksha.cli.copy.service.CopyServiceTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OneShotFeaturesWriteExecutorTest extends FeaturesWriteExecutorsCommonTest {
    @Test
    void shouldCopyInOneBatch() throws FeaturesWriteExecutorException {
        // Given
        FeaturesWriteExecutor parallelFeaturesWriteExecutor = createFeaturesWriteExecutor();

        // And
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(storage, sessionOptions);

        // And
        int numOfTuples = 10_000;
        FeatureTupleList featureTuples = generateFeatureTuples(numOfTuples);

        // And
        int expectedNumOfBatches = 1;

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
        assertEquals(expectedNumOfBatches, writeRequests.size());
    }

    @Override
    protected FeaturesWriteExecutor createFeaturesWriteExecutor() {
        return new OneShotFeaturesWriteExecutor();
    }
}