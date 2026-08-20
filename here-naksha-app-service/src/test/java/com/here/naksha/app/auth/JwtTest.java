package com.here.naksha.app.auth;


import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.common.NakshaTestWebClient;
import com.here.naksha.app.common.TestUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.here.naksha.app.common.CommonApiTestSetup.setupSpaceAndRelatedResources;
import static com.here.naksha.app.common.TestUtil.*;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

public class JwtTest extends ApiTest {
    // For this test suite, the default test-config.json denotes that
    // the service is launched in DUMMY auth mode

    @BeforeAll
    static void setup(){
        setupSpaceAndRelatedResources(new NakshaTestWebClient(), "Auth/setup");
    }

    private static final String SPACE_ID = "auth_test_space";

    @Test
    public void testDummyModeNoJWT() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        // Providing no JWT, the master token should be employed automatically
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId);
        assertThat(response).hasStatus(200);
    }

    @Test
    public void testDummyModeAppIdAuthorExistsInXyzNamespace() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String bodyJson = loadFileOrFail("Auth/JWTAppIdAuthorExists/create_features.json");
        final String expectedBodyPart = loadFileOrFail("Auth/JWTAppIdAuthorExists/feature_response_part.json");

        final String jwtClaims = TestUtil.loadFileOrFail("Auth/validJwtClaims.json");
        // Sign the following JWT payload
        final String jwt = generateJWT(jwtClaims);
        HttpResponse<String> response = getNakshaClient().post("hub/spaces/" + SPACE_ID + "/features", bodyJson, streamId,"Bearer "+jwt);

        // Then: Perform assertions
        assertThat(response)
                .hasStatus(200)
                .hasStreamIdHeader(streamId)
                .hasJsonBody(expectedBodyPart, "Create Feature response body doesn't match");
    }

    @Test
    public void testDummyModeInvalidJWT() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        // Providing an invalid JWT, should return HTTP 401 code
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId, "Bearer rdzftugzhkjn");
        assertThat(response).hasStatus(401);
    }

    @Test
    public void testDummyModeJWTSignedByUnknownSymmetricKey() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        // Providing an invalid JWT, should return HTTP 401 code
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        assertThat(response).hasStatus(401);
    }

    @Test
    public void testDummyModeJWTSignedByUnknownKey() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String jwtClaims = TestUtil.loadFileOrFail("Auth/validJwtClaims.json");
        // Sign the following JWT payload
        final String jwt = generateJWT(jwtClaims,"unit_test_data/Auth/test_keys/rsa256");
        // Providing an invalid JWT, should return HTTP 401 code
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId, "Bearer "+jwt);
        assertThat(response).hasStatus(401);
    }

    @Test
    public void testDummyModeValidJWT() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String jwtClaims = TestUtil.loadFileOrFail("Auth/validJwtClaims.json");
        // Sign the following JWT payload
        final String jwt = generateJWT(jwtClaims);
        // Providing a valid JWT, should success
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId, "Bearer "+jwt);
        assertThat(response).hasStatus(200);
    }

    @Test
    public void testDummyModeExpiredJWT() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String jwtClaims = TestUtil.loadFileOrFail("Auth/expiredJwtClaims.json");
        // Sign the following JWT payload
        final String jwt = generateJWT(jwtClaims);
        // Providing an expired JWT, should fail
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId, "Bearer "+jwt);
        assertThat(response).hasStatus(401);
    }

    /**
     * Parameterized auth matrix test.
     * Inputs per scenario:
     *  - HTTP method (GET/POST/PUT/DELETE)
     *  - endpoint
     *  - JSON body file (nullable)
     *  - JWT token
     *  - expected response status
     */
    @ParameterizedTest
    @MethodSource("authorizationScenarios")
    void testAuthorizationMatrix(String httpMethod, String endpoint, String bodyFile, String jwt, int expectedStatus) throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String authHeader = jwt != null ? "Bearer " + jwt : null;
        final String body = bodyFile != null ? loadFileOrFail(bodyFile) : "{}";

        HttpResponse<String> response = switch (httpMethod) {
            case "GET" -> authHeader != null ? getNakshaClient().get(endpoint, streamId, authHeader) : getNakshaClient().get(endpoint, streamId);
            case "POST" -> getNakshaClient().post(endpoint, body, streamId, authHeader);
            case "PUT" -> getNakshaClient().put(endpoint, body, streamId, authHeader);
            case "DELETE" -> getNakshaClient().delete(endpoint, streamId, authHeader);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + httpMethod);
        };

        assertThat(response).hasStatus(expectedStatus);
    }

    static Stream<Arguments> authorizationScenarios() {
        List<Arguments> scenarios = new ArrayList<>();
        final List<String> readOnlyJwts = List.of(readOnlyJwt(), xyzHubReadOnlyJwt());
        final String storageId = "auth_test_storage";
        final String handlerId = "auth_test_handler";
        final String featureId = "auth_test_feature_id";

        for (String jwt : readOnlyJwts) {
            // Positive scenario: read-only token can read.
            scenarios.add(Arguments.of("GET", "hub/storages", null, jwt, 200));

            // Negative scenarios: read-only token cannot write.
            scenarios.add(Arguments.of("POST", "hub/storages", "Auth/WriteAuthorizationNegative/create_storage.json", jwt, 403));
            scenarios.add(Arguments.of("PUT", "hub/storages/" + storageId, "Auth/WriteAuthorizationNegative/update_storage.json", jwt, 403));
            scenarios.add(Arguments.of("DELETE", "hub/storages/" + storageId, null, jwt, 403));

            scenarios.add(Arguments.of("POST", "hub/handlers", "Auth/WriteAuthorizationNegative/create_event_handler.json", jwt, 403));
            scenarios.add(Arguments.of("PUT", "hub/handlers/" + handlerId, "Auth/WriteAuthorizationNegative/update_event_handler.json", jwt, 403));
            scenarios.add(Arguments.of("DELETE", "hub/handlers/" + handlerId, null, jwt, 403));

            scenarios.add(Arguments.of("POST", "hub/spaces", "Auth/WriteAuthorizationNegative/create_space.json", jwt, 403));
            scenarios.add(Arguments.of("PUT", "hub/spaces/" + SPACE_ID, "Auth/WriteAuthorizationNegative/update_space.json", jwt, 403));
            scenarios.add(Arguments.of("DELETE", "hub/spaces/" + SPACE_ID, null, jwt, 403));

            scenarios.add(Arguments.of("POST", "hub/spaces/" + SPACE_ID + "/features", "Auth/WriteAuthorizationNegative/create_features.json", jwt, 403));
            scenarios.add(Arguments.of("PUT", "hub/spaces/" + SPACE_ID + "/features", "Auth/WriteAuthorizationNegative/update_features.json", jwt, 403));
            scenarios.add(Arguments.of("DELETE", "hub/spaces/" + SPACE_ID + "/features/" + featureId, null, jwt, 403));
        }

        return scenarios.stream();
    }
}