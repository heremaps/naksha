package com.here.naksha.app.service;

import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.lib.core.models.naksha.Space;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.net.http.HttpResponse;
import java.util.UUID;

import static com.here.naksha.app.common.TestUtil.*;
import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UpdateFeatureTestHelper {

    final @NotNull NakshaApp app;
    final @NotNull NakshaTestWebClient nakshaClient;

    public UpdateFeatureTestHelper(final @NotNull NakshaApp app, final @NotNull NakshaTestWebClient nakshaClient) {
        this.app = app;
        this.nakshaClient = nakshaClient;
    }

    void tc0500_testUpdateFeatures() throws Exception {
        // Test API : PUT /hub/spaces/{spaceId}/features
        // Read request body
        final String bodyJson = loadFileOrFail("TC0500_updateFeatures/update_request.json");
        // TODO: include geometry after Cursor-related changes ->
        final Space space = parseJsonFileOrFail("TC0300_createFeaturesWithNewIds/create_space.json", Space.class);
        final String expectedBodyPart = loadFileOrFail("TC0500_updateFeatures/response_no_geometry.json");
        final String streamId = UUID.randomUUID().toString();

        // When: Create Features request is submitted to NakshaHub Space Storage instance
        final HttpResponse<String> response =
                nakshaClient.put("hub/spaces/" + space.getId() + "/features", bodyJson, streamId);

        // Then: Perform assertions
        assertEquals(200, response.statusCode(), "ResCode mismatch");
        JSONAssert.assertEquals(
                "Update Feature response body doesn't match",
                expectedBodyPart,
                response.body(),
                JSONCompareMode.LENIENT);
        assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
    }

    void tc0501_testUpdateFeatureById() throws Exception {
        // Test API : PUT /hub/spaces/{spaceId}/features/{featureId}

        // Read request body
        final String bodyJson = loadFileOrFail("TC0501_updateOneFeatureById/update_request_and_response.json");
        // TODO: include geometry after Cursor-related changes ->
        final Space space = parseJsonFileOrFail("TC0300_createFeaturesWithNewIds/create_space.json", Space.class);
        final String expectedBodyPart = bodyJson;
        final String streamId = UUID.randomUUID().toString();

        // When: Create Features request is submitted to NakshaHub Space Storage instance
        final HttpResponse<String> response =
                nakshaClient.put("hub/spaces/" + space.getId() + "/features/my-custom-id-301-1", bodyJson, streamId);

        // Then: Perform assertions
        assertEquals(200, response.statusCode(), "ResCode mismatch");
        JSONAssert.assertEquals(
                "Update Feature response body doesn't match",
                expectedBodyPart,
                response.body(),
                JSONCompareMode.LENIENT);
        assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
    }

    void tc0502_testUpdateFeatureByWrongUriId() throws Exception {
        // Test API : PUT /hub/spaces/{spaceId}/features/{featureId}

        // Read request body
        final String bodyJson = loadFileOrFail("TC0502_updateFeatureWithWrongUriId/request.json");
        // TODO: include geometry after Cursor-related changes ->
        final Space space = parseJsonFileOrFail("TC0300_createFeaturesWithNewIds/create_space.json", Space.class);
        final String expectedBodyPart = loadFileOrFail("TC0502_updateFeatureWithWrongUriId/response.json");
        final String streamId = UUID.randomUUID().toString();

        // When: Create Features request is submitted to NakshaHub Space Storage instance
        final HttpResponse<String> response =
                nakshaClient.put("hub/spaces/" + space.getId() + "/features/wrong-id", bodyJson, streamId);

        // Then: Perform assertions
        assertEquals(409, response.statusCode(), "ResCode mismatch");
        JSONAssert.assertEquals(
                "Update Feature error response doesn't match",
                expectedBodyPart,
                response.body(),
                JSONCompareMode.LENIENT);
        assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
    }

    void tc0503_testUpdateFeatureWithMismatchingId() throws Exception {
        // Test API : PUT /hub/spaces/{spaceId}/features/{featureId}

        // Read request body
        final String bodyJson = loadFileOrFail("TC0502_updateFeatureWithWrongUriId/request.json");
        // TODO: include geometry after Cursor-related changes ->
        final Space space = parseJsonFileOrFail("TC0300_createFeaturesWithNewIds/create_space.json", Space.class);
        final String expectedBodyPart = loadFileOrFail("TC0503_updateFeatureMismatchingId/response.json");
        final String streamId = UUID.randomUUID().toString();

        // When: Create Features request is submitted to NakshaHub Space Storage instance
        final HttpResponse<String> response =
                nakshaClient.put("hub/spaces/" + space.getId() + "/features/my-custom-id-301-1", bodyJson, streamId);

        // Then: Perform assertions
        assertEquals(400, response.statusCode(), "ResCode mismatch");
        JSONAssert.assertEquals(
                "Update Feature error response doesn't match",
                expectedBodyPart,
                response.body(),
                JSONCompareMode.LENIENT);
        assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
    }
}
