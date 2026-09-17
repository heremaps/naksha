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
package com.here.naksha.storage.http.circuitbreaker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.naksha.storage.http.HttpStorage;
import org.jetbrains.annotations.NotNull;

/**
 * A Circuit breaker configuration as used by the {@link HttpStorage}.
 */
public class CircuitBreakerProps {

  private static final String SLIDING_WINDOW_SIZE = "slidingWindowSize";
  private static final String MINIMUM_NUMBER_OF_CALLS = "minimumNumberOfCalls";
  private static final String SLOW_CALL_DURATION_THRESHOLD_MS = "slowCallDurationThresholdMs";
  private static final String SLOW_CALL_RATE_THRESHOLD = "slowCallRateThreshold";
  private static final String WAIT_DURATION_IN_OPEN_STATE_MS = "waitDurationInOpenStateMs";
  private static final String PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE = "permittedNumberOfCallsInHalfOpenState";

  @JsonProperty(SLIDING_WINDOW_SIZE)
  private int slidingWindowSize;

  @JsonProperty(MINIMUM_NUMBER_OF_CALLS)
  private int minimumNumberOfCalls;

  @JsonProperty(SLOW_CALL_DURATION_THRESHOLD_MS)
  private long slowCallDurationThresholdMs;

  @JsonProperty(SLOW_CALL_RATE_THRESHOLD)
  private int slowCallRateThreshold;

  @JsonProperty(WAIT_DURATION_IN_OPEN_STATE_MS)
  private long waitDurationInOpenStateMs;

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
