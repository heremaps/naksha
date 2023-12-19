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
import static org.junit.jupiter.api.Assertions.*;

import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.service.models.FeatureCollectionRequest;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.geojson.implementation.XyzReference;
import com.here.naksha.lib.core.models.naksha.Space;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.ArraySizeComparator;

public class ValDryRunTestHelper {

  final @NotNull NakshaApp app;
  final @NotNull NakshaTestWebClient nakshaClient;

  public ValDryRunTestHelper(final @NotNull NakshaApp app, final @NotNull NakshaTestWebClient nakshaClient) {
    this.app = app;
    this.nakshaClient = nakshaClient;
  }

  private void standardAssertions(
      final @NotNull HttpResponse<String> actualResponse,
      final int expectedStatusCode,
      final @NotNull String expectedBodyPart,
      final @NotNull String expectedStreamId)
      throws JSONException {
    assertEquals(expectedStatusCode, actualResponse.statusCode(), "ResCode mismatch");
    JSONAssert.assertEquals(
        "Create Feature response body doesn't match",
        expectedBodyPart,
        actualResponse.body(),
        JSONCompareMode.LENIENT);
    assertEquals(expectedStreamId, getHeader(actualResponse, HDR_STREAM_ID), "StreamId mismatch");
  }

  private void additionalCustomAssertions_tc3000(final @NotNull String reqBody, final @NotNull String resBody)
      throws JSONException {
    final FeatureCollectionRequest collectionRequest = parseJson(reqBody, FeatureCollectionRequest.class);
    final XyzFeatureCollection collectionResponse = parseJson(resBody, XyzFeatureCollection.class);
    final List<String> updatedIds = collectionResponse.getUpdated();
    final List<XyzFeature> features = collectionResponse.getFeatures();
    final List<XyzFeature> violations = collectionResponse.getViolations();
    JSONAssert.assertEquals(
        "{updated:[" + collectionRequest.getFeatures().size() + "]}",
        resBody,
        new ArraySizeComparator(JSONCompareMode.LENIENT));
    assertEquals(
        updatedIds.size(), features.size(), "Mismatch between updated and features list size in the response");
    final String newFeatureId = features.get(2).getId();
    assertNotNull(newFeatureId, "Feature Id must not be null");
    for (int i = 3; i <= 5; i++) {
      final XyzFeature violation = violations.get(i);
      final List<XyzReference> references = violation.getProperties().getReferences();
      assertNotNull(references, "References missing for violation at idx " + i);
      for (final XyzReference reference : references) {
        assertNotNull(reference.getId(), "Id missing in references for violation at idx " + i);
        assertEquals(newFeatureId, reference.getId(), "Violation referenced featured id doesn't match");
      }
    }
  }

  void additionalCustomAssertions_tc3001(final @NotNull String resBody) {
    final XyzFeatureCollection collectionResponse = parseJson(resBody, XyzFeatureCollection.class);
    assertNull(collectionResponse.getViolations(), "No violations were expected");
  }

  void tc3000_testValDryRunReturningViolations() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate features returned with mock violations
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

    // Then: Perform standard assertions
    standardAssertions(response, 200, expectedBodyPart, streamId);
    // Then: Perform additional custom assertions for matching violation references
    additionalCustomAssertions_tc3000(bodyJson, response.body());
  }

  void tc3001_testValDryRunNoViolations() throws Exception {
    // NOTE : This test depends on setup done as part of tc3000_testValDryRunReturningViolations

    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate features returned without any violations
    final String streamId = UUID.randomUUID().toString();

    // Given: PUT features request
    final String spaceId = "local-space-4-val-dry-run";
    final String bodyJson = loadFileOrFail("ValDryRun/TC3001_WithoutViolations/upsert_features.json");
    final String expectedBodyPart = loadFileOrFail("ValDryRun/TC3001_WithoutViolations/feature_response_part.json");

    // When: Request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.post("hub/spaces/" + spaceId + "/features", bodyJson, streamId);

    // Then: Perform standard assertions
    standardAssertions(response, 200, expectedBodyPart, streamId);
    // Then: Perform additional custom assertions for matching violation references
    additionalCustomAssertions_tc3001(response.body());
  }

  void tc3002_testValDryRunUnsupportedOperation() throws Exception {
    // NOTE : This test depends on setup done as part of tc3000_testValDryRunReturningViolations

    // Test API : DELETE /hub/spaces/{spaceId}/features
    // Validate request gets rejected as validation is not supported for DELETE endpoint
    final String streamId = UUID.randomUUID().toString();

    // Given: DELETE features request
    final String spaceId = "local-space-4-val-dry-run";
    final String expectedBodyPart =
        loadFileOrFail("ValDryRun/TC3002_UnsupportedOperation/feature_response_part.json");

    // When: Request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response =
        nakshaClient.delete("hub/spaces/" + spaceId + "/features?id=some-feature-id", streamId);

    // Then: Perform standard assertions
    standardAssertions(response, 501, expectedBodyPart, streamId);
  }
}
