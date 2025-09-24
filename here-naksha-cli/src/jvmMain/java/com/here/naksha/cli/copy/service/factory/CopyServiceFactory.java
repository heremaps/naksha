package com.here.naksha.cli.copy.service.factory;

import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.copy.service.executors.OneShotFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class CopyServiceFactory {
    public enum FeaturesWriteExecutors {
        PARALLEL("Each batch in a new transaction. " +
                "Multi-threaded. " +
                "Default number of threads Runtime.getRuntime().availableProcessors(). " +
                "Default max batch size %s. ".formatted(ParallelFeaturesWriteExecutor.DEFAULT_MAX_BATCH_SIZE) +
                "Default queue multiplier %s. ".formatted(ParallelFeaturesWriteExecutor.DEFAULT_QUEUE_MULTI)
        ),
        ONE_SHOT("No batches, no multi-threads. " +
                "Loads all features into memory. " +
                "Writes in a single transaction. "
        );

        private final String description;

        FeaturesWriteExecutors(@NotNull String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return name() + ": " + description + System.lineSeparator();
        }
    }

    /**
     * Creates {@link CopyService}'s instance for your needs!
     *
     * @param storageProvider       the storage provider for the {@link CopyService}
     * @param sessionOptions        the session options for the {@link CopyService}
     * @param featuresWriteExecutor the type of executor to be used in the {@link CopyService}
     * @param threads               the number of threads in the pool for {@link CopyService}'s {@link FeaturesWriteExecutor};
     *                              If not applicable, just pass {@code null} and default wil be used.
     *                              Otherwise, if the {@code featuresWriteExecutor} is not accepting this parameter,
     *                              a {@link CopyServiceFactoryException} is thrown.
     * @param queueMulti            the multiplier used to calculate the size of the {@link CopyService}'s {@link FeaturesWriteExecutor} task queue;
     *                              If not applicable, just pass {@code null} and default wil be used.
     *                              Otherwise, if the {@code featuresWriteExecutor} is not accepting this parameter,
     *                              a {@link CopyServiceFactoryException} is thrown.
     * @param maxBatchSize          the max batch size for {@link CopyService}'s {@link FeaturesWriteExecutor};
     *                              If not applicable, just pass {@code null} and default wil be used.
     *                              Otherwise, if the {@code featuresWriteExecutor} is not accepting this parameter,
     *                              a {@link CopyServiceFactoryException} is thrown.
     * @return configured {@link CopyService} instance
     * @throws CopyServiceFactoryException if the {@link CopyService} cannot be created
     */
    @NotNull
    public CopyService create(
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions,
            @NotNull CopyServiceFactory.FeaturesWriteExecutors featuresWriteExecutor,
            @Nullable Integer threads,
            @Nullable Integer queueMulti,
            @Nullable Integer maxBatchSize
    ) {
        FeaturesWriteExecutor fwe = switch (featuresWriteExecutor) {
            case PARALLEL -> {
                threads = Optional.ofNullable(threads).orElse(ParallelFeaturesWriteExecutor.DEFAULT_THREADS);
                queueMulti = Optional.ofNullable(queueMulti).orElse(ParallelFeaturesWriteExecutor.DEFAULT_QUEUE_MULTI);
                maxBatchSize = Optional.ofNullable(maxBatchSize).orElse(ParallelFeaturesWriteExecutor.DEFAULT_MAX_BATCH_SIZE);
                yield new ParallelFeaturesWriteExecutor(
                        threads,
                        queueMulti,
                        maxBatchSize
                );
            }
            case ONE_SHOT -> {
                requireThreadsIsNull(threads, featuresWriteExecutor);
                requireQueueMultiIsNull(queueMulti, featuresWriteExecutor);
                requireMaxBatchSizeIsNull(maxBatchSize, featuresWriteExecutor);
                yield new OneShotFeaturesWriteExecutor();
            }
        };

        return new CopyService(
                fwe,
                storageProvider,
                sessionOptions
        );
    }

    /**
     * Creates a {@link CopyService} by calling
     * {@link CopyServiceFactory#create(StorageProvider, SessionOptions, FeaturesWriteExecutors, Integer, Integer, Integer)},
     * passing {@code null} for the three optional {@code Integer} parameters.
     */
    public CopyService create(
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions,
            @NotNull CopyServiceFactory.FeaturesWriteExecutors featuresWriteExecutorBuilder
    ) {
        return create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutorBuilder,
                null, null, null
        );
    }

    private void requireThreadsIsNull(Integer threads, FeaturesWriteExecutors featuresWriteExecutor) {
        if (threads != null) {
            throw new CopyServiceFactoryException("%s does not accept threads as parameter!".formatted(featuresWriteExecutor.name()));
        }
    }

    private void requireQueueMultiIsNull(Integer queueMulti, FeaturesWriteExecutors featuresWriteExecutor) {
        if (queueMulti != null) {
            throw new CopyServiceFactoryException("%s does not accept queueMulti as parameter!".formatted(featuresWriteExecutor.name()));
        }
    }

    private void requireMaxBatchSizeIsNull(Integer maxBatchSize, FeaturesWriteExecutors featuresWriteExecutor) {
        if (maxBatchSize != null) {
            throw new CopyServiceFactoryException("%s does not accept maxBatchSize as parameter!".formatted(featuresWriteExecutor.name()));
        }
    }
}
