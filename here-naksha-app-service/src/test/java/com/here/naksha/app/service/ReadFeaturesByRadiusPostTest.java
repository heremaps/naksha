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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.here.naksha.app.common.CommonApiTestSetup.createSpace;
import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

class ReadFeaturesByRadiusPostTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

  private static final String SPACE_ID = "read_features_by_radius_post_test_space";

  /*
  For this test suite, we upfront create various Features using different combination of Geometry, Tags and properties.
  And then in subsequent tests, we validate the REST API behaviour by providing different filter conditions

  Main Space:
    - feature 1 - Point1, Tag-1, Tag-Ref, Property-1
    - feature 2 - Point2-within-5m-of-Point1, Tag-2, Tag-Ref, Property-2
    - feature 3 - Point2-within-5m-of-Point1, Tag-3, Tag-Ref, Property-1, Property-2
    - feature 4 - Point3-outside-5m-of-Point1, all above tags, all above properties

  Test Cases:
    TC  1 - Point1, radius=0 (should return feature 1 only)
    TC  2 - Point1, radius=5m (should return features 1,2,3)
    TC  3 - Point1, radius=5m, Prop-2 (should return features 2,3)
    TC  4 - Point1, radius=5m, Tag-1 (should return feature 1 only)
    TC  5 - Point1, radius=5m, Prop-1, Tag-3 (should return feature 3 only)
    TC  6 - Point1, radius=5m, Limit-2 (should return features 1,2)
    #####TC  7 - RefSpace, RefFeature1, radius=5m, Tag-2 (should return feature 2 only)
    #####TC  8 - RefSpace, RefFeature1, radius=5m, Tag-3, Prop-1 (should return only feature 3)
    #####TC  9 - RefSpace, RefFeature3, radius=5m (should return no features)
    TC 10 - Point4-outside-100m-of-Point1, radius=5m (should return no features)
    #####TC 11 - Invalid RefSpace (should return 404)
    #####TC 12 - RefSpace, Invalid RefFeature4 (should return 404)
    TC 13 - Missing Lat/Lon (should return 400)
    TC 14 - Point1, radius=-1m (should return 400)
    TC 15 - Invalid Lat (should return 400)
    TC 16 - Invalid Lon (should return 400)
    #####TC 17 - RefSpace, RefFeature4 (missing geometry) (should return 404)
    TC 18 - LineString, radius=5 (should return features 1,2,3)
  */

  @BeforeAll
  static void setup() throws URISyntaxException, IOException, InterruptedException {
    setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByRadiusPost/setup");
    // create features in main space
    String initialFeaturesJson = loadFileOrFail("ReadFeatures/ByRadiusPost/setup/create_features.json");
    nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
  }

  private static Arguments standardTestSpec(final String testDesc,
                                            final @Nullable String fPathOfRequestBody,
                                            final @Nullable List<String> queryParamList,
                                            final @NotNull String fPathOfExpectedResBody,
                                            final int expectedResCode) {
    return Arguments.arguments(fPathOfRequestBody, queryParamList, fPathOfExpectedResBody, Named.named(testDesc, expectedResCode));
  }

  private static Stream<Arguments> standardTestParams() {
    return Stream.of(
            standardTestSpec(
                    "tc01_testGetByRadiusWithPoint",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    null,
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/feature_response_part.json",
                    200
            )/*,
            standardTestSpec(
                    "tc02_testGetByRadiusWithLatLonRadius",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6123&lat=50.1234",
                            "radius=5"
                    ),
                    "ReadFeatures/ByRadiusPost/TC02_withLatLonRadius/feature_response_part.json",
                    200
            ),
            standardTestSpec(
                    "tc03_testGetByRadiusWithLatLonRadiusProp",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6123&lat=50.1234",
                            "radius=5",
                            "p.length=10"
                    ),
                    "ReadFeatures/ByRadiusPost/TC03_withLatLonRadiusProp/feature_response_part.json",
                    200
            ),
            standardTestSpec(
                    "tc04_testGetByRadiusWithLatLonRadiusTag",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6123&lat=50.1234",
                            "radius=5",
                            "tags=tag-1"
                    ),
                    "ReadFeatures/ByRadiusPost/TC04_withLatLonRadiusTag/feature_response_part.json",
                    200
            ),
            standardTestSpec(
                    "tc05_testGetByRadiusWithLatLonRadiusTagProp",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6123&lat=50.1234",
                            "radius=5",
                            "tags=tag-3",
                            "p.speedLimit='60'"
                    ),
                    "ReadFeatures/ByRadiusPost/TC05_withLatLonRadiusTagProp/feature_response_part.json",
                    200
            ),
            standardTestSpec(
                    "tc06_testGetByRadiusWithLatLonRadiusLimit",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6123&lat=50.1234",
                            "radius=5",
                            "limit=2"
                    ),
                    "ReadFeatures/ByRadiusPost/TC06_withLatLonRadiusLimit/feature_response_part.json",
                    200
            ),
            standardTestSpec(
                    "tc10_testGetByRadiusWithLatLonOutOfRadius",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6133&lat=50.1234",
                            "radius=5"
                    ),
                    "ReadFeatures/ByRadiusPost/TC10_withLatLonOutOfRadius/feature_response_part.json",
                    200
            ),
            standardTestSpec(
                    "tc13_testGetByRadiusWithoutLatLon",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    null,
                    "ReadFeatures/ByRadiusPost/TC13_withoutLatLon/feature_response_part.json",
                    400
            ),
            standardTestSpec(
                    "tc14_testGetByRadiusWithInvalidRadius",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6123&lat=50.1234",
                            "radius=-1"
                    ),
                    "ReadFeatures/ByRadiusPost/TC14_withInvalidRadius/feature_response_part.json",
                    400
            ),
            standardTestSpec(
                    "tc15_testGetByRadiusWithInvalidLat",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=8.6123&lat=-91",
                            "radius=5"
                    ),
                    "ReadFeatures/ByRadiusPost/TC15_withInvalidLat/feature_response_part.json",
                    400
            ),
            standardTestSpec(
                    "tc16_testGetByRadiusWithInvalidLon",
                    "ReadFeatures/ByRadiusPost/TC01_withPoint/request_body.json",
                    List.of(
                            "lon=-181&lat=50.1234",
                            "radius=5"
                    ),
                    "ReadFeatures/ByRadiusPost/TC16_withInvalidLon/feature_response_part.json",
                    400
            )*/
    );

  }

  @ParameterizedTest
  @MethodSource("standardTestParams")
  void commonTestExecution(
          final @Nullable String fPathOfRequestBody,
          final @Nullable List<String> queryParamList,
          final @NotNull String fPathOfExpectedResBody,
          final int expectedResCode) throws Exception {
    // Given: Request parameters
    String urlQueryParams = "";
    if (queryParamList!=null && !queryParamList.isEmpty()) {
      urlQueryParams += String.join("&", queryParamList);
    }
    final String streamId = UUID.randomUUID().toString();

    // Given: Request body
    final String requestBody = (fPathOfRequestBody!=null) ? loadFileOrFail(fPathOfRequestBody) : "";

    // Given: Expected response body
    final String expectedBodyPart = loadFileOrFail(fPathOfExpectedResBody);

    // When: Get Features By Radius request is submitted to NakshaHub
    final HttpResponse<String> response = nakshaClient
            .post("hub/spaces/" + SPACE_ID + "/spatial?" + urlQueryParams, requestBody, streamId);

    // Then: Perform standard assertions
    assertThat(response)
            .hasStatus(expectedResCode)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Response body doesn't match");
  }

}
