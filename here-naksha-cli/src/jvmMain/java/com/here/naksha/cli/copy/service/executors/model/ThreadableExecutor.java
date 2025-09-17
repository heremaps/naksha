package com.here.naksha.cli.copy.service.executors.model;

/**
 * Interface for executors that support setting the number of threads.
 */
public interface ThreadableExecutor {
    /**
     * Sets the number of threads to be used by the executor.
     *
     * @param threads the number of threads; must be greater than 0
     * @throws IllegalArgumentException if {@code threads} is less than or equal to 0
     */
    void setThreads(int threads);
}
