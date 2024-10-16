package com.here.naksha.storage.http.connector.integration;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static com.here.naksha.storage.http.connector.integration.Commons.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BBoxTest {

    @BeforeEach
    void rmFeatures() {
        rmAllFeatures();
    }

    @Test
    void bbox_notContains() throws URISyntaxException {
        createFromJsonFile("bbox/feature_1.json");

        String bbox = "bbox?west=-1&north=0&east=0&south=-3";
        Response dhResponse = dataHub().get(bbox);
        Response nResponse = naksha().get(bbox);
        assertSameIds(dhResponse, nResponse);

        List<Map> features = dhResponse.body().jsonPath().getList("features", Map.class);
        assertEquals(0, features.size());
    }

    @Test
    void bbox_contains() throws URISyntaxException {
        createFromJsonFile("bbox/feature_1.json");

        String bbox = "bbox?west=-3&north=0&east=0&south=-1";
        Response dhResponse = Commons.dataHub().get(bbox);
        Response nResponse = naksha().get(bbox);
        assertSameIds(dhResponse, nResponse);

        List<Map> features = dhResponse.body().jsonPath().getList("features", Map.class);
        assertEquals(1, features.size());
    }


}
