package com.here.naksha.app.service;

import static com.here.naksha.app.common.CommonApiTestSetup.setupHandlerAndSpace;
import static com.here.naksha.app.common.TestUtil.loadFileOrFail;
import static com.here.naksha.app.common.TestUtil.parseJson;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;
import static naksha.base.Platform.javaProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import java.net.http.HttpResponse;
import java.util.UUID;

import naksha.base.Guid;
import naksha.base.TupleNumber;
import naksha.geo.SpFeatureCollection;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatchFeatureTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();

    private static final String SPACE_ID = "patch_features_test_space";

    public PatchFeatureTest() {
        super(nakshaClient);
    }

    @BeforeAll
    static void setup() {
        setupHandlerAndSpace(nakshaClient, "PatchFeatures/setup");
    }

    @Test
    void testPatchOneFeatureById() throws Exception {
        // Test API : PATCH /hub/spaces/{spaceId}/features/{featureId}
        // Given: initial features
        final String streamId = UUID.randomUUID().toString();
        final String createFeaturesJson = loadFileOrFail("PatchFeatures/testPatchOneFeatureById/create_features.json");
        HttpResponse<String> response = nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", createFeaturesJson, streamId);
        assertEquals(200, response.statusCode(), "ResCode mismatch, failure creating initial features");

        // When: request is submitted to NakshaHub Space Storage instance
        final String bodyJson = loadFileOrFail("PatchFeatures/testPatchOneFeatureById/patch_request.json");
        response = nakshaClient
                .patch(
                        "hub/spaces/" + SPACE_ID + "/features/feature-1-to-patch",
                        bodyJson,
                        streamId);

        // Then: Perform assertions
        final String expectedBodyPart = loadFileOrFail("PatchFeatures/testPatchOneFeatureById/response.json");
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Patch Feature response body doesn't match");
    }

    @Test
    void testPatchOneFeatureByIdNotExisting() throws Exception {
        // Test API : PATCH /hub/spaces/{spaceId}/features/{featureId}
        // Given: initial features
        final String streamId = UUID.randomUUID().toString();

        // When: request is submitted to NakshaHub Space Storage instance
        final String bodyJson = loadFileOrFail("PatchFeatures/testPatchOneFeatureByIdNotExisting/patch_request.json");
        HttpResponse<String> response = nakshaClient
                .patch(
                        "hub/spaces/" + SPACE_ID + "/features/feature-2-to-patch",
                        bodyJson,
                        streamId);

        // Then: Perform assertions
        final String expectedBodyPart = loadFileOrFail("PatchFeatures/testPatchOneFeatureByIdNotExisting/response.json");
        assertThat(response)
                .hasStatus(404)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Patch Feature error response body doesn't match");
    }

    @Test
    void testPatchOneFeatureByIdWrongUuid() throws Exception {
        // Test API : PATCH /hub/spaces/{spaceId}/features/{featureId}
        // Given: initial features
        final String streamId = UUID.randomUUID().toString();
        final String createFeaturesJson = loadFileOrFail("PatchFeatures/testPatchOneFeatureByIdWrongUuid/create_features.json");
        HttpResponse<String> response = nakshaClient.post("hub/spaces/" + SPACE_ID + "/features", createFeaturesJson, streamId);
        assertEquals(200, response.statusCode(), "ResCode mismatch, failure creating initial features");

        String uuid = null;
        final var body = response.body();
        final var features = parseJson(body, SpFeatureCollection.class).getFeatures();
        for (var feature : features) {
            assertNotNull(feature, "Result contains null feature");
            final String id = feature.getId();
            assertNotNull(id, "Result contains feature without 'id' property");
            if ("feature-3-to-patch".equals(id)) {
                final var f = javaProxy(feature, NakshaFeature.class);
                assertNotNull(f, "Failed to cast feature to NakshaFeature");
                uuid = f.getProperties().getXyz().getUuid();
                assertNotNull(uuid, "Feature does not contain 'properties->@ns:com:here:xyz->uuid' property'");
            }
        }

        TupleNumber tupleNumber = TupleNumber.fromStringOrGuid(uuid);

        var newWrongUuid = new Guid("feature-3-to-patch",
                new TupleNumber(
                        tupleNumber.databaseNumber,
                        tupleNumber.catalogNumber,
                        tupleNumber.collectionNumber,
                        tupleNumber.featureNumber,
                        tupleNumber.version - 4L //older, invalid version, to simulate wrong uuid
                )).toString();
        // NOTE: if the wrong uuid is different in databaseNumber, or catalogNumber, or collectionNumber, the response will be 400 (mapped from NakshaError.ILLEGAL_ARGUMENT, thrown by storage) instead of 409.
        // But this requires manual modification of the uuid in the input JSON, which is not an expected behavior from the client, so we don't handle that currently.

        // When: request is submitted to NakshaHub Space Storage instance
        final String bodyJson = loadFileOrFail("PatchFeatures/testPatchOneFeatureByIdWrongUuid/patch_request.json")
                .replace("${uuid}", newWrongUuid);
        response = nakshaClient
                .patch(
                        "hub/spaces/" + SPACE_ID + "/features/feature-3-to-patch",
                        bodyJson,
                        streamId);

        // Then: Perform assertions
        final String expectedBodyPart = loadFileOrFail("PatchFeatures/testPatchOneFeatureByIdWrongUuid/response.json");
        assertThat(response)
                .hasStatus(409)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Patch Feature error response body doesn't match");
    }
}
