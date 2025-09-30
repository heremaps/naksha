package com.here.naksha.storage.http.connector.integration.utils;



import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;


public class DataHub {

    private static final String SPACE = System.getenv("dataHubSpace");
    private static final String TOKEN = System.getenv("dataHubToken");


    public static void createFeatureFromJsonFile(String pathInIntegrationResources) {
        Commons.createFeatureFromJsonFile(request(), pathInIntegrationResources);
    }

    public static void createFeatureFromJsonTemplateFile(String pathInIntegrationResources, String... args) {
        Commons.createFeatureFromJsonTemplateFile(request(), pathInIntegrationResources, args);
    }

    public static RequestSpecification request() {
        return RestAssured
                .given()
                .header("Authorization", "Bearer " + TOKEN)
                .baseUri("https://xyz.api.here.com/hub/spaces/" + SPACE)
                .log().ifValidationFails();
    }
}
