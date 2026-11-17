/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

import static com.here.naksha.app.common.CommonApiTestSetup.setupHandlerAndSpace;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static com.here.naksha.app.common.TestUtil.urlEncoded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.assertions.ResponseAssertions;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import naksha.model.XyzFeatureCollection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class IterateFeaturesTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

  private static final String SPACE_ID = "iterate_features_test_space";

  @BeforeAll
  static void setup() throws URISyntaxException, IOException, InterruptedException {
    setupHandlerAndSpace(nakshaClient, "ReadFeatures/Iterate/setup");
    String initialFeaturesJson = loadFileOrFail("ReadFeatures/Iterate/setup/create_features.json");
    nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
  }

  @Test
  void tc1100_testIterateAllInSinglePage() throws URISyntaxException, InterruptedException, JSONException, IOException {
    // Test API : GET /hub/spaces/{spaceId}/iterate
    // Validate all features getting returned in single iteration without handle,
    // when number of features are less than limit specified
    // Given: iterate parameters
    final String limitQueryParam = "limit=5";
    final String expectedBodyPart = loadFileOrFail("ReadFeatures/Iterate/TC1100_allInSinglePage/iterate_response.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Iterate Features request is submitted to NakshaHub
    final HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + limitQueryParam, streamId);

    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match")
        .hasNoNextPageToken();
  }

  @Test
  void tc1101_testIterateInTwoPages() throws URISyntaxException, InterruptedException, JSONException, IOException {
    // Test API : GET /hub/spaces/{spaceId}/iterate
    // Validate all features getting returned in two iterations, with:
    // - first iteration returning features and nextPageToken
    // - second iteration accepting handle and returning all other features without nextPageToken

    // Given: iterate parameters for first request
    final String limitQueryParam = "limit=3";
    final String firstExpectedBodyPart = loadFileOrFail("ReadFeatures/Iterate/TC1101_inTwoPages/iterate_response_1.json");
    final String secondExpectedBodyPart = loadFileOrFail("ReadFeatures/Iterate/TC1101_inTwoPages/iterate_response_2.json");
    final Map<String, JSONObject> expectedFeatures = featuresById(firstExpectedBodyPart, secondExpectedBodyPart);
    final Set<String> returnedIds = new HashSet<>();
    final String firstStreamId = UUID.randomUUID().toString();

    // When: First iterate Features request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + limitQueryParam, firstStreamId);
    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(firstStreamId);
    assertIteratePage(response, firstExpectedBodyPart, expectedFeatures, returnedIds);

    // Given: iterate parameters for second request
    final String handleQueryParam = "handle=" + urlEncoded(parseJson(response.body(), XyzFeatureCollection.class).getNextPageToken());
    final String secondStreamId = UUID.randomUUID().toString();

    // When: Second iterate Features request is submitted to NakshaHub
    response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + handleQueryParam, secondStreamId);
    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(secondStreamId)
        .hasNoNextPageToken();
    assertIteratePage(response, secondExpectedBodyPart, expectedFeatures, returnedIds);
    assertEquals(expectedFeatures.keySet(), returnedIds, "Iteration did not return all features");
  }

  @Test
  void tc1102_testIterateInThreePages() throws URISyntaxException, InterruptedException, JSONException, IOException {
    // Test API : GET /hub/spaces/{spaceId}/iterate
    // Validate all features getting returned in three iterations, with:
    // - first iteration returning features and nextPageToken
    // - second iteration accepting handle and returning next set of features and nextPageToken
    // - third iteration accepting handle and returning all other features without nextPageToken

    // Given: iterate parameters for first request
    final String limitQueryParam = "limit=2";
    final String firstExpectedBodyPart = loadFileOrFail("ReadFeatures/Iterate/TC1102_inThreePages/iterate_response_1.json");
    final String secondExpectedBodyPart = loadFileOrFail("ReadFeatures/Iterate/TC1102_inThreePages/iterate_response_2.json");
    final String thirdExpectedBodyPart = loadFileOrFail("ReadFeatures/Iterate/TC1102_inThreePages/iterate_response_3.json");
    final Map<String, JSONObject> expectedFeatures =
        featuresById(firstExpectedBodyPart, secondExpectedBodyPart, thirdExpectedBodyPart);
    final Set<String> returnedIds = new HashSet<>();
    final String firstStreamId = UUID.randomUUID().toString();
    // When: First iterate Features request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + limitQueryParam, firstStreamId);
    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(firstStreamId);
    assertIteratePage(response, firstExpectedBodyPart, expectedFeatures, returnedIds);

    // Given: iterate parameters for second request
    final String secondHandleQueryParam = "handle=" + urlEncoded(parseJson(response.body(), XyzFeatureCollection.class).getNextPageToken());
    final String secondStreamId = UUID.randomUUID().toString();
    // When: Second iterate Features request is submitted to NakshaHub
    response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + secondHandleQueryParam, secondStreamId);
    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(secondStreamId);
    assertIteratePage(response, secondExpectedBodyPart, expectedFeatures, returnedIds);

    // Given: iterate parameters for third request
    final String thirdHandleQueryParam = "handle=" + urlEncoded(parseJson(response.body(), XyzFeatureCollection.class).getNextPageToken());
    final String thirdStreamId = UUID.randomUUID().toString();
    // When: Third iterate Features request is submitted to NakshaHub
    response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + thirdHandleQueryParam, thirdStreamId);
    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(thirdStreamId)
        .hasNoNextPageToken();
    assertIteratePage(response, thirdExpectedBodyPart, expectedFeatures, returnedIds);
    assertEquals(expectedFeatures.keySet(), returnedIds, "Iteration did not return all features");
  }


  @Test
  void tc1103_testIterateWithInvalidHandle() throws URISyntaxException, InterruptedException, JSONException, IOException {
    // Test API : GET /hub/spaces/{spaceId}/iterate
    // Validate API returns error (gracefully) when unexpected handle value is supplied

    // Given: iterate parameters
    final String handleQueryParam = "handle=" + urlEncoded("{\"limit\":0,\"offset\":5}");
    final String expectedBodyPart = loadFileOrFail("ReadFeatures/Iterate/TC1103_invalidHandle/iterate_response.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Iterate Features request is submitted to NakshaHub
    final HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + handleQueryParam, streamId);

    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(400)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Iterate Feature Error response body doesn't match");
  }

  @Test
  void tc1104_testIterateInTwoPagesWithPropSelection() throws URISyntaxException, InterruptedException, IOException, JSONException {
    // Test API : GET /hub/spaces/{spaceId}/iterate
    // Validate all features getting returned in two iterations, with:
    // - first iteration returning features and nextPageToken, selecting: 1 normal prop, 1 URI encoded prop, 1 prop not existing
    // - second iteration accepting handle but use the wrong selection delimiter (should return 400)

    // Given: iterate parameters for first request
    final String limitQueryParam = "limit=3";
    final String firstStreamId = UUID.randomUUID().toString();
    final String firstExpectedBodyPart = loadFileOrFail(
        "ReadFeatures/Iterate/TC1104_testIterateInTwoPagesWithPropSelection/iterate_response_1.json")
        .replaceAll("\\{\\{streamId}}", firstStreamId);
    final Map<String, JSONObject> expectedFeatures = featuresById(
        loadFileOrFail("ReadFeatures/Iterate/TC1104_testIterateInTwoPagesWithPropSelection/all_features.json"));
    final String selectionParams = "selection=p.speedLimit,p.unknown_prop,%s".formatted(urlEncoded("p.@ns:com:here:xyz.tags"));

    // When: First iterate Features request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + limitQueryParam + "&" + selectionParams,
        firstStreamId);
    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(firstStreamId);
    assertIteratePage(response, firstExpectedBodyPart, expectedFeatures, new HashSet<>());

    // Given: iterate parameters for second request
    final String handleQueryParam = "handle=" + urlEncoded(parseJson(response.body(), XyzFeatureCollection.class).getNextPageToken());
    final String secondExpectedBodyPart = loadFileOrFail(
        "ReadFeatures/Iterate/TC1104_testIterateInTwoPagesWithPropSelection/iterate_response_2.json");
    final String secondStreamId = UUID.randomUUID().toString();
    final String selectionWrongDelimiterParams = "selection=p.speedLimit+p.unknown_prop";

    // When: Second iterate Features request is submitted to NakshaHub
    response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + handleQueryParam + "&" + selectionWrongDelimiterParams,
        secondStreamId);
    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(400)
        .hasStreamIdHeader(secondStreamId)
        .hasJsonBody(secondExpectedBodyPart, "Final Iterate response body doesn't match");
  }

  @Test
  void tc1106_shouldFailForInvalidQueryParam() throws URISyntaxException, InterruptedException, IOException {
    // Given: iterate parameters for first request
    final String invalidLimitQueryParam = "limit=3.5";
    final String expectedErrorBody = loadFileOrFail("ReadFeatures/Iterate/TC1106_invalidQueryParam/error_response.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Iterate Features request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/iterate" + "?" + invalidLimitQueryParam, streamId);

    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
        .hasStatus(400)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedErrorBody, "Expected error body doesn't match");
  }

  /**
   * Iterate does not guarantee any order, so the features of a page are only checked to be expected and not returned before.
   *
   * @param response         the iterate response.
   * @param expectedPage     the expected page, defines the expected feature count and all other members (e.g. nextPageToken).
   * @param expectedFeatures all features that may be returned, by id.
   * @param returnedIds      the ids returned by previous pages; ids of this page are added.
   */
  private static void assertIteratePage(
      HttpResponse<String> response,
      String expectedPage,
      Map<String, JSONObject> expectedFeatures,
      Set<String> returnedIds) throws JSONException {
    final JSONObject expected = new JSONObject(expectedPage);
    final int expectedCount = expected.getJSONArray("features").length();
    expected.remove("features");
    JSONAssert.assertEquals("Iterate response body doesn't match", expected.toString(), response.body(), JSONCompareMode.LENIENT);
    final JSONArray actualFeatures = new JSONObject(response.body()).getJSONArray("features");
    assertEquals(expectedCount, actualFeatures.length(), "Unexpected number of features in iterate page");
    for (int i = 0; i < actualFeatures.length(); i++) {
      final JSONObject actualFeature = actualFeatures.getJSONObject(i);
      final String id = actualFeature.getString("id");
      final JSONObject expectedFeature = expectedFeatures.get(id);
      assertNotNull(expectedFeature, "Unexpected feature returned: " + id);
      assertTrue(returnedIds.add(id), "Feature returned more than once: " + id);
      JSONAssert.assertEquals("Feature " + id + " doesn't match", expectedFeature, actualFeature, JSONCompareMode.LENIENT);
    }
  }

  private static Map<String, JSONObject> featuresById(String... featureCollections) throws JSONException {
    final Map<String, JSONObject> featuresById = new HashMap<>();
    for (String featureCollection : featureCollections) {
      final JSONArray features = new JSONObject(featureCollection).getJSONArray("features");
      for (int i = 0; i < features.length(); i++) {
        final JSONObject feature = features.getJSONObject(i);
        featuresById.put(feature.getString("id"), feature);
      }
    }
    return featuresById;
  }
}
