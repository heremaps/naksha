package com.here.naksha.cli.storages;

import com.here.naksha.cli.TestUtils;
import com.here.naksha.lib.core.models.geojson.HQuad;
import naksha.base.JvmList;
import naksha.geo.SpBoundingBox;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static naksha.model.util.ResultHelper.extractResponseItems;
import static org.junit.jupiter.api.Assertions.*;

class GeneratingStorageTest {
    SessionOptions sessionOptions = new SessionOptions();

    @BeforeAll
    static void beforeAll() {
        NakshaContext.currentContext().withAppId("test");
    }

    @ParameterizedTest
    @MethodSource
    void shouldRead(int countOfFeatures, JvmList tileIds, String tileIdsCsv) {
        // Given: storage
        GeneratingStorage storage = new GeneratingStorage();

        // And: config with count and tileIds
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.getProperties()
                .withCount(countOfFeatures)
                .withTileIds(tileIds)
                .withTileIdsCsvFilePath(tileIdsCsv);

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

        // And: features in tileIds from sources (list and csv file)
        List<String> expectedTileIds = getExpectedTileIdsFromSources(tileIds, tileIdsCsv);
        assertFeaturesInTiles(nakshaFeatureList, expectedTileIds);
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

    private static Stream<Arguments> shouldRead() {
        String csvFileName = "tile_ids.csv";
        String absolutePathCsvFile = TestUtils.getAbsolutePathOfResource(csvFileName);

        return Stream.of(
                Arguments.of(
                        0,
                        new JvmList("122013100013", "122013100020"),
                        absolutePathCsvFile
                ),
                Arguments.of(
                        1,
                        new JvmList("122013100013", "122013100020"),
                        absolutePathCsvFile
                ),
                Arguments.of(
                        50,
                        new JvmList("122013100013", "122013100020"),
                        absolutePathCsvFile
                ),
                Arguments.of(
                        2137,
                        new JvmList("122013100013", "122013100020", "122013100021"),
                        null
                ),
                Arguments.of(
                        7321,
                        null,
                        absolutePathCsvFile
                )
        );
    }

    private List<String> getExpectedTileIdsFromSources(JvmList tileIds, String tileIdsCsv) {
        List<String> features = new ArrayList<>();

        if (tileIdsCsv != null) {
            features.addAll(assertDoesNotThrow(() -> Files.readAllLines(Path.of(tileIdsCsv))));
        }

        if (tileIds != null) {
            tileIds.forEach(tileId -> features.add((String) tileId));
        }

        return features;
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

    private void assertFeaturesInTiles(List<NakshaFeature> features, List<String> tileIds) {
        List<SpBoundingBox> tilesBboxes = tileIds.stream()
                .map(tileId -> new HQuad(tileId, true).getBoundingBox())
                .toList();

        assertTrue(
                features
                        .stream()
                        .allMatch(feature -> isFeatureInBboxes(feature, tilesBboxes)),
                "All features should be in the given tiles!"
        );
    }
}