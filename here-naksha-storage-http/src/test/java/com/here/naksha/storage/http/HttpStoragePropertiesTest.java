package com.here.naksha.storage.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class HttpStoragePropertiesTest {

    final static String TEST_RESOURCE_DIR = "/unit_test_data/HttpStorageProperties/";
    final static ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void t01_testConvertAllFields() throws IOException {
        var properties = convertResourceToProperties("t01_testConvertAllFields");

        assertEquals("https://example.org", properties.url);
        assertEquals(60, properties.connectTimeout);
        assertEquals(3600, properties.socketTimeout);

        assertEquals("Bearer <token>", properties.headers.get("Authorization"));
        assertEquals("application/json", properties.headers.get("Content-Type"));
        assertEquals(2, properties.headers.size());
    }

    @Test
    void t02_testConvertMissingToNull() throws IOException {
        var properties = convertResourceToProperties("t02_testConvertMissingToNull");

        assertEquals("https://example.org", properties.url);
        assertNull(properties.connectTimeout);
        assertNull(properties.socketTimeout);

        assertNull(properties.headers);
    }

    @Test
    void t03_testDontThrowOnExcessFields() {
        assertDoesNotThrow(
                () -> convertResourceToProperties("t03_testDontThrowOnExcessFields")
        );
    }

    @Test
    void t04_testThrowOnMissingMandatory() {
        assertThrows(
                MismatchedInputException.class,
                () -> convertResourceToProperties("t04_testThrowOnMissingMandatory")
        );
    }

    private HttpStorageProperties convertResourceToProperties(String fileName) throws IOException {
        InputStream testResourceStream = this.getClass().getResourceAsStream(TEST_RESOURCE_DIR + fileName + ".json");
        return objectMapper.readValue(testResourceStream, HttpStorageProperties.class);
    }
}