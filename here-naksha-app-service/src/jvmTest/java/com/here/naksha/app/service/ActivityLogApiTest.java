package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.createSpace;
import static com.here.naksha.app.common.CommonApiTestSetup.setupHandlerAndSpace;
import static com.here.naksha.app.common.FeatureMetadata.ExtractionUtil.featureMetadataFromCollectionResp;
import static com.here.naksha.app.common.FeatureMetadata.ExtractionUtil.featureMetadataFromFeatureResp;
import static com.here.naksha.app.common.FeatureMetadata.ExtractionUtil.featuresMetadataById;
import static com.here.naksha.app.common.TestUtil.urlEncoded;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.CommonApiTestSetup;
import com.here.naksha.app.common.FeatureMetadata;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.TestUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActivityLogApiTest extends ApiTest {

  private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
  private static final String REGULAR_SPACE_ID = "regular_space_ah";
  private static final String ACTIVITY_SPACE_ID = "activity_history_space";
  private static final String TEST_BASE_DIR = "ActivityLog";

  @BeforeAll
  static void setup() throws Exception {
    setupHandlerAndSpace(nakshaClient, TEST_BASE_DIR + "/setup/regularSpace");
    createHandler(nakshaClient, TEST_BASE_DIR + "/setup/activityLogSpace/create_event_handler.json");
    createSpace(nakshaClient, TEST_BASE_DIR + "/setup/activityLogSpace/create_space.json");
  }

  @Test
  void tc1300_testActivityLogAfterCreateByUuid() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1300_afterCreateByUuid/create_features.json");
    String expectedGetResponse = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1300_afterCreateByUuid/get_response.json");
    String streamId = UUID.randomUUID().toString();

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());

    // And: Client queries activity log space for this feature
    HttpResponse<String> getActivityResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features/" + createdFeature.uuid(),
        streamId);

    // Then: Activity response is valid and conveys expected data
    assertThat(getActivityResp)
        .hasStreamIdHeader(streamId)
        .hasStatus(200)
        .hasJsonBody(formattedJson(expectedGetResponse, Map.of(
            "${id}", createdFeature.uuid(),
            "${activityLogId}", "TC1300_feature",
            "\"${createdAt}\"", createdFeature.createdAt(),
            "\"${updatedAt}\"", createdFeature.updatedAt()
        )));
  }

  @Test
  void tc1301_testActivityLogAfterCreateByFeatureId() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1301_afterCreateByFeatureId/create_features.json");
    String expectedGetResponse = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1301_afterCreateByFeatureId/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1301_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getActivityResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery,
        streamId);

    // Then: Activity response is valid and conveys expected data
    assertThat(getActivityResp)
        .hasStreamIdHeader(streamId)
        .hasStatus(200)
        .hasJsonBody(formattedJson(expectedGetResponse, Map.of(
            "${id}", createdFeature.uuid(),
            "${activityLogId}", featureId,
            "\"${createdAt}\"", createdFeature.createdAt(),
            "\"${updatedAt}\"", createdFeature.updatedAt()
        )));
  }

  @Test
  void tc1302_testActivityLogAfterUpdateByUuid() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1302_afterUpdateByUuid/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1302_afterUpdateByUuid/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1302_afterUpdateByUuid/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1302_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson, streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: Client queries activity log space for this feature
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features/" + updatedFeature.uuid(), streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${id}", updatedFeature.uuid(),
            "${activityLogId}", featureId,
            "${puuid}", createdFeature.uuid(),
            "\"${createdAt}\"", updatedFeature.createdAt(),
            "\"${updatedAt}\"", updatedFeature.updatedAt()
        )));
  }

  @Test
  void tc1303_testActivityLogAfterUpdateByFeatureId() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1303_afterUpdateByFeatureId/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1303_afterUpdateByFeatureId/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1303_afterUpdateByFeatureId/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1303_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${firstId}", updatedFeature.uuid(),
            "${firstPuuid}", createdFeature.uuid(),
            "\"${firstCreatedAt}\"", updatedFeature.createdAt(),
            "\"${firstUpdatedAt}\"", updatedFeature.updatedAt(),
            "${secondId}", createdFeature.uuid(),
            "${activityLogId}", featureId,
            "\"${secondCreatedAt}\"", createdFeature.createdAt(),
            "\"${secondUpdatedAt}\"", createdFeature.updatedAt()
        )));
  }

  @Test
  void tc1304_testActivityLogAfterDeleteByUuid() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1304_afterDeleteByUuid/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1304_afterDeleteByUuid/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1304_afterDeleteByUuid/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1304_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: This feature is deleted
    HttpResponse<String> deleteResp = nakshaClient.delete("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, streamId);
    assertThat(deleteResp).hasStatus(200);
    FeatureMetadata deletedFeature = featureMetadataFromFeatureResp(deleteResp.body());

    // And: Client queries activity log space for this feature
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features/" + deletedFeature.uuid(), streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${id}", deletedFeature.uuid(),
            "${activityLogId}", featureId,
            "${puuid}", updatedFeature.uuid(),
            "\"${createdAt}\"", deletedFeature.createdAt(),
            "\"${updatedAt}\"", deletedFeature.updatedAt()
        )));
  }

  @Test
  void tc1305_testActivityLogAfterDeleteByFeatureId() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1305_afterDeleteByFeatureId/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1305_afterDeleteByFeatureId/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1305_afterDeleteByFeatureId/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1305_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: This feature is deleted
    HttpResponse<String> deleteResp = nakshaClient.delete("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata deletedFeature = featureMetadataFromFeatureResp(deleteResp.body());

    // And: Client queries activity log space for this feature
    String featureIdNamespaceQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + featureId;
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + featureIdNamespaceQuery, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, mapOf(
            "${firstId}", deletedFeature.uuid(),
            "${firstPuuid}", updatedFeature.uuid(),
            "\"${firstCreatedAt}\"", deletedFeature.createdAt(),
            "\"${firstUpdatedAt}\"", deletedFeature.updatedAt(),
            "${secondId}", updatedFeature.uuid(),
            "${secondPuuid}", createdFeature.uuid(),
            "\"${secondCreatedAt}\"", updatedFeature.createdAt(),
            "\"${secondUpdatedAt}\"", updatedFeature.updatedAt(),
            "${thirdId}", createdFeature.uuid(),
            "\"${thirdCreatedAt}\"", createdFeature.createdAt(),
            "\"${thirdUpdatedAt}\"", createdFeature.updatedAt(),
            "${activityLogId}", featureId
        )));
  }

  @Test
  void tc1306_testActivityLogWithSourceId() throws URISyntaxException, IOException, InterruptedException {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1306_withSourceId/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1306_withSourceId/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1306_withSourceId/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String sourceIdSpace = "source_id_ah_test_space";
    String activityLogSpace = "activity_history_space_source_id_tests";
    String featureId = "TC1306_feature";

    // And: space with sourceId handling
    CommonApiTestSetup.createHandler(nakshaClient, TEST_BASE_DIR + "/TC1306_withSourceId/sourceIdSpace/create_default_handler.json");
    CommonApiTestSetup.createHandler(nakshaClient, TEST_BASE_DIR + "/TC1306_withSourceId/sourceIdSpace/create_source_id_handler.json");
    CommonApiTestSetup.createSpace(nakshaClient, TEST_BASE_DIR + "/TC1306_withSourceId/sourceIdSpace/create_space.json");

    // And: space with activity log that is based on sourceId handling space
    CommonApiTestSetup.createHandler(nakshaClient, TEST_BASE_DIR + "/TC1306_withSourceId/activityLogSpace/create_event_handler.json");
    CommonApiTestSetup.createSpace(nakshaClient, TEST_BASE_DIR + "/TC1306_withSourceId/activityLogSpace/create_space.json");

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + sourceIdSpace + "/features", createFeatureJson, streamId);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + sourceIdSpace + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: Client queries activity log space for this feature
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + activityLogSpace + "/features/" + updatedFeature.uuid(), streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${uuid}", updatedFeature.uuid(),
            "${puuid}", createdFeature.uuid(),
            "\"${createdAt}\"", updatedFeature.createdAt(),
            "\"${updatedAt}\"", updatedFeature.updatedAt()
        )));
  }

  @Test
  void tc1307_testActivityLogByBBox() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1307_byBbox/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1307_byBbox/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1307_byBbox/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1307_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: Client queries activity log space against new tags and bbox
    final String bboxQueryParam = "west=-180&south=-90&east=180&north=90";
    final String tagsQueryParam = "tags=tc1307+two";
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/bbox?" + tagsQueryParam + "&" + bboxQueryParam,
        streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${uuid}", updatedFeature.uuid(),
            "${puuid}", createdFeature.uuid(),
            "\"${createdAt}\"", updatedFeature.createdAt(),
            "\"${updatedAt}\"", updatedFeature.updatedAt()
        )));
  }

  @Test
  void tc1308_testActivityLogByTile() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1308_byTile/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1308_byTile/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1308_byTile/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1308_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    FeatureMetadata createdFeature = featureMetadataFromCollectionResp(createResp.body());
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: Client queries activity log space against new tags and bbox
    final String tagsQueryParam = "tags=tc1308+two";
    final HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/tile/quadkey/1?" + tagsQueryParam,
        streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${uuid}", updatedFeature.uuid(),
            "${puuid}", createdFeature.uuid(),
            "\"${createdAt}\"", updatedFeature.createdAt(),
            "\"${updatedAt}\"", updatedFeature.updatedAt()
        )));
  }

  @Test
  void tc1309_testActivityLogForMultipleFeatureIds() throws Exception {
    // Given: Test files
    String createFeaturesJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1309_multipleFeatureIds/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1309_multipleFeatureIds/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1309_multipleFeatureIds/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String firstFeatureId = "TC1309_feature_1";
    String secondFeatureId = "TC1309_feature_2";

    // When: New features are created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeaturesJson, streamId);
    assertThat(createResp).hasStatus(200);
    Map<String, FeatureMetadata> createdFeatures = featuresMetadataById(createResp.body());
    FeatureMetadata fistCreatedFeature = createdFeatures.get(firstFeatureId);
    FeatureMetadata secondCreatedFeature = createdFeatures.get(secondFeatureId);

    // And: Second feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + secondFeatureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: Client queries activity log space for this feature
    String encodedLogId = urlEncoded("p.@ns:com:here:xyz:log.id");
    String logIdQuery = "%s=%s,%s".formatted(encodedLogId, firstFeatureId, secondFeatureId);
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/search?" + logIdQuery, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${feature_1_created_uuid}", fistCreatedFeature.uuid(),
            "${feature_2_created_uuid}", secondCreatedFeature.uuid(),
            "${feature_2_updated_uuid}", updatedFeature.uuid(),
            "\"${feature_1_created_at}\"", fistCreatedFeature.createdAt(),
            "\"${feature_1_updated_at}\"", fistCreatedFeature.updatedAt(),
            "\"${feature_2_created_created_at}\"", secondCreatedFeature.createdAt(),
            "\"${feature_2_created_updated_at}\"", secondCreatedFeature.updatedAt(),
            "\"${feature_2_updated_created_at}\"", updatedFeature.createdAt(),
            "\"${feature_2_updated_updated_at}\"", updatedFeature.updatedAt()
        )));
  }

  @Test
  void tc1310_testActivityLogForMultipleUuids() throws Exception {
    // Given: Test files
    String createFeaturesJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1310_multipleUuids/create_features.json");
    String updateFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1310_multipleUuids/update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1310_multipleUuids/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String firstFeatureId = "TC1310_feature_1";
    String secondFeatureId = "TC1310_feature_2";

    // When: New features are created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeaturesJson, streamId);
    assertThat(createResp).hasStatus(200);
    Map<String, FeatureMetadata> createdFeatures = featuresMetadataById(createResp.body());
    FeatureMetadata fistCreatedFeature = createdFeatures.get(firstFeatureId);
    FeatureMetadata secondCreatedFeature = createdFeatures.get(secondFeatureId);

    // And: First feature is deleted
    HttpResponse<String> deleteResp = nakshaClient.delete("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + firstFeatureId, streamId);
    assertThat(deleteResp).hasStatus(200);
    FeatureMetadata deletedFeature = featureMetadataFromFeatureResp(deleteResp.body());

    // And: Second feature is updated
    HttpResponse<String> updateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + secondFeatureId, updateFeatureJson,
        streamId);
    assertThat(updateResp).hasStatus(200);
    FeatureMetadata updatedFeature = featureMetadataFromFeatureResp(updateResp.body());

    // And: Client queries activity log space for deleted feature (f1) and updated feature (f2)
    String uuidsQuery = "id=%s&id=%s".formatted(urlEncoded(updatedFeature.uuid()), urlEncoded(deletedFeature.uuid()));
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features?" + uuidsQuery, streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, mapOf(
            "${feature_1_created_uuid}", fistCreatedFeature.uuid(),
            "${feature_1_deleted_uuid}", deletedFeature.uuid(),
            "${feature_2_created_uuid}", secondCreatedFeature.uuid(),
            "${feature_2_updated_uuid}", updatedFeature.uuid(),
            "\"${feature_1_created_created_at}\"", fistCreatedFeature.createdAt(),
            "\"${feature_1_created_updated_at}\"", fistCreatedFeature.updatedAt(),
            "\"${feature_1_deleted_created_at}\"", deletedFeature.createdAt(),
            "\"${feature_1_deleted_updated_at}\"", deletedFeature.updatedAt(),
            "\"${feature_2_created_created_at}\"", secondCreatedFeature.createdAt(),
            "\"${feature_2_created_updated_at}\"", secondCreatedFeature.updatedAt(),
            "\"${feature_2_updated_created_at}\"", updatedFeature.createdAt(),
            "\"${feature_2_updated_updated_at}\"", updatedFeature.updatedAt()
        )));
  }

  @Test
  void tc1311_shouldNotReturnAnythingIfLogIdAndUuidConcernDifferentFeatures() throws Exception {
    // Given: Test files
    String createFirstJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1311_invalidMix/create_first.json");
    String createSecondJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1311_invalidMix/create_second.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1311_invalidMix/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String firstFeatureId = "TC1311_feature_1";

    // When: First feature is created
    HttpResponse<String> firstCreatedResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFirstJson, streamId);
    assertThat(firstCreatedResp).hasStatus(200);

    // And: Second feature is created
    HttpResponse<String> secondCreateResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createSecondJson, streamId);
    assertThat(secondCreateResp).hasStatus(200);
    String secondCreatedUuid = featureMetadataFromCollectionResp(secondCreateResp.body()).uuid();

    // And: Client queries activity log space using both activityLogNs (in query) and UUID (in path)
    String secondUuidQuery = "f.id=%s".formatted(urlEncoded(secondCreatedUuid));
    String firstIdNsQuery = urlEncoded("p.@ns:com:here:xyz:log.id") + "=" + firstFeatureId;
    HttpResponse<String> getResp = nakshaClient.get(
        "hub/spaces/" + ACTIVITY_SPACE_ID + "/search?%s&%s".formatted(secondUuidQuery, firstIdNsQuery), streamId);

    // Then
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(expectedActivityResp);
  }

  @Test
  void tc1312_shouldReturnOnlyLimitedFeaturesForUuid() throws Exception {
    // Given: Test files
    String createFeatureJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1312_limitedActivity/create_features.json");
    String firstUpdateJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1312_limitedActivity/first_update_feature.json");
    String secondUpdateJson = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1312_limitedActivity/second_update_feature.json");
    String expectedActivityResp = TestUtil.loadFileOrFail(TEST_BASE_DIR + "/TC1312_limitedActivity/get_response.json");
    String streamId = UUID.randomUUID().toString();
    String featureId = "TC1312_feature";

    // When: New feature is created
    HttpResponse<String> createResp = nakshaClient.post("hub/spaces/" + REGULAR_SPACE_ID + "/features", createFeatureJson, streamId);
    assertThat(createResp).hasStatus(200);

    // And: This feature is updated for the first time
    HttpResponse<String> firstUpdateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, firstUpdateJson,
        streamId);
    assertThat(firstUpdateResp).hasStatus(200);
    FeatureMetadata firstUpdatedFeature = featureMetadataFromFeatureResp(firstUpdateResp.body());

    // And: This feature is updated for the second time
    HttpResponse<String> secondUpdateResp = nakshaClient.put("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, secondUpdateJson,
        streamId);
    assertThat(secondUpdateResp).hasStatus(200);
    FeatureMetadata secondUpdatedFeature = featureMetadataFromFeatureResp(secondUpdateResp.body());

    // And: This feature is deleted
    HttpResponse<String> deleteResp = nakshaClient.delete("hub/spaces/" + REGULAR_SPACE_ID + "/features/" + featureId, streamId);
    assertThat(deleteResp).hasStatus(200);

    // And: Client queries activity log space for the second updated state
    HttpResponse<String> getResp = nakshaClient.get("hub/spaces/" + ACTIVITY_SPACE_ID + "/features/" + secondUpdatedFeature.uuid(),
        streamId);

    // Then: Expected ActivityLog response matches the response
    assertThat(getResp)
        .hasStatus(200)
        .hasStreamIdHeader(streamId)
        .hasJsonBody(formattedJson(expectedActivityResp, Map.of(
            "${id}", secondUpdatedFeature.uuid(),
            "${activityLogId}", featureId,
            "${puuid}", firstUpdatedFeature.uuid(),
            "\"${createdAt}\"", secondUpdatedFeature.createdAt(),
            "\"${updatedAt}\"", secondUpdatedFeature.updatedAt()
        )));
  }

  private String formattedJson(String json, Map<String, Object> propsToOverride) {
    for (Entry<String, Object> entry : propsToOverride.entrySet()) {
      json = json.replace(entry.getKey(), entry.getValue().toString());
    }
    return json;
  }

  private static Map mapOf(Object... args) {
    if (args.length % 2 != 0) {
      throw new IllegalArgumentException("Expected even number of args (key-value pairs!)");
    }
    Map map = new HashMap();
    for (int i = 0; i < args.length; i += 2) {
      map.put(args[i], args[i + 1]);
    }
    return map;
  }
}
