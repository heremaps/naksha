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
    private static final String STORAGE_ID = "auth_test_storage";
    private static final String HANDLER_ID = "auth_test_handler";
    private static final String FEATURE_ID = "auth_test_feature_id";
    private static final List<String> READ_ONLY_JWTS = List.of(readOnlyJwt(), xyzHubReadOnlyJwt());

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
     * Parameterized POST test: 4 resources × 2 URM types ("naksha" / "xyz-hub").
     * Verifies that a read-only JWT receives HTTP 403 Forbidden on all POST write endpoints.
     */
    @ParameterizedTest
    @MethodSource("postWriteAuthorizationDenialScenarios")
    void testPostWriteAuthorizationDenied(String endpoint, String bodyFile, String jwt) throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String authHeader = "Bearer " + jwt;
        HttpResponse<String> response = getNakshaClient().post(endpoint, loadFileOrFail(bodyFile), streamId, authHeader);
        assertThat(response).hasStatus(403);
    }

    /**
     * Parameterized PUT test: 4 resources × 2 URM types ("naksha" / "xyz-hub").
     * Verifies that a read-only JWT receives HTTP 403 Forbidden on all PUT write endpoints.
     */
    @ParameterizedTest
    @MethodSource("putWriteAuthorizationDenialScenarios")
    void testPutWriteAuthorizationDenied(String endpoint, String bodyFile, String jwt) throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String authHeader = "Bearer " + jwt;
        HttpResponse<String> response = getNakshaClient().put(endpoint, loadFileOrFail(bodyFile), streamId, authHeader);
        assertThat(response).hasStatus(403);
    }

    /**
     * Parameterized DELETE test: 4 resources × 2 URM types ("naksha" / "xyz-hub").
     * Verifies that a read-only JWT receives HTTP 403 Forbidden on all DELETE write endpoints.
     */
    @ParameterizedTest
    @MethodSource("deleteWriteAuthorizationDenialScenarios")
    void testDeleteWriteAuthorizationDenied(String endpoint, String jwt) throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String authHeader = "Bearer " + jwt;
        HttpResponse<String> response = getNakshaClient().delete(endpoint, streamId, authHeader);
        assertThat(response).hasStatus(403);
    }

    static Stream<Arguments> postWriteAuthorizationDenialScenarios() {
        List<Arguments> scenarios = new ArrayList<>();
        for (String jwt : READ_ONLY_JWTS) {
            scenarios.add(Arguments.of("hub/storages", "Auth/WriteAuthorizationNegative/create_storage.json", jwt));
            scenarios.add(Arguments.of("hub/handlers", "Auth/WriteAuthorizationNegative/create_event_handler.json", jwt));
            scenarios.add(Arguments.of("hub/spaces",   "Auth/WriteAuthorizationNegative/create_space.json", jwt));
            scenarios.add(Arguments.of("hub/spaces/" + SPACE_ID + "/features", "Auth/WriteAuthorizationNegative/create_features.json", jwt));
        }
        return scenarios.stream();
    }

    static Stream<Arguments> putWriteAuthorizationDenialScenarios() {
        List<Arguments> scenarios = new ArrayList<>();
        for (String jwt : READ_ONLY_JWTS) {
            scenarios.add(Arguments.of("hub/storages/" + STORAGE_ID, "Auth/WriteAuthorizationNegative/update_storage.json", jwt));
            scenarios.add(Arguments.of("hub/handlers/" + HANDLER_ID, "Auth/WriteAuthorizationNegative/update_event_handler.json", jwt));
            scenarios.add(Arguments.of("hub/spaces/" + SPACE_ID, "Auth/WriteAuthorizationNegative/update_space.json", jwt));
            scenarios.add(Arguments.of("hub/spaces/" + SPACE_ID + "/features", "Auth/WriteAuthorizationNegative/update_features.json", jwt));
        }
        return scenarios.stream();
    }

    static Stream<Arguments> deleteWriteAuthorizationDenialScenarios() {
        List<Arguments> scenarios = new ArrayList<>();
        for (String jwt : READ_ONLY_JWTS) {
            scenarios.add(Arguments.of("hub/storages/" + STORAGE_ID, jwt));
            scenarios.add(Arguments.of("hub/handlers/" + HANDLER_ID, jwt));
            scenarios.add(Arguments.of("hub/spaces/" + SPACE_ID, jwt));
            scenarios.add(Arguments.of("hub/spaces/" + SPACE_ID + "/features/" + FEATURE_ID, jwt));
        }
        return scenarios.stream();
    }
}