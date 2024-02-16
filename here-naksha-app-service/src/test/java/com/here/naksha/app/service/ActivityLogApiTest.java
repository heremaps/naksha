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
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActivityLogApiTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
  private static final String REGULAR_SPACE_ID = "regular_space_ah";
  private static final String ACTIVITY_SPACE_ID = "activity_history_space";

  @BeforeAll
  static void setup() throws Exception {
    setupSpaceAndRelatedResources(nakshaClient, "ActivityLog/setup/activityLogSpace");
    createHandler(nakshaClient, "ActivityLog/setup/regularSpace/create_event_handler.json");
    createSpace(nakshaClient, "ActivityLog/setup/regularSpace/create_space.json");
  }

  @Test
  void tc1300_testActivityLogAfterCreateByUuid() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1300_afterCreateByUuid/create_features.json");
    String expectedGetResponse = TestUtil.loadFileOrFail("ActivityLog/TC1300_afterCreateByUuid/get_response.json");
    String streamId = UUID.randomUUID().toString();

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    String featureUuid = singleUuidFromCollectionResponse(createResp.body());

    // And: Client queries activity log space for this feature
    HttpResponse<String> getActivityResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features/" + featureUuid,
        streamId);

    // Then: Activity response is valid and conveys expected data
    assertThat(getActivityResp)
        .hasStreamIdHeader(streamId)
        .hasStatus(200)
        .hasJsonBody(formattedJson(expectedGetResponse, Map.of(
            "${id}", featureUuid,
            "${activityLogId}", "TC1300_feature"
        )));
  }

  @Test
  void tc1301_testActivityLogAfterCreateByFeatureId() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1301_afterCreateByFeatureId/create_features.json");
    String expectedGetResponse = TestUtil.loadFileOrFail("ActivityLog/TC1301_afterCreateByFeatureId/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1301_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    String featureUuid = singleUuidFromCollectionResponse(createResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getActivityResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery,
        streamId);

    // Then: Activity response is valid and conveys expected data
    assertThat(getActivityResp)
        .hasStreamIdHeader(streamId)
        .hasStatus(200)
        .hasJsonBody(formattedJson(expectedGetResponse, Map.of(
            "${id}", featureUuid,
            "${activityLogId}", featureId
        )));
  }

  @Test
  void tc1302_testActivityLogAfterUpdateByUuid() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1302_afterUpdateByUuid/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1302_afterUpdateByUuid/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail("ActivityLog/TC1302_afterUpdateByUuid/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1302_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    String updatedFeatureUuid = uuidFromFeatureResponse(updateResp.body());

    // And: Client queries activity log space for this feature
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features/" + updatedFeatureUuid, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${id}", updatedFeatureUuid,
            "${activityLogId}", featureId
        )));
  }

  @Test
  void tc1303_testActivityLogAfterUpdateByFeatureId() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1303_afterUpdateByFeatureId/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1303_afterUpdateByFeatureId/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail("ActivityLog/TC1303_afterUpdateByFeatureId/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1303_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    String createdFeatureUuid = singleUuidFromCollectionResponse(createResp.body());

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    String updatedFeatureUuid = uuidFromFeatureResponse(updateResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${firstId}", updatedFeatureUuid,
            "${secondId}", createdFeatureUuid,
            "${activityLogId}", featureId
        )));
  }

  @Test
  void tc1304_testActivityLogAfterDeleteByUuid() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1304_afterDeleteByUuid/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1304_afterDeleteByUuid/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail("ActivityLog/TC1304_afterDeleteByUuid/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1304_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);

    // And: This feature is deleted
    HttpResponse<String> deleteResp = nakshaClient.delete("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, streamId);
    assertThat(updateResp).hasStatus(200);
    String deletedFeatureUuid = uuidFromFeatureResponse(deleteResp.body());

    // And: Client queries activity log space for this feature
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features/" + deletedFeatureUuid, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${id}", deletedFeatureUuid,
            "${activityLogId}", featureId
        )));
  }

  @Test
  void tc1305_testActivityLogAfterDeleteByFeatureId() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1305_afterDeleteByFeatureId/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1305_afterDeleteByFeatureId/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail("ActivityLog/TC1305_afterDeleteByFeatureId/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1305_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    String createdFeatureUuid = singleUuidFromCollectionResponse(createResp.body());

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    String updatedFeatureUuid = uuidFromFeatureResponse(updateResp.body());

    // And: This feature is deleted
    HttpResponse<String> deleteResp = nakshaClient.delete("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, streamId);
    assertThat(updateResp).hasStatus(200);
    String deletedFeatureUuid = uuidFromFeatureResponse(deleteResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${firstId}", deletedFeatureUuid,
            "${secondId}", updatedFeatureUuid,
            "${thirdId}", createdFeatureUuid,
            "${activityLogId}", featureId
        )));
  }

  private String formattedJson(String json, Map<String, Object> propsToOverride){
    for (Entry<String, Object> entry : propsToOverride.entrySet()) {
      json = json.replace(entry.getKey(), entry.getValue().toString());
    }
    return json;
  }

  private String uuidFromFeatureResponse(String featureResponse) {
    XyzFeature feature = JsonSerializable.deserialize(featureResponse, XyzFeature.class);
    return feature.getProperties().getXyzNamespace().getUuid();
  }

  private String singleUuidFromCollectionResponse(String featureCollectionResponseJson) {
    List<String> uuids = uuidsFromCollectionResponse(featureCollectionResponseJson);
    assertEquals(1, uuids.size(), "Expected single uuid but response contained 0/multiple features (uuids: " + join(", ", uuids) + ")");
    return uuids.get(0);
  }

  private List<String> uuidsFromCollectionResponse(String featureCollectionResponseJson) {
    return JsonSerializable.deserialize(featureCollectionResponseJson, XyzFeatureCollection.class)
        .getFeatures().stream()
        .map(XyzFeature::getProperties)
        .map(XyzProperties::getXyzNamespace)
        .map(XyzNamespace::getUuid)
        .toList();
  }

  private static String urlEncoded(String text) {
    return URLEncoder.encode(text, UTF_8);
  }
}
