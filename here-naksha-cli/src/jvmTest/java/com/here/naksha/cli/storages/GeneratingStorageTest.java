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
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        int count = 10;
        StringList tileIds = StringList.of("122013100023", "122013100000", "122013100021");
        String featureTemplateFile = getSampleFeatureTemplateFile();
        config.getProperties()
                .withCount(count)
                .withTileIds(tileIds)
                .withFeatureTemplateFilePath(featureTemplateFile);

        // And: init storage with config
        storage.initStorage(config, false, false);

        // And: template
        NakshaFeature featureTemplate = loadFeatureTemplate(featureTemplateFile);

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = extractResponseItems((SuccessResponse) response, NakshaFeature.class);
        assertEquals(count, generatedFeatures.size());

        // And: check features
        for (NakshaFeature generatedFeature : generatedFeatures) {
            assertIsGeneratedId(generatedFeature.getId(), defaultIdsPrefix);
            assertFeatureInTiles(generatedFeature, tileIds);
            assertNull(generatedFeature.getReferencePoint(), "Reference point should be null. But feature (id: %s) caused fail.");
            assertFeatureUseTemplate(generatedFeature, featureTemplate);
        }
    }

    @Test
    void shouldReadWithCustomIdsPrefix() {
        // Given: custom idsPrefix
        String idsPrefix = defaultIdsPrefix + "test";

        // And: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        int count = 10;
        StringList tileIds = StringList.of("122013100023", "122013100000", "122013100021");
        config.getProperties()
                .withCount(count)
                .withTileIds(tileIds)
                .withIdsPrefix(idsPrefix);

        // And: init storage with config
        storage.initStorage(config, false, false);

        // And: empty template because it is not provided
        NakshaFeature featureTemplate = new NakshaFeature();

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = extractResponseItems((SuccessResponse) response, NakshaFeature.class);
        assertEquals(count, generatedFeatures.size());

        // And: check features
        for (NakshaFeature generatedFeature : generatedFeatures) {
            assertIsGeneratedId(generatedFeature.getId(), idsPrefix);
            assertFeatureInTiles(generatedFeature, tileIds);
            assertNull(generatedFeature.getReferencePoint(), "Reference point should be null. But feature (id: %s) caused fail.");
            assertFeatureUseTemplate(generatedFeature, featureTemplate);
        }
    }

    @ParameterizedTest
    @MethodSource
    void shouldReadWithTileIdsList(int countOfFeatures, StringList tileIds) {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.getProperties()
                .withCount(countOfFeatures)
                .withTileIds(tileIds);

        // And: init storage with config
        storage.initStorage(config, false, false);

        // And: empty template because it is not provided
        NakshaFeature featureTemplate = new NakshaFeature();

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = extractResponseItems((SuccessResponse) response, NakshaFeature.class);
        assertEquals(countOfFeatures, generatedFeatures.size());

        // And: check features
        for (NakshaFeature generatedFeature : generatedFeatures) {
            assertIsGeneratedId(generatedFeature.getId(), defaultIdsPrefix);
            assertFeatureInTiles(generatedFeature, tileIds);
            assertNull(generatedFeature.getReferencePoint(), "Reference point should be null. But feature (id: %s) caused fail.");
            assertFeatureUseTemplate(generatedFeature, featureTemplate);
        }
    }

    @ParameterizedTest
    @MethodSource
    void shouldReadWithTileIdsCsv(int countOfFeatures, String tileIdsFile) {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.getProperties()
                .withCount(countOfFeatures)
                .withTileIdsCsvFilePath(tileIdsFile);

        // And: init storage with config
        storage.initStorage(config, false, false);

        // And: empty template because it is not provided
        NakshaFeature featureTemplate = new NakshaFeature();

        // And:
        List<String> tileIds = getExpectedTileIdsFromSource(tileIdsFile);

        // When: read features
        Response response = storage.useReadSession(sessionOptions, reader ->
                reader.execute(new ReadFeatures())
        );

        // Then: success response
        assertInstanceOf(SuccessResponse.class, response);

        // And: features received
        List<NakshaFeature> generatedFeatures = extractResponseItems((SuccessResponse) response, NakshaFeature.class);
        assertEquals(countOfFeatures, generatedFeatures.size());

        // And: check features
        for (NakshaFeature generatedFeature : generatedFeatures) {
            assertIsGeneratedId(generatedFeature.getId(), defaultIdsPrefix);
            assertFeatureInTiles(generatedFeature, tileIds);
            assertNull(generatedFeature.getReferencePoint(), "Reference point should be null. But feature (id: %s) caused fail.");
            assertFeatureUseTemplate(generatedFeature, featureTemplate);
        }
    }


    @Test
    void shouldFailWhenTileIdsAreNotProvided() {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(null)
                .withTileIdsCsvFilePath(null);

        // And: init storage with config
        storage.initStorage(config, false, false);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        NakshaError nakshaError = exception.getError();
        assertEquals("Provide tileIds in the config properties.", nakshaError.getMsg());
    }

    @Test
    void shouldFailWhenMoreThanOneSourceOfTileIds() {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(StringList.of("0"))
                .withTileIdsCsvFilePath(getSampleFeatureTemplateFile());

        // And: init storage with config
        storage.initStorage(config, false, false);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        NakshaError nakshaError = exception.getError();
        assertEquals("Provide only one source of tileIds.", nakshaError.getMsg());
    }

    @Test
    void shouldFailWhenTileIdsListIsEmpty() {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(StringList.of());

        // And: init storage with config
        storage.initStorage(config, false, false);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        NakshaError nakshaError = exception.getError();
        assertEquals("Should be at least one tileId.", nakshaError.getMsg());
    }

    @Test
    void shouldFailWhenProblemWithTileIdsFile() {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withTileIds(null)
                .withTileIdsCsvFilePath(getInvalidFile());

        // And: init storage with config
        storage.initStorage(config, false, false);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        NakshaError nakshaError = exception.getError();
        assertEquals("Problem while loading tileIds from CSV file!", nakshaError.getMsg());
    }

    @Test
    void shouldFailWhenProblemWithFeatureTemplateFile() {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withFeatureTemplateFilePath(getInvalidFile());

        // And: init storage with config
        storage.initStorage(config, false, false);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        NakshaError nakshaError = exception.getError();
        assertEquals("Problem while loading the feature template!", nakshaError.getMsg());
    }

    @Test
    void shouldFailWhenCountIsNotProvided() {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config
        GeneratingStorageConfig config = getSampleConfig();
        config.getProperties()
                .withCount(null);

        // And: init storage with config
        storage.initStorage(config, false, false);

        // When: read features
        NakshaException exception = assertThrows(
                NakshaException.class, () -> storage.useReadSession(sessionOptions, reader ->
                        reader.execute(new ReadFeatures())
                ));

        // Then:
        NakshaError nakshaError = exception.getError();
        assertEquals("Provide count in the config properties.", nakshaError.getMsg());
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

    private GeneratingStorageConfig getSampleConfig() {
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.getProperties()
                .withCount(100)
                .withTileIds(StringList.of("122013100023", "122013100000", "122013100021"));
        return config;
    }

    private void assertFeatureUseTemplate(NakshaFeature generatedFeature, NakshaFeature featureTemplate) {
        assertPropertiesDeepEquals(featureTemplate, generatedFeature);
        assertEquals(featureTemplate.getType(), generatedFeature.getType());
        assertEquals(featureTemplate.getMomType(), generatedFeature.getMomType());
        assertEquals(featureTemplate.getTitle(), generatedFeature.getTitle());
        assertEquals(featureTemplate.getDescription(), generatedFeature.getDescription());
    }

    private void assertPropertiesDeepEquals(NakshaFeature expected, NakshaFeature actual) {
        NakshaProperties expectedProperties = expected.getProperties();
        assertTrue(
                expectedProperties.contentDeepEquals(actual.getProperties()),
                "Properties should be deep equal to template's properties. But feature (id: %s) caused fail."
                        .formatted(actual.getId())
        );
    }

    private void assertIsGeneratedId(String id, String expectedIdsPrefix) {
        assertTrue(
                id.length() > expectedIdsPrefix.length(),
                "Length of id (%s) should be > than expected prefix.".formatted(id)
        );
        String prefix = id.substring(0, expectedIdsPrefix.length());
        assertEquals(expectedIdsPrefix, prefix);
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

    private void assertFeatureInTiles(NakshaFeature feature, List<String> tileIds) {
        List<SpBoundingBox> tilesBboxes = tileIds.stream()
                .map(tileId -> new HQuad(tileId, true).getBoundingBox())
                .toList();

        assertTrue(
                isFeatureInBboxes(feature, tilesBboxes),
                "Feature(id: %s) is not in the tiles.".formatted(feature.getId())
        );
    }
}