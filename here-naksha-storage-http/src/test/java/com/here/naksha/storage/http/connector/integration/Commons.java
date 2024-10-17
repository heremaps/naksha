package com.here.naksha.storage.http.connector.integration;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.hamcrest.Matchers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Commons {

    // Use you values
    private static final String NAKSHA_SPACE = System.getenv("nakshaSpace");
    private static final String DH_SPACE = System.getenv("dataHubSpace");
    public static final String TOKEN = System.getenv("dataHubToken");

    static void rmAllFeatures() {
        Response iterateResponse = dataHub().get("iterate");
        List<String> featuresIds = responseToIds(iterateResponse);
        dataHub().with().queryParam("id", featuresIds).delete("features");
        dataHub().get("iterate").then().body("features", Matchers.hasSize(0));
    }

    static void assertSameIds(Response dhResponse, Response nResponse) {
        List<String> nResponseMap = responseToIds(nResponse);
        List<String> dhResponseMap = responseToIds(dhResponse);
        assertEquals(nResponseMap,dhResponseMap);
    }

    static RequestSpecification dataHub() {
        String token = new String(Base64.getDecoder().decode(TOKEN));
        return RestAssured
                .given()
                .header("Authorization", "Bearer " + token)
                .baseUri("https://xyz.api.here.com/hub/spaces/" + DH_SPACE);
    }

    static RequestSpecification naksha() {
        return RestAssured.given().baseUri("http://localhost:8080/hub/spaces/" + NAKSHA_SPACE);
    }

    static List<String> responseToIds(Response response){
        return response.body().jsonPath().getList("features").stream().map(e -> ((Map) e).get("id").toString()).toList();
    }

    static void createFromJsonFile(String pathInIntegrationResources) throws URISyntaxException {
        String pathInResources = "com/here/naksha/storage/http/connector/integration/" + pathInIntegrationResources;
        URI feature1 = ClassLoader.getSystemResource(pathInResources).toURI();
        dataHub().body(new File(feature1)).post("features").then().statusCode(200);
    }

    static void createFromJsonFileFormatted(String pathInIntegrationResources, String... args)  {
        try {
            String pathInResources = "com/here/naksha/storage/http/connector/integration/" + pathInIntegrationResources;
            Path featureTemplatePath = Path.of(ClassLoader.getSystemResource(pathInResources).toURI());
            String body = Files.readString(featureTemplatePath).formatted(args);
            dataHub().body(body).post("features").then().statusCode(200);
        } catch (URISyntaxException | IOException e) {
            fail(e);
        }
    }
}