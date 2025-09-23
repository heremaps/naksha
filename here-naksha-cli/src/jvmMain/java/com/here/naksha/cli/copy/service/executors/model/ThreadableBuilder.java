package com.here.naksha.cli.copy.service.executors.model;

/**
 * Builder interface for configuring an executor that supports setting the number of threads.
 */
public interface ThreadableBuilder {
    /**
     * Sets the number of threads to be used by the executor.
     *
     * @param threads the number of threads; must be greater than {@code 0}
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if {@code threads} is less than or equal to {@code 0}
     */
    ThreadableBuilder withThreads(int threads);

    /**
     * Sets the multiplier used to calculate the size of the executor's task queue.
     * <p>
     * The queue size is computed as: {@code threads * queueMulti}.
     *
     * @param queueMulti the multiplier used to compute the task queue size; must be greater than {@code 0}
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if {@code queueMulti} is less than or equal to {@code 0}
     */
    ThreadableBuilder withQueueMulti(int queueMulti);
}
