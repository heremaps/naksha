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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resilience4j-based implementation of {@link ICircuitBreaker}. Manages per-resourceId circuit
 * breaker instances with slow-call-based state transitions.
 *
 * <p>Package-private: This class should only be instantiated through {@link CircuitBreakerProvider}
 * factory methods. Consumers must use the factory to obtain circuit breaker instances.
 */
class Resilience4jCircuitBreaker implements ICircuitBreaker {

  private static final Logger log = LoggerFactory.getLogger(Resilience4jCircuitBreaker.class);

  private final CircuitBreakerRegistry registry;

  public Resilience4jCircuitBreaker() {
    this.registry = CircuitBreakerRegistry.ofDefaults();
  }

  @Override
  public <T> T execute(@NotNull String resourceId, @NotNull Callable<T> operation) throws Exception {
    CircuitBreaker cb = registry.find(resourceId).orElse(null);
    if (cb == null) {
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
    registry.circuitBreaker(resourceId, buildCircuitBreakerConfig(resourceId, config));
    return this;
  }

  @Override
  public void remove(@NotNull String resourceId) {
    registry.remove(resourceId);
  }

  @Override
  public void clear() {
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

  public @Nullable CircuitBreaker findCircuitBreaker(@NotNull String resourceId) {
    return registry.find(resourceId).orElse(null);
  }

  private @NotNull CircuitBreakerConfig buildCircuitBreakerConfig(
      @NotNull String resourceId, @NotNull CircuitBreakerProps config) {
    log.info(
        "Creating CircuitBreaker for resourceId: {} with config: slidingWindowSize={}, minimumNumberOfCalls={}, slowCallDurationThresholdMs={}, slowCallRateThreshold={}%, waitDurationInOpenStateMs={}, permittedNumberOfCallsInHalfOpenState={}",
        resourceId,
        config.getSlidingWindowSize(),
        config.getMinimumNumberOfCalls(),
        config.getSlowCallDurationThresholdMs(),
        config.getSlowCallRateThreshold(),
        config.getWaitDurationInOpenStateMs(),
        config.getPermittedNumberOfCallsInHalfOpenState());

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
