package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor.Builder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParallelFeaturesWriteExecutorTest extends FeaturesWriteExecutorsCommonTest {
    private final Builder builder = new Builder();

    @Test
    void shouldBuild() {
        // When
        ParallelFeaturesWriteExecutor featuresWriteExecutor = builder
                .withThreads(10)
                .withQueueMulti(10)
                .withMaxBatchSize(1000)
                .build();

        // Then
        assertNotNull(featuresWriteExecutor);
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0})
    void shouldThrowWhenBuilderWithNonPositiveMaxBatchSize(int maxBatchSize) {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> builder.withMaxBatchSize(maxBatchSize));
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0})
    void shouldThrowWhenBuilderWithNonPositiveThreads(int thread) {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> builder.withThreads(thread));
    }

    @ParameterizedTest
    @ValueSource(ints = {-10, -1, 0})
    void shouldThrowWhenBuilderWithNonPositiveQueueMulti(int queueMulti) {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> builder.withQueueMulti(queueMulti));
    }

    @Override
    protected FeaturesWriteExecutor createFeaturesWriteExecutor() {
        return new Builder().build();
    }
}