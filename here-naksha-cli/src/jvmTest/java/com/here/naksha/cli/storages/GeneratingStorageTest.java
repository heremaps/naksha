package com.here.naksha.cli.storages;

import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static naksha.model.util.ResultHelper.extractResponseItems;
import static org.junit.jupiter.api.Assertions.*;

class GeneratingStorageTest {
    SessionOptions sessionOptions = new SessionOptions();

    @BeforeAll
    static void beforeAll() {
        NakshaContext.currentContext().withAppId("test");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50})
    void shouldRead(int countOfFeatures) {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = configWithGivenCountOfFeatures(countOfFeatures);

        // And: init storage with config
        storage.initStorage(config, false, false);

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> nakshaFeatureList = extractResponseItems((SuccessResponse) response, NakshaFeature.class);
        assertEquals(countOfFeatures, nakshaFeatureList.size());
    }

    @Test
    void shouldFailWhenWrite() {
        // Given
        GeneratingStorage storage = new GeneratingStorage();

        // When: create write session
        NakshaException exception = assertThrows(NakshaException.class, () -> storage.newWriteSession(sessionOptions));

        // Then: fail
        assertEquals(NakshaError.UNSUPPORTED_OPERATION, exception.getError().getCode());
        assertEquals("Read-only storage!", exception.getMessage());
    }

    private GeneratingStorageConfig configWithGivenCountOfFeatures(int count) {
        GeneratingStorageConfig config = new GeneratingStorageConfig();

        NakshaProperties nakshaProperties = new NakshaProperties();
        nakshaProperties.setRaw("count", count);

        GeneratingStorageConfigProperties properties = new GeneratingStorageConfigProperties(nakshaProperties);

        config.init(properties);

        return config;
    }
}