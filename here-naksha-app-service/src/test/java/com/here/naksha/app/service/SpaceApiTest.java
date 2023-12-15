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

import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static com.here.naksha.app.common.TestUtil.getHeader;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.naksha.Space;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

// @ExtendWith({ApiTestMaintainer.class})
class SpaceApiTest extends ApiTest {

  @Test
  @Order(3)
  void tc0200_testCreateSpace() throws Exception {
    // Test API : POST /hub/spaces
    // 1. Load test data
    final String spaceJson = loadFileOrFail("TC0200_createSpace/create_space.json");
    final String expectedBodyPart = loadFileOrFail("TC0200_createSpace/response.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/spaces", spaceJson, streamId);

    // 3. Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Expecting new space in response", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(4)
  void tc0201_testCreateDuplicateSpace() throws Exception {
    // Test API : POST /hub/spaces
    // 1. Load test data
    final String duplicatedSpace = loadFileOrFail("TC0200_createSpace/create_space.json");
    final String expectedBodyPart = loadFileOrFail("TC0201_createDupSpace/response.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().post("hub/spaces", duplicatedSpace, streamId);

    // 3. Perform assertions
    assertEquals(409, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Expecting conflict error message", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(4)
  void tc0220_testGetSpaceById() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}
    // 1. Load test data
    final String expectedBodyPart = loadFileOrFail("TC0200_createSpace/response.json");
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().get("hub/spaces/test-space", streamId);

    // 3. Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals("Expecting space response", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(4)
  void tc0221_testGetSpaceByWrongId() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}
    // 1. Load test data
    final String streamId = UUID.randomUUID().toString();

    // 2. Perform REST API call
    final HttpResponse<String> response = getNakshaClient().get("hub/spaces/not-real-space", streamId);

    // 3. Perform assertions
    assertEquals(404, response.statusCode(), "ResCode mismatch");
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  @Test
  @Order(4)
  void tc0240_testGetSpaces() throws Exception {
    // Given: created spaces
    List<String> expectedSpaceIds = List.of("tc_240_space_1", "tc_240_space_2");
    final String streamId = UUID.randomUUID().toString();
    getNakshaClient().post("hub/spaces", loadFileOrFail("TC0240_getSpaces/create_space_1.json"), streamId);
    getNakshaClient().post("hub/spaces", loadFileOrFail("TC0240_getSpaces/create_space_2.json"), streamId);

    // When: Fetching all spaces
    final HttpResponse<String> response = getNakshaClient().get("hub/spaces", streamId);

    // Then: Expect all saved spaces are returned
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
    List<XyzFeature> returnedXyzFeatures =
        parseJson(response.body(), XyzFeatureCollection.class).getFeatures();
    boolean allReturnedFeaturesAreSpaces =
        returnedXyzFeatures.stream().allMatch(feature -> Space.class.isAssignableFrom(feature.getClass()));
    Assertions.assertTrue(allReturnedFeaturesAreSpaces);
    List<String> spaceIds =
        returnedXyzFeatures.stream().map(XyzFeature::getId).toList();
    Assertions.assertTrue(spaceIds.containsAll(expectedSpaceIds));
  }

  @Test
  @Order(5)
  void tc0260_testUpdateSpace() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}
    // Given:
    final String updateStorageJson = loadFileOrFail("TC0260_updateSpace/update_space.json");
    final String expectedRespBody = loadFileOrFail("TC0260_updateSpace/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/spaces/test-space", updateStorageJson, streamId);

    // Then:
    assertEquals(200, response.statusCode());
    JSONAssert.assertEquals(expectedRespBody, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }

  @Test
  @Order(5)
  void tc0261_testUpdateNonexistentSpace() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}
    // Given:
    final String updateSpaceJson = loadFileOrFail("TC0261_updateNonexistentSpace/update_space.json");
    final String expectedErrorResponse = loadFileOrFail("TC0261_updateNonexistentSpace/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/spaces/non-existent-space", updateSpaceJson, streamId);

    // Then:
    assertEquals(404, response.statusCode());
    JSONAssert.assertEquals(expectedErrorResponse, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }

  @Test
  @Order(5)
  void tc0263_testUpdateSpaceWithWithMismatchingId() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}
    // Given:
    final String bodyWithDifferentSpaceId = loadFileOrFail("TC0263_updateSpaceWithMismatchingId/update_space.json");
    final String expectedErrorResponse = loadFileOrFail("TC0263_updateSpaceWithMismatchingId/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When:
    final HttpResponse<String> response =
        getNakshaClient().put("hub/spaces/test-space", bodyWithDifferentSpaceId, streamId);

    // Then:
    assertEquals(400, response.statusCode());
    JSONAssert.assertEquals(expectedErrorResponse, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID));
  }
}
