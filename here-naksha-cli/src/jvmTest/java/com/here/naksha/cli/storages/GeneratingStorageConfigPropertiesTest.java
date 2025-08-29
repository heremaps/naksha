package com.here.naksha.cli.storages;

import com.here.naksha.cli.validations.exceptions.FieldValidationException;
import naksha.base.JvmList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.here.naksha.cli.storages.GeneratingStorageConfigProperties.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratingStorageConfigPropertiesTest {
    GeneratingStorageConfigProperties configProperties = new GeneratingStorageConfigProperties();

    @ParameterizedTest
    @MethodSource
    void shouldThrowWhenValidatingWithInvalidCountField(Object invalidCount) {
        // Given
        configProperties.setRaw(COUNT_KEY, invalidCount);

        // When & Then
        assertThrowsFieldValidationExceptionOnValidate();
    }

    @ParameterizedTest
    @MethodSource
    void shouldThrowWhenValidatingWithInvalidTileIdsField(Object invalidTileIds) {
        // Given
        configProperties.setRaw(TILE_IDS_KEY, invalidTileIds);

        // When & Then
        assertThrowsFieldValidationExceptionOnValidate();
    }

    @ParameterizedTest
    @MethodSource("requireStringOrNullInvalidCases")
    void shouldThrowWhenValidatingWithInvalidIdsPrefixField(Object invalidIdsPrefix) {
        // Given
        configProperties.setRaw(IDS_PREFIX_KEY, invalidIdsPrefix);

        // When & Then
        assertThrowsFieldValidationExceptionOnValidate();
    }

    @ParameterizedTest
    @MethodSource("requireStringOrNullInvalidCases")
    void shouldThrowWhenValidatingWithInvalidTileIdsCsvFilePath(Object invalidTileIdsCsvFilePath) {
        // Given
        configProperties.setRaw(TILE_IDS_CSV_FILE_PATH_KEY, invalidTileIdsCsvFilePath);

        // When & Then
        assertThrowsFieldValidationExceptionOnValidate();
    }

    @ParameterizedTest
    @MethodSource("requireStringOrNullInvalidCases")
    void shouldThrowWhenValidatingWithInvalidFeatureTemplateFilePath(Object invalidFeatureTemplateFilePath) {
        // Given
        configProperties.setRaw(FEATURE_TEMPLATE_FILE_PATH_KEY, invalidFeatureTemplateFilePath);

        // When & Then
        assertThrowsFieldValidationExceptionOnValidate();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10})
    void shouldThrowWhenSettingInvalidCount(int invalidCount) {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> configProperties.setCount(invalidCount)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10})
    void shouldThrowWhenWithInvalidCount(int invalidCount) {
        // When & Then
        assertThrows(
                IllegalArgumentException.class,
                () -> configProperties.withCount(invalidCount)
        );
    }

    private static Stream<Arguments> requireStringOrNullInvalidCases() {
        return Stream.of(
                Arguments.of(new Object()),
                Arguments.of(321)
        );
    }

    private static Stream<Arguments> shouldThrowWhenValidatingWithInvalidTileIdsField() {
        return Stream.of(
                Arguments.of(new Object()),
                Arguments.of(new JvmList("02110", 1)),
                Arguments.of(new JvmList(321, 11)),
                Arguments.of(new JvmList(new Object(), new Object()))
        );
    }

    private static Stream<Arguments> shouldThrowWhenValidatingWithInvalidCountField() {
        return Stream.of(
                Arguments.of(-1),
                Arguments.of(-10),
                Arguments.of(new Object())
        );
    }

    private void assertThrowsFieldValidationExceptionOnValidate() {
        assertThrows(
                FieldValidationException.class,
                configProperties::validateFields
        );
    }
}