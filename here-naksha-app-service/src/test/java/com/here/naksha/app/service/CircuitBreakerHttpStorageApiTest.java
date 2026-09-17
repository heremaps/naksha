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
package com.here.naksha.app.service;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.here.naksha.app.common.ApiTest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

@WireMockTest(httpPort = CircuitBreakerHttpStorageApiTest.MOCK_PORT)
class CircuitBreakerHttpStorageApiTest extends ApiTest {
  public static final int MOCK_PORT = 9094;
  private static final String ENDPOINT = "/my_env/my_storage/my_feat_type/features";
  private static final String TEST_DIR_PATH = "CircuitBreaker/";
  private static final String SETUP_DIR_PATH = TEST_DIR_PATH + "setup/";
  private static final String FEATURE_RESPONSE_FIXTURE = SETUP_DIR_PATH + "feature_response.json";

  @Test
  void tc1000_testWorksWithoutCircuitBreakerConfigInStorage() throws Exception {
    // Test API: GET /hub/spaces/{spaceId}/features/{featureId}
    // Validate requests pass through when circuit breaker config is not present on storage.
    // Given: Space setup with HTTP storage that has no circuit breaker configuration
    SetupIds ids = createStorageHandlerSpace("cb_no_config", loadFileOrFail(TEST_DIR_PATH + "TC1000_noCircuitBreaker/no_cb_storage_properties.json"));
    String streamId = UUID.randomUUID().toString();

    // Given: downstream storage returns feature response
    UrlPattern endpointPath = urlPathEqualTo(ENDPOINT + "/1");
    stubFor(get(endpointPath).willReturn(okJson(loadFileOrFail(FEATURE_RESPONSE_FIXTURE))));

    // When: feature read request is submitted to NakshaHub
    HttpResponse<String> response = getNakshaClient().get("hub/spaces/" + ids.spaceId + "/features/1", streamId);

    // Then: request succeeds and is forwarded to storage
    assertThat(response).hasStatus(200).hasStreamIdHeader(streamId);
    verify(1, getRequestedFor(endpointPath));
  }

  @Test
  void tc1100_testThrowsValidationErrorForPartialCircuitBreakerConfig() throws Exception {
    // Test API: POST /hub/storages
    // Validate storage creation fails when circuit breaker config is partially defined.
    HttpResponse<String> response = createStorage(
        "cb_partial_config", loadFileOrFail(TEST_DIR_PATH + "TC1100_partialCircuitBreakerConfig/partial_cb_storage_properties.json"));
    assertThat(response).hasStatus(400);
    assertTrue(response.body().contains("IllegalArgument"));
  }

  @Test
  void tc1200_testThrowsValidationErrorWhenSlowDurationIsGreaterThanOrEqualSocketTimeout() throws Exception {
    // Test API: POST /hub/storages
    // Validate slowCallDurationThresholdMs must be lower than socketTimeout.
    HttpResponse<String> response = createStorage(
        "cb_invalid_slow_duration", loadFileOrFail(TEST_DIR_PATH + "TC1200_invalidSlowDuration/invalid_slow_duration_storage_properties.json"));
    assertTrue(response.body().contains("slowCallDurationThresholdMs must be lower than socketTimeout"));
  }

  @Test
  void tc1300_testThrowsValidationErrorWhenMinimumCallsExceedSlidingWindow() throws Exception {
    // Test API: POST /hub/storages
    // Validate minimumNumberOfCalls cannot exceed slidingWindowSize.
    HttpResponse<String> response = createStorage(
        "cb_invalid_minimum_calls", loadFileOrFail(TEST_DIR_PATH + "TC1300_invalidMinimumCalls/invalid_minimum_calls_storage_properties.json"));
    assertTrue(response.body().contains("minimumNumberOfCalls must be <= slidingWindowSize"));
  }

  @Test
  void tc1400_testThrowsValidationErrorWhenSlowCallRateThresholdIsGreaterThan100() throws Exception {
    // Test API: POST /hub/storages
    // Validate slowCallRateThreshold must stay within 1..100.
    HttpResponse<String> response = createStorage(
        "cb_invalid_slow_rate_threshold", loadFileOrFail(TEST_DIR_PATH + "TC1400_invalidSlowRateThreshold/invalid_slow_rate_threshold_storage_properties.json"));
    assertTrue(response.body().contains("slowCallRateThreshold must be in range 1..100"));
  }

  @Test
  void tc2000_testTransitionsClosedToOpenToHalfOpenToClosed() throws Exception {
    // Test API: GET /hub/spaces/{spaceId}/features/{featureId}
    // Validate circuit breaker transitions CLOSED -> OPEN -> HALF_OPEN -> CLOSED using timings.
    // CbConfig: "slidingWindowSize": 10, "minimumNumberOfCalls": 5, "slowCallDurationThresholdMs": 500, "slowCallRateThreshold": 50, "waitDurationInOpenStateMs": 5000, "permittedNumberOfCallsInHalfOpenState": 4
    SetupIds ids = createStorageHandlerSpace(
        "cb_transition_to_closed", loadFileOrFail(TEST_DIR_PATH + "TC2000_validCircuitBreaker/with_valid_cb_storage_properties.json"));
    UrlPattern endpointPath = urlPathEqualTo(ENDPOINT + "/1");

    // Given: storage responses are slow enough to count toward slow-call threshold.
    stubFor(get(endpointPath).willReturn(okJson(loadFileOrFail(FEATURE_RESPONSE_FIXTURE)).withFixedDelay(600)));

    // When: 6 requests are issued sequentially, the first 5 succeed and the sixth is blocked.
    for (int i = 0; i < 5; i++) {
      assertThat(callFeatureById(ids.spaceId)).hasStatus(200);
    }
    HttpResponse<String> openResponse = callFeatureById(ids.spaceId);

    // Then: the breaker opens and further requests are rejected immediately.
    assertTrue(containsCallNotPermitted(openResponse));
    for (int i = 0; i < 20; i++) {
      assertTrue(containsCallNotPermitted(callFeatureById(ids.spaceId)));
    }
    verify(5, getRequestedFor(endpointPath));

    // Given: OPEN wait duration elapsed and probes are now fast.
    Thread.sleep(5100);
    stubFor(get(endpointPath).willReturn(okJson(loadFileOrFail(FEATURE_RESPONSE_FIXTURE))));

    // When: 6 concurrent probes are sent in HALF_OPEN, only 4 are permitted.
    List<HttpResponse<String>> halfOpenResponses = callFeatureConcurrently(ids.spaceId, 6);
    assertEquals(4, countSuccessfulResponses(halfOpenResponses));
    assertEquals(2, countCallNotPermittedResponses(halfOpenResponses));

    // Then: after successful probes, the breaker closes and the next calls pass through.
    verify(9, getRequestedFor(endpointPath));
    for (int i = 0; i < 10; i++) {
      assertThat(callFeatureById(ids.spaceId)).hasStatus(200);
    }
  }

  @Test
  void tc2100_testTransitionsClosedToOpenToHalfOpenToOpen() throws Exception {
    // Test API: GET /hub/spaces/{spaceId}/features/{featureId}
    // Validate circuit breaker transitions CLOSED -> OPEN -> HALF_OPEN -> OPEN using timings.
    // CbConfig: "slidingWindowSize": 8, "minimumNumberOfCalls": 4, "slowCallDurationThresholdMs": 400, "slowCallRateThreshold": 50, "waitDurationInOpenStateMs": 4000, "permittedNumberOfCallsInHalfOpenState": 3
    SetupIds ids = createStorageHandlerSpace(
        "cb_transition_to_open",
        loadFileOrFail(TEST_DIR_PATH + "TC2100_validCircuitBreaker/with_valid_cb_storage_properties.json"));
    UrlPattern endpointPath = urlPathEqualTo(ENDPOINT + "/1");

    // Given: downstream responses are slow enough to trip the breaker.
    stubFor(get(endpointPath).willReturn(okJson(loadFileOrFail(FEATURE_RESPONSE_FIXTURE)).withFixedDelay(500)));

    // When: 6 requests are issued sequentially, the first 4 succeed and the next 2 are blocked.
    for (int i = 0; i < 4; i++) {
      assertThat(callFeatureById(ids.spaceId)).hasStatus(200);
    }
    HttpResponse<String> openResponse = callFeatureById(ids.spaceId);
    HttpResponse<String> openResponse2 = callFeatureById(ids.spaceId);

    // Then: the breaker is OPEN and blocks the remaining requests immediately.
    assertTrue(containsCallNotPermitted(openResponse));
    assertTrue(containsCallNotPermitted(openResponse2));
    for (int i = 0; i < 10; i++) {
      assertTrue(containsCallNotPermitted(callFeatureById(ids.spaceId)));
    }
    verify(4, getRequestedFor(endpointPath));

    // Given: OPEN wait duration elapsed and HALF_OPEN probes are still slow.
    Thread.sleep(4200);
    List<HttpResponse<String>> halfOpenResponses = callFeatureConcurrently(ids.spaceId, 5);

    // Then: only 3 probes are permitted and the breaker opens again after slow responses.
    assertEquals(3, countSuccessfulResponses(halfOpenResponses));
    assertEquals(2, countCallNotPermittedResponses(halfOpenResponses));
    for (int i = 0; i < 5; i++) {
      assertTrue(containsCallNotPermitted(callFeatureById(ids.spaceId)));
    }
    verify(7, getRequestedFor(endpointPath));
  }

  @Test
  void tc2200_testFast5xxResponsesDoNotOpenCircuitBreaker() throws Exception {
    // Test API: GET /hub/spaces/{spaceId}/features/{featureId}
    // Validate fast 5xx responses do not trip the circuit breaker counters.
    SetupIds ids = createStorageHandlerSpace(
        "cb_fast_5xx",
        loadFileOrFail(TEST_DIR_PATH + "TC2000_validCircuitBreaker/with_valid_cb_storage_properties.json"));
    UrlPattern endpointPath = urlPathEqualTo(ENDPOINT + "/1");

    // Given: storage responds quickly with 5xx errors.
    stubFor(get(endpointPath).willReturn(com.github.tomakehurst.wiremock.client.WireMock.serverError()));

    // When: multiple fast 5xx responses are returned.
    for (int i = 0; i < 10; i++) {
      HttpResponse<String> response = callFeatureById(ids.spaceId);
      assertThat(response).hasStatus(500);
      assertTrue(!containsCallNotPermitted(response));
    }
    verify(10, getRequestedFor(endpointPath));

    // Given: downstream recovers.
    stubFor(get(endpointPath).willReturn(okJson(loadFileOrFail(FEATURE_RESPONSE_FIXTURE))));

    // Then: the breaker is still closed and traffic continues to flow.
    assertThat(callFeatureById(ids.spaceId)).hasStatus(200);
    verify(11, getRequestedFor(endpointPath));
  }

  private HttpResponse<String> callFeatureById(String spaceId) throws Exception {
    return getNakshaClient().get("hub/spaces/" + spaceId + "/features/1", UUID.randomUUID().toString());
  }

  private List<HttpResponse<String>> callFeatureConcurrently(String spaceId, int requestCount) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(requestCount);
    CountDownLatch ready = new CountDownLatch(requestCount);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<HttpResponse<String>>> futures = new ArrayList<>();
      for (int i = 0; i < requestCount; i++) {
        futures.add(executor.submit(() -> {
          ready.countDown();
          start.await(5, TimeUnit.SECONDS);
          return callFeatureById(spaceId);
        }));
      }
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<HttpResponse<String>> responses = new ArrayList<>(requestCount);
      for (Future<HttpResponse<String>> future : futures) {
        responses.add(future.get(10, TimeUnit.SECONDS));
      }
      return responses;
    } finally {
      executor.shutdownNow();
    }
  }

  private int countSuccessfulResponses(List<HttpResponse<String>> responses) {
    int count = 0;
    for (HttpResponse<String> response : responses) {
      if (response.statusCode() == 200) {
        count++;
      }
    }
    return count;
  }

  private int countCallNotPermittedResponses(List<HttpResponse<String>> responses) {
    int count = 0;
    for (HttpResponse<String> response : responses) {
      if (containsCallNotPermitted(response)) {
        count++;
      }
    }
    return count;
  }

  private boolean containsCallNotPermitted(HttpResponse<String> response) {
    if (response.statusCode() != 500) {
      return false;
    }
    final String body = response.body();
    return body.contains("CircuitBreaker") && body.contains("does not permit further calls");
  }

  private SetupIds createStorageHandlerSpace(String prefix, String storagePropertiesJson) throws Exception {
    String storageId = prefix + "_storage";
    String handlerId = prefix + "_handler";
    String spaceId = prefix + "_space";
    assertThat(createStorage(prefix, storagePropertiesJson)).hasStatus(200);
    String handlerJson = applyTokens(loadFileOrFail(SETUP_DIR_PATH + "create_event_handler_template.json"), "{{handlerId}}", handlerId, "{{storageId}}", storageId);
    String spaceJson = applyTokens(loadFileOrFail(SETUP_DIR_PATH + "create_space_template.json"), "{{spaceId}}", spaceId, "{{handlerId}}", handlerId);

    assertThat(getNakshaClient().post("hub/handlers", handlerJson, UUID.randomUUID().toString())).hasStatus(200);
    assertThat(getNakshaClient().post("hub/spaces", spaceJson, UUID.randomUUID().toString())).hasStatus(200);
    return new SetupIds(storageId, handlerId, spaceId);
  }

  private HttpResponse<String> createStorage(String prefix, String storagePropertiesJson) throws Exception {
    String storageId = prefix + "_storage";
    String storageJson = applyTokens(loadFileOrFail(SETUP_DIR_PATH + "create_storage_template.json"), "{{storageId}}", storageId, "{{storageProperties}}", storagePropertiesJson);
    return getNakshaClient().post("hub/storages", storageJson, UUID.randomUUID().toString());
  }

  private String applyTokens(String template, String... keyValuePairs) {
    String result = template;
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      result = result.replace(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return result;
  }

  private record SetupIds(String storageId, String handlerId, String spaceId) {}
}
