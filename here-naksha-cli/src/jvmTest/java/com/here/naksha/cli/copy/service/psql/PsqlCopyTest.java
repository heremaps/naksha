package com.here.naksha.cli.copy.service.psql;

import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.CopyServiceException;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.test_containers.MapController;
import com.here.naksha.cli.test_containers.StorageController;
import com.here.naksha.cli.test_containers.TestContainersPsqlStoragePool;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class PsqlCopyTest {
    private CopyService copyService;
    private final String srcCollectionId = "srccolid";
    private final String targetCollectionId = "targetcolid";
    private SessionOptions sessionOptions;
    private MapController srcMapController;
    private MapController targetMapController;
    private StorageController srcStorageController;
    private StorageController targetStorageController;
    private final List<NakshaFeature> features = getSampleFeatures();

    @BeforeEach
    void beforeEach() {
        NakshaContext.currentContext().withAppId("testapp");
        sessionOptions = SessionOptions.from(NakshaContext.currentContext());
        copyService = new CopyService(new StorageProvider(), sessionOptions);
    }

    @Test
    void shouldCopyFeaturesBetweenMapsOnTheSameStorage() throws CopyServiceException {
        // Given: storage with maps and collections
        prepareMapsAndCollectionsOnOneStorage();

        // And: source copy element
        CopyElement srcCopyElement = getSrcCopyElement();

        // And: target copy element
        CopyElement targetCopyElement = getTargetCopyElement();

        // When: copying
        copyService.copy(srcCopyElement, targetCopyElement);

        // Then: target test collection contains features from source
        List<NakshaFeature> actualFeatures = targetMapController.readFeatures(targetCollectionId, sessionOptions);
        assertFeatures(actualFeatures);
    }

    @Test
    void shouldCopyFeaturesBetweenStorages() throws CopyServiceException {
        // Given: storages with maps and collections
        prepareMapsAndCollectionsOnDifferentStorages();

        // And: source copy element
        CopyElement srcCopyElement = getSrcCopyElement();

        // And: target copy element
        CopyElement targetCopyElement = getTargetCopyElement();

        // When: copying
        copyService.copy(srcCopyElement, targetCopyElement);

        // Then: target test collection contains features from source
        List<NakshaFeature> actualFeatures = targetMapController.readFeatures(targetCollectionId, sessionOptions);
        assertFeatures(actualFeatures);
    }

    private void assertFeatures(List<NakshaFeature> actualFeatures) {
        Assertions.assertEquals(features.size(), actualFeatures.size());
        Assertions.assertIterableEquals(
                features.stream().map(NakshaFeature::getId).toList(),
                actualFeatures.stream().map(NakshaFeature::getId).toList()
        );
    }

    private CopyElement getSrcCopyElement() {
        return new CopyElement.Builder(srcStorageController.getNakshaStorage(), srcCollectionId)
                .setMapId(srcMapController.getMapId())
                .build();
    }

    private CopyElement getTargetCopyElement() {
        return new CopyElement.Builder(targetStorageController.getNakshaStorage(), targetCollectionId)
                .setMapId(targetMapController.getMapId())
                .build();
    }

    private List<NakshaFeature> getSampleFeatures() {
        return List.of(
                new NakshaFeature("1"),
                new NakshaFeature("2")
        );
    }

    private MapController createMapWithEmptyCollection(StorageController storageController, String collectionId) {
        MapController mapController = storageController.getMapControllerOfUniqueMap(sessionOptions);
        mapController.addCollectionToTheMap(collectionId, sessionOptions);
        return mapController;
    }

    private void addFeaturesToCollection(MapController mapController, String collectionId, List<NakshaFeature> features) {
        mapController.addFeatures(collectionId, features, sessionOptions);
    }

    private void prepareMapsAndCollectionsOnOneStorage() {
        prepareSrcMap();

        targetStorageController = srcStorageController;
        targetMapController = createMapWithEmptyCollection(targetStorageController, targetCollectionId);
    }

    private void prepareMapsAndCollectionsOnDifferentStorages() {
        prepareSrcMap();

        targetStorageController = TestContainersPsqlStoragePool.getInstance(1);
        targetMapController = createMapWithEmptyCollection(targetStorageController, targetCollectionId);
    }

    private void prepareSrcMap() {
        srcStorageController = TestContainersPsqlStoragePool.getInstance(0);
        srcMapController = createMapWithEmptyCollection(srcStorageController, srcCollectionId);
        addFeaturesToCollection(srcMapController, srcCollectionId, features);
    }
}
