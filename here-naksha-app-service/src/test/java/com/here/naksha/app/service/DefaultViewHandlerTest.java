package com.here.naksha.app.service;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static com.here.naksha.app.common.CommonApiTestSetup.*;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

public class DefaultViewHandlerTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
    private static final String SPACE_ID = "mod-dev:topology-view";
    private static final String DELTA_CONFIGURED_SPACE = "mod-dev:topology-delta";
    private static final String DLB_CONFIGURED_SPACE = "mod-dev:topology-dlb";
    private static final String BASE_CONFIGURED_SPACE = "mod-dev:topology-base";

    @BeforeAll
    static void setup() throws URISyntaxException, IOException, InterruptedException {
        //create storages
        createStorage(nakshaClient, "ViewHandler/setup/create_storage_sfw.json");
        createStorage(nakshaClient, "ViewHandler/setup/create_storage_mod_dlb.json");
        createStorage(nakshaClient, "ViewHandler/setup/create_storage_mod_delta.json");
        createStorage(nakshaClient, "ViewHandler/setup/create_storage_mod_view_dev.json");

        //create handlers
        createHandler(nakshaClient, "ViewHandler/setup/create_handler_sfw.json");
        createHandler(nakshaClient, "ViewHandler/setup/create_handler_mod_dlb.json");
        createHandler(nakshaClient, "ViewHandler/setup/create_handler_mod_delta.json");
        createHandler(nakshaClient, "ViewHandler/setup/create_handler_view_handler.json");

        //create spaces
        createSpace(nakshaClient, "ViewHandler/setup/create_space_sfw.json");
        createSpace(nakshaClient, "ViewHandler/setup/create_space_mod_dlb.json");
        createSpace(nakshaClient, "ViewHandler/setup/create_space_mod_delta.json");
        createSpace(nakshaClient, "ViewHandler/setup/create_view_space.json");

        //setup data
        final String deltaBodyJson = loadFileOrFail("ViewHandler/setup/create_features_delta.json");
        nakshaClient.post("hub/spaces/" + DELTA_CONFIGURED_SPACE + "/features", deltaBodyJson, UUID.randomUUID().toString());
        final String dlbBodyJson = loadFileOrFail("ViewHandler/setup/create_features_dlb.json");
        nakshaClient.post("hub/spaces/" + DLB_CONFIGURED_SPACE + "/features", dlbBodyJson, UUID.randomUUID().toString());
        final String baseBodyJson = loadFileOrFail("ViewHandler/setup/create_features_base.json");
        nakshaClient.post("hub/spaces/" + BASE_CONFIGURED_SPACE + "/features", baseBodyJson, UUID.randomUUID().toString());
    }

    @Test
    void tc5001_createFeatureByViewHandler() throws Exception {

        // Given: Load data to be sent to view handler
        final String bodyJson = loadFileOrFail("ViewHandler/TC5001_createFeature/create_feature.json");
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5001_createFeature/feature_response_part.json");
        final String idsQueryParam = "id=FeatId-create-5001";
        String viewStreamId = UUID.randomUUID().toString();
        String streamId = UUID.randomUUID().toString();

        // When: Sent create feature request to view space.
        HttpResponse<String> viewResponse = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, viewStreamId);
        HttpResponse<String> spaceSearchResponse = getNakshaClient().get("hub/spaces/" + DELTA_CONFIGURED_SPACE + "/features?" + idsQueryParam, streamId);

        // Then: Assert that feature was correctly create by view space and was located in correct space(top one -> delta)
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(viewStreamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
                .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
                .hasInsertedIdsMatchingFeatureIds(null)
                .hasUuids();

        assertThat(spaceSearchResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }
    @Test
    void tc5002_upsertFeatureByViewHandler_featureAvailableInAllLayers() throws Exception {
        // Given: Load data to be sent to view handler. Object with this id is available in all spaces.
        final String bodyJson = loadFileOrFail("ViewHandler/TC5002_upsertFeature/create_feature.json");
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5002_upsertFeature/feature_response_part.json");
        final String idsQueryParam = "id=FeatId-create-5002";
        String viewStreamId = UUID.randomUUID().toString();
        String streamId = UUID.randomUUID().toString();

        // When: Sent create feature request to view space.
        HttpResponse<String> viewResponse = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, viewStreamId);
        HttpResponse<String> spaceSearchResponse = getNakshaClient().get("hub/spaces/" + DELTA_CONFIGURED_SPACE + "/features?" + idsQueryParam, streamId);

        // Then: Assert that feature was correctly updated by view space and was located in correct space(top one -> delta)
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(viewStreamId)
                .hasJsonBody(expectedBodyPart, "Upsert Feature response body doesn't match")
                .hasUuids();

        assertThat(spaceSearchResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Upsert Feature response body doesn't match");

    }
    @Test
    void tc5003_upsertFeatureByViewHandler_featureAvailableInDlbAndBase() throws Exception {
        // Given: Load data to be sent to view handler. Object with this id is available in DLB and BASE.
        final String bodyJson = loadFileOrFail("ViewHandler/TC5003_upsertFeature/create_feature.json");
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5003_upsertFeature/feature_response_part.json");
        final String idsQueryParam = "id=FeatId-create-5003";
        String viewStreamId = UUID.randomUUID().toString();
        String streamId = UUID.randomUUID().toString();

        // When: Sent create feature request to view space.
        HttpResponse<String> viewResponse = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, viewStreamId);
        HttpResponse<String> spaceSearchResponse = getNakshaClient().get("hub/spaces/" + DELTA_CONFIGURED_SPACE + "/features?" + idsQueryParam, streamId);

        // Then: Assert that feature was correctly created by view space and was located in correct space(top one -> delta)
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(viewStreamId)
                .hasJsonBody(expectedBodyPart, "Upsert Feature response body doesn't match")
                .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
                .hasInsertedIdsMatchingFeatureIds(null)
                .hasUuids();

        assertThat(spaceSearchResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Upsert Feature response body doesn't match");
    }

    @Test
    void tc5004_upsertFeatureByViewHandler_featureAvailableOnlyInBase() throws Exception{
        // Given: Load data to be sent to view handler. Object with this id is available in DLB and BASE.
        final String bodyJson = loadFileOrFail("ViewHandler/TC5004_upsertFeature/create_feature.json");
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5004_upsertFeature/feature_response_part.json");
        final String idsQueryParam = "id=FeatId-create-5004";
        String viewStreamId = UUID.randomUUID().toString();
        String streamId = UUID.randomUUID().toString();

        // When: Sent create feature request to view space.
        HttpResponse<String> viewResponse = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, viewStreamId);
        HttpResponse<String> spaceSearchResponse = getNakshaClient().get("hub/spaces/" + DELTA_CONFIGURED_SPACE + "/features?" + idsQueryParam, streamId);

        // Then: Assert that feature was correctly created by view space and was located in correct space(top one -> delta)
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(viewStreamId)
                .hasJsonBody(expectedBodyPart, "Upsert Feature response body doesn't match")
                .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
                .hasInsertedIdsMatchingFeatureIds(null)
                .hasUuids();

        assertThat(spaceSearchResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Upsert Feature response body doesn't match");
    }


    @Test
    void tc5010_searchById_GetFromDeltaWhenAvailableAtAllSpaces() throws Exception {
        //given Feature with this id is available in all spaces (delta,dlb,base)
        final String idsQueryParam = "id=FeatId-getById-5010";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5010_searchById/feature_response_part.json");

        //when : perform search by id operation
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then: expect that data from delta space will be retrieved
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }


    @Test
    void tc5011_searchById_GetFromDeltaWhenAvailableAtDeltaAndDlb() throws Exception {
        //given Feature with this id is available in delta and dlb spaces
        final String idsQueryParam = "id=FeatId-getById-5011";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5011_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then: expect that data from delta space will be retrieved
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }


    @Test
    void tc5012_searchById_GetFromDeltaWhenAvailableAtDeltaAndBase() throws Exception {
        //given Feature with this id is available in delta and base spaces
        final String idsQueryParam = "id=FeatId-getById-5012";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5012_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then: expect that data from delta space will be retrieved
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }


    @Test
    void tc5013_searchById_GetFromDlbWhenAvailableAtDlbAndBase() throws Exception {
        //given Feature with this id is available in dlb and base spaces
        final String idsQueryParam = "id=FeatId-getById-5013";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5013_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then: expect that data from dlb space will be retrieved
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }

    @Test
    void tc5014_searchById_GetFromBaseWhenAvailableOnlyAtBase() throws Exception {
        //given Feature with this id is available in base spaces
        final String idsQueryParam = "id=FeatId-getById-5014";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5014_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then: expect that data from base space will be retrieved
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }

    @Test
    void tc5015_searchById_ResultFromMultipleSpaces() throws Exception {
        //given get id from multiple spaces
        final String idsQueryParam = "id=FeatId-getById-5011&id=FeatId-getById-5013&id=FeatId-getById-5014";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5015_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then expect that from each space result will be returned
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }
}
