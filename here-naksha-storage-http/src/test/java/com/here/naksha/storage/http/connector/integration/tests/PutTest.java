package com.here.naksha.storage.http.connector.integration.tests;

import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.storage.http.connector.integration.utils.DataHub;
import com.here.naksha.storage.http.connector.integration.utils.Naksha;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.here.naksha.storage.http.connector.integration.utils.Commons.assertStatusCode200;
import static com.here.naksha.storage.http.connector.integration.utils.Commons.readTestResourcesFile;
import static com.here.naksha.storage.http.connector.integration.utils.Commons.rmAllFeatures;
import static io.restassured.RestAssured.withArgs;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PutTest {
    public static final String FEATURE_A_ID = "A";
    public static final String FEATURE_B_ID = "B";
    public static final String FEATURE_C_ID = "C";
    public static final String FEATURE_D_ID = "D";
    public static final String UUID_KEY = "uuid";
    public static final String PUUID_KEY = "puuid";
    private static final String CREATED_AT_KEY = "createdAt";
    private static final String UPDATED_AT_KEY = "updatedAt";

    @BeforeEach
    void rmFeatures() {
        rmAllFeatures();
    }

    @Test
    void put() {
        Response responseANew = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1"))); // insert single to empty database
        assertStatusCode200(responseANew);
        OutputFeature outputFeatureANew = new OutputFeature(FEATURE_A_ID, responseANew);
        outputFeatureANew.performNewFeatureAssertions();


        Response responseAUpdated = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "2"))); // update single
        assertStatusCode200(responseAUpdated);
        OutputFeature outputFeatureAUpdated = new OutputFeature(FEATURE_A_ID, responseAUpdated);
        outputFeatureAUpdated.performUpdatedFeatureAssertions(
                outputFeatureANew.uuid,
                outputFeatureANew.createdAt
        );

        Response responseBNew = putFeature(new InputFeature(FEATURE_B_ID, Map.of("p", "1"))); // insert single to non empty database
        assertStatusCode200(responseBNew);
        OutputFeature outputFeatureBNew = new OutputFeature(FEATURE_B_ID, responseBNew);
        outputFeatureBNew.performNewFeatureAssertions();

        Response responseComplex = putFeatures(List.of( // complex request, update single again
                new InputFeature(FEATURE_A_ID, Map.of("p", "3")),
                new InputFeature(FEATURE_B_ID, Map.of("p", "2")),
                new InputFeature(FEATURE_C_ID, Map.of("p", "1")),
                new InputFeature(FEATURE_D_ID, Map.of("p", "1"))
        ));
        assertStatusCode200(responseComplex);
        OutputFeature outputFeatureAUpdatedAgain = new OutputFeature(FEATURE_A_ID, responseComplex);
        OutputFeature outputFeatureBUpdated = new OutputFeature(FEATURE_B_ID, responseComplex);
        OutputFeature outputFeatureCNew = new OutputFeature(FEATURE_C_ID, responseComplex);
        OutputFeature outputFeatureDNew = new OutputFeature(FEATURE_D_ID, responseComplex);
        outputFeatureAUpdatedAgain.performUpdatedFeatureAssertions(
                outputFeatureAUpdated.uuid,
                outputFeatureAUpdated.createdAt
        );
        outputFeatureBUpdated.performUpdatedFeatureAssertions(
                outputFeatureBNew.uuid,
                outputFeatureBNew.createdAt
        );
        outputFeatureCNew.performNewFeatureAssertions();
        outputFeatureDNew.performNewFeatureAssertions();

        Response iterateResponse = DataHub.request().get("/iterate");
        new OutputFeature(FEATURE_A_ID, iterateResponse).performExistingAssertions(outputFeatureAUpdatedAgain);
        new OutputFeature(FEATURE_B_ID, iterateResponse).performExistingAssertions(outputFeatureBUpdated);
        new OutputFeature(FEATURE_C_ID, iterateResponse).performExistingAssertions(outputFeatureCNew);
        new OutputFeature(FEATURE_D_ID, iterateResponse).performExistingAssertions(outputFeatureDNew);
    }

    @Test
    void putShouldReplaceProperties(){
        Response responseANew = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1", "q", "1")));
        assertStatusCode200(responseANew);

        Response responseAUpdated = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "2")));
        assertStatusCode200(responseAUpdated);

        Response iterateResponse = DataHub.request().get("/iterate");
        new OutputFeature(FEATURE_A_ID, iterateResponse).assertOnlyOneFeatureWithId();

        // assert that properties are replaced as a whole, not only patched
        iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.p",equalTo("2"));
        iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.q",equalTo(null));
    }

    @Test
    void putEmpty() {
        Response responseEmpty = putFeatures(List.of());
        responseEmpty.then().assertThat().statusCode(400)
                .and().body("type", equalTo("ErrorResponse"))
                .and().body("error", equalTo("IllegalArgument"))
                .and().body("errorMessage", equalTo("Can't update empty features"));

        DataHub.request().get("iterate").then().assertThat().body("features.isEmpty()", equalTo(true));
    }

    @Test
    void updateWithMatchingUuid(){
        Response responseNew = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1")));
        assertStatusCode200(responseNew);
        OutputFeature outputNew = new OutputFeature(FEATURE_A_ID, responseNew);

        Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
                Map.of(UUID_KEY, outputNew.uuid)
        );
        Response responseUpdated = putFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid));
        assertStatusCode200(responseUpdated);
        new OutputFeature(FEATURE_A_ID, responseUpdated).performUpdatedFeatureAssertions(
                outputNew.uuid,
                outputNew.createdAt
        );
    }

    @Test
    void errorOnUpdateWithNonMatchingUuid(){
        Response responseNew = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1"))); // insert single to empty database
        assertStatusCode200(responseNew);
        OutputFeature outputNew = new OutputFeature(FEATURE_A_ID, responseNew);

        Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
                Map.of(UUID_KEY, UUID.randomUUID())
        );
        Response responseUpdated = putFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid)); // insert single to empty database
        String expectedErrorMessage = "The feature with id urn:here::here:landmark3d.Landmark3dPhotoreal:A cannot be replaced. The provided UUID doesn't match the UUID of the head state: %s"
                .formatted(outputNew.uuid);
        responseUpdated.then()
                .assertThat().statusCode(HTTP_CONFLICT)
                .and().body("type", equalTo("ErrorResponse"))
                .and().body("error", equalTo("Conflict"))
                .and().body("errorMessage", equalTo(expectedErrorMessage));
    }

    @Test
    void errorOnNewWithUuid(){
        Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
                Map.of(UUID_KEY, UUID.randomUUID())
        );
        Response responseUuid = putFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid));
        responseUuid.then().assertThat().statusCode(greaterThanOrEqualTo(400))
                .and().body("type", equalTo("ErrorResponse"));

        DataHub.request().get("iterate").then().assertThat().body("features.isEmpty()", equalTo(true));
    }

    Response putFeature(InputFeature feature) {
        return putFeatures(List.of(feature));
    }

    Response putFeatures(List<InputFeature> features) {
        String featuresArrayJson = features.stream()
                .map(InputFeature::toJson)
                .collect(Collectors.joining(", ", "[", "]"));
        String featuresCollectionJson = readTestResourcesFile("postAndPut/feature_collection_template.json").formatted(featuresArrayJson);
        RequestSpecification request = Naksha.request()
                .with().body(featuresCollectionJson)
                .with().header("Content-Type", "application/json");
        return request.put("/features");
    }

    private record InputFeature(String shortId, Map properties) {
        String toJson() {
            return readTestResourcesFile("postAndPut/feature_template.json")
                    .formatted(shortId, JsonSerializable.serialize(properties));
        }
    }

    private static class OutputFeature {
        private static final long TEN_SECONDS_IN_MS = 100000;

        private final String shortId;
        private final Response response;
        private final String uuid;
        private final String puuid;
        private final long createdAt;
        private final long updatedAt;

        public OutputFeature(String shortId, Response response) {
            this.shortId = shortId;
            this.response = response;
            this.uuid = getXyzNamespaceProperty(UUID_KEY);
            this.puuid = getXyzNamespaceProperty(PUUID_KEY);
            this.createdAt = getXyzNamespaceProperty(CREATED_AT_KEY);
            this.updatedAt = getXyzNamespaceProperty(UPDATED_AT_KEY);
        }

        private void performNewFeatureAssertions() {
            assertOnlyOneFeatureWithId();
            assertNotNull(uuid);
            assertNull(puuid);
            assertEquals(createdAt, updatedAt);
            assertTrue(System.currentTimeMillis() > createdAt);
            assertTrue(System.currentTimeMillis() < createdAt + TEN_SECONDS_IN_MS);
        }

        private void performUpdatedFeatureAssertions(String expectedPuuid, long expectedCreatedAt) {
            assertOnlyOneFeatureWithId();
            assertNotNull(uuid);
            assertEquals(expectedPuuid, puuid);

            assertEquals(expectedCreatedAt, createdAt);
            assertTrue(updatedAt > createdAt);
            assertTrue(System.currentTimeMillis() > updatedAt);
            assertTrue(System.currentTimeMillis() < updatedAt + TEN_SECONDS_IN_MS);
        }

        private void performExistingAssertions(OutputFeature expectedFeature) {
            assertOnlyOneFeatureWithId();
            assertEquals(expectedFeature.uuid, uuid);
            assertEquals(expectedFeature.puuid, puuid);
            assertEquals(expectedFeature.createdAt, createdAt);
            assertEquals(expectedFeature.updatedAt, updatedAt);
        }

        private void assertOnlyOneFeatureWithId() {
            response.then()
                    .assertThat()
                    .body("features.findAll{it.id.endsWith(':%s')}.size()", withArgs(shortId), equalTo(1));
        }

        private <T> T getXyzNamespaceProperty(String propertyName) {
            return response.then()
                    .extract()
                    .path("features.find{it.id.endsWith(':%s')}.properties.@ns:com:here:xyz.%s", shortId, propertyName);

        }
    }
}
