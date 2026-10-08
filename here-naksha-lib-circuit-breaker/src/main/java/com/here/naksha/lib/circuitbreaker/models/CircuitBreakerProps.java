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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration properties for the sliding-window circuit breaker pattern. Controls how the
 * circuit breaker monitors and manages the state transitions based on slow request rates.
 */
public class CircuitBreakerProps {

  private static final String SLIDING_WINDOW_SIZE = "slidingWindowSize";
  private static final String MINIMUM_NUMBER_OF_CALLS = "minimumNumberOfCalls";
  private static final String SLOW_CALL_DURATION_THRESHOLD_MS = "slowCallDurationThresholdMs";
  private static final String SLOW_CALL_RATE_THRESHOLD = "slowCallRateThreshold";
  private static final String WAIT_DURATION_IN_OPEN_STATE_MS = "waitDurationInOpenStateMs";
  private static final String PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE = "permittedNumberOfCallsInHalfOpenState";

  /**
   * The total number of most recent requests that should be measured to make a decision on
   * opening/closing the circuit breaker.
   *
   * <p>Example: A slidingWindowSize of 20 means the circuit breaker evaluates the last 20
   * requests.
   *
   * <p>Unit: Number of requests (count-based sliding window)
   */
  @JsonProperty(SLIDING_WINDOW_SIZE)
  private int slidingWindowSize;

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
  @JsonProperty(MINIMUM_NUMBER_OF_CALLS)
  private int minimumNumberOfCalls;

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
  @JsonProperty(SLOW_CALL_DURATION_THRESHOLD_MS)
  private long slowCallDurationThresholdMs;

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
   * <p>Unit: Percentage (0-100)
   */
  @JsonProperty(SLOW_CALL_RATE_THRESHOLD)
  private int slowCallRateThreshold;

  /**
   * The duration the circuit breaker waits in OPEN state before transitioning to HALF_OPEN.
   *
   * <p>During the OPEN state, the circuit breaker rejects most requests and throttles only a
   * small number. After this wait duration expires, it automatically transitions to HALF_OPEN to
   * probe if the underlying service has recovered.
   *
   * <p>Example: A waitDurationInOpenStateMs of 60000 means the circuit breaker will wait 60
   * seconds in OPEN state before attempting to probe recovery via HALF_OPEN.
   *
   * <p>Unit: Milliseconds (ms)
   */
  @JsonProperty(WAIT_DURATION_IN_OPEN_STATE_MS)
  private long waitDurationInOpenStateMs;

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
  @JsonProperty(PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE)
  private int permittedNumberOfCallsInHalfOpenState;

  @JsonCreator
  public CircuitBreakerProps(
      @JsonProperty(SLIDING_WINDOW_SIZE) @NotNull Integer slidingWindowSize,
      @JsonProperty(MINIMUM_NUMBER_OF_CALLS) @NotNull Integer minimumNumberOfCalls,
      @JsonProperty(SLOW_CALL_DURATION_THRESHOLD_MS) @NotNull Long slowCallDurationThresholdMs,
      @JsonProperty(SLOW_CALL_RATE_THRESHOLD) @NotNull Integer slowCallRateThreshold,
      @JsonProperty(WAIT_DURATION_IN_OPEN_STATE_MS) @NotNull Long waitDurationInOpenStateMs,
      @JsonProperty(PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE) @NotNull
          Integer permittedNumberOfCallsInHalfOpenState) {
    this.slidingWindowSize = slidingWindowSize;
    this.minimumNumberOfCalls = minimumNumberOfCalls;
    this.slowCallDurationThresholdMs = slowCallDurationThresholdMs;
    this.slowCallRateThreshold = slowCallRateThreshold;
    this.waitDurationInOpenStateMs = waitDurationInOpenStateMs;
    this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
  }

  public int getSlidingWindowSize() {
    return slidingWindowSize;
  }

  public int getMinimumNumberOfCalls() {
    return minimumNumberOfCalls;
  }

  public long getSlowCallDurationThresholdMs() {
    return slowCallDurationThresholdMs;
  }

  public int getSlowCallRateThreshold() {
    return slowCallRateThreshold;
  }

  public long getWaitDurationInOpenStateMs() {
    return waitDurationInOpenStateMs;
  }

  public int getPermittedNumberOfCallsInHalfOpenState() {
    return permittedNumberOfCallsInHalfOpenState;
  }
}
