package com.here.naksha.cli.copy.service.executors.model;

/**
 * Interface for executors that support configuring the maximum batch size.
 */
public interface BatchableExecutor {
    /**
     * Sets the maximum batch size for processing.
     *
     * @param maxBatchSize the maximum size of a batch; must be greater than 0
     * @throws IllegalArgumentException if {@code maxBatchSize} is less than or equal to 0
     */
    void setMaxBatchSize(int maxBatchSize);
}
