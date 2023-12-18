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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.lib.core.models.naksha.Space;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class ValDryRunTestHelper {

  final @NotNull NakshaApp app;
  final @NotNull NakshaTestWebClient nakshaClient;

  public ValDryRunTestHelper(final @NotNull NakshaApp app, final @NotNull NakshaTestWebClient nakshaClient) {
    this.app = app;
    this.nakshaClient = nakshaClient;
  }

  void tc3000_testValDryRunReturningViolations() throws Exception {
    // Test API : PUT /hub/spaces/{spaceId}/features
    final String streamId = UUID.randomUUID().toString();

    // Given: MockDryRun event handler and related space in place
    final String handler = loadFileOrFail("ValDryRun/TC3000_WithViolations/create_event_handler.json");
    nakshaClient.post("hub/handlers", handler, streamId);
    final String spaceJson = loadFileOrFail("ValDryRun/TC3000_WithViolations/create_space.json");
    nakshaClient.post("hub/spaces", spaceJson, streamId);
    final Space space = parseJson(spaceJson, Space.class);

    // Given: PUT features request
    final String bodyJson = loadFileOrFail("ValDryRun/TC3000_WithViolations/upsert_features.json");
    final String expectedBodyPart = loadFileOrFail("ValDryRun/TC3000_WithViolations/feature_response_part.json");

    // When: Request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.post("hub/spaces/" + space.getId() + "/features", bodyJson, streamId);

    // Then: Perform assertions
    assertEquals(200, response.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Val Dry Run response body doesn't match", expectedBodyPart, response.body(), JSONCompareMode.LENIENT);
    assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
  }
}
