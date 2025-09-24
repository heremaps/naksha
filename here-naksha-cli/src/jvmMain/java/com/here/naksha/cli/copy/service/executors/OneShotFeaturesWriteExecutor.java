package com.here.naksha.cli.copy.service.executors;

import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorException;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorInfo;
import naksha.model.IStorage;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static naksha.model.util.RequestHelper.createFeaturesRequest;

/**
 * Writes all features in a single transaction without batching or multi-threading.
 * <p>
 * This implementation loads all features into memory before writing — a one-shot operation.
 * </p>
 * <p><strong>Note:</strong> This approach may consume a large amount of memory depending on the data size.</p>
 *
 * <p>Characteristics:</p>
 * <ul>
 *   <li>No batching</li>
 *   <li>No multi-threading</li>
 *   <li>All features are loaded into memory</li>
 *   <li>Writes in a single transaction</li>
 * </ul>
 */
public final class OneShotFeaturesWriteExecutor implements FeaturesWriteExecutor {
    /**
     * {@inheritDoc}
     * <p>
     * This implementation calls {@link naksha.model.util.ResultHelper#extractResponseItems(SuccessResponse, Class)} on the {@code featureTuples},
     * so may be modified.
     */
    @Override
    public FeaturesWriteExecutorInfo write(
            @NotNull IStorage storage,
            @NotNull CopyElement target,
            @NotNull FeatureTupleList featureTuples,
            @NotNull SessionOptions sessionOptions
    ) throws FeaturesWriteExecutorException {
        List<NakshaFeature> nakshaFeatures = loadFeatures(featureTuples);
        WriteRequest addFeaturesRequest = createFeaturesRequest(target.getMapId(), target.getCollectionId(), nakshaFeatures);
        Response response = performWriteRequest(storage, addFeaturesRequest, sessionOptions);
        requireSuccessResponse(response);
        return new FeaturesWriteExecutorInfo(nakshaFeatures.size());
    }

    private List<NakshaFeature> loadFeatures(FeatureTupleList featureTuples) {
        featureTuples.loadAll(0, featureTuples.size(), true, true);
        return featureTuples.stream().map(FeatureTuple::getFeature).toList();
    }

    private void requireSuccessResponse(Response response) throws FeaturesWriteExecutorException {
        switch (response) {
            case SuccessResponse _ -> { /* nothing to do */ }
            case ErrorResponse errorResponse -> throw new FeaturesWriteExecutorException(
                    "Problem with writing to target!",
                    new NakshaException(errorResponse.getError())
            );
            default -> throw new FeaturesWriteExecutorException("Unexpected response from target!");
        }
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
