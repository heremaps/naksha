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
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resilience4j-based implementation of {@link ICircuitBreaker}. Manages per-resourceId circuit
 * breaker instances with slow-call-based state transitions.
 *
 * <p>Singleton pattern enforced through {@link #getInstance()} factory method. Direct instantiation
 * is prevented via private constructor to ensure only one instance exists application-wide.
 */
class Resilience4jCircuitBreaker implements ICircuitBreaker {

  private static final Logger log = LoggerFactory.getLogger(Resilience4jCircuitBreaker.class);
  private static Resilience4jCircuitBreaker instance = null;

  private final CircuitBreakerRegistry registry;

  private Resilience4jCircuitBreaker() {
    this.registry = CircuitBreakerRegistry.ofDefaults();
  }

  /**
   * Get the singleton instance of Resilience4jCircuitBreaker.
   *
   * @return singleton instance
   */
  @NotNull
  static Resilience4jCircuitBreaker getInstance() {
    if (instance == null) {
      synchronized (Resilience4jCircuitBreaker.class) {
        if (instance == null) {
          instance = new Resilience4jCircuitBreaker();
        }
      }
    }
    return instance;
  }

  @Override
  public <T> T execute(@NotNull String resourceId, @NotNull Callable<T> operation) throws Exception {
    CircuitBreaker cb = registry.find(resourceId).orElse(null);
    if (cb == null) {
      log.warn(
          "CircuitBreaker not found for resourceId: {}. Executing operation without CircuitBreaker protection.",
          resourceId);
      return operation.call();
    }
    try {
      return cb.executeCallable(operation);
    } catch (CallNotPermittedException e) {
      // Wrap Resilience4j exception with abstraction
      throw new CircuitBreakerOpenException(
          "Circuit breaker '" + resourceId + "' is " + cb.getState() + " and does not permit further calls",
          e);
    }
  }

  @Override
  public @NotNull ICircuitBreaker getOrCreateCircuitBreaker(
      @NotNull String resourceId, @NotNull CircuitBreakerProps config) {
    if (!exists(resourceId)) {
      log.info("Creating CircuitBreaker for resourceId: {} with config: {}", resourceId, config);
      registry.circuitBreaker(resourceId, buildCircuitBreakerConfig(config));
    }
    return this;
  }

  @Override
  public void remove(@NotNull String resourceId) {
    log.info("Removing CircuitBreaker for resourceId: {}", resourceId);
    registry.remove(resourceId);
  }

  @Override
  public void clear() {
    int count = registry.getAllCircuitBreakers().size();
    log.info("Clearing entire CircuitBreaker registry ({} entries)", count);
    registry.getAllCircuitBreakers().forEach(cb -> registry.remove(cb.getName()));
  }

  @Override
  public boolean isClosed(@NotNull String resourceId) {
    return registry.find(resourceId)
        .map(cb -> cb.getState() == CircuitBreaker.State.CLOSED)
        .orElse(false);
  }

  @Override
  public boolean exists(@NotNull String resourceId) {
    return registry.find(resourceId).isPresent();
  }

  private @NotNull CircuitBreakerConfig buildCircuitBreakerConfig(@NotNull CircuitBreakerProps config) {
    return CircuitBreakerConfig.custom()
        .slidingWindowType(SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(config.getSlidingWindowSize())
        .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
        .slowCallDurationThreshold(java.time.Duration.ofMillis(config.getSlowCallDurationThresholdMs()))
        .slowCallRateThreshold(config.getSlowCallRateThreshold())
        .waitDurationInOpenState(java.time.Duration.ofMillis(config.getWaitDurationInOpenStateMs()))
        .permittedNumberOfCallsInHalfOpenState(config.getPermittedNumberOfCallsInHalfOpenState())
        .ignoreExceptions(Throwable.class)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build();
  }
}
