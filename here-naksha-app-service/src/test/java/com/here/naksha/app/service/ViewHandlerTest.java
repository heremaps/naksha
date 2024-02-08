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

public class ViewHandlerTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient(150);
    private static final String SPACE_ID = "mod-dev:topology-view";

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

    }

    @Test
    void tc2004_searchWithoutSourceId() throws Exception {
        //given
        final String bboxQueryParam = "west=-180&south=-90&east=180&north=90";
        final String propQueryParam = "p.speedLimit='60'";


        String streamId = UUID.randomUUID().toString();

        // When
        HttpResponse<String> response = nakshaClient
                .get("hub/spaces/" + SPACE_ID + "/bbox?" + bboxQueryParam + "&" + propQueryParam , streamId);

        // Then
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody("", "Get Feature response body doesn't match");
    }


}
