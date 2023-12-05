package com.here.naksha.app.service;

import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.lib.core.models.naksha.Space;
import org.jetbrains.annotations.NotNull;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.net.http.HttpResponse;
import java.util.UUID;

import static com.here.naksha.app.common.TestUtil.*;
import static com.here.naksha.app.common.TestUtil.HDR_STREAM_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DeleteFeatureTestHelper {


    final @NotNull NakshaApp app;
    final @NotNull NakshaTestWebClient nakshaClient;

    public DeleteFeatureTestHelper(final @NotNull NakshaApp app, final @NotNull NakshaTestWebClient nakshaClient) {
        this.app = app;
        this.nakshaClient = nakshaClient;
    }

    void tc0900_testUpdateFeatures() throws Exception {
        // Test API : DELETE /hub/spaces/{spaceId}/features
        final String streamId = UUID.randomUUID().toString();

        // Preparation: create storage, event handler, space, and initial features
        final String storage = loadFileOrFail("TC0900_deleteFeatures/create_storage.json");
        nakshaClient.post("hub/storages", storage, streamId);
        final String handler = loadFileOrFail("TC0900_deleteFeatures/create_handler.json");
        nakshaClient.post("hub/handlers", handler, streamId);
        final String spaceJson = loadFileOrFail("TC0900_deleteFeatures/create_space.json");
        nakshaClient.post("hub/spaces", spaceJson, streamId);
        final Space space = parseJsonFileOrFail("TC0900_deleteFeatures/create_space.json", Space.class);
        final String createFeaturesJson = loadFileOrFail("TC0900_deleteFeatures/create_features.json");
        nakshaClient.post("hub/spaces/" + space.getId() + "/features", createFeaturesJson, streamId);
        final String expectedBodyPart = loadFileOrFail("TC0900_deleteFeatures/create_features.json");

        // When: request is submitted to NakshaHub Space Storage instance
        //TODO how to add feature ids
        final HttpResponse<String> response =
                nakshaClient.delete("hub/spaces/" + space.getId() + "/features", streamId);

        // Then: Perform assertions
        assertEquals(200, response.statusCode(), "ResCode mismatch");
        JSONAssert.assertEquals(
                "Delete Feature response body doesn't match",
                expectedBodyPart,
                response.body(),
                JSONCompareMode.LENIENT);
        assertEquals(streamId, getHeader(response, HDR_STREAM_ID), "StreamId mismatch");
    }
}
