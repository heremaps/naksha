package com.here.naksha.cli.copy.service.factory;

import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.copy.service.executors.OneShotFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.BatchableExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.ThreadableExecutor;
import naksha.model.SessionOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class CopyServiceFactory {
    public enum FeaturesWriteExecutors {
        PARALLEL("Each batch in a new transaction. " +
                "Multi-threaded. " +
                "Default number of threads Runtime.getRuntime().availableProcessors(). " +
                "Default max batch size 256.",
                ParallelFeaturesWriteExecutor::new
        ),
        ONE_SHOT("No batches, no multi-threads. " +
                "Loads all features into memory. " +
                "Writes in a single transaction. " +
                "ONE SHOT, ONE KILL (YOUR MEMORY)",
                OneShotFeaturesWriteExecutor::new
        );

        private final String description;
        private final Supplier<FeaturesWriteExecutor> supplier;

        FeaturesWriteExecutors(@NotNull String description, @NotNull Supplier<FeaturesWriteExecutor> supplier) {
            this.description = description;
            this.supplier = supplier;
        }

        @Override
        public String toString() {
            return name() + ": " + description + System.lineSeparator();
        }

        FeaturesWriteExecutor createInstance() {
            return this.supplier.get();
        }
    }

    /**
     * Creates {@link CopyService}'s instance for your needs!
     *
     * @param storageProvider       the storage provider for the {@link CopyService}
     * @param sessionOptions        the session options for the {@link CopyService}
     * @param featuresWriteExecutor the type of executor to be used in the {@link CopyService}
     * @param threads               the number of threads in the pool for {@link CopyService}'s {@link ThreadableExecutor};
     *                              If not applicable, just pass {@code null} and default wil be used.
     *                              Otherwise, if the {@code featuresWriteExecutor} is not {@link ThreadableExecutor},
     *                              a {@link CopyServiceFactoryException} is thrown.
     * @param maxBatchSize          the max batch size for {@link CopyService}'s {@link BatchableExecutor};
     *                              If not applicable, just pass {@code null} and default wil be used.
     *                              Otherwise, if the {@code featuresWriteExecutor} is not {@link BatchableExecutor},
     *                              a {@link CopyServiceFactoryException} is thrown.
     * @return configured {@link CopyService} instance
     * @throws CopyServiceFactoryException if the {@link CopyService} cannot be created
     */
    @NotNull
    public CopyService create(
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions,
            @NotNull FeaturesWriteExecutors featuresWriteExecutor,
            @Nullable Integer threads,
            @Nullable Integer maxBatchSize
    ) {
        FeaturesWriteExecutor executorInstance = featuresWriteExecutor.createInstance();
        setMaxBatchSizeIfNotNull(executorInstance, featuresWriteExecutor, maxBatchSize);
        setThreadsIfNotNull(executorInstance, featuresWriteExecutor, threads);
        return new CopyService(
                executorInstance,
                storageProvider,
                sessionOptions
        );
    }

    private void setThreadsIfNotNull(FeaturesWriteExecutor executorInstance, FeaturesWriteExecutors featuresWriteExecutor, Integer threads) {
        if (threads == null) {
            return;
        }
        if (executorInstance instanceof ThreadableExecutor threadableExecutor) {
            threadableExecutor.setThreads(threads);
        } else {
            throw new CopyServiceFactoryException("%s is not threadable!".formatted(featuresWriteExecutor.name()));
        }
    }

    private void setMaxBatchSizeIfNotNull(FeaturesWriteExecutor executorInstance, FeaturesWriteExecutors featuresWriteExecutor, Integer maxBatchSize) {
        if (maxBatchSize == null) {
            return;
        }
        if (executorInstance instanceof BatchableExecutor batchableExecutor) {
            batchableExecutor.setMaxBatchSize(maxBatchSize);
        } else {
            throw new CopyServiceFactoryException("%s is not batchable!".formatted(featuresWriteExecutor.name()));
        }
    }
}
