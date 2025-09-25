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
import static com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor.DEFAULT_QUEUE_MULTI;
import static com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor.DEFAULT_THREADS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelFeaturesWriteExecutorTest extends FeaturesWriteExecutorsCommonTest {
    @ParameterizedTest
    @ValueSource(ints = {100, 200, 666, 10_000, 20_000})
    void shouldCopyInBatches(int maxBatchSize) throws FeaturesWriteExecutorException {
        // Given
        ParallelFeaturesWriteExecutor parallelFeaturesWriteExecutor = new ParallelFeaturesWriteExecutor(
                DEFAULT_THREADS,
                DEFAULT_QUEUE_MULTI,
                maxBatchSize
        );

        // And
        IStorage storage = createTargetStorage(sessionOptions);
        IWriteSession writeSession = createWriteSessionForStorageReturningSuccessResponse(storage, sessionOptions);

        // And
        int numOfTuples = 10_000;
        FeatureTupleList featureTuples = generateFeatureTuples(numOfTuples);

        // And
        int expectedNumOfBatches = Math.ceilDiv(numOfTuples, maxBatchSize);

        // When
        parallelFeaturesWriteExecutor.write(
                storage,
                targetCopyElement,
                featureTuples,
                sessionOptions
        );

        // And
        List<WriteRequest> writeRequests = captureRequestsOfType(writeSession, WriteRequest.class);

        // Then: should copy in batches
        assertEquals(expectedNumOfBatches, writeRequests.size());

        // And: batchSize <= maxBatchSize
        writeRequests.forEach(writeRequest -> {
                    int batchSize = writeRequest.getWrites().size();
                    assertTrue(
                            batchSize <= maxBatchSize,
                            "Batch size should be <= maxBatchSize, but %s > %s".formatted(batchSize, maxBatchSize)
                    );
                }
        );
    }

    @Override
    protected FeaturesWriteExecutor createFeaturesWriteExecutor() {
        return new ParallelFeaturesWriteExecutor();
    }
}