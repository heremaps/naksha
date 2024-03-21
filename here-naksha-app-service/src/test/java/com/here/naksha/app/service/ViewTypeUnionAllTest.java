package com.here.naksha.app.service;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static com.here.naksha.app.common.CommonApiTestSetup.createHandler;
import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

@WireMockTest(httpPort = 9090)
public class ViewTypeUnionAllTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

    private static final String HTTP_SPACE_ID = "base_union_all_test_http_storage";
    private static final String PSQL_SPACE_ID = "delta_union_all_test_psql_storage";

    @BeforeAll
    static void setup() throws URISyntaxException, IOException, InterruptedException {
        // Set up Http Storage based Space
        setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/http_storage_space");
        // Set up (standard) Psql Storage based Space
        setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/psql_storage_space");
        // Set up View Space over Psql and Http Storage based spaces
//        createHandler(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/view_space/create_sourceId_handler.json");
//        setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByBBoxHttpStorage/setup/view_space");
        // Load some test data in PsqlStorage based Space
        final String initialFeaturesJson = loadFileOrFail("ReadFeatures/ByBBoxHttpStorage/setup/psql_storage_space/create_features.json");
        final HttpResponse<String> response = nakshaClient.post("hub/spaces/" + PSQL_SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
        assertThat(response).hasStatus(200);
    }
}
