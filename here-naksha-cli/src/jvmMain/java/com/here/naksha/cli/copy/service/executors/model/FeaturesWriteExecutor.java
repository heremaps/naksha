package com.here.naksha.cli.copy.service.executors.model;

import com.here.naksha.cli.copy.service.CopyElement;
import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.request.FeatureTupleList;
import org.jetbrains.annotations.NotNull;

public interface FeaturesWriteExecutor {
    /**
     * Writes the given feature tuples to the specified target within the provided storage.
     * Note: The {@code featureTuples} object may be modified or even cleared during execution.
     *
     * @param storage        the storage where the data will be written
     * @param target         the target element to which data will be written
     * @param featureTuples  the list of feature tuples to write; may be modified or cleared during execution
     * @param sessionOptions the session options used by the write's session
     * @return information about the write operation
     * @throws FeaturesWriteExecutorException if the write operation fails
     */
    FeaturesWriteExecutorInfo write(
            @NotNull IStorage storage,
            @NotNull CopyElement target,
            @NotNull FeatureTupleList featureTuples,
            @NotNull SessionOptions sessionOptions
    ) throws FeaturesWriteExecutorException;
}
