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

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.assertions.ResponseAssertions;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.storage.http.HttpStorageReadSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ReadFeaturesByIdsHttpTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

  private static final String SPACE_ID = "read_features_by_ids_http_test_space";

  @BeforeAll
  static void setup() throws URISyntaxException, IOException, InterruptedException {
    setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByIdsHttp/setup");
  }

  @Disabled("Need http mock or local server")
  @Test
  void tc01_testReadFeatureById() throws Exception {
    final String featureId = "1";
    final String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByIdsHttp/TC01_ExistingId/feature_response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Get Features request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features/" + featureId, streamId);

    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");

    // Then: also match individual JSON attributes (in addition to whole object comparison above)
    final XyzFeature feature = parseJson(response.body(), XyzFeature.class);
//        assertNotNull(
//                feature.getProperties().getXyzNamespace().getUuid(), "UUID found missing in response for feature");
  }

  @Disabled("Need http mock or local server")
  @Test
  void tc02_testReadFeatureForMissingId() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/features/{featureId}
    // Validate request gets failed when attempted to load feature for missing Id
    // Given: Feature By Id request, against existing space, for missing feature Id
    final String featureId = "missing-id";
    final String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByIdsHttp/TC02_MissingId/feature_response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Get Features request is submitted to NakshaHub Space Storage instance
    final HttpResponse<String> response = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features/" + featureId, streamId);

    // Then: Perform assertions
    ResponseAssertions.assertThat(response)
            .hasStatus(404)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");
  }

  /**
   * Temporary test for debugging. Will be removed
   */
  @Disabled("Need http mock or local server")
  @Test
  void tc099_testTmpTest() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/features
    // Validate empty collection getting returned for missing ids
    // Given: Features By Ids request (against configured space)
    String bboxQuery = "hub/spaces/" + SPACE_ID + "/bbox?north=1&east=2&south=3&west=4";
    String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByIdsHttp/TC99_TmpTest/feature_response_part.json");
    String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    HttpResponse<String> response = getNakshaClient().get(bboxQuery, streamId);

    // Then: Perform assertions
    assertTrue(
            HttpStorageReadSession.testLog.containsAll(
                    List.of("GET_BY_BBOX", "north = 1.0", "east = 2.0")
            ),
            String.join(", ", HttpStorageReadSession.testLog)
    );

    ResponseAssertions.assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");
  }

}
