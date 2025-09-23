package com.here.naksha.cli.copy.service.factory;

import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.copy.service.executors.OneShotFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.BatchableBuilder;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorBuilder;
import com.here.naksha.cli.copy.service.executors.model.ThreadableBuilder;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class CopyServiceFactory {
    public enum FeaturesWriteExecutorsBuilders {
        PARALLEL("Each batch in a new transaction. " +
                "Multi-threaded. " +
                "Default number of threads Runtime.getRuntime().availableProcessors(). " +
                "Default max batch size 256.",
                ParallelFeaturesWriteExecutor.Builder::new
        ),
        ONE_SHOT("No batches, no multi-threads. " +
                "Loads all features into memory. " +
                "Writes in a single transaction. ",
                OneShotFeaturesWriteExecutor.Builder::new
        );

        private final String description;
        private final Supplier<FeaturesWriteExecutorBuilder> supplier;

        FeaturesWriteExecutorsBuilders(@NotNull String description, @NotNull Supplier<FeaturesWriteExecutorBuilder> supplier) {
            this.description = description;
            this.supplier = supplier;
        }

        @Override
        public String toString() {
            return name() + ": " + description + System.lineSeparator();
        }

        FeaturesWriteExecutorBuilder createInstance() {
            return this.supplier.get();
        }
    }

    /**
     * Creates {@link CopyService}'s instance for your needs!
     *
     * @param storageProvider              the storage provider for the {@link CopyService}
     * @param sessionOptions               the session options for the {@link CopyService}
     * @param featuresWriteExecutorBuilder the type of executor to be used in the {@link CopyService}
     * @param threads                      the number of threads in the pool for {@link CopyService}'s {@link FeaturesWriteExecutor};
     *                                     If not applicable, just pass {@code null} and default wil be used.
     *                                     Otherwise, if the {@code featuresWriteExecutorBuilder} is not {@link ThreadableBuilder},
     *                                     a {@link CopyServiceFactoryException} is thrown.
     * @param queueMulti                   the multiplier used to calculate the size of the {@link CopyService}'s {@link FeaturesWriteExecutor} task queue;
     *                                     If not applicable, just pass {@code null} and default wil be used.
     *                                     Otherwise, if the {@code featuresWriteExecutorBuilder} is not {@link ThreadableBuilder},
     *                                     a {@link CopyServiceFactoryException} is thrown.
     * @param maxBatchSize                 the max batch size for {@link CopyService}'s {@link FeaturesWriteExecutor};
     *                                     If not applicable, just pass {@code null} and default wil be used.
     *                                     Otherwise, if the {@code featuresWriteExecutorBuilder} is not {@link BatchableBuilder},
     *                                     a {@link CopyServiceFactoryException} is thrown.
     * @return configured {@link CopyService} instance
     * @throws CopyServiceFactoryException if the {@link CopyService} cannot be created
     */
    @NotNull
    public CopyService create(
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions,
            @NotNull FeaturesWriteExecutorsBuilders featuresWriteExecutorBuilder,
            @Nullable Integer threads,
            @Nullable Integer queueMulti,
            @Nullable Integer maxBatchSize
    ) {
        FeaturesWriteExecutorBuilder featuresWriteExecutorBuilderInstance = featuresWriteExecutorBuilder.createInstance();
        setMaxBatchSizeIfNotNull(featuresWriteExecutorBuilderInstance, featuresWriteExecutorBuilder, maxBatchSize);
        setThreadsIfNotNull(featuresWriteExecutorBuilderInstance, featuresWriteExecutorBuilder, threads);
        setQueueMultiIfNotNull(featuresWriteExecutorBuilderInstance, featuresWriteExecutorBuilder, queueMulti);
        FeaturesWriteExecutor featuresWriteExecutor = featuresWriteExecutorBuilderInstance.build();
        return new CopyService(
                featuresWriteExecutor,
                storageProvider,
                sessionOptions
        );
    }

    /**
     * Creates a {@link CopyService} by calling
     * {@link CopyServiceFactory#create(StorageProvider, SessionOptions, FeaturesWriteExecutorsBuilders, Integer, Integer, Integer)},
     * passing {@code null} for the three optional {@code Integer} parameters.
     */
    public CopyService create(
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions,
            @NotNull FeaturesWriteExecutorsBuilders featuresWriteExecutorBuilder
    ) {
        return create(
                storageProvider,
                sessionOptions,
                featuresWriteExecutorBuilder,
                null, null, null
        );
    }

    private void setQueueMultiIfNotNull(
            FeaturesWriteExecutorBuilder executorInstance,
            FeaturesWriteExecutorsBuilders featuresWriteExecutorBuilder,
            Integer queueMulti
    ) {
        if (queueMulti == null) {
            return;
        }
        if (executorInstance instanceof ThreadableBuilder threadableBuilder) {
            threadableBuilder.withQueueMulti(queueMulti);
        } else {
            throw new CopyServiceFactoryException("%s is not threadable!".formatted(featuresWriteExecutorBuilder.name()));
        }
    }

    private void setThreadsIfNotNull(
            FeaturesWriteExecutorBuilder executorInstance,
            FeaturesWriteExecutorsBuilders featuresWriteExecutorBuilder,
            Integer threads
    ) {
        if (threads == null) {
            return;
        }
        if (executorInstance instanceof ThreadableBuilder threadableBuilder) {
            threadableBuilder.withThreads(threads);
        } else {
            throw new CopyServiceFactoryException("%s is not threadable!".formatted(featuresWriteExecutorBuilder.name()));
        }
    }

    private void setMaxBatchSizeIfNotNull(
            FeaturesWriteExecutorBuilder executorInstance,
            FeaturesWriteExecutorsBuilders featuresWriteExecutorBuilder,
            Integer maxBatchSize
    ) {
        if (maxBatchSize == null) {
            return;
        }
        if (executorInstance instanceof BatchableBuilder batchableBuilder) {
            batchableBuilder.withMaxBatchSize(maxBatchSize);
        } else {
            throw new CopyServiceFactoryException("%s is not batchable!".formatted(featuresWriteExecutorBuilder.name()));
        }
    }
}
