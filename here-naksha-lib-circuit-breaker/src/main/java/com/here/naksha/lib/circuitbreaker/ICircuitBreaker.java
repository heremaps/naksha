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
   * <p><b>Operation Callback:</b>
   * <ul>
   *   <li><b>CLOSED:</b> Always invoked immediately
   *   <li><b>HALF_OPEN:</b> Invoked only if permits available, else exception thrown
   *   <li><b>OPEN:</b> Never invoked, exception thrown immediately
   * </ul>
   *
   * <p><b>Exception When OPEN/HALF_OPEN (no permits):</b> Throws {@code CircuitBreakerOpenException}
   * instead of executing the operation.
   *
   * @param resourceId Unique identifier for circuit breaker instance (e.g., storageId)
   * @param operation The callable operation to execute
   * @param <T> Return type of the operation
   * @return Result of the operation if executed successfully
   * @throws CircuitBreakerOpenException If circuit breaker is OPEN or HALF_OPEN with no permits
   * @throws Exception If operation fails or other errors occur
   */
  <T> T execute(@NotNull String resourceId, @NotNull Callable<T> operation) throws Exception;

  /**
   * Get or create the circuit breaker for the given resourceId. Thread-safe.
   *
   * @param resourceId Unique identifier for circuit breaker instance
   * @param config Circuit breaker configuration for this resourceId
   * @return this instance for method chaining (never null)
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
