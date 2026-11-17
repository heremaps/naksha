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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.lib.view.ViewSuccessResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import naksha.base.Id;
import naksha.base.Guid;
import naksha.base.TupleNumber;
import naksha.geo.SpFeatureCollection;
import naksha.model.objects.NakshaFeature;
import org.json.JSONArray;
import org.json.JSONObject;
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
    static void setup() throws Exception {
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
        JSONObject observer = new JSONObject().put("id", "patch_snapshot_race_handler").put("type", "EventHandler")
            .put("title", "PATCH snapshot race observer").put("description", "Test-only deterministic read/write interleaving")
            .put("className", PatchSnapshotRaceHandler.class.getName()).put("active", true).put("properties", new JSONObject());
        assertThat(nakshaClient.post("hub/handlers", observer.toString(), UUID.randomUUID().toString())).hasStatus(200);
        JSONObject viewSpace = new JSONObject(loadFileOrFail(TEST_DIR_SETUP_PATH + "view_space/create_space.json"));
        JSONArray originalHandlers = viewSpace.getJSONArray("eventHandlerIds");
        JSONArray handlers = new JSONArray().put("patch_snapshot_race_handler");
        for (int i = 0; i < originalHandlers.length(); i++) handlers.put(originalHandlers.getString(i));
        viewSpace.put("eventHandlerIds", handlers);
        assertThat(nakshaClient.put("hub/spaces/" + VIEW_SPACE_ID, viewSpace.toString(), UUID.randomUUID().toString())).hasStatus(200);
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

    @Test
    void tc03_competingDeltaCreationRetriesAndRollsBackEarlierBatchWrites() throws Exception {
        // Force the conflict onto the later CREATE in the storage's identity order.
        String first = "cow_batch_a";
        String racing = "cow_batch_b";
        if (new Id(first).compareTo(new Id(racing)) > 0) {
            String swap = first;
            first = racing;
            racing = swap;
        }
        final String firstId = first;
        final String racingId = racing;
        String base = new JSONObject().put("type", "FeatureCollection").put("features", new JSONArray()
            .put(featureJson(firstId, new JSONObject().put("baseOnly", "first")))
            .put(featureJson(racingId, new JSONObject().put("baseOnly", "stale-base")))).toString();
        stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(okJson(base)));

        AtomicInteger snapshots = new AtomicInteger();
        PatchSnapshotRaceHandler.AFTER_READ.set(snapshot -> {
            try {
                ViewSuccessResult result = org.junit.jupiter.api.Assertions.assertInstanceOf(ViewSuccessResult.class, snapshot);
                int number = snapshots.incrementAndGet();
                if (number == 1) {
                    assertEquals(Set.of(firstId, racingId), result.getInheritedFeatureIds(), "The first read must see base-only features");
                    // This independent HTTP transaction commits after the captured read, before the PATCH can CREATE its delta.
                    String winner = new JSONObject().put("type", "FeatureCollection").put("features", new JSONArray()
                        .put(featureJson(racingId, new JSONObject().put("winnerOnly", "preserved")))).toString();
                    assertThat(nakshaClient.post("hub/spaces/" + PSQL_SPACE_ID + "/features", winner,
                        UUID.randomUUID().toString())).hasStatus(200);
                } else if (number == 2) {
                    assertEquals(Set.of(firstId), result.getInheritedFeatureIds(),
                        "The failed attempt's earlier CREATE must be rolled back before the retry read");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            String patch = new JSONObject().put("type", "FeatureCollection").put("features", new JSONArray()
                .put(featureJson(firstId, new JSONObject().put("patched", true)))
                .put(featureJson(racingId, new JSONObject().put("patched", true)))).toString();
            HttpResponse<String> response = nakshaClient.post("hub/spaces/" + VIEW_SPACE_ID + "/features", patch,
                UUID.randomUUID().toString());
            assertThat(response).hasStatus(200).hasFeatureCount(2);
            assertEquals(2, snapshots.get(), "Exactly one collision and one reread are expected");
            JSONArray features = new JSONObject(response.body()).getJSONArray("features");
            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                assertTrue(feature.getJSONObject("properties").getBoolean("patched"));
                if (racingId.equals(feature.getString("id"))) {
                    assertEquals("preserved", feature.getJSONObject("properties").getString("winnerOnly"));
                    assertTrue(!feature.getJSONObject("properties").has("baseOnly"), "Stale base fields must not overwrite the winning delta");
                }
            }
        } finally {
            PatchSnapshotRaceHandler.AFTER_READ.set(null);
        }
    }

    @Test
    void tc04_versionedBaseRequiresMatchingExplicitUuidBeforeCopyOnWrite() throws Exception {
        String id = "cow_versioned_base";
        String sourceUuid = new Guid(id, new TupleNumber(1L, 2, 3, 4L, 100L)).toString();
        String staleUuid = new Guid(id, new TupleNumber(1L, 2, 3, 4L, 104L)).toString();
        JSONObject sourceProperties = new JSONObject().put("baseOnly", true)
            .put("@ns:com:here:xyz", new JSONObject().put("uuid", sourceUuid));
        stubFor(get(urlPathEqualTo(ENDPOINT)).willReturn(okJson(new JSONObject().put("type", "FeatureCollection")
            .put("features", new JSONArray().put(featureJson(id, sourceProperties))).toString())));
        JSONObject patchProperties = new JSONObject().put("patched", true)
            .put("@ns:com:here:xyz", new JSONObject().put("uuid", staleUuid));
        JSONObject patch = new JSONObject().put("type", "FeatureCollection")
            .put("features", new JSONArray().put(featureJson(id, patchProperties)));
        assertThat(nakshaClient.post("hub/spaces/" + VIEW_SPACE_ID + "/features", patch.toString(),
            UUID.randomUUID().toString())).hasStatus(409);
        assertThat(nakshaClient.get("hub/spaces/" + PSQL_SPACE_ID + "/features?id=" + id,
            UUID.randomUUID().toString())).hasStatus(200).hasFeatureCount(0);

        patchProperties.getJSONObject("@ns:com:here:xyz").put("uuid", sourceUuid);
        assertThat(nakshaClient.post("hub/spaces/" + VIEW_SPACE_ID + "/features", patch.toString(),
            UUID.randomUUID().toString())).hasStatus(200).hasFeatureCount(1);
        assertThat(nakshaClient.get("hub/spaces/" + PSQL_SPACE_ID + "/features?id=" + id,
            UUID.randomUUID().toString())).hasStatus(200).hasFeatureCount(1);
    }

    private static JSONObject featureJson(String id, JSONObject properties) throws org.json.JSONException {
        return new JSONObject().put("id", id).put("type", "Feature").put("properties", properties);
    }

}
