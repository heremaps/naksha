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

public class PostTest {
  private static final String FEATURE_A_ID = "A";
  private static final String FEATURE_B_ID = "B";
  private static final String FEATURE_C_ID = "C";
  private static final String FEATURE_D_ID = "D";

  @BeforeEach
  void rmFeatures() {
    rmAllFeatures();
  }

  @Test
  void post() {
    Response responseANew = postFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1"))); // insert single to empty database
    assertStatusCode200(responseANew);
    OutputFeature outputFeatureANew = new OutputFeature(FEATURE_A_ID, responseANew);
    outputFeatureANew.performNewFeatureAssertions();

    Response responseAUpdated = postFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "2"))); // update single
    assertStatusCode200(responseAUpdated);
    OutputFeature outputFeatureAUpdated = new OutputFeature(FEATURE_A_ID, responseAUpdated);
    outputFeatureAUpdated.performUpdatedFeatureAssertions(
      outputFeatureANew.getUuid(),
      outputFeatureANew.getCreatedAt()
    );

    Response responseBNew = postFeature(new InputFeature(FEATURE_B_ID, Map.of("p", "1"))); // insert single to non empty database
    assertStatusCode200(responseBNew);
    OutputFeature outputFeatureBNew = new OutputFeature(FEATURE_B_ID, responseBNew);
    outputFeatureBNew.performNewFeatureAssertions();

    Response responseComplex = postFeatures(List.of( // complex request, update single again
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
    new OutputFeature("A", iterateResponse).performExistingAssertions(outputFeatureAUpdatedAgain);
    new OutputFeature("B", iterateResponse).performExistingAssertions(outputFeatureBUpdated);
    new OutputFeature("C", iterateResponse).performExistingAssertions(outputFeatureCNew);
    new OutputFeature("D", iterateResponse).performExistingAssertions(outputFeatureDNew);
  }

  @Test
  void postShouldPatchProperties() {
    Response responseANew = postFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1", "q", "1")));
    assertStatusCode200(responseANew);

    Response responseAUpdated = postFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "2")));
    assertStatusCode200(responseAUpdated);

    Response iterateResponse = DataHub.request().get("/iterate");
    new OutputFeature(FEATURE_A_ID, iterateResponse).assertOnlyOneFeatureWithId();

    // assert that properties are patched, not replaced as a whole
    iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.p", equalTo("2"));
    iterateResponse.then().body("features.find{it.id.endsWith(':A')}.properties.q", equalTo("1"));
  }

  @Test
  void postEmpty() {
    Response responseEmpty = postFeatures(List.of());
    responseEmpty.then().assertThat().statusCode(400)
      .and().body("type", equalTo("ErrorResponse"))
      .and().body("error", equalTo("IllegalArgument"))
      .and().body("errorMessage", equalTo("Can't create empty features"));

    assertDbEmpty();
  }

  @Test
  void addAndDeleteTags() {
    InputFeature featureA = new InputFeature(FEATURE_A_ID, Map.of("p", "1", "q", "1"));
    Response responseANew = createPostFeaturesRequest(featureA)
      .with().queryParam("addTags", "tag1", "tag2")
      .post("/features");
    assertStatusCode200(responseANew);
    OutputFeature outputFeatureA = new OutputFeature(FEATURE_A_ID, responseANew);
    assertEquals(List.of("tag1", "tag2"), outputFeatureA.getXyzNamespaceProperty("tags"));

    Response responseAChangedTags = createPostFeaturesRequest(featureA)
      .with().queryParam("addTags", "tag3")
      .with().queryParam("removeTags", "tag1")
      .post("/features");
    assertStatusCode200(responseAChangedTags);
    OutputFeature outputFeatureAChangedTags = new OutputFeature(FEATURE_A_ID, responseAChangedTags);
    assertEquals(List.of("tag2", "tag3"), outputFeatureAChangedTags.getXyzNamespaceProperty("tags"));
  }

  @Test
  void updateWithMatchingUuid() {
    Response responseNew = postFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1")));
    assertStatusCode200(responseNew);
    OutputFeature outputNew = new OutputFeature(FEATURE_A_ID, responseNew);

    Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
      Map.of(UUID_KEY, outputNew.getUuid())
    );
    Response responseUpdated = postFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid));
    assertStatusCode200(responseUpdated);
    new OutputFeature(FEATURE_A_ID, responseUpdated).performUpdatedFeatureAssertions(
      outputNew.getUuid(),
      outputNew.getCreatedAt()
    );
  }

  @Test
  void errorOnUpdateWithNonMatchingUuid() {
    Response responseNew = postFeature(new InputFeature(FEATURE_A_ID, Map.of("p", "1"))); // insert single to empty database
    assertStatusCode200(responseNew);
    OutputFeature outputNew = new OutputFeature(FEATURE_A_ID, responseNew);

    Map propertiesWithUuid = Map.of("@ns:com:here:xyz",
      Map.of(UUID_KEY, UUID.randomUUID())
    );
    Response responseUpdated = postFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid)); // insert single to empty database
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
    Response responseUuid = postFeature(new InputFeature(FEATURE_A_ID, propertiesWithUuid));
    responseUuid.then().assertThat().statusCode(greaterThanOrEqualTo(400))
      .and().body("type", equalTo("ErrorResponse"));

assertDbEmpty();  }

  private Response postFeature(InputFeature feature) {
    return createPostFeaturesRequest(feature).post("/features");
  }

  private Response postFeatures(List<InputFeature> features) {
    return createPostFeaturesRequest(features).post("/features");
  }

  private RequestSpecification createPostFeaturesRequest(InputFeature feature) {
    return createPostFeaturesRequest(List.of(feature));
  }

  private RequestSpecification createPostFeaturesRequest(List<InputFeature> features) {
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
