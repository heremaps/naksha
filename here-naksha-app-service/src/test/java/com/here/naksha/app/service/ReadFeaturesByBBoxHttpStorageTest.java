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

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.urlEncoded;

import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

@WireMockTest(httpPort = 9090)
class ReadFeaturesByBBoxHttpStorageTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

  private static final String HTTP_SPACE_ID = "read_features_by_bbox_space_4_http_storage";
  private static final String PSQL_SPACE_ID = "read_features_by_bbox_space_4_psql_storage";
  private static final String VIEW_SPACE_ID = "read_features_by_bbox_space_4_view_storage";
  private static final String ENDPOINT = "/my_env/my_storage/my_feat_type/bbox";

  /*
  For this test suite, we upfront create various Features using different combination of Tags and Geometry.
  To know what exact features we create, check the create_features.json test file for test tc0700_xx().
  And then in subsequent tests, we validate the various GetByBBox APIs using different query parameters.
  */
  @BeforeAll
  static void setup() throws URISyntaxException, IOException, InterruptedException {
    // Set up Http Storage based Space
    setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/http_storage_space");
    // Set up (standard) Psql Storage based Space
    setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/psql_storage_space");
    // Set up View Space over Psql and Http Storage based spaces
    createHandler(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/view_space/create_sourceId_handler.json");
    setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/view_space");
    // Load some test data in PsqlStorage based Space
    final String initialFeaturesJson = loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/setup/psql_storage_space/create_features.json");
    final HttpResponse<String> response = nakshaClient.post("hub/spaces/" + PSQL_SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
    assertThat(response).hasStatus(200);
  }

  @Test
  void tc0700_testGetByBBoxWithSingleTag_willIgnoreTag() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/bbox
    // Validate features getting returned for given BBox coordinate and given single tag value
    // Given: Features By BBox request (against above space)
    final String bboxQueryParam = "west=-180&south=-90&east=180&north=90";
    final String tagsQueryParam = "tags=one";
    final String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/TC0700_SingleTag/feature_response_part.json");
    String streamId = UUID.randomUUID().toString();

    final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);
    stubFor(get(endpointPath)
            .withQueryParam("west", equalTo("-180.0"))
            .withQueryParam("south", equalTo("-90.0"))
            .withQueryParam("east", equalTo("180.0"))
            .withQueryParam("north", equalTo("90.0"))
            .willReturn(okJson(expectedBodyPart)));
    // Now the tags are not supported and will be ignored.

    // When: Get Features By BBox request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient
            .get("hub/spaces/" + HTTP_SPACE_ID + "/bbox?" + tagsQueryParam + "&" + bboxQueryParam, streamId);

    // Then: Perform assertions
    assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");

    // Then: Verify request reached endpoint once
    verify(1, getRequestedFor(endpointPath));
  }

  @Test
  void tc0706_testGetByBBoxWithLimit() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/bbox
    // Validate features returned match with given BBox condition and limit

    // Given: Features By BBox request (against configured space)
    final String bboxQueryParam = "west=-180&south=-90&east=180&north=90";
    final String tagsQueryParam = "tags=one";
    final String limitQueryParam = "limit=2";
    final String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/TC0706_WithLimit/feature_response_part.json");
    String streamId = UUID.randomUUID().toString();

    final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);
    stubFor(get(endpointPath)
            .withQueryParam("west", equalTo("-180.0"))
            .withQueryParam("south", equalTo("-90.0"))
            .withQueryParam("east", equalTo("180.0"))
            .withQueryParam("north", equalTo("90.0"))
            .withQueryParam("limit", equalTo("2"))
            .willReturn(okJson(expectedBodyPart)));

    // When: Get Features By BBox request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient
            .get(
                    "hub/spaces/" + HTTP_SPACE_ID + "/bbox?" + tagsQueryParam + "&" + bboxQueryParam + "&"
                            + limitQueryParam,
                    streamId);

    // Then: Perform assertions
    assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");

    // Then: Verify request reached endpoint once
    verify(1, getRequestedFor(endpointPath));
  }

  @Test
  void tc0707_testGetByBBox() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/bbox
    // Validate features returned match with given BBox condition
    // Given: Features By BBox request (against configured space)
    final String bboxQueryParam = "west=8.6476&south=50.1175&east=8.6729&north=50.1248";
    final String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/TC0707_BBoxOnly/feature_response_part.json");
    String streamId = UUID.randomUUID().toString();

    final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);
    stubFor(get(endpointPath)
            .withQueryParam("west", equalTo("8.6476"))
            .withQueryParam("south", equalTo("50.1175"))
            .withQueryParam("east", equalTo("8.6729"))
            .withQueryParam("north", equalTo("50.1248"))
            .willReturn(okJson(expectedBodyPart)));

    // When: Get Features By BBox request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + HTTP_SPACE_ID + "/bbox?" + bboxQueryParam, streamId);

    // Then: Perform assertions
    assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");

    // Then: Verify request reached endpoint once
    verify(1, getRequestedFor(endpointPath));
  }

  @Test
  void tc0710_testGetByBBoxWithInvalidCoordinate() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/bbox
    // Validate API error when BBox coordinates are invalid
    // Given: Features By BBox request (against configured space)
    final String bboxQueryParam = "west=-181&south=50.1175&east=8.6729&north=50.1248";
    final String expectedBodyPart =
            loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/TC0710_InvalidCoordinate/feature_response_part.json");
    String streamId = UUID.randomUUID().toString();

    // When: Get Features By BBox request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + HTTP_SPACE_ID + "/bbox?" + bboxQueryParam, streamId);

    // Then: Perform assertions
    assertThat(response)
            .hasStatus(400)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");

    // Then: Verify request did not reach endpoint
    verify(0, getRequestedFor(urlPathEqualTo(ENDPOINT)));
  }


  @Test
  void tc0711_testGetByBBoxOnViewSpace() throws Exception {
    // Test API : GET /hub/spaces/{spaceId}/bbox
    // Validate features returned match with given BBox condition using View space over psql and http storage based spaces

    // Given: Features By BBox request (against view space)
    final String bboxQueryParam = "west=-180&south=-90&east=180&north=90";
    final String httpStorageMockResponse =
            loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/TC0711_BBoxOnViewSpace/http_storage_response.json");
    final String expectedViewResponse =
            loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/TC0711_BBoxOnViewSpace/feature_response_part.json");
    String streamId = UUID.randomUUID().toString();

    final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);
    stubFor(get(endpointPath)
            .withQueryParam("west", equalTo("-180.0"))
            .withQueryParam("south", equalTo("-90.0"))
            .withQueryParam("east", equalTo("180.0"))
            .withQueryParam("north", equalTo("90.0"))
            .willReturn(okJson(httpStorageMockResponse)));

    // When: Get Features By BBox request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + VIEW_SPACE_ID + "/bbox?" + bboxQueryParam, streamId);

    // Then: Perform assertions
    assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedViewResponse, "Get Feature response body doesn't match");

    // Then: Verify request reached endpoint once
    verify(1, getRequestedFor(endpointPath));
  }

  @ParameterizedTest
  @MethodSource("queryParams")
  void tc800_testPropertySearch(String inputQueryString, String outputQueryString) throws Exception {
    final String bboxQueryParam = "west=-180.0&north=90.0&east=180.0&south=-90.0&limit=30000";

    String streamId = UUID.randomUUID().toString();

    final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);

    // When: Get Features By BBox request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient
            .get("hub/spaces/" + HTTP_SPACE_ID + "/bbox?" + bboxQueryParam + "&" + inputQueryString, streamId);

    stubFor(any(anyUrl()));

    RequestPatternBuilder requestedFor = getRequestedFor(endpointPath);
    for (String s : outputQueryString.split("&")) {
      String[] split = s.trim().split("=", 2);
      requestedFor.withQueryParam(split[0],equalTo(split[1]));
    }
    verify(1, requestedFor);
  }

  private static Stream<Arguments> queryParams() {
    return Stream.of(
            Arguments.of("properties.alone_prop=1","properties.alone_prop=1"),
            Arguments.of("properties.alone_prop=1,2,3,4","properties.alone_prop=1,2,3,4"),
            Arguments.of(
                    "properties.json_prop="+urlEncoded("{\"arr1\":[1,2],\"arr2\":[]}"),
                    "properties.json_prop={\"arr1\":[1,2],\"arr2\":[]}"
            ),
            Arguments.of("properties.long_or=1,2,3,4,4,4,3,2,1,true,false","properties.long_or=1,2,3,4,4,4,3,2,1,true,false"),
            Arguments.of("properties.very.very.very.nested.even.more=1","properties.very.very.very.nested.even.more=1"),
            Arguments.of(
                    // Naksha does not interpret encoded %3E (">" sign) as a gt operation and passes it as properties.prop_> path with "=" operation
                    "properties.prop%3E=value_2,value_22",
                    // Http storege actually sends request with ">" reencoded to %3E, but Wiremock expects decoded string in verify matcher
                    "properties.prop>=value_2,value_22"
            ),
            Arguments.of("properties.prop=lte=1","properties.prop=lte=1"),
            Arguments.of("properties.prop=.null","properties.prop=.null"),
            Arguments.of("properties.prop=null","properties.prop=null"),
            Arguments.of("properties.prop!=.null","properties.prop!=.null"),
            Arguments.of("""
                            properties.prop_2!=value_2,value_22
                            &properties.prop_3=.null,value_33
                            &properties.prop_4!=.null,value_44
                            &properties.prop_5=gte=5.5,55
                            &properties.prop_5_1=cs=%7B%22id%22%3A%22123%22%7D,%5B%7B%22id%22%3A%22123%22%7D%5D
                            &properties.prop_5_2!=%7B%22id%22%3A%22123%22%7D,%7B%22id%22%3A%22456%22%7D,.null
                            &properties.prop_6=lte=6,66
                            &properties.prop_7=gt=7,77
                            &properties.prop_8=lt=8,88
                            &properties.array_1=cs=%40element_1,element_2
                            &properties.prop_10=gte=555,5555
                            &properties.prop_11=lte=666,6666
                            &properties.prop_12=gt=777,7777
                            &properties.prop_13=lt=888,8888
                            &properties.@ns:com:here:xyz.tags=cs=%7B%22id%22%3A%22123%22%7D,%5B%7B%22id%22%3A%22123%22%7D%5D,element_4
                            &properties.@ns:com:here:xyz.tags=cs=element_5""".replace(System.lineSeparator(),"")
                    ,
                    """
                            properties.prop_2!=value_2,value_22
                            &properties.prop_3=.null,value_33
                            &properties.prop_4!=.null,value_44
                            &properties.prop_5=gte=5.5,55
                            &properties.prop_5_1=cs={\"id\":\"123\"},[{\"id\":\"123\"}],[{\"id\":\"123\"}]
                            &properties.prop_5_2!={\"id\":\"123\"},{\"id\":\"456\"},.null
                            &properties.prop_6=lte=6,66
                            &properties.prop_7=gt=7,77
                            &properties.prop_8=lt=8,88
                            &properties.array_1=cs=@element_1,element_2
                            &properties.prop_10=gte=555,5555
                            &properties.prop_11=lte=666,6666
                            &properties.prop_12=gt=777,7777
                            &properties.prop_13=lt=888,8888""".replace(System.lineSeparator(),"")
            )
    );
  }
}
