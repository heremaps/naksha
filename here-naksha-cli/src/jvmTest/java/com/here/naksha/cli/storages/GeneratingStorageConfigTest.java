package com.here.naksha.cli.storages;

import com.here.naksha.cli.validations.exceptions.FieldValidationException;
import naksha.base.JvmBoxingUtil;
import org.junit.jupiter.api.Test;

import static naksha.model.objects.NakshaFeature.PROPERTIES_KEY;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratingStorageConfigTest {
    GeneratingStorageConfig config = new GeneratingStorageConfig();

    @Test
    void shouldCreateAndInit() {
        // When
        Object raw = config.getRaw(PROPERTIES_KEY);

        // Then
        GeneratingStorageConfigProperties properties = JvmBoxingUtil.box(raw, GeneratingStorageConfigProperties.class);
        assertNotNull(properties);
    }

    @Test
    void shouldThrowWhenValidatingWithAbsentProperties() {
        // Given
        config.setRaw(PROPERTIES_KEY, null);

        // When & Then
        assertThrows(
                FieldValidationException.class,
                config::validateFields
        );
    }
}