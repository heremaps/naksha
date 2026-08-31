package com.here.naksha.app.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.createStorage;
import static com.here.naksha.app.common.CommonApiTestSetup.setupHandlerAndSpace;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static naksha.base.Platform.javaProxy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import naksha.geo.SpFeatureCollection;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@WireMockTest(httpPort = 9094)
public class PatchOnViewWithHttpStorageTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
    private static final String PSQL_SPACE_ID = "patch_on_view_test_psql_space";
    private static final String VIEW_SPACE_ID = "patch_on_view_test_view_space";
    private static final String ENDPOINT = "/my_env/my_storage/my_feat_type/features";
    private static final String TEST_DIR_SETUP_PATH = "PatchOnViewWithHttpStorage/setup/";

    @BeforeAll
    static void setup() throws URISyntaxException, IOException, InterruptedException {
        // Set up Http Storage
        createStorage(nakshaClient, TEST_DIR_SETUP_PATH + "http_storage_space/create_storage.json");
        // Set up Base space - using Http Storage
        setupHandlerAndSpace(nakshaClient, TEST_DIR_SETUP_PATH + "http_storage_space");
        // Set up Delta space - using Psql Storage
        createHandler(nakshaClient, TEST_DIR_SETUP_PATH + "psql_storage_space/create_sourceId_handler.json");
        setupHandlerAndSpace(nakshaClient, TEST_DIR_SETUP_PATH + "psql_storage_space");
        // Set up View Storage, View space - using above Delta and Base spaces
        createStorage(nakshaClient, TEST_DIR_SETUP_PATH + "view_space/create_storage.json");
        setupHandlerAndSpace(nakshaClient, TEST_DIR_SETUP_PATH + "view_space");
        // Load some test data in Delta space
        final String initialFeaturesJson = loadFileOrFail(TEST_DIR_SETUP_PATH + "psql_storage_space/create_features.json");
        final HttpResponse<String> response = nakshaClient.post("hub/spaces/" + PSQL_SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
        assertThat(response).hasStatus(200);
        final var body = response.body();
        final var features = parseJson(body, SpFeatureCollection.class).getFeatures();
        for (var feature : features) {
          assertNotNull(feature, "Result contains null feature");
          final String id = feature.getId();
          assertNotNull(id, "Result contains feature without 'id' property");
          if ("my-custom-id-01".equals(id)) {
            final var f = javaProxy(feature, NakshaFeature.class);
            assertNotNull(f, "Failed to cast feature to NakshaFeature");
            final String uuid1 = f.getProperties().getXyz().getUuid();
            assertNotNull(uuid1, "Feature does not contain 'properties->@ns:com:here:xyz->uuid' property'");
          }
        }
    }

    @Test
    void tc01_testPatchForFeatureOnlyInBase() throws Exception {
        // This test is to validate that for a Patch request against a Feature in Base layer,
        // we are successfully able to read Base layer using Http Storage (and not Psql Storage)

        // Test API : POST /hub/spaces/{spaceId}/features
        // Given: input patch feature request and final expected response body
        final String streamId = UUID.randomUUID().toString();
        final String patchRequestJson = loadFileOrFail("PatchOnViewWithHttpStorage/TC01_patchFeatureOnlyInBase/patch_request.json");
        final String expectedBodyPart = loadFileOrFail("PatchOnViewWithHttpStorage/TC01_patchFeatureOnlyInBase/response_body_part.json");

        // Given: Mock Http Response from Base space
        final String baseMockResponse = loadFileOrFail("PatchOnViewWithHttpStorage/TC01_patchFeatureOnlyInBase/base_mock_response.json");
        final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);
        stubFor(get(endpointPath)
                .withQueryParam("id" , equalTo("my-custom-id-04"))
                .willReturn(okJson(baseMockResponse)));

        // When: Patch request is submitted on a View space to NakshaHub
        final HttpResponse<String> response = nakshaClient
                .post("hub/spaces/" + VIEW_SPACE_ID + "/features", patchRequestJson, streamId);

        // Then: Validate that the Base feature gets patched successfully
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Patch Feature response body doesn't match");

        verify(1, getRequestedFor(endpointPath));
    }

    @Test
    void tc02_testPatchForFeatureOnlyInDelta() throws Exception {
        // This test is to validate that for a Patch request against a Feature in Delta layer,
        // no-record-found situation from Base layer (using Http Storage) doesn't cause entire request to fail.

        // Test API : PATCH /hub/spaces/{spaceId}/features/{featureId}
        // Given: input patch feature request and final expected response body
        final String streamId = UUID.randomUUID().toString();
        final String featureId = "my-custom-id-01";
        final String patchRequestJson = loadFileOrFail("PatchOnViewWithHttpStorage/TC02_patchFeatureOnlyInDelta/patch_request.json");
        final String expectedBodyPart = loadFileOrFail("PatchOnViewWithHttpStorage/TC02_patchFeatureOnlyInDelta/response_body_part.json");

        // Given: Mock Http Response from Base space (no record found)
        final String baseMockResponse = loadFileOrFail("PatchOnViewWithHttpStorage/TC02_patchFeatureOnlyInDelta/base_mock_response.json");
        final UrlPattern endpointPath = urlPathEqualTo(ENDPOINT);
        stubFor(get(endpointPath)
                .withQueryParam("id" , equalTo(featureId))
                .willReturn(okJson(baseMockResponse)));

        // When: Patch request is submitted on a View space to NakshaHub
        final HttpResponse<String> response = nakshaClient
                .patch("hub/spaces/" + VIEW_SPACE_ID + "/features/"+featureId, patchRequestJson, streamId);

        // Then: Validate that the Delta feature gets patched successfully
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Patch Feature response body doesn't match");
    }

}
