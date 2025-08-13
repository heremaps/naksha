package com.here.naksha.cli.storages;

import com.here.naksha.cli.parsers.JsonFileParser;
import com.here.naksha.lib.core.models.geojson.HQuad;
import naksha.base.StringList;
import naksha.geo.SpBoundingBox;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.here.naksha.cli.TestUtils.getAbsolutePathOfResource;
import static naksha.model.util.ResultHelper.extractResponseItems;
import static org.junit.jupiter.api.Assertions.*;

class GeneratingStorageTest {
    private final JsonFileParser jsonFileParser = new JsonFileParser();
    private final SessionOptions sessionOptions = new SessionOptions();
    private final String defaultIdsPrefix = GeneratingStorageService.DEFAULT_IDS_PREFIX;

    @BeforeAll
    static void beforeAll() {
        NakshaContext.currentContext().withAppId("test");
    }

    @Test
    void shouldReadWithFeatureTemplate() {
        // Given: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        int count = 10;
        StringList tileIds = StringList.of("122013100023", "122013100000", "122013100021");
        String featureTemplateFile = getSampleFeatureTemplateFile();
        config.getProperties()
                .withCount(count)
                .withTileIds(tileIds)
                .withFeatureTemplateFilePath(featureTemplateFile);

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // And: template
        NakshaFeature featureTemplate = loadFeatureTemplate(featureTemplateFile);

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        SuccessResponse successResponse = assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = assertFeaturesReceived(successResponse, count);

        // And: features properly generated
        assertFeaturesProperlyGenerated(generatedFeatures, tileIds, featureTemplate, defaultIdsPrefix);
    }

    @Test
    void shouldReadWithCustomIdsPrefix() {
        // Given: custom idsPrefix
        String idsPrefix = defaultIdsPrefix + "test";

        // And: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        int count = 10;
        StringList tileIds = StringList.of("122013100023", "122013100000", "122013100021");
        config.getProperties()
                .withCount(count)
                .withTileIds(tileIds)
                .withIdsPrefix(idsPrefix);

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // And: empty template because it is not provided
        NakshaFeature featureTemplate = new NakshaFeature();

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        SuccessResponse successResponse = assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = assertFeaturesReceived(successResponse, count);

        // And: features properly generated
        assertFeaturesProperlyGenerated(generatedFeatures, tileIds, featureTemplate, idsPrefix);
    }

    @ParameterizedTest
    @MethodSource
    void shouldReadWithTileIdsList(int countOfFeatures, StringList tileIds) {
        // Given: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.getProperties()
                .withCount(countOfFeatures)
                .withTileIds(tileIds);

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // And: empty template because it is not provided
        NakshaFeature featureTemplate = new NakshaFeature();

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        SuccessResponse successResponse = assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = assertFeaturesReceived(successResponse, countOfFeatures);

        // And: features properly generated
        assertFeaturesProperlyGenerated(generatedFeatures, tileIds, featureTemplate, defaultIdsPrefix);
    }

    @ParameterizedTest
    @MethodSource
    void shouldReadWithTileIdsCsv(int countOfFeatures, String tileIdsFile) {
        // Given: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.getProperties()
                .withCount(countOfFeatures)
                .withTileIdsCsvFilePath(tileIdsFile);

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // And: empty template because it is not provided
        NakshaFeature featureTemplate = new NakshaFeature();

        // And:
        List<String> tileIds = getExpectedTileIdsFromSource(tileIdsFile);

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        SuccessResponse successResponse = assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = assertFeaturesReceived(successResponse, countOfFeatures);

        // And: features properly generated
        assertFeaturesProperlyGenerated(generatedFeatures, tileIds, featureTemplate, defaultIdsPrefix);
    }

    @Test
    void shouldFailWhenTileIdsAreNotProvided() {
        // Given: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(null)
                .withTileIdsCsvFilePath(null);

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        assertErrorMessageAndCode(exception, "Provide tileIds in the config properties.", NakshaError.ILLEGAL_ARGUMENT);
    }

    @Test
    void shouldFailWhenMoreThanOneSourceOfTileIds() {
        // Given: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(StringList.of("0"))
                .withTileIdsCsvFilePath(getSampleFeatureTemplateFile());

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        assertErrorMessageAndCode(exception, "Provide only one source of tileIds.", NakshaError.ILLEGAL_ARGUMENT);
    }

    @Test
    void shouldFailWhenTileIdsListIsEmpty() {
        // Given: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(StringList.of());

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        assertErrorMessageAndCode(exception, "Should be at least one tileId.", NakshaError.ILLEGAL_ARGUMENT);
    }

    @Test
    void shouldFailWhenProblemWithTileIdsFile() {
        // Given: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(null)
                .withTileIdsCsvFilePath(getInvalidFile());

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        assertErrorMessageAndCode(exception, "Problem while loading tileIds from CSV file!", NakshaError.EXCEPTION);
    }

    @Test
    void shouldFailWhenProblemWithFeatureTemplateFile() {
        // Given: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withFeatureTemplateFilePath(getInvalidFile());

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        assertErrorMessageAndCode(exception, "Problem while loading the feature template!", NakshaError.EXCEPTION);
    }

    @Test
    void shouldFailWhenCountIsNotProvided() {
        // Given: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withCount(null);

        // And: storage
        GeneratingStorage storage = generatingStorageWithConfig(config);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        assertErrorMessageAndCode(exception, "Provide count in the config properties.", NakshaError.ILLEGAL_ARGUMENT);
    }

    @Test
    void shouldFailWhenWrite() {
        // Given
        GeneratingStorage storage = new GeneratingStorage();

        // When: create write session
        NakshaException exception = assertThrows(NakshaException.class, () -> storage.newWriteSession(sessionOptions));

        // Then: fail
        assertErrorMessageAndCode(exception, "Read-only storage!", NakshaError.UNSUPPORTED_OPERATION);
    }

    private static Stream<Arguments> shouldReadWithTileIdsList() {
        return Stream.of(
                Arguments.of(
                        0,
                        new StringList("122013100013", "122013100020")
                ),
                Arguments.of(
                        1,
                        new StringList("122013100013", "122013100020")
                ),
                Arguments.of(
                        50,
                        new StringList("122013100013", "122013100020")
                ),
                Arguments.of(
                        2137,
                        new StringList("122013100023", "122013100000", "122013100021")
                )
        );
    }

    private static Stream<Arguments> shouldReadWithTileIdsCsv() {
        String tileIdsCsvFileName = "tile_ids.csv";
        String absolutePathTileIdsCsvFile = getAbsolutePathOfResource(tileIdsCsvFileName);
        return Stream.of(
                Arguments.of(
                        0,
                        absolutePathTileIdsCsvFile
                ),
                Arguments.of(
                        1,
                        absolutePathTileIdsCsvFile
                ),
                Arguments.of(
                        50,
                        absolutePathTileIdsCsvFile
                ),
                Arguments.of(
                        2137,
                        absolutePathTileIdsCsvFile
                )
        );
    }

    private void assertErrorMessageAndCode(NakshaException exception, String message, String code) {
        NakshaError nakshaError = exception.getError();
        assertEquals(code, exception.getError().getCode());
        assertEquals(message, nakshaError.getMsg());
    }

    private GeneratingStorageConfig getSampleConfig() {
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.getProperties()
                .withCount(100)
                .withTileIds(StringList.of("122013100023", "122013100000", "122013100021"));
        return config;
    }

    private void assertFeatureUseTemplate(NakshaFeature generatedFeature, NakshaFeature featureTemplate, String message) {
        assertPropertiesDeepEquals(featureTemplate, generatedFeature, message);
        assertEquals(featureTemplate.getType(), generatedFeature.getType(), message);
        assertEquals(featureTemplate.getMomType(), generatedFeature.getMomType(), message);
        assertEquals(featureTemplate.getTitle(), generatedFeature.getTitle(), message);
        assertEquals(featureTemplate.getDescription(), generatedFeature.getDescription(), message);
    }

    private void assertPropertiesDeepEquals(NakshaFeature expected, NakshaFeature actual, String message) {
        NakshaProperties expectedProperties = expected.getProperties();
        assertTrue(
                expectedProperties.contentDeepEquals(actual.getProperties()),
                "Properties are not equal." + message
        );
    }

    private void assertIsGeneratedId(String id, String expectedIdsPrefix, String message) {
        assertTrue(id.startsWith(expectedIdsPrefix), message);
    }

    private String getInvalidFile() {
        return "";
    }

    private NakshaFeature loadFeatureTemplate(String featureTemplateFilePath) {
        Path path = Path.of(featureTemplateFilePath);
        return assertDoesNotThrow(() -> jsonFileParser.parse(path, NakshaFeature.class));
    }

    private List<String> getExpectedTileIdsFromSource(String tileIdsCsv) {
        return assertDoesNotThrow(() -> Files.readAllLines(Path.of(tileIdsCsv)));
    }

    private String getSampleFeatureTemplateFile() {
        String featureTemplateFileName = "sample_topology_feature.json";
        return getAbsolutePathOfResource(featureTemplateFileName);
    }

    private boolean doesBoundingBoxContainFeature(SpBoundingBox boundingBox, NakshaFeature feature) {
        SpBoundingBox featureBbox = new SpBoundingBox(feature.getGeometry());
        if (featureBbox.getMinLatitude() < boundingBox.getMinLatitude()) {
            return false;
        }
        if (featureBbox.getMinLongitude() < boundingBox.getMinLongitude()) {
            return false;
        }
        if (featureBbox.getMaxLatitude() > boundingBox.getMaxLatitude()) {
            return false;
        }
        if (featureBbox.getMaxLongitude() > boundingBox.getMaxLongitude()) {
            return false;
        }
        return true;
    }

    private boolean isFeatureInBboxes(NakshaFeature feature, List<SpBoundingBox> boundingBoxes) {
        return boundingBoxes
                .stream()
                .anyMatch(tileBbox -> doesBoundingBoxContainFeature(tileBbox, feature));
    }

    private void assertFeatureInTiles(NakshaFeature feature, List<String> tileIds, String message) {
        List<SpBoundingBox> tilesBboxes = tileIds.stream()
                .map(tileId -> new HQuad(tileId, true).getBoundingBox())
                .toList();

        assertTrue(
                isFeatureInBboxes(feature, tilesBboxes),
                message
        );
    }


    private GeneratingStorage generatingStorageWithConfig(GeneratingStorageConfig config) {
        GeneratingStorage storage = new GeneratingStorage();
        storage.initStorage(config, false, false);
        return storage;
    }

    private void assertFeaturesProperlyGenerated(
            List<NakshaFeature> generatedFeatures,
            List<String> tileIds,
            NakshaFeature featureTemplate,
            String idsPrefix
    ) {
        for (NakshaFeature generatedFeature : generatedFeatures) {
            assertIsGeneratedId(
                    generatedFeature.getId(),
                    idsPrefix,
                    "Id should have prefix. But feature (id: %s) caused fail.".formatted(generatedFeature.getId())
            );
            assertFeatureInTiles(
                    generatedFeature,
                    tileIds,
                    "Feature(id: %s) is not in the tiles.".formatted(generatedFeature.getId())
            );
            assertNull(generatedFeature.getReferencePoint(),
                    "Reference point should be null. But feature (id: %s) caused fail.".formatted(generatedFeature.getId())
            );
            assertFeatureUseTemplate(
                    generatedFeature,
                    featureTemplate,
                    "Feature should use template. But feature (id: %s) caused fail.".formatted(generatedFeature.getId())
            );
        }
    }

    private List<NakshaFeature> assertFeaturesReceived(SuccessResponse response, int count) {
        List<NakshaFeature> generatedFeatures = extractResponseItems(response, NakshaFeature.class);
        assertEquals(count, generatedFeatures.size());
        return generatedFeatures;
    }

}