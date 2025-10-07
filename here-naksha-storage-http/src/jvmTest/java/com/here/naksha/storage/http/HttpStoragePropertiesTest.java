package com.here.naksha.storage.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpStoragePropertiesTest {

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
}