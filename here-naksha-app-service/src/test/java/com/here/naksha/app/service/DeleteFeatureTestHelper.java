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

public class DeleteFeatureTestHelper {

  final @NotNull NakshaApp app;
  final @NotNull NakshaTestWebClient nakshaClient;

  public DeleteFeatureTestHelper(final @NotNull NakshaApp app, final @NotNull NakshaTestWebClient nakshaClient) {
    this.app = app;
    this.nakshaClient = nakshaClient;
  }

  void tc0900_testDeleteFeatures() throws Exception {
    // Test API : DELETE /hub/spaces/{spaceId}/features
    final String streamId = UUID.randomUUID().toString();

    // Preparation: create storage, event handler, space, and initial features
    final String storage = loadFileOrFail("TC0900_deleteFeatures/create_storage.json");
    nakshaClient.post("hub/storages", storage, streamId);
    final String handler = loadFileOrFail("TC0900_deleteFeatures/create_handler.json");
    nakshaClient.post("hub/handlers", handler, streamId);
    final String spaceJson = loadFileOrFail("TC0900_deleteFeatures/create_space.json");
    nakshaClient.post("hub/spaces", spaceJson, streamId);
    final Space space = parseJsonFileOrFail("TC0900_deleteFeatures/create_space.json", Space.class);
    final String createFeaturesJson = loadFileOrFail("TC0900_deleteFeatures/create_features.json");
    nakshaClient.post("hub/spaces/" + space.getId() + "/features", createFeaturesJson, streamId);
    final String expectedBodyPart = loadFileOrFail("TC0900_deleteFeatures/create_features.json");

    // When: request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response = nakshaClient.delete(
        "hub/spaces/" + space.getId() + "/features?id=feature-1-to-delete&id=feature-2-to-delete", streamId);

    // Then: Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Delete Feature response body doesn't match",
        expectedBodyPart,
        response.body(),
        JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }
}
