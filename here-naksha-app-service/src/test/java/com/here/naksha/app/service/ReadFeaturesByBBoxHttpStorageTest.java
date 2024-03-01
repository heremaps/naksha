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
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

@WireMockTest(httpPort = 8089)
class ReadFeaturesByBBoxHttpStorageTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

  private static final String SPACE_ID = "read_features_by_bbox_http_test_space";
  private static final String ENDPOINT = "/my_env/my_storage/my_feat_type/bbox";

  /*
  For this test suite, we upfront create various Features using different combination of Tags and Geometry.
  To know what exact features we create, check the create_features.json test file for test tc0700_xx().
  And then in subsequent tests, we validate the various GetByBBox APIs using different query parameters.
  */
  @BeforeAll
  static void setup() throws URISyntaxException, IOException, InterruptedException {
    setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup");
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
            .get("hub/spaces/" + SPACE_ID + "/bbox?" + tagsQueryParam + "&" + bboxQueryParam, streamId);

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
                    "hub/spaces/" + SPACE_ID + "/bbox?" + tagsQueryParam + "&" + bboxQueryParam + "&"
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
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/bbox?" + bboxQueryParam, streamId);

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
    HttpResponse<String> response = nakshaClient.get("hub/spaces/" + SPACE_ID + "/bbox?" + bboxQueryParam, streamId);

    // Then: Perform assertions
    assertThat(response)
            .hasStatus(400)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(expectedBodyPart, "Get Feature response body doesn't match");

    // Then: Verify request did not reach endpoint
    verify(0, getRequestedFor(urlPathEqualTo(ENDPOINT)));
  }

  @Test
  void propsearch() throws Exception {
    final String bboxQueryParam = "west=-180.0&north=90.0&east=180.0&south=-90.0&limit=30000";
    final String propSearch = "properties.prop_2!=value_2,value_22" +
            "&properties.prop_3=.null,value_33" +
            "&properties.prop_4!=.null,value_44" +
            "&properties.prop_5=gte=5.5,55" +
            "&properties.prop_5_1=cs=%7B%22id%22%3A%22123%22%7D,%5B%7B%22id%22%3A%22123%22%7D%5D" +
            "&properties.prop_5_2!=%7B%22id%22%3A%22123%22%7D,%7B%22id%22%3A%22456%22%7D,.null" +
            "&properties.prop_6=lte=6,66" +
            "&properties.prop_7=gt=7,77" +
            "&properties.prop_8=lt=8,88" +
            "&properties.array_1=cs=%40element_1,element_2" +
            "&properties.prop_10=gte=555,5555" +
            "&properties.prop_11=lte=666,6666" +
            "&properties.prop_12=gt=777,7777" +
            "&properties.prop_13=lt=888,8888" +
            "&properties.@ns:com:here:xyz.tags=cs=%7B%22id%22%3A%22123%22%7D,%5B%7B%22id%22%3A%22123%22%7D%5D,element_4" +
            "&properties.@ns:com:here:xyz.tags=cs=element_5";

    String streamId = UUID.randomUUID().toString();

    final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);

    // When: Get Features By BBox request is submitted to NakshaHub
    HttpResponse<String> response = nakshaClient
            .get("hub/spaces/" + SPACE_ID + "/bbox?" + bboxQueryParam + "&" + propSearch, streamId);


    verify(1, getRequestedFor(endpointPath)
            .withQueryParam("properties.prop_2!", equalTo("value_2,value_22"))
            .withQueryParam("properties.prop_3", equalTo(".null,value_33"))
            .withQueryParam("properties.prop_4!", equalTo(".null,value_44"))
            .withQueryParam("properties.prop_5", equalTo("gte=5.5,55"))
            // The parameters reaching the endpoint are url-encoded but wiremock expects decoded strings in equalTo()
            .withQueryParam("properties.prop_5_1", equalTo("cs={\"id\":\"123\"},[{\"id\":\"123\"}],[{\"id\":\"123\"}]"))
            .withQueryParam("properties.prop_5_2!", equalTo("{\"id\":\"123\"},{\"id\":\"456\"},.null"))
            .withQueryParam("properties.prop_6", equalTo("lte=6,66"))
            .withQueryParam("properties.prop_7", equalTo("gt=7,77"))
            .withQueryParam("properties.prop_8", equalTo("lt=8,88"))
            .withQueryParam("properties.array_1", equalTo("cs=@element_1,element_2"))
            .withQueryParam("properties.prop_10", equalTo("gte=555,5555"))
            .withQueryParam("properties.prop_11", equalTo("lte=666,6666"))
            .withQueryParam("properties.prop_12", equalTo("gt=777,7777"))
            .withQueryParam("properties.prop_13", equalTo("lt=888,8888"))
    );
  }
}
