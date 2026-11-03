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
package com.here.naksha.lib.circuitbreaker.models;

import naksha.base.JvmAnyObjectUtil;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration properties for the sliding-window circuit breaker pattern. Controls how the
 * circuit breaker monitors and manages the state transitions based on slow request rates.
 */
public class CircuitBreakerProps extends NakshaProperties {

  public static final String SLIDING_WINDOW_SIZE = "slidingWindowSize";
  public static final String MINIMUM_NUMBER_OF_CALLS = "minimumNumberOfCalls";
  public static final String SLOW_CALL_DURATION_THRESHOLD_MS = "slowCallDurationThresholdMs";
  public static final String SLOW_CALL_RATE_THRESHOLD = "slowCallRateThreshold";
  public static final String WAIT_DURATION_IN_OPEN_STATE_MS = "waitDurationInOpenStateMs";
  public static final String PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE = "permittedNumberOfCallsInHalfOpenState";

  public CircuitBreakerProps() {}

  /**
   * The total number of most recent requests that should be measured to make a decision on
   * opening/closing the circuit breaker.
   *
   * <p>Example: A slidingWindowSize of 20 means the circuit breaker evaluates the last 20
   * requests.
   *
   * <p>Unit: Number of requests (count-based sliding window)
   */
  public Integer getSlidingWindowSize() {
    final Integer value = JvmAnyObjectUtil.getProperty(this, SLIDING_WINDOW_SIZE, Integer.class);
    return value != null ? value : parseIntOrNull(getRaw(SLIDING_WINDOW_SIZE));
  }

  public void setSlidingWindowSize(final @Nullable Integer slidingWindowSize) {
    setRaw(SLIDING_WINDOW_SIZE, slidingWindowSize);
  }

  /**
   * The minimum number of requests that must be observed before the circuit breaker checks
   * against slow-call threshold values.
   *
   * <p>When starting from zero, the circuit breaker will not make any state transition decisions
   * until at least this many requests have been observed.
   *
   * <p>Example: A minimumNumberOfCalls of 10 means the circuit breaker needs at least 10
   * requests before evaluating if it should open.
   *
   * <p>Unit: Number of requests (count)
   */
  public Integer getMinimumNumberOfCalls() {
    final Integer value = JvmAnyObjectUtil.getProperty(this, MINIMUM_NUMBER_OF_CALLS, Integer.class);
    return value != null ? value : parseIntOrNull(getRaw(MINIMUM_NUMBER_OF_CALLS));
  }

  public void setMinimumNumberOfCalls(final @Nullable Integer minimumNumberOfCalls) {
    setRaw(MINIMUM_NUMBER_OF_CALLS, minimumNumberOfCalls);
  }

  /**
   * The duration threshold above which a request is considered "slow".
   *
   * <p>Any request that takes longer than this threshold will be counted as a slow call. The
   * circuit breaker uses the percentage of slow calls against the slow call rate threshold to
   * determine state transitions.
   *
   * <p>Example: A slowCallDurationThresholdMs of 10000 means any request taking more than 10
   * seconds is considered slow.
   *
   * <p>Unit: Milliseconds (ms)
   */
  public Long getSlowCallDurationThresholdMs() {
    final Long value = JvmAnyObjectUtil.getProperty(this, SLOW_CALL_DURATION_THRESHOLD_MS, Long.class);
    return value != null ? value : parseLongOrNull(getRaw(SLOW_CALL_DURATION_THRESHOLD_MS));
  }

  public void setSlowCallDurationThresholdMs(final @Nullable Long slowCallDurationThresholdMs) {
    setRaw(SLOW_CALL_DURATION_THRESHOLD_MS, slowCallDurationThresholdMs);
  }

  /**
   * The percentage threshold of slow calls that triggers the circuit breaker to open.
   *
   * <p>When the percentage of slow calls (out of the sliding window) exceeds this threshold, the
   * circuit breaker transitions to OPEN state and starts rejecting requests. If the slow rate
   * remains above this threshold during HALF_OPEN state, the circuit breaker reopens.
   *
   * <p>Example: A slowCallRateThreshold of 50 means if 50% or more of the requests in the
   * sliding window are slow, the circuit breaker opens.
   *
   * <p>Unit: Percentage (1-100)
   */
  public Integer getSlowCallRateThreshold() {
    final Integer value = JvmAnyObjectUtil.getProperty(this, SLOW_CALL_RATE_THRESHOLD, Integer.class);
    return value != null ? value : parseIntOrNull(getRaw(SLOW_CALL_RATE_THRESHOLD));
  }

  public void setSlowCallRateThreshold(final @Nullable Integer slowCallRateThreshold) {
    setRaw(SLOW_CALL_RATE_THRESHOLD, slowCallRateThreshold);
  }

  /**
   * The duration the circuit breaker waits in OPEN state before transitioning to HALF_OPEN.
   *
   * <p>During the OPEN state, the circuit breaker rejects all requests. After this wait duration
   * expires, it automatically transitions to HALF_OPEN to probe if the underlying service has
   * recovered.
   *
   * <p>Example: A waitDurationInOpenStateMs of 60000 means the circuit breaker will wait 60
   * seconds in OPEN state before attempting to probe recovery via HALF_OPEN.
   *
   * <p>Unit: Milliseconds (ms)
   */
  public Long getWaitDurationInOpenStateMs() {
    final Long value = JvmAnyObjectUtil.getProperty(this, WAIT_DURATION_IN_OPEN_STATE_MS, Long.class);
    return value != null ? value : parseLongOrNull(getRaw(WAIT_DURATION_IN_OPEN_STATE_MS));
  }

  public void setWaitDurationInOpenStateMs(final @Nullable Long waitDurationInOpenStateMs) {
    setRaw(WAIT_DURATION_IN_OPEN_STATE_MS, waitDurationInOpenStateMs);
  }

  /**
   * The maximum number of requests allowed when the circuit breaker is in HALF_OPEN
   * state.
   *
   * <p>During HALF_OPEN, the circuit breaker allows a small number of test requests through to
   * determine if the underlying service has recovered. If these test requests result in a slow
   * rate below the threshold, the circuit closes. Otherwise, it reopens.
   *
   * <p>Example: A permittedNumberOfCallsInHalfOpenState of 5 means only 5 requests are allowed
   * during the HALF_OPEN probe phase.
   *
   * <p>Unit: Number of requests (count)
   */
  public Integer getPermittedNumberOfCallsInHalfOpenState() {
    final Integer value = JvmAnyObjectUtil.getProperty(this, PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE, Integer.class);
    return value != null ? value : parseIntOrNull(getRaw(PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE));
  }

  public void setPermittedNumberOfCallsInHalfOpenState(final @Nullable Integer permittedNumberOfCallsInHalfOpenState) {
    setRaw(PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE, permittedNumberOfCallsInHalfOpenState);
  }

  private static @Nullable Integer parseIntOrNull(final @Nullable Object raw) {
    if (!(raw instanceof String)) {
      return null;
    }
    try {
      return Integer.parseInt(((String) raw).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static @Nullable Long parseLongOrNull(final @Nullable Object raw) {
    if (!(raw instanceof String)) {
      return null;
    }
    try {
      return Long.parseLong(((String) raw).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public @NotNull String toString() {
    return "CircuitBreakerProps{" + "slidingWindowSize="
        + getSlidingWindowSize() + ", minimumNumberOfCalls="
        + getMinimumNumberOfCalls() + ", slowCallDurationThresholdMs="
        + getSlowCallDurationThresholdMs() + ", slowCallRateThreshold="
        + getSlowCallRateThreshold() + "%" + ", waitDurationInOpenStateMs="
        + getWaitDurationInOpenStateMs() + ", permittedNumberOfCallsInHalfOpenState="
        + getPermittedNumberOfCallsInHalfOpenState() + '}';
  }
}
