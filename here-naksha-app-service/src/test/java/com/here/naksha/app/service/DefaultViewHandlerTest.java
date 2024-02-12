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

        // Given:
        final String bodyJson = loadFileOrFail("ViewHandler/TC5001_createFeature/create_feature.json");
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5001_createFeature/feature_response_part.json");
        final String idsQueryParam = "id=my-custom-id-5001-1";
        String viewStreamId = UUID.randomUUID().toString();
        String streamId = UUID.randomUUID().toString();

        // When
        HttpResponse<String> viewResponse = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, viewStreamId);
        HttpResponse<String> spaceSearchResponse = getNakshaClient().get("hub/spaces/" + DELTA_CONFIGURED_SPACE + "/features?" + idsQueryParam, streamId);

        // Then
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
    void tc5002_searchById_GetFromDeltaWhenAvailableAtDeltaAndDlb() throws Exception {
        //given
        final String idsQueryParam = "id=my-custom-id-5002-1";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5002_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }

    @Test
    void tc5003_searchById_GetFromDeltaWhenAvailableAtDeltaAndBase() throws Exception {
        //given
        final String idsQueryParam = "id=my-custom-id-5003-1";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5003_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }
    @Test
    void tc5004_searchById_GetFromDlbWhenAvailableAtDlbAndBase() throws Exception {
        //given
        final String idsQueryParam = "id=my-custom-id-5004-1";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5004_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }

    @Test
    void tc5005_searchById_GetFromBaseWhenAvailableOnlyAtBase() throws Exception {
        //given
        final String idsQueryParam = "id=my-custom-id-5005-1";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5005_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }

    @Test
    void tc5006_searchById_ResultFromMultipleSpaces() throws Exception {
        //given
        final String idsQueryParam = "id=my-custom-id-5002-1&id=my-custom-id-5004-1&id=my-custom-id-5005-1";
        final String streamId = UUID.randomUUID().toString();
        final String expectedBodyPart = loadFileOrFail("ViewHandler/TC5006_searchById/feature_response_part.json");

        //when
        HttpResponse<String> viewResponse = getNakshaClient().get("hub/spaces/" + SPACE_ID + "/features?" + idsQueryParam, streamId);

        //then
        assertThat(viewResponse)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }
}
