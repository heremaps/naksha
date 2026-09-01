package com.here.naksha.storage.http;

import org.junit.jupiter.api.Test;
import naksha.base.JvmBoxingUtil;
import naksha.base.JvmJsonUtil;
import naksha.base.Platform;
import naksha.model.objects.NakshaStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpStoragePropertiesTest {

    private static final String TEST_RESOURCE_DIR = "/unit_test_data/HttpStorageProperties/";

    @Test
    void shouldReturnDefaultValuesOnCreation() {
        // Given: a new HttpStorageProperties object created with the default constructor
        final HttpStorageProperties properties = new HttpStorageProperties();

        // Then: the getters should return the predefined default values
        assertEquals(HttpStorageProperties.DEF_CONNECTION_TIMEOUT_SEC, properties.getConnectTimeout());
        assertEquals(HttpStorageProperties.DEF_SOCKET_TIMEOUT_SEC, properties.getSocketTimeout());
        assertEquals(HttpStorageProperties.DEF_MAX_RETRIES, properties.getMaxRetries());
        assertEquals(HttpStorageProperties.DEFAULT_HEADERS, properties.getHeaders());
    }

    @Test
    void should_set_and_get_all_properties_correctly() {
        // Given: a new properties object
        final HttpStorageProperties properties = new HttpStorageProperties();
        final String testUrl = "https://example.com/test";
        final int testConnectTimeout = 15;
        final int testSocketTimeout = 45;
        final int testMaxRetries = 5;

        // When: we set all properties using the public setters
        properties.setUrl(testUrl);
        properties.setConnectTimeout(testConnectTimeout);
        properties.setSocketTimeout(testSocketTimeout);
        properties.setMaxRetries(testMaxRetries);

        // Then: the getters should return the exact values that were set
        assertEquals(testUrl, properties.getUrl());
        assertEquals(testConnectTimeout, properties.getConnectTimeout());
        assertEquals(testSocketTimeout, properties.getSocketTimeout());
        assertEquals(testMaxRetries, properties.getMaxRetries());
    }

    @Test
    void shouldNormalizeRawHeadersMapFromBoxedProperties() {
        final HttpStorageProperties properties = new HttpStorageProperties();
        final Map<String, String> rawHeaders = new HashMap<>();
        rawHeaders.put("Authorization", "Bearer <token>");
        rawHeaders.put("Content-Type", "application/json");

        // Simulate the v3 boxed/proxy state before the typed getter runs its normalization logic.
        properties.setRaw("headers", rawHeaders);

        final Map<String, String> headers = properties.getHeaders();
        assertEquals("Bearer <token>", headers.get("Authorization"));
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals(2, headers.size());
        assertEquals(headers, properties.getRaw("headers"));
        assertTrue(headers instanceof HttpStorageProperties.HeaderMap);
        assertInstanceOf(Map.class, properties.getRaw("headers"));
        assertNotSame(rawHeaders, headers);
    }

    @Test
    void shouldStoreHeadersAsProxyWhenUsingSetter() {
        final HttpStorageProperties properties = new HttpStorageProperties();
        final Map<String, String> headers = Map.of(
                "Authorization", "Bearer exampleToken",
                "Content-Type", "application/json"
        );

        properties.setHeaders(headers);

        assertEquals(headers, properties.getHeaders());
        assertTrue(properties.getHeaders() instanceof HttpStorageProperties.HeaderMap);
        assertInstanceOf(Map.class, properties.getRaw("headers"));
        assertNotSame(headers, properties.getRaw("headers"));
    }

    @Test
    void shouldReturnDefaultsForInvalidRawValues() {
        final HttpStorageProperties properties = new HttpStorageProperties();

        properties.setRaw("headers", "invalid");

        assertEquals(HttpStorageProperties.DEFAULT_HEADERS, properties.getHeaders());
    }

    @Test
    void shouldDeserializeAllFieldsFromJson() {
        final HttpStorageProperties properties = jsonResourceToPropertiesOrFail("t01_testConvertAllFields");

        assertEquals("https://example.org", properties.getUrl());
        assertEquals(60, properties.getConnectTimeout());
        assertEquals(3600, properties.getSocketTimeout());

        final Map<String, String> headers = properties.getHeaders();
        assertEquals("Bearer <token>", headers.get("Authorization"));
        assertEquals("application/json", headers.get("Content-Type"));
        assertEquals(2, headers.size());
    }

    @Test
    void shouldPreserveHeadersWhenBoxingStoragePropertiesFromJson() {
        final String storageJson;
        try {
            storageJson = jsonResourceToStringOrFail("t05_testBoxStorageProperties");
        } catch (IOException e) {
            fail("Unable to convert json resource", e);
            return;
        }

        final NakshaStorage storage = JvmBoxingUtil.box(Platform.fromJSON(storageJson), NakshaStorage.class);
        assertNotNull(storage);

        final HttpStorageProperties properties = JvmBoxingUtil.box(storage.getProperties(), HttpStorageProperties.class);
        assertNotNull(properties);

        final Object rawHeaders = properties.getPath("headers");
        assertInstanceOf(Map.class, rawHeaders);
        assertFalse(rawHeaders instanceof HttpStorageProperties.HeaderMap);

        final Map<String, String> headers = properties.getHeaders();
        assertEquals("Bearer boxed-token", headers.get("Authorization"));
        assertEquals("demo", headers.get("X-Tenant"));
        assertFalse(headers.containsKey("Accept-Encoding"));
        assertEquals(2, headers.size());
        assertTrue(headers instanceof HttpStorageProperties.HeaderMap);
    }

    @Test
    void shouldDeserializeMissingValuesToDefaultsFromJson() {
        final HttpStorageProperties properties = jsonResourceToPropertiesOrFail("t02_testConvertMissingToNull");

        assertEquals("https://example.org", properties.getUrl());
        assertEquals(HttpStorageProperties.DEF_CONNECTION_TIMEOUT_SEC, properties.getConnectTimeout());
        assertEquals(HttpStorageProperties.DEF_SOCKET_TIMEOUT_SEC, properties.getSocketTimeout());
        assertEquals(HttpStorageProperties.DEF_MAX_RETRIES, properties.getMaxRetries());
        assertEquals(HttpStorageProperties.DEFAULT_HEADERS, properties.getHeaders());
    }

    @Test
    void shouldIgnoreExcessFieldsInJson() {
        assertDoesNotThrow(() -> jsonResourceToPropertiesOrFail("t03_testDontThrowOnExcessFields"));
    }

    @Test
    void shouldDeserializeMissingUrlAsNullInJson() {
        final HttpStorageProperties properties = jsonResourceToPropertiesOrFail("t04_testThrowOnMissingMandatory");

        assertNull(properties.getUrl());
        assertEquals(60, properties.getConnectTimeout());
        assertEquals(3600, properties.getSocketTimeout());
        assertEquals("Bearer <token>", properties.getHeaders().get("Authorization"));
        assertEquals("application/json", properties.getHeaders().get("Content-Type"));
    }

    @Test
    void shouldUseNormalizedHeadersMapInKeyProperties() {
        final HttpStorageProperties properties = new HttpStorageProperties();
        final Map<String, String> rawHeaders = new HashMap<>();
        rawHeaders.put("Authorization", "Bearer <token>");
        rawHeaders.put("Content-Type", "application/json");
        properties.setUrl("https://example.org");
        properties.setRaw("headers", rawHeaders);

        final RequestSender.KeyProperties fromProperties = RequestSender.KeyProperties.fromHttpStorageProperties("test-storage", properties);

        assertNotNull(fromProperties.getDefaultHeaders());
        assertTrue(fromProperties.getDefaultHeaders() instanceof HttpStorageProperties.HeaderMap);
        assertNotSame(rawHeaders, fromProperties.getDefaultHeaders());
        assertEquals("Bearer <token>", fromProperties.getDefaultHeaders().get("Authorization"));
        assertEquals("application/json", fromProperties.getDefaultHeaders().get("Content-Type"));
        assertEquals(2, fromProperties.getDefaultHeaders().size());
    }

    private HttpStorageProperties jsonResourceToPropertiesOrFail(String fileName) {
        try {
            String json = jsonResourceToStringOrFail(fileName);
            HttpStorageProperties properties = JvmJsonUtil.readJsonAs(json, HttpStorageProperties.class);
            assertNotNull(properties);
            return properties;
        } catch (IOException e) {
            fail("Unable to convert json resource", e);
            return null;
        }
    }

    private String jsonResourceToStringOrFail(String fileName) throws IOException {
        String resource = TEST_RESOURCE_DIR + fileName + ".json";

        try (InputStream testResourceStream = this.getClass().getResourceAsStream(resource)) {
            if (testResourceStream == null) {
                throw new IOException("Could not access " + resource + " resource");
            }
            return new String(testResourceStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
