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
  void tc1300_testActivityLogAfterCreate() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1300_afterCreate/create_features.json");
    String expectedGetResponse = TestUtil.loadFileOrFail("ActivityLog/TC1300_afterCreate/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1300_feature";

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
        .hasJsonBody(expectedGetResponse);

    // And: Activity payload has the same id as feature's id
    assertEquals(featureUuid, singleIdFromCollectionResponse(getActivityResp.body()));
  }

  @Test
  void tc1300_testActivityLogAfterUpdate() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1301_afterUpdate/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail("ActivityLog/TC1301_afterUpdate/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail("ActivityLog/TC1301_afterUpdate/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1301_feature";

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
        .hasJsonBody(expectedActivityResp);

    // And: ids of ActivityLog match uuids of Features
    assertEquals(
        List.of(updatedFeatureUuid, createdFeatureUuid),
        idsFromCollectionResponse(getResp.body())
    );
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

  private static String singleIdFromCollectionResponse(String featureCollectionResponseJson) {
    List<String> ids = idsFromCollectionResponse(featureCollectionResponseJson);
    assertEquals(1, ids.size(), "Expected single id but response contained 0/multiple features (ids: " + join(", ", ids) + ")");
    return ids.get(0);
  }

  private static List<String> idsFromCollectionResponse(String featureCollectionResponseJson) {
    return JsonSerializable.deserialize(featureCollectionResponseJson, XyzFeatureCollection.class)
        .getFeatures().stream()
        .map(XyzFeature::getId)
        .toList();
  }

  private static String urlEncoded(String text) {
    return URLEncoder.encode(text, UTF_8);
  }
}
