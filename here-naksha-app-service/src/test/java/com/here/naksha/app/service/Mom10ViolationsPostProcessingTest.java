package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.*;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static com.here.naksha.app.common.TestUtil.*;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class Mom10ViolationsPostProcessingTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

    private static final String SPACE_ID = "local-space-4-mom10-val-dry-run";

    @BeforeAll
    static void setup() throws URISyntaxException, IOException, InterruptedException {
        createHandler(nakshaClient, "Mom10ValDryRun/setup/create_context_loader_handler.json");
        createHandler(nakshaClient, "Mom10ValDryRun/setup/create_validation_handler.json");
        createHandler(nakshaClient, "Mom10ValDryRun/setup/create_endorsement_handler.json");
        createHandler(nakshaClient, "Mom10ValDryRun/setup/create_echo_handler.json");
        createSpace(nakshaClient, "Mom10ValDryRun/setup/create_space.json");
    }

    @Test
    void testValDryRunMom10ReturningViolations() throws Exception {
        // Test API : POST /hub/spaces/{spaceId}/features
        // Validate features returned with mock violations
        final String streamId = UUID.randomUUID().toString();

        // Given: PUT features request
        final String bodyJson = loadFileOrFail("Mom10ValDryRun/testViolationsPostProcessingResult/upsert_features.json");
        final String expectedBodyPart = loadFileOrFail("Mom10ValDryRun/testViolationsPostProcessingResult/feature_response_part.json");

        final HttpResponse<String> response =
                nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);

        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Validation dry-run response body doesn't match")
                .hasBodyWithout(
                        new String[] {"properties", XyzProperties.HERE_DELTA_NS},
                        new String[] {"properties", XyzProperties.HERE_META_NS})
                .hasViolationsWithout(
                        new String[] {"properties", XyzProperties.HERE_DELTA_NS},
                        new String[] {"properties", XyzProperties.HERE_META_NS});
    }
}