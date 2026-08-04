package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorInfo;
import naksha.base.AtomicInt;
import naksha.base.JvmAtomicInt;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static naksha.model.util.RequestHelper.createFeaturesRequest;

/**
 * Writes features in batches and in parallel, with each batch executed in a separate transaction.
 * This implementation does not fail if a single batch fails; failed batches are skipped.
 */
public final class ParallelFeaturesWriteExecutor implements FeaturesWriteExecutor {
    public static final int DEFAULT_THREADS = Runtime.getRuntime().availableProcessors();
    public static final int DEFAULT_QUEUE_MULTI = 4;
    public static final int DEFAULT_MAX_BATCH_SIZE = 256;
    private static final Logger logger = LoggerFactory.getLogger(ParallelFeaturesWriteExecutor.class);
    private final int threads;
    private final int queueMulti;
    private final int maxBatchSize;

    /**
     * @param threads      the number of threads to be used; must be a positive integer
     * @param queueMulti   the queue multiplier; must be a positive integer; the queue size is computed as: threads * queueMulti
     * @param maxBatchSize the maximum batch size; must be a positive integer
     */
    public ParallelFeaturesWriteExecutor(
            int threads,
            int queueMulti,
            int maxBatchSize
    ) {
        this.threads = threads;
        this.queueMulti = queueMulti;
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * Calls {@link ParallelFeaturesWriteExecutor#ParallelFeaturesWriteExecutor(int, int, int)} with defaults
     */
    public ParallelFeaturesWriteExecutor() {
        this(
                DEFAULT_THREADS,
                DEFAULT_QUEUE_MULTI,
                DEFAULT_MAX_BATCH_SIZE
        );
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation clears the contents of {@code featureTuples} during execution to facilitate garbage collection.
     * This helps reduce memory usage by ensuring that references to the contained elements are removed as soon as possible.
     * <p>
     * <strong>Important:</strong> Ensure that no other code holds references to the elements of {@code featureTuples},
     * as they will be eligible for garbage collection once cleared.
     */
    @Override
    public FeaturesWriteExecutorInfo write(
            @NotNull IStorage storage,
            @NotNull CopyElement target,
            @NotNull FeatureTupleList featureTuples,
            @NotNull SessionOptions sessionOptions
    ) throws FeaturesWriteExecutorException {
        int totalToCopy = featureTuples.size();
        NakshaContext context = NakshaContext.currentContext();
        int copied = executeWritesInParallelBatches(storage, target, context, featureTuples, sessionOptions);
        if (totalToCopy != copied) {
            double ratio = calculateRatio(copied, totalToCopy);
            throw new FeaturesWriteExecutorException(
                    "Some batches failed! Copied %s/%s, %.2f%%".formatted(copied, totalToCopy, ratio * 100)
            );
        }
        return new FeaturesWriteExecutorInfo(copied);
    }

    private int executeWritesInParallelBatches(
            IStorage storage,
            CopyElement target,
            NakshaContext context,
            FeatureTupleList featureTuples,
            SessionOptions sessionOptions
    ) {
        AtomicInt copied = new JvmAtomicInt(0);
        int totalToCopy = featureTuples.size();
        try (ThreadPoolExecutor executorService = new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(threads * queueMulti),
                new ThreadPoolExecutor.CallerRunsPolicy()
        )) {
            while (!featureTuples.isEmpty()) {
                FeatureTupleList batch = popBatch(featureTuples, maxBatchSize);
                executorService.execute(
                        writeBatch(storage, target, context, copied, totalToCopy, batch, sessionOptions)
                );
            }
        }
        return copied.get();
    }

    private Runnable writeBatch(
            IStorage storage,
            CopyElement target,
            NakshaContext context,
            AtomicInt copiedElements,
            int totalToCopy,
            FeatureTupleList batch,
            SessionOptions sessionOptions
    ) {
        return () -> {
            try {
                context.attachToCurrentThread();
                List<NakshaFeature> nakshaFeatures = loadFeatures(batch);
                logger.debug("Batch on thread: {}", Thread.currentThread().getName());
                Response response = addFeatures(storage, target.getMapId(), target.getCollectionId(), nakshaFeatures, sessionOptions);
                requireSuccessResponse(response);
                int copiedTotal = copiedElements.addAndGet(nakshaFeatures.size());
                double ratio = calculateRatio(copiedTotal, totalToCopy);
                logger.debug("Batch copied! {} features saved! Batch size: {}", nakshaFeatures.size(), batch.size());
                logger.info("Copied {}/{}, {}%", copiedTotal, totalToCopy, ratio * 100);
            } catch (Exception e) {
                logger.error("Batch failed!", e);
            }
        };
    }

    private void requireSuccessResponse(Response response) {
        switch (response) {
            case SuccessResponse _ -> { /* nothing to do */ }
            case ErrorResponse errorResponse -> throw new NakshaException(errorResponse.getError());
            case Response _ -> throw new NakshaException(NakshaError.EXCEPTION, "Unexpected response when writing!");
        }
    }

    private double calculateRatio(int copiedElements, int total) {
        return (double) copiedElements / total;
    }

    private FeatureTupleList popBatch(FeatureTupleList list, int maxBatchSize) {
        int batchSize = Math.min(list.size(), maxBatchSize);
        FeatureTupleList batch = new FeatureTupleList();
        batch.setCapacity(batchSize);
        for (int i = 0; i < batchSize; ++i) {
            batch.add(list.removeLast());
        }
        return batch;
    }

    private Response addFeatures(
            IStorage storage,
            String mapId,
            String collectionId,
            List<NakshaFeature> features,
            SessionOptions sessionOptions
    ) throws FeaturesWriteExecutorException {
        WriteRequest writeRequest = createFeaturesRequest(
                mapId,
                collectionId,
                features
        );

        return performWriteRequest(storage, writeRequest, sessionOptions);
    }

    private Response performWriteRequest(
            IStorage storage,
            WriteRequest writeRequest,
            SessionOptions sessionOptions
    ) throws FeaturesWriteExecutorException {
        try {
            return storage.useWriteSession(
                    sessionOptions,
                    writer -> {
                        Response r = writer.execute(writeRequest);
                        if (r instanceof SuccessResponse) {
                            writer.commit();
                        } else {
                            writer.rollback();
                        }
                        return r;
                    });
        } catch (Exception e) {
            throw new FeaturesWriteExecutorException("Problem while writing features to target!", e);
        }
    }

    private List<NakshaFeature> loadFeatures(FeatureTupleList featureTuples) {
        featureTuples.loadAll(0, featureTuples.size(), true, true);
        return featureTuples.stream().map(FeatureTuple::getCachedFeature).toList();
    }
}
