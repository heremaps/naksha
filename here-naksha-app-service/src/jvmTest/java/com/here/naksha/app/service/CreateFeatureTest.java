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

import static com.here.naksha.app.common.CommonApiTestSetup.setupHandlerAndSpace;
import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static com.here.naksha.app.common.TestUtil.getHeader;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static com.here.naksha.app.common.TestUtil.urlEncoded;
import static com.here.naksha.app.common.assertions.ResponseAssertions.IGNORE_XYZ;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.lib.core.models.naksha.Space;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import naksha.base.JvmBoxingUtil;
import naksha.base.Platform;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class CreateFeatureTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
  private static final String SPACE_ID = "create_features_test_space";

  private static boolean runBigPayloadTests() {
    // by default enabled
    return !Boolean.parseBoolean(System.getenv("DISABLE_BIG_PAYLOAD_TESTS"));
  }

  @BeforeAll
  static void setup() {
    setupHandlerAndSpace(nakshaClient, "CreateFeatures/setup");
  }

  @Test
  void tc0300_testCreateFeaturesWithNewIds() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: Create Features request (against above Space)
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0300_createFeaturesWithNewIds/create_features.json");
    final String expectedBodyPart = loadFileOrFail("CreateFeatures/TC0300_createFeaturesWithNewIds/feature_response_part.json");
    String streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match", IGNORE_XYZ)
        .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
        .hasInsertedIdsMatchingFeatureIds(null)
        .hasUuids();
  }

  @Test
  void tc0301_testCreateFeaturesWithGivenIds() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate features getting created successfully with the given Ids
    String streamId;
    HttpResponse<String> response;

    // Given: Create Features request
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0301_createFeaturesWithGivenIds/create_features.json");
    final String expectedBodyPart = loadFileOrFail("CreateFeatures/TC0301_createFeaturesWithGivenIds/feature_response_part.json");
    streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
        .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
        .hasInsertedIdsMatchingFeatureIds(null)
        .hasUuids()
    ;
  }

  // Disabled - we don't want to verify prefix now
  //  @Test
  void tc0302_testCreateFeaturesWithPrefixId() throws Exception {
    // NOTE : This test depends on setup done as part of tc0300_testCreateFeaturesWithNewIds

    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate features getting created successfully with the given prefix Id
    String streamId;
    HttpRequest request;
    HttpResponse<String> response;

    // Given: Feature ID prefix
    final String prefixId = "1000";
    // Given: Create Features request
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0302_createFeaturesWithPrefixId/create_features.json");
    final String expectedBodyPart = loadFileOrFail("CreateFeatures/TC0302_createFeaturesWithPrefixId/feature_response_part.json");
    streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    response = getNakshaClient()
        .post("hub/spaces/" + SPACE_ID + "/features?prefixId=" + urlEncoded(prefixId), bodyJson, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
        .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
        .hasInsertedIdsMatchingFeatureIds(prefixId)
        .hasUuids()
    ;
  }

  @Test
  void tc0303_testCreateFeaturesWithAddTags() throws Exception {
    // NOTE : This test depends on setup done as part of tc0300_testCreateFeaturesWithNewIds

    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate features getting created successfully with the given addTags param
    String streamId;
    HttpRequest request;
    HttpResponse<String> response;

    // Given: addTags API query param
    final String tagQueryParam = "addTags=100"
                                 + "&addTags=New_Normalized_Tag"
                                 + "&addTags=" + urlEncoded("@New_Non_Normalized_Tag")
                                 + "&addTags=Existing_Normalized_Tag"
                                 + "&addTags=" + urlEncoded("@Existing_Non_Normalized_Tag");
    // Given: Create Features request
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0303_createFeaturesWithAddTags/create_features.json");
    final String expectedBodyPart = loadFileOrFail("CreateFeatures/TC0303_createFeaturesWithAddTags/feature_response_part.json");
    streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features?" + tagQueryParam, bodyJson, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
        .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
        .hasInsertedIdsMatchingFeatureIds(null)
        .hasUuids()
    ;
  }

  @Test
  void tc0304_testCreateFeaturesWithRemoveTags() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate features getting created successfully with the given removeTags param
    String streamId;
    HttpRequest request;
    HttpResponse<String> response;

    // Given: removeTags API query param
    final String tagQueryParam = "removeTags=non_existing_tag"
                                 + "&removeTags=Existing_Normalized_Tag"
                                 + "&removeTags=" + URLEncoder.encode("@Existing_Non_Normalized_Tag", UTF_8);
    // Given: Create Features request
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0304_createFeaturesWithRemoveTags/create_features.json");
    final String expectedBodyPart =
        loadFileOrFail("CreateFeatures/TC0304_createFeaturesWithRemoveTags/feature_response_part.json");
    streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features?" + tagQueryParam, bodyJson, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
        .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
        .hasInsertedIdsMatchingFeatureIds(null)
        .hasUuids()
    ;
  }

  @Test
  void tc0305_testCreateFeaturesWithDupIds() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate request is successful, when features with the same ID submitted again (replacing existing features)
    String streamId;
    HttpResponse<String> response;

    // Given: New Features in place
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0305_createFeaturesWithDupIds/initial_features_upsert.json");
    streamId = UUID.randomUUID().toString();
    response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);
    assertEquals(200, response.statusCode(), "ResCode mismatch");

    // When: Create Features request is submitted with the same Ids
    final String request = loadFileOrFail("CreateFeatures/TC0305_createFeaturesWithDupIds/second_features_upsert.json");
    response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", request, streamId);

    // Then: Perform assertions
    final String expectedBodyPart = loadFileOrFail("CreateFeatures/TC0305_createFeaturesWithDupIds/expected_partial_response.json");
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
        .hasMatchingInsertedCount(2)
        .hasInsertedIdsMatchingFeatureIds(null);
  }

  @Test
  void tc0307_testCreateFeaturesWithNoHandler() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate request gets failed if we attempt creating features with no associated Event Handler
    String streamId = UUID.randomUUID().toString();

    // Given: Space (without EventHandler) configured in Admin storage
    final String spaceJson = loadFileOrFail("CreateFeatures/TC0307_createFeaturesWithNoHandler/create_space.json");
    final Space space = JvmBoxingUtil.box(Platform.fromJson(spaceJson), Space.class);
    HttpResponse<String> response = getNakshaClient().post("hub/spaces", spaceJson, streamId);
    assertEquals(200, response.statusCode(), "ResCode mismatch. Failed creating Event Handler");

    // Given: Create Features request (against above Space)
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0307_createFeaturesWithNoHandler/create_features.json");
    final String expectedBodyPart = loadFileOrFail("CreateFeatures/TC0307_createFeaturesWithNoHandler/feature_response_part.json");

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    response = getNakshaClient().post("hub/spaces/" + space.getId() + "/features", bodyJson, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(404)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
  }

  @Test
  void tc0308_testCreateFeaturesWithNoSpace() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Validate request gets failed if we attempt creating features on missing space
    String streamId;
    HttpResponse<String> response;

    // Given: Create Features request (against missing space)
    final String spaceId = "missing_space";
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0308_createFeaturesWithNoSpace/create_features.json");
    final String expectedBodyPart = loadFileOrFail("CreateFeatures/TC0308_createFeaturesWithNoSpace/feature_response_part.json");
    streamId = UUID.randomUUID().toString();

    // When: Create Features request is submitted to NakshaHub Space Storage instance
    response = getNakshaClient().post("hub/spaces/" + spaceId + "/features", bodyJson, streamId);

    // Then: Perform assertions
    assertThat(response)
        .hasStatus(404)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
  }

  @Test
  void tc0309_testCreateFeaturesWithUuid() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    final String streamId = UUID.randomUUID().toString();

    // Given: new Features in Space
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0309_createFeaturesWithUuid/create_features.json");
    final HttpResponse<String> response =
        getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);
    assertEquals(200, response.statusCode(), "ResCode mismatch");

    // Given: existing feature is fetched
    final HttpResponse<String> getResponse =
        getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features/my-custom-id-309-1", streamId);
    final NakshaFeature feature = parseJson(getResponse.body(), NakshaFeature.class);
    Assertions.assertNotNull(feature);
    final NakshaProperties newPropsOldUuid = feature.getProperties();
    final NakshaProperties newPropsOutdatedUuid = newPropsOldUuid.copy(true);
    final NakshaProperties nullUuidProps = new NakshaProperties();

    // And: first set of new properties with UUID that points to current feature state (head)
    newPropsOldUuid.put("speedLimit", "30");
    newPropsOldUuid.put("newProperty", "was patched in");
    newPropsOldUuid.remove("existingProperty");

    // And: second set of new properties that are correct and point to currentl feature state (head)
    // Note: this `outdatedUuid` will should outdated after first patch (UUID of head state should change)
    newPropsOutdatedUuid.put("speedLimit", "120");

    // And: set ofm properties with Null UUID
    nullUuidProps.put("uuid", null);
    nullUuidProps.put("patchedWithNullUUID", "yesyesyes");

    // When: execute request with first set of properties with correct UUID, should success
    feature.setProperties(newPropsOldUuid);
    final HttpResponse<String> responsePatchSuccess = getNakshaClient()
        .post(
            "hub/spaces/" + SPACE_ID + "/features",
            """
                {
                "type": "FeatureCollection",
                "features": [
                """ + Platform.toJSON(feature) + "]}",
            streamId);

    // Then: first patch succeeded
    final String firstResponse = loadFileOrFail("CreateFeatures/TC0309_createFeaturesWithUuid/first_response.json");
    assertThat(responsePatchSuccess)
        .hasStreamIdHeader(getHeader(responsePatchSuccess, HDR_STREAM_ID))
        .hasStatus(200)
        .hasJsonBody(firstResponse);

    // When: execute request with now outdated UUID, should fail
    feature.setProperties(newPropsOutdatedUuid);
    final HttpResponse<String> responseUpdateFail = getNakshaClient()
        .post(
            "hub/spaces/" + SPACE_ID + "/features",
            """
                {
                "type": "FeatureCollection",
                "features": [
                """ + Platform.toJSON(feature) + "]}",
            streamId);

    // Then: second patch should fail due to UUID conflict
    assertEquals(409, responseUpdateFail.statusCode(), "ResCode mismatch");

    // When: executing request with null UUID, should success with overriding
    feature.setProperties(nullUuidProps);
    final HttpResponse<String> responseSuccessNoUuidGiven = getNakshaClient()
        .post(
            "hub/spaces/" + SPACE_ID + "/features",
            """
                {
                "type": "FeatureCollection",
                "features": [
                """ + Platform.toJSON(feature) + "]}",
            streamId);

    // Then: third patch should succeed
    final String thirdResponse = loadFileOrFail("CreateFeatures/TC0309_createFeaturesWithUuid/third_response.json");
    assertThat(responseSuccessNoUuidGiven)
        .hasStreamIdHeader(getHeader(responsePatchSuccess, HDR_STREAM_ID))
        .hasStatus(200)
        .hasJsonBody(thirdResponse);
  }

  // TODO : This test is disabled for now due to failure in FibMap for generating payload bigger than 6MB
  /*
  @Test
  @EnabledIf("runBigPayloadTests")
  void tc0310_testBigRequestPayload() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: Input base Feature JSON
    final String basePayload = loadFileOrFail("CreateFeatures/TC0310_createBigFeature/feature_base_payload.json");
    final XyzFeature feature = JsonUtil.parseJson(basePayload, XyzFeature.class);

    // Given: Big Feature generated from base Feature
    final long targetBodySize = (5 * 1024 * 1024) - 1024; // keep buffer of 1KB
    FeatureUtil.generateBigFeature(feature, targetBodySize-feature.serialize().length());

    // Given: Big request payload ready to test
    final String requestPayload = JsonUtil.toJson(new FeatureCollectionRequest().withFeatures(List.of(feature)));
    String streamId = UUID.randomUUID().toString();

    assertTrue(requestPayload.length() >= targetBodySize, "Request payload size isn't big enough");

    // When: Big Feature request is submitted to NakshaHub
    HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", requestPayload, streamId);

    // Then: Perform assertions that we get success response
    assertThat(response)
            .hasStatus(200)
            .hasStreamIdHeader(streamId)
            .hasJsonBody(requestPayload, "Create Feature response body doesn't match")
            .hasInsertedCountMatchingWithFeaturesInRequest(requestPayload)
            .hasInsertedIdsMatchingFeatureIds(null)
            .hasUuids();
  }
  */

  @Test
  @EnabledIf("runBigPayloadTests")
  void tc0311_testFixedSize22MBRequestPayload() throws Exception {
    // Test API : POST /hub/spaces/{spaceId}/features
    // Given: Big Input request payload of 22MB
    final long expBodySize = 22 * 1024 * 1024;
    final Duration timeout = Duration.ofSeconds(30); // bigger timeout for this request
    final String bodyJson = loadFileOrFail("CreateFeatures/TC0311_create22MBFeature/big_admin_payload_20485579.json");
    String streamId = UUID.randomUUID().toString();

    assertTrue(bodyJson.length() >= expBodySize, "Request payload size not as big as %s bytes".formatted(expBodySize));

    // When: Big Feature request is submitted to NakshaHub
    HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId, timeout);

    // Then: Perform assertions that we get success (and not 413 - Request Entity Too Large)
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasResBodySizeGTE(expBodySize);

    // When: We query the same Feature from NakshaHub
    streamId = UUID.randomUUID().toString();
    final String idQueryParam = "id=%s".formatted(urlEncoded("feature-id-20485579"));
    response = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idQueryParam, streamId, timeout, null);

    // Then: Perform assertions that we get success (and not 413 - Request Entity Too Large)
    assertThat(response)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasResBodySizeGTE(expBodySize);
  }

}
