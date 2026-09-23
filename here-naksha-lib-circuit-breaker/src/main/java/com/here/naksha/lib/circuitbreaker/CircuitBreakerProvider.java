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
import org.jetbrains.annotations.Nullable;

/**
 * Abstraction for circuit breaker provider. Implementations manage per-resourceId circuit breaker
 * instances.
 */
public interface CircuitBreakerProvider {

  /**
   * Execute a callable operation with circuit breaker protection scoped to resourceId.
   *
   * @param resourceId Unique identifier for circuit breaker instance (e.g., storageId)
   * @param operation The callable operation to execute
   * @param config Circuit breaker configuration for this resourceId
   * @param <T> Return type of the operation
   * @return Result of the operation
   * @throws Exception If operation fails or circuit breaker is open
   */
  <T> T executeWithCircuitBreaker(
      @NotNull String resourceId, @NotNull Callable<T> operation, @NotNull CircuitBreakerProps config)
      throws Exception;

  /**
   * Get or create the circuit breaker for the given resourceId.
   */
  @Nullable
  CircuitBreakerHandle getCircuitBreaker(@NotNull String resourceId, @NotNull CircuitBreakerProps config);

  /**
   * Remove circuit breaker for the given resourceId.
   */
  void remove(@NotNull String resourceId);

  /**
   * Clear all circuit breakers.
   */
  void clear();
}
