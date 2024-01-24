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
import static com.here.naksha.app.common.TestUtil.urlEncoded;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

public class SourceIdHandlerApiTest extends ApiTest {

    private static final NakshaTestWebClient nakshaClient = new NakshaTestWebClient();
    private static final String SPACE_ID = "source_id_handler_test_space";

    @BeforeAll
    static void setup() throws URISyntaxException, IOException, InterruptedException {
        createStorage(nakshaClient, "SourceHandlerId/setup/create_storage.json");
        createHandler(nakshaClient, "SourceHandlerId/setup/create_event_handler.json");
        createHandler(nakshaClient, "SourceHandlerId/setup/create_default_event_handler.json");
        createSpace(nakshaClient, "SourceHandlerId/setup/create_space.json");
    }


    @Test
    void tc2000_testCreateFeaturesWithSourceIdInMeta() throws Exception {

        // Given:
        final String bodyJson = loadFileOrFail("SourceHandlerId/TC2000_createFeatureWithoutTag/create_feature_without_tag.json");
        final String expectedBodyPart = loadFileOrFail("SourceHandlerId/TC2000_createFeatureWithoutTag/feature_response_part.json");
        String streamId = UUID.randomUUID().toString();

        // When
        HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);

        // Then
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
                .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
                .hasInsertedIdsMatchingFeatureIds(null)
                .hasUuids();

    }
    @Test
    void tc2001_testCreateFeaturesWithSourceIdInMetaWithTags() throws Exception {

        // Given:
        final String bodyJson = loadFileOrFail("SourceHandlerId/TC2001_createFeatureWithTag/create_feature_with_tag.json");
        final String expectedBodyPart = loadFileOrFail("SourceHandlerId/TC2001_createFeatureWithTag/feature_response_part.json");
        String streamId = UUID.randomUUID().toString();

        // When
        HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId);

        // Then
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match")
                .hasInsertedCountMatchingWithFeaturesInRequest(bodyJson)
                .hasInsertedIdsMatchingFeatureIds(null)
                .hasUuids();

    }


    @Test
    void tc0725_testBBox2WithSearchOnObjectContains() throws Exception {
        // Test API : GET /hub/spaces/{spaceId}/bbox
        // Validate features returned match with given BBox and Search conditions (Object "contains")


        // (speedLimit eq 50 and sourceId = 10) and proder = 4 -> speedlim eq 50 and proOrde =4

        //  (speedLimit eq 50 and sourceId = 10) and proder = 4 ->  (speedLimit eq 50 and xyzTags.contains(xyz_source_id_10) ) and proder = 4

        // Given: Features By BBox request (against configured space)
        final String bboxQueryParam = "west=8.6476&south=50.1175&east=8.6729&north=50.1248";
//        final String propQueryParam = "%s=eq=%s".formatted(
//                urlEncoded("p.@ns:com:here:mom:meta.sourceId"),
//                urlEncoded("Task_2")
//        );
        final String propQueryParam = "%s=eq=%s&%s=ne=%s&tags=funny,bunny&%s=eq=%s".formatted(
                urlEncoded("p.@ns:com:here:mom:meta.sourceId"),
                urlEncoded("Task_2"),
                urlEncoded("p.@ns:com:here:mom:meta.sourceId"),
                urlEncoded("Task_1"),
                urlEncoded("p.@ns:com:here:mom:meta.sourceId"),
                urlEncoded(".null")
        );
//        final String expectedBodyPart =
//                loadFileOrFail("ReadFeatures/ByBBox/TC0725_BBox2_PropObjectContains/feature_response_part.json");
        String streamId = UUID.randomUUID().toString();

        // When: Get Features By BBox request is submitted to NakshaHub
        HttpResponse<String> response = nakshaClient
                .get("hub/spaces/" + SPACE_ID + "/bbox?" + bboxQueryParam + "&" + propQueryParam, streamId);

        System.out.println(response.body());        // Then: Perform assertions
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody("", "Get Feature response body doesn't match");
    }


}
