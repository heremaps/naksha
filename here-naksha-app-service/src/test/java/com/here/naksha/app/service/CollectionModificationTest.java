package com.here.naksha.app.service;

import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.TestUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.UUID;

import static com.here.naksha.app.common.CommonApiTestSetup.*;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

public class CollectionModificationTest extends ActivityLogApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
    private static final String REGULAR_SPACE_ID = "regular_space_collection_mod_test";
    private static final String ACTIVITY_SPACE_ID = "history_space_collection_mod_test";

    @BeforeAll
    static void setup() throws Exception {
        setupSpaceAndRelatedResources(nakshaClient, "CollectionModification/setup/regularSpace");
        createHandler(nakshaClient, "CollectionModification/setup/historySpace/create_event_handler.json");
        createSpace(nakshaClient, "CollectionModification/setup/historySpace/create_space.json");
    }

    @Test
    void tc1400_testActivityLogAfterDeleteByFeatureId() throws Exception {
        // Given: Test files
        String createFeatureJson = TestUtil.loadFileOrFail("CollectionModification/TC1400_WriteCollectionSuccess/create_features.json");
        String updateFeatureJson = TestUtil.loadFileOrFail("CollectionModification/TC1400_WriteCollectionSuccess/update_feature.json");
        String expectedActivityResp = TestUtil.loadFileOrFail("CollectionModification/TC1400_WriteCollectionSuccess/get_response.json");
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
                        "${firstId}", deletedFeature.uuid,
                        "${firstPuuid}", updatedFeature.uuid,
                        "\"${firstCreatedAt}\"", deletedFeature.createdAt,
                        "\"${firstUpdatedAt}\"", deletedFeature.updatedAt,
                        "${secondId}", updatedFeature.uuid,
                        "${secondPuuid}", createdFeature.uuid,
                        "\"${secondCreatedAt}\"", updatedFeature.createdAt,
                        "\"${secondUpdatedAt}\"", updatedFeature.updatedAt,
                        "${thirdId}", createdFeature.uuid,
                        "\"${thirdCreatedAt}\"", createdFeature.createdAt,
                        "\"${thirdUpdatedAt}\"", createdFeature.updatedAt,
                        "${activityLogId}", featureId
                )));
    }


}
