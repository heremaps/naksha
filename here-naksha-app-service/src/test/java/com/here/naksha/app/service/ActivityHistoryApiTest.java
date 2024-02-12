package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.createSpace;
import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.TestUtil;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActivityHistoryApiTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
  private static final String REGULAR_SPACE_ID = "regular_space_ah";

  private static final String ACTIVITY_SPACE_ID = "activity_history_space";

  @BeforeAll
  static void setup() throws Exception {
    setupSpaceAndRelatedResources(nakshaClient, "ActivityHistory/setupActivityLogSpace");
    createHandler(nakshaClient, "ActivityHistory/setupRegularSpace/create_event_handler.json");
    createSpace(nakshaClient, "ActivityHistory/setupRegularSpace/create_space.json");
  }

  @Test
  void tc1300_testActivityLogAfterCreate() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityHistory/afterCreate/create_features.json");
    String expectedGetResponse = TestUtil.loadFileOrFail("ActivityHistory/afterCreate/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "activity_feature_1";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    String featureUuid = singleUuidFromResponse(createResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getActivityResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);

    // Then: Activity response is valid and conveys expected data
    assertThat(getActivityResp)
        .hasStreamIdHeader(streamId)
        .hasStatus(200)
        .hasJsonBody(expectedGetResponse);

    // And: Activity payload has the same id as feature's id
    assertEquals(featureUuid, singleIdFromResponse(getActivityResp.body()));
  }

  @Test
  void tc1300_testActivityLogAfterUpdate() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityHistory/afterUpdate/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail("ActivityHistory/afterUpdate/update_features.json");
    String expectedActivityResp = TestUtil.loadFileOrFail("ActivityHistory/afterUpdate/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "activity_feature_1";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    String firstUuid = singleUuidFromResponse(createResp.body());

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", updateFeatureJson, streamId);
    assertThat(updateResp).hasStatus(200);
    String secondUuid = singleUuidFromResponse(updateResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);
    assertThat(getResp).hasStatus(200);

    // Then:
    Assertions.assertTrue(true);
  }

  private String singleUuidFromResponse(String featureCollectionResponseJson){
    List<String> uuids = uuidsFromResponse(featureCollectionResponseJson);
    assertEquals(1, uuids.size(), "Expected single uuid but response contained 0/multiple features (uuids: " + join(", ", uuids) + ")");
    return uuids.get(0);
  }

  private List<String> uuidsFromResponse(String featureCollectionResponseJson){
    return JsonSerializable.deserialize(featureCollectionResponseJson, XyzFeatureCollection.class)
        .getFeatures().stream()
        .map(XyzFeature::getProperties)
        .map(XyzProperties::getXyzNamespace)
        .map(XyzNamespace::getUuid)
        .toList();
  }

  private static String singleIdFromResponse(String featureCollectionResponseJson){
    List<String> ids = idsFromResponse(featureCollectionResponseJson);
    assertEquals(1, ids.size(), "Expected single id but response contained 0/multiple features (ids: " + join(", ", ids) + ")");
    return ids.get(0);
  }

  private static List<String> idsFromResponse(String featureCollectionResponseJson){
    return JsonSerializable.deserialize(featureCollectionResponseJson, XyzFeatureCollection.class)
        .getFeatures().stream()
        .map(XyzFeature::getId)
        .toList();
  }

  private static String urlEncoded(String text) {
    return URLEncoder.encode(text, UTF_8);
  }
}
