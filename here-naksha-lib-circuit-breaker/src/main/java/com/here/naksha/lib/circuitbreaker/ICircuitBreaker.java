/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.circuitbreaker;

import com.here.naksha.lib.circuitbreaker.models.CircuitBreakerProps;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * Circuit breaker interface. Manages per-resourceId circuit breaker instances with thread-safe
 * operations.
 */
public interface ICircuitBreaker {

  /**
   * Execute a callable operation with circuit breaker protection scoped to resourceId.
   *
   * <p><b>Operation callback conditions based on CircuitBreaker state associated with a resourceId:</b>
   * <ul>
   *   <li>when in <b>CLOSED</b> state: Operation invoked immediately as normal
   *   <li>when in <b>HALF_OPEN</b> state: Only N Operations invoked, where "N" is `permittedNumberOfCallsInHalfOpenState` configuration, else CircuitBreakerOpenException thrown
   *   <li>when in <b>OPEN</b> state: Operation NOT invoked, instead CircuitBreakerOpenException thrown immediately
   * </ul>
   *
   * <p><b>CircuitBreakerOpenException thrown when in OPEN/HALF_OPEN (outside permitted limits) state:</b> Throws {@code CircuitBreakerOpenException}.
   *
   * @param resourceId Unique resource identifier (e.g. some storageId, Http endpoint) against which the CircuitBreaker protection is to be applied.
   * @param operation The callable operation to be executed under CircuitBreaker protection
   * @param <T> Return type of the operation
   * @return Result of the operation if executed successfully
   * @throws CircuitBreakerOpenException If circuit breaker is OPEN or HALF_OPEN (exceeding permitted limits)
   * @throws Exception If operation fails or other errors occur
   */
  <T> T execute(@NotNull String resourceId, @NotNull Callable<T> operation) throws Exception;

  /**
   * Get or create the circuit breaker for the given resourceId. It is implementation's responsibility to provide thread-safety for this function.
   *
   * <p><b>Important Note on Configuration Changes:</b>
   * This function does <b>NOT</b> compare or detect changes in the CircuitBreaker configuration between invocations.
   * It is the <b>caller's responsibility</b> to detect configuration changes and handle them by:
   * <ol>
   *   <li>Calling {@link #remove(String)} to invalidate the existing circuit breaker instance
   *   <li>Then invoking this method with the new configuration to ensure the circuit breaker is (re)created with updated settings
   * </ol>
   * If the configuration changes but {@code remove()} is not called first, the old circuit breaker instance
   * (with its previous state and configuration) will continue to be used.
   *
   * @param resourceId Unique resource identifier (e.g. some storageId, Http endpoint) against which the CircuitBreaker protection is to be applied.
   * @param config Circuit Breaker configuration to be applied for all future operations against the given resourceId
   * @return the CircuitBreaker provider implementation
   */
  @NotNull
  ICircuitBreaker getOrCreateCircuitBreaker(@NotNull String resourceId, @NotNull CircuitBreakerProps config);

  /**
   * Remove circuit breaker for the given resourceId.
   *
   * @param resourceId Unique identifier for circuit breaker instance to remove
   */
  void remove(@NotNull String resourceId);

  /**
   * Clear all circuit breakers.
   */
  void clear();

  /**
   * Check if the circuit breaker for the given resourceId is in CLOSED state.
   *
   * <p><b>This is a READ-ONLY operation:</b> Only reads state, does not modify anything.
   *
   * @param resourceId Unique identifier for circuit breaker instance
   * @return true if circuit breaker is in CLOSED state, false otherwise (OPEN, HALF_OPEN, or not found)
   */
  boolean isClosed(@NotNull String resourceId);

  /**
   * Check if a circuit breaker exists for the given resourceId.
   *
   * <p><b>This is a READ-ONLY operation:</b> Only checks existence, does not modify anything.
   *
   * @param resourceId Unique identifier for circuit breaker instance
   * @return true if circuit breaker exists for this resourceId, false otherwise
   */
  boolean exists(@NotNull String resourceId);
}
