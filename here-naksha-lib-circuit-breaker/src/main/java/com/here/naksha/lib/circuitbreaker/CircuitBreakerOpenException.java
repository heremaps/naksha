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

/**
 * Exception thrown when a circuit breaker is OPEN or HALF_OPEN and no permits are available.
 *
 * <p>This exception indicates that a request was rejected by the circuit breaker because:
 * <ul>
 *   <li><b>OPEN state:</b> The underlying service is considered unhealthy, so the breaker is
 *       rejecting all requests to protect it.
 *   <li><b>HALF_OPEN state:</b> The breaker is probing for recovery, but the maximum number of
 *       concurrent probe requests has been reached.
 * </ul>
 *
 * <p>Clients should retry the request after some delay, or use alternative services.
 */
public class CircuitBreakerOpenException extends Exception {

  /**
   * Constructs a new CircuitBreakerOpenException with the specified detail message.
   *
   * @param message the detail message
   */
  public CircuitBreakerOpenException(String message) {
    super(message);
  }

  /**
   * Constructs a new CircuitBreakerOpenException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause (which is saved for later retrieval)
   */
  public CircuitBreakerOpenException(String message, Throwable cause) {
    super(message, cause);
  }
}
