package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.*;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static com.here.naksha.app.common.TestUtil.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        // When: Request is submitted to NakshaHub Space Storage instance
        final HttpResponse<String> response =
                nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);

        // Then: Perform standard assertions
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Validation dry-run response body doesn't match");

        // Verify old namespaces are absent in the raw HTTP payload.
        // Not doing object conversion because it adds the old namespaces back for backward compatibility, but we want to ensure that the raw payload doesn't contain them.
        final JsonNode rawResponse = new ObjectMapper().readTree(response.body());
        for (JsonNode featureNode : rawResponse.path("features")) {
            final JsonNode propertiesNode = featureNode.path("properties");
            assertFalse(propertiesNode.has(XyzProperties.HERE_DELTA_NS), "Old delta namespace should be absent in raw feature payload");
            assertFalse(propertiesNode.has(XyzProperties.HERE_META_NS), "Old meta namespace should be absent in raw feature payload");
        }
        for (JsonNode violationNode : rawResponse.path("violations")) {
            final JsonNode propertiesNode = violationNode.path("properties");
            assertFalse(propertiesNode.has(XyzProperties.HERE_DELTA_NS), "Old delta namespace should be absent in raw violation payload");
            assertFalse(propertiesNode.has(XyzProperties.HERE_META_NS), "Old meta namespace should be absent in raw violation payload");
        }
    }
}