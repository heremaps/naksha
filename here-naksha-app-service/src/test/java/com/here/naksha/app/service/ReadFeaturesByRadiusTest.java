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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static com.here.naksha.app.common.CommonApiTestSetup.createSpace;
import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.urlEncoded;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

class ReadFeaturesByRadiusTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

  private static final String SPACE_ID = "read_features_by_radius_test_space";
  private static final String REF_SPACE_ID = "read_features_by_radius_test_ref_space";

  /*
  For this test suite, we upfront create various Features using different combination of Geometry, Tags and properties.
  And then in subsequent tests, we validate the REST API behaviour by providing different filter conditions

  Main Space:
    - feature 1 - Point1, Tag-1, Tag-Ref, Property-1
    - feature 2 - Point2-within-5m-of-Point1, Tag-2, Tag-Ref, Property-2
    - feature 3 - Point2-within-5m-of-Point1, Tag-3, Tag-Ref, Property-1, Property-2
    - feature 4 - Point3-outside-5m-of-Point1, all above tags, all above properties

  Ref Space:
    - feature 1 - Point4-within-5m-of-point1, Tag-Ref, Property-5
    - feature 2 - Point5-outside-5m-of-Point1, Tag-Ref, Property-5
    - feature 3 - Point6-outside-100m-of-Point1

  Test Cases:
    TC  1 - Point1, radius=0 (should return feature 1 only)
    TC  2 - Point1, radius=5m (should return features 1,2,3)
    TC  3 - Point1, radius=5m, Prop-2 (should return feature 2 only)
    TC  4 - Point1, radius=5m, Tag-1 (should return feature 1 only)
    TC  5 - Point1, radius=5m, Prop-1, Tag-3 (should return feature 3 only)
    TC  6 - Point1, radius=5m, Limit-2 (should return features 1,2)
    TC  6 - RefSpace, RefFeature1, radius=5m, Tag-1 or Tag-2, Limit-2 (should return features 1,2)
    TC  7 - RefSpace, RefFeature1, radius=5m, Tag-1 or Tag-2, Prop-2 (should return only feature 2)
    TC  8 - RefSpace, RefFeature2, radius=5m (should return no features)
    TC  9 - Point4-outside-100m-of-Point1, radius=5m (should return no features)
    TC 10 - RefSpace, RefFeature3, radius=5m (should return no features)
    TC 11 - Invalid RefSpace (should return 404)
    TC 12 - RefSpace, Invalid RefFeature4 (should return 404)
    TC 13 - Missing Lat/Lon (should return 400)
    TC 14 - Point1, radius=-1m (should return 400)
    TC 15 - LineString-with-Point1, radius=5m (should return features 1,2,3)
  */

  @BeforeAll
  static void setup() throws URISyntaxException, IOException, InterruptedException {
    setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByRadius/setup");
    // create another space that will be used for providing feature reference
    createSpace(nakshaClient, "ReadFeatures/ByRadius/setup/create_ref_space.json");
    // create features in main space
    String initialFeaturesJson = loadFileOrFail("ReadFeatures/ByRadius/setup/create_features.json");
    nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
    // create features in reference space
    initialFeaturesJson = loadFileOrFail("ReadFeatures/ByRadius/setup/create_ref_features.json");
    nakshaClient.post("hub/spaces/" + REF_SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
  }

  @Test
  void testGetByRadiusWithLatLon() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/spatial
    // Validate features getting returned for given Lat,Lon coordinates

    // Given: Request parameters
    final String latLonParam = "lon=8.6123&lat=50.1234";
    final String expectedBodyPart =
        loadFileOrFail("ReadFeatures/ByRadius/testGetByRadiusWithLatLon/feature_response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Get Features By BBox request is submitted to NakshaHub
    final HttpResponse<String> response = nakshaClient
        .get("hub/spaces/" + SPACE_ID + "/spatial?" + latLonParam, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");
  }


  @Test
  void testGetByRadiusWithLatLonRadius() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/spatial
    // Validate features getting returned for given Lat,Lon coordinates and Radius parameter

    // Given: Request parameters
    final String latLonParam = "lon=8.6123&lat=50.1234";
    final String radiusParam = "radius=5";
    final String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByRadius/testGetByRadiusWithLatLonRadius/feature_response_part.json");
    final String streamId = UUID.randomUUID().toString();

    // When: Get Features By BBox request is submitted to NakshaHub
    final HttpResponse<String> response = nakshaClient
            .get("hub/spaces/" + SPACE_ID + "/spatial?" + latLonParam + "&" + radiusParam, streamId);

    // Then: Perform assertions
    assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");
  }


}
