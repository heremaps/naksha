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

import static com.here.naksha.app.common.TestUtil.*;
import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.lib.core.models.naksha.Space;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class UpdateFeatureTestHelper {

  final @NotNull NakshaApp app;
  final @NotNull NakshaTestWebClient nakshaClient;

  public UpdateFeatureTestHelper(final @NotNull NakshaApp app, final @NotNull NakshaTestWebClient nakshaClient) {
    this.app = app;
    this.nakshaClient = nakshaClient;
  }

  void tc0500_testUpdateFeatures() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}/features
    final String streamId = UUID.randomUUID().toString();

    // Preparation: create storage, event handler and space
    final String storage = loadFileOrFail("TC0500_updateFeatures/create_storage.json");
    nakshaClient.post("hub/storages", storage, streamId);
    final String handler = loadFileOrFail("TC0500_updateFeatures/create_handler.json");
    nakshaClient.post("hub/handlers", handler, streamId);
    final String spaceJson = loadFileOrFail("TC0500_updateFeatures/create_space.json");
    nakshaClient.post("hub/spaces", spaceJson, streamId);
    // Read request body
    final String bodyJson = loadFileOrFail("TC0500_updateFeatures/update_request.json");
    // TODO: include geometry after Cursor-related changes ->
    final Space space = parseJsonFileOrFail("TC0500_updateFeatures/create_space.json", Space.class);
    final String expectedBodyPart = loadFileOrFail("TC0500_updateFeatures/response_no_geometry.json");

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.put("hub/spaces/" + space.getId() + "/features", bodyJson, streamId);

    // Then: Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Update Feature response body doesn't match",
        expectedBodyPart,
        response.body(),
        JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  void tc0501_testUpdateFeatureById() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}/features/{featureId}

    // Read request body
    final String bodyJson = loadFileOrFail("TC0501_updateOneFeatureById/update_request_and_response.json");
    // TODO: include geometry after Cursor-related changes ->
    final Space space = parseJsonFileOrFail("TC0500_updateFeatures/create_space.json", Space.class);
    final String expectedBodyPart = bodyJson;
    final String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.put("hub/spaces/" + space.getId() + "/features/my-custom-id-301-1", bodyJson, streamId);

    // Then: Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Update Feature response body doesn't match",
        expectedBodyPart,
        response.body(),
        JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  void tc0502_testUpdateFeatureByWrongUriId() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}/features/{featureId}

    // Read request body
    final String bodyJson = loadFileOrFail("TC0502_updateFeatureWithWrongUriId/request.json");
    // TODO: include geometry after Cursor-related changes ->
    final Space space = parseJsonFileOrFail("TC0500_updateFeatures/create_space.json", Space.class);
    final String expectedBodyPart = loadFileOrFail("TC0502_updateFeatureWithWrongUriId/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.put("hub/spaces/" + space.getId() + "/features/wrong-id", bodyJson, streamId);

    // Then: Perform assertions
    assertEquals(409, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Update Feature error response doesn't match",
        expectedBodyPart,
        response.body(),
        JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }

  void tc0503_testUpdateFeatureWithMismatchingId() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}/features/{featureId}

    // Read request body
    final String bodyJson = loadFileOrFail("TC0502_updateFeatureWithWrongUriId/request.json");
    // TODO: include geometry after Cursor-related changes ->
    final Space space = parseJsonFileOrFail("TC0500_updateFeatures/create_space.json", Space.class);
    final String expectedBodyPart = loadFileOrFail("TC0503_updateFeatureMismatchingId/response.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.put("hub/spaces/" + space.getId() + "/features/my-custom-id-301-1", bodyJson, streamId);

    // Then: Perform assertions
    assertEquals(400, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Update Feature error response doesn't match",
        expectedBodyPart,
        response.body(),
        JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }
}
