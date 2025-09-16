package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorInfo;
import naksha.base.AtomicInt;
import naksha.base.JvmAtomicInt;
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
import static naksha.model.util.ResultHelper.extractResponseItems;

public final class ParallelFeaturesWriteExecutor implements FeaturesWriteExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ParallelFeaturesWriteExecutor.class);
    private final int cores = Runtime.getRuntime().availableProcessors();
    private final int queueMulti = 4;
    private final int maxBatchSize = 256;

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always clears the {@code featureTuples} during execution to ensure memory efficiency.
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
                cores,
                cores,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(cores * queueMulti),
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
                List<NakshaFeature> nakshaFeatures = extractResponseItems(
                        new SuccessResponse(batch), NakshaFeature.class
                );
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
}
