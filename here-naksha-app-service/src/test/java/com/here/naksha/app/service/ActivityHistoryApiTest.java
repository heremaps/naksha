package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.createSpace;
import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.TestUtil;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeatureCollection;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
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

    // Created Featur gets retrieved (so that we will know it's uuid)
    HttpResponse<String> getFeatureResp = nakshaClient.get("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, streamId);
    assertThat(getFeatureResp).hasStatus(200);
    String featureUuid = JsonSerializable.deserialize(getFeatureResp.body(), XyzFeature.class).getProperties().getXyzNamespace().getUuid();

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=activity_feature_1";
    HttpResponse<String> getActivityResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);

    // Then:
    assertThat(getActivityResp)
        .hasStreamIdHeader(streamId)
        .hasStatus(200)
        .hasJsonBody(expectedGetResponse);

    // And:
    String activityFeatureId = JsonSerializable.deserialize(getActivityResp.body(), XyzFeatureCollection.class).getFeatures().get(0).getId();
    Assertions.assertEquals(featureUuid, activityFeatureId);
  }

  @Test
  void tc1300_testActivityLogAfterUpdate() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail("ActivityHistory/afterUpdate/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail("ActivityHistory/afterUpdate/update_features.json");
    String streamId = UUID.randomUUID().toString();

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // tmp
    String featureIdNamespaceQuery0 = urlEncoded("p.@ns:com:here:xyz:log.id") + "=activity_feature_1";
    HttpResponse<String> getResp0 = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery0, streamId);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", updateFeatureJson, streamId);
    assertThat(updateResp).hasStatus(200);

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=activity_feature_1";
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);
    assertThat(getResp).hasStatus(404);

    // Then:
    Assertions.assertTrue(true);
  }

  private static String urlEncoded(String text) {
    return URLEncoder.encode(text, UTF_8);
  }
}
