package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.request.FeatureTupleList;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.here.naksha.cli.copy.service.CopyServiceTestUtils.*;
import static com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor.DEFAULT_QUEUE_MULTI;
import static com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor.DEFAULT_THREADS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelFeaturesWriteExecutorTest extends FeaturesWriteExecutorTest {
    @ParameterizedTest
    @MethodSource
    void shouldCopyInBatches(int maxBatchSize, int numOfTuples, int expectedNumOfBatches) throws FeaturesWriteExecutorException {
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

    private static Stream<Arguments> shouldCopyInBatches() {
        return Stream.of(
                // maxBatchSize, numOfTuples, expectedNumOfBatches
                Arguments.of(100, 10_000, 100),
                Arguments.of(10, 10_000, 1000),
                Arguments.of(1000, 999, 1),
                Arguments.of(666, 2137, 4)
        );
    }

    @Override
    protected FeaturesWriteExecutor createFeaturesWriteExecutor() {
        return new ParallelFeaturesWriteExecutor();
    }
}