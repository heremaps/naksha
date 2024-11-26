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

import static com.here.naksha.storage.http.connector.integration.utils.Commons.*;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PutTest {
  private static final String FEATURE_A_ID = "A";
  private static final String FEATURE_B_ID = "B";
  private static final String FEATURE_C_ID = "C";
  private static final String FEATURE_D_ID = "D";

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
      outputFeatureANew.getUuid(),
      outputFeatureANew.getCreatedAt()
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
      outputFeatureAUpdated.getUuid(),
      outputFeatureAUpdated.getCreatedAt()
    );
    outputFeatureBUpdated.performUpdatedFeatureAssertions(
      outputFeatureBNew.getUuid(),
      outputFeatureBNew.getCreatedAt()
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
  void putShouldReplaceProperties() {
    Response responseANew = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1", "q", "1")));
    assertStatusCode200(responseANew);

    Response responseAUpdated = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "2")));
    assertStatusCode200(responseAUpdated);

    Response iterateResponse = DataHub.request().get("/iterate");
    new OutputFeature(FEATURE_A_ID, iterateResponse).assertOnlyOneFeatureWithId();

    // assert that properties are replaced as a whole, not only patched
    iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.p", equalTo("2"));
    iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.q", equalTo(null));
  }

  @Test
  void addNewWithTags() { // Adding and deleting tags with PUT to existing features does not work properly yet
    InputFeature featureA = new InputFeature(FEATURE_A_ID, Map.of("p", "1", "q", "1"));
    Response responseANew = createPutFeaturesRequest(featureA)
      .with().queryParam("addTags", "tag1", "tag2")
      .put("/features");
    assertStatusCode200(responseANew);
    OutputFeature outputFeatureA = new OutputFeature(FEATURE_A_ID, responseANew);
    assertEquals(List.of("tag1", "tag2"), outputFeatureA.getXyzNamespaceProperty("tags"));
  }

  @Test
  void putEmpty() {
    Response responseEmpty = putFeatures(List.of());
    responseEmpty.then().assertThat().statusCode(400)
      .and().body("type", equalTo("ErrorResponse"))
      .and().body("error", equalTo("IllegalArgument"))
      .and().body("errorMessage", equalTo("Can't update empty features"));
    assertDbEmpty();
  }

  @Test
  void updateWithMatchingUuid() {
    Response responseNew = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1")));
    assertStatusCode200(responseNew);
    OutputFeature outputNew = new OutputFeature(FEATURE_A_ID, responseNew);

    Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
      Map.of(UUID_KEY, outputNew.getUuid())
    );
    Response responseUpdated = putFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid));
    assertStatusCode200(responseUpdated);
    new OutputFeature(FEATURE_A_ID, responseUpdated).performUpdatedFeatureAssertions(
      outputNew.getUuid(),
      outputNew.getCreatedAt()
    );
  }

  @Test
  void errorOnUpdateWithNonMatchingUuid() {
    Response responseNew = putFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1"))); // insert single to empty database
    assertStatusCode200(responseNew);
    OutputFeature outputNew = new OutputFeature(FEATURE_A_ID, responseNew);

    Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
      Map.of(UUID_KEY, UUID.randomUUID())
    );
    Response responseUpdated = putFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid)); // insert single to empty database
    String expectedErrorMessage = "The feature with id urn:here::here:landmark3d.Landmark3dPhotoreal:A cannot be replaced. The provided UUID doesn't match the UUID of the head state: %s"
      .formatted(outputNew.getUuid());
    responseUpdated.then()
      .assertThat().statusCode(HTTP_CONFLICT)
      .and().body("type", equalTo("ErrorResponse"))
      .and().body("error", equalTo("Conflict"))
      .and().body("errorMessage", equalTo(expectedErrorMessage));
  }

  @Test
  void errorOnNewWithUuid() {
    Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
      Map.of(UUID_KEY, UUID.randomUUID())
    );
    Response responseUuid = putFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid));
    responseUuid.then().assertThat().statusCode(greaterThanOrEqualTo(400))
      .and().body("type", equalTo("ErrorResponse"));

    assertDbEmpty();
  }

  private Response putFeature(InputFeature feature) {
    return createPutFeaturesRequest(feature).put("/features");
  }

  private Response putFeatures(List<InputFeature> features) {
    return createPutFeaturesRequest(features).put("/features");
  }

  private RequestSpecification createPutFeaturesRequest(InputFeature feature) {
    return createPutFeaturesRequest(List.of(feature));
  }

  private RequestSpecification createPutFeaturesRequest(List<InputFeature> features) {
    String featuresArrayJson = features.stream()
      .map(InputFeature::toJson)
      .collect(Collectors.joining(", ", "[", "]"));
    String featuresCollectionJson = readTestResourcesFile("postAndPut/feature_collection_template.json").formatted(featuresArrayJson);
    RequestSpecification request = Naksha.request()
      .with().body(featuresCollectionJson)
      .with().header("Content-Type", "application/json");
    return request;
  }

  private record InputFeature(String shortId, Map properties) {
    String toJson() {
      return readTestResourcesFile("postAndPut/feature_template.json")
        .formatted(shortId, JsonSerializable.serialize(properties));
    }
  }
}
