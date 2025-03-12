package com.here.naksha.app.service;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.assertions.ResponseAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;
import java.net.http.HttpResponse;

import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;

class CORSPreflightTest extends ApiTest{
    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
    private static final String ORIGIN_HEADER = "https://testurl.com/";
    private static final String SPACE_ID = "read_features_by_ids_test_space";

    @BeforeAll
    static void setup() throws URISyntaxException, IOException, InterruptedException {
        setupSpaceAndRelatedResources(nakshaClient, "ReadFeatures/ByIds/setup");
        String initialFeaturesJson = loadFileOrFail("ReadFeatures/ByIds/setup/create_features.json");
        nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", initialFeaturesJson, UUID.randomUUID().toString());
    }

    @Test
    void testCorsPreflightRequestGet() throws Exception {
        // Test API : OPTIONS /hub/storages for GET method
        // Validate the CORS headers returned by the OPTIONS request for GET
        String requestMethod = "GET";

        // When: OPTIONS request is sent to NakshaHub Space Storage instance
        HttpResponse<String> response = getNakshaClient().options("hub/storages", ORIGIN_HEADER, requestMethod);

        // Then: Perform assertions
        ResponseAssertions.assertThat(response)
                .hasStatus(204)
                .hasHeader("access-control-allow-credentials", "true")
                .hasHeader("access-control-allow-origin", ORIGIN_HEADER)
                .hasHeader("access-control-allow-methods", "OPTIONS,GET,POST,PUT,DELETE,PATCH,HEAD")
                .hasHeader("access-control-allow-headers", "*")
                .hasHeader("access-control-max-age", "86400");
    }

    @Test
    void testCorsPreflightRequestPost() throws Exception {
        // Test API : OPTIONS /hub/storages for POST method
        // Validate the CORS headers returned by the OPTIONS request for POST
        String requestMethod = "POST";

        // When: OPTIONS request is sent to NakshaHub Space Storage instance
        HttpResponse<String> response = getNakshaClient().options("hub/storages", ORIGIN_HEADER, requestMethod);

        // Then: Perform assertions
        ResponseAssertions.assertThat(response)
                .hasStatus(204)
                .hasHeader("access-control-allow-credentials", "true")
                .hasHeader("access-control-allow-origin", ORIGIN_HEADER)
                .hasHeader("access-control-allow-methods", "OPTIONS,GET,POST,PUT,DELETE,PATCH,HEAD")
                .hasHeader("access-control-allow-headers", "*")
                .hasHeader("access-control-max-age", "86400");
    }

    @Test
    void testCorsPreflightRequestPut() throws Exception {
        // Test API : OPTIONS /hub/storages for PUT method
        // Validate the CORS headers returned by the OPTIONS request for PUT
        String requestMethod = "PUT";

        // When: OPTIONS request is sent to NakshaHub Space Storage instance
        HttpResponse<String> response = getNakshaClient().options("hub/storages", ORIGIN_HEADER, requestMethod);

        // Then: Perform assertions
        ResponseAssertions.assertThat(response)
                .hasStatus(204)
                .hasHeader("access-control-allow-credentials", "true")
                .hasHeader("access-control-allow-origin", ORIGIN_HEADER)
                .hasHeader("access-control-allow-methods", "OPTIONS,GET,POST,PUT,DELETE,PATCH,HEAD")
                .hasHeader("access-control-allow-headers", "*")
                .hasHeader("access-control-max-age", "86400");
    }

    @Test
    void testCorsPreflightRequestPatch() throws Exception {
        // Test API : OPTIONS /hub/storages for PATCH method
        // Validate the CORS headers returned by the OPTIONS request for PATCH
        String requestMethod = "PATCH";

        // When: OPTIONS request is sent to NakshaHub Space Storage instance
        HttpResponse<String> response = getNakshaClient().options("hub/storages", ORIGIN_HEADER, requestMethod);

        // Then: Perform assertions
        ResponseAssertions.assertThat(response)
                .hasStatus(204)
                .hasHeader("access-control-allow-credentials", "true")
                .hasHeader("access-control-allow-origin", ORIGIN_HEADER)
                .hasHeader("access-control-allow-methods", "OPTIONS,GET,POST,PUT,DELETE,PATCH,HEAD")
                .hasHeader("access-control-allow-headers", "*")
                .hasHeader("access-control-max-age", "86400");
    }

    @Test
    void testCorsPreflightRequestDelete() throws Exception {
        // Test API : OPTIONS /hub/storages for DELETE method
        // Validate the CORS headers returned by the OPTIONS request for DELETE
        String requestMethod = "DELETE";

        // When: OPTIONS request is sent to NakshaHub Space Storage instance
        HttpResponse<String> response = getNakshaClient().options("hub/storages", ORIGIN_HEADER, requestMethod);

        // Then: Perform assertions
        ResponseAssertions.assertThat(response)
                .hasStatus(204)
                .hasHeader("access-control-allow-credentials", "true")
                .hasHeader("access-control-allow-origin", ORIGIN_HEADER)
                .hasHeader("access-control-allow-methods", "OPTIONS,GET,POST,PUT,DELETE,PATCH,HEAD")
                .hasHeader("access-control-allow-headers", "*")
                .hasHeader("access-control-max-age", "86400");
    }
}
