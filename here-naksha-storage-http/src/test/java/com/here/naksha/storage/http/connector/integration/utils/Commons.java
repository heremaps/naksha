package com.here.naksha.storage.http.connector.integration.utils;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Commons {

  public static final String URN_PREFIX = "urn:here::here:landmark3d.Landmark3dPhotoreal:";
  public static final String UUID_KEY = "uuid";
  public static final String TEST_RESOURCES_DIR = "com/here/naksha/storage/http/connector/integration/";

  public static void rmAllFeatures() {
    Response iterateResponse = DataHub.request().get("iterate");
    List<String> featuresIds = responseToIds(iterateResponse);
    DataHub.request().with().queryParam("id", featuresIds).delete("features");
    DataHub.request().get("iterate").then().body("features", Matchers.hasSize(0));
  }

  public static void assertSameIds(Response dhResponse, Response nResponse) {
    List<String> nResponseMap = responseToIds(nResponse);
    List<String> dhResponseMap = responseToIds(dhResponse);
    assertEquals(nResponseMap, dhResponseMap);
  }

  public static List<String> responseToIds(Response response) {
    response.
      then().assertThat().body("$", hasKey("features"))
      .and().log().ifValidationFails();
    return response.body().jsonPath().getList("features").stream().map(e -> ((Map) e).get("id").toString()).toList();
  }

  public static boolean responseHasExactShortIds(List<String> expectedShortIds, Response response) {
    List<String> expectedIds = expectedShortIds.stream().map(e -> URN_PREFIX + e).toList();
    List<String> responseIds = responseToIds(response);
    return expectedIds.equals(responseIds);
  }

  public static void createFeatureFromJsonFile(RequestSpecification rs, String pathInIntegrationResources) {
    createFeatureFromJsonTemplateFile(rs, pathInIntegrationResources);
  }

  public static ValidatableResponse createFeatureFromJsonTemplateFile(RequestSpecification rs, String pathInIntegrationResources, String... args) {
    String body = readTestResourcesFile(pathInIntegrationResources).formatted(args);
    return rs.with().body(body)
      .when().post("features")
      .then()
      .assertThat().statusCode(200)
      .and().log().ifValidationFails();
  }

  public static void assertStatusCode200(Response response) {
    response.then().log().all().assertThat().statusCode(200);
  }

  public static void assertDbEmpty() {
    DataHub.request().get("iterate").then().assertThat().body("features.isEmpty()", equalTo(true));
  }

  public static @NotNull String readTestResourcesFile(String pathInIntegrationResources) {
    try {
      String pathInResources = TEST_RESOURCES_DIR + pathInIntegrationResources;
      Path featureTemplatePath = Path.of(ClassLoader.getSystemResource(pathInResources).toURI());
      return Files.readString(featureTemplatePath);
    } catch (URISyntaxException | IOException e) {
      fail(e);
      return "";
    }
  }


}