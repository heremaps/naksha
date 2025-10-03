package com.here.naksha.storage.http.connector.integration.tests;

import com.here.naksha.storage.http.connector.integration.utils.DataHub;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.here.naksha.storage.http.connector.integration.utils.Commons.assertStatusCode200;
import static com.here.naksha.storage.http.connector.integration.utils.Commons.rmAllFeatures;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PostTest extends WriteCollectionTest {
    @BeforeEach
    void rmFeatures() {
        rmAllFeatures();
    }

    @Override
    Response sendRequest(RequestSpecification requestSpecification){
        return requestSpecification.post("/features");
    }

    @Test
    void postShouldPatchProperties() {
        Response responseANew = writeFeature(new WriteCollectionTest.InputFeature(FEATURE_A_ID, Map.of("p", "1", "q", "1")));
        assertStatusCode200(responseANew);

        Response responseAUpdated = writeFeature(new WriteCollectionTest.InputFeature(FEATURE_A_ID, Map.of("p", "2")));
        assertStatusCode200(responseAUpdated);

        Response iterateResponse = DataHub.request().get("/iterate");
        new OutputFeature(FEATURE_A_ID, iterateResponse).assertOnlyOneFeatureWithId();

        // assert that properties are patched, not replaced as a whole
        iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.p", equalTo("2"));
        iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.q", equalTo("1"));
    }


    @Test
    void postShouldModifyTags() {
        WriteCollectionTest.InputFeature featureA = new WriteCollectionTest.InputFeature(FEATURE_A_ID, Map.of("p", "1", "q", "1"));
        Response responseANew = createWriteFeaturesRequest(featureA)
                .with().queryParam("addTags", "tag1", "tag2")
                .post("/features");
        assertStatusCode200(responseANew);
        OutputFeature outputFeatureA = new OutputFeature(FEATURE_A_ID, responseANew);
        assertEquals(List.of("tag1", "tag2"), outputFeatureA.getXyzNamespaceProperty("tags"));

        Response responseAChangedTags = createWriteFeaturesRequest(featureA)
                .with().queryParam("addTags", "tag3")
                .with().queryParam("removeTags", "tag1")
                .post("/features");
        assertStatusCode200(responseAChangedTags);
        OutputFeature outputFeatureAChangedTags = new OutputFeature(FEATURE_A_ID, responseAChangedTags);
        assertEquals(List.of("tag2", "tag3"), outputFeatureAChangedTags.getXyzNamespaceProperty("tags"));
    }

    @Override
    protected String expectedWriteErrorMessage(String detailedMessage) {
        return "Error encountered while writing the patched features to storage";
    }
}
