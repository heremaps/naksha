package com.here.naksha.cli.copy.service.executors.model;

/**
 * Builder interface for configuring an executor that supports setting the maximum number of items that can be included in a single batch.
 */
public interface BatchableBuilder {
    /**
     * Sets the maximum number of items that can be included in a single batch.
     *
     * @param maxBatchSize the maximum batch size; must be greater than {@code 0}
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if {@code maxBatchSize} is less than or equal to {@code 0}
     */
    BatchableBuilder withMaxBatchSize(int maxBatchSize);
}
