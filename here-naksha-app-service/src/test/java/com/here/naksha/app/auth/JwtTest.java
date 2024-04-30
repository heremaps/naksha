package com.here.naksha.app.auth;


import com.here.naksha.app.common.ApiTest;
import com.here.naksha.app.service.http.auth.NakshaAuthProvider;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.IoHelp.LoadedBytes;
import com.here.naksha.lib.hub.NakshaHubConfig;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static com.here.naksha.app.common.assertions.ResponseAssertions.assertThat;

public class JwtTest extends ApiTest {
    // For this test suite, the default test-config.json denotes that
    // the service is launched in DUMMY auth mode
    @Test
    public void testDummyModeNoJWT() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        // Providing no JWT, the master token should be employed automatically
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId);
        assertThat(response).hasStatus(200);
    }

    @Test
    public void testDummyModeInvalidJWT() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        // Providing an invalid JWT, should return HTTP 401 code
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId, "Bearer rdzftugzhkjn");
        assertThat(response).hasStatus(401);
    }

    @Test
    public void testDummyModeValidJWT() throws Exception {
        final String streamId = UUID.randomUUID().toString();
        final String jwtKey;
        // Load private key
        {
            final String path = "auth/jwt.key";
            final LoadedBytes loaded = IoHelp.readBytesFromHomeOrResource(path, false, NakshaHubConfig.APP_NAME);
            jwtKey = new String(loaded.getBytes(), StandardCharsets.UTF_8);
        }
        final JWTAuthOptions authOptions = new JWTAuthOptions()
                .setJWTOptions(new JWTOptions().setAlgorithm("RS256"))
                .addPubSecKey(new PubSecKeyOptions().setAlgorithm("RS256").setBuffer(jwtKey))
                ;
        final NakshaAuthProvider nakshaAuthProvider = new NakshaAuthProvider(Vertx.vertx(), authOptions);
        final String jwt = nakshaAuthProvider.generateToken(new JsonObject("""
                {
                    "appId": "web-client-app-id",
                    "userId": "my-user-id",
                    "urm": {
                        "naksha": {
                            "readFeatures": [
                                {
                                    "storageId": "dev-*"
                                }
                            ]
                        }
                    },
                    "iat": 1704063599,
                    "exp": 3000000000
                }"""));
        // Providing a valid JWT, should success
        HttpResponse<String> response = getNakshaClient().get("hub/storages", streamId, "Bearer "+jwt);
        assertThat(response).hasStatus(200);
    }
}