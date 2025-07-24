package com.here.naksha.cli.copy.service.psql;

import com.here.naksha.cli.TestContainersPsqlStorage;
import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.CopyServiceException;
import com.here.naksha.cli.copy.service.StorageProvider;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

class PsqlCopyTest {
    private static CopyService copyService;
    private static TestContainersPsqlStorage testContainersPsqlStorage;
    private static final String SRC_MAP_ID = "srcmapid";
    private static final String SRC_COLLECTION_ID = "srccolid";
    private static final String TARGET_MAP_ID = "targetmapid";
    private static final String TARGET_COLLECTION_ID = "targetcolid";

    @BeforeAll
    static void beforeAll() {
        NakshaContext.currentContext().withAppId("app");
        SessionOptions sessionOptions = SessionOptions.from(NakshaContext.currentContext());

        copyService = new CopyService(new StorageProvider(), sessionOptions);

        testContainersPsqlStorage = initAndGetTestContainersPsqlStorage(sessionOptions);
    }

    @AfterAll
    static void afterAll() {
        testContainersPsqlStorage.stop();
    }

    @Test
    void shouldCopyBetweenMaps() throws CopyServiceException {
        // Given: source copy element
        CopyElement srcCopyElement = getSrcCopyElement();

        // And: target copy element
        CopyElement targetCopyElement = getTargetCopyElement();

        // And: sample features in source test collection
        List<NakshaFeature> features = getSampleFeatures();
        testContainersPsqlStorage.addFeatures(SRC_MAP_ID, SRC_COLLECTION_ID, features);

        // When: copying
        copyService.copy(srcCopyElement, targetCopyElement);

        // Then: target test contains features from source
        List<NakshaFeature> actualFeatures = testContainersPsqlStorage.readFeatures(targetCopyElement);
        Assertions.assertEquals(features.size(), actualFeatures.size());
    }

    private CopyElement getSrcCopyElement() {
        NakshaStorage srcNakshaStorage = testContainersPsqlStorage.getNakshaStorage();
        return new CopyElement.Builder(srcNakshaStorage, SRC_COLLECTION_ID)
                .setMapId(SRC_MAP_ID)
                .build();
    }

    private CopyElement getTargetCopyElement() {
        NakshaStorage targetNakshaStorage = testContainersPsqlStorage.getNakshaStorage();
        return new CopyElement.Builder(targetNakshaStorage, TARGET_COLLECTION_ID)
                .setMapId(TARGET_MAP_ID)
                .build();
    }

    private List<NakshaFeature> getSampleFeatures() {
        return List.of(
                new NakshaFeature("1"),
                new NakshaFeature("2")
        );
    }

    private static TestContainersPsqlStorage initAndGetTestContainersPsqlStorage(SessionOptions sessionOptions) {
        testContainersPsqlStorage = new TestContainersPsqlStorage(sessionOptions);
        testContainersPsqlStorage.start();
        testContainersPsqlStorage.addMapToTheStorage(SRC_MAP_ID);
        testContainersPsqlStorage.addMapToTheStorage(TARGET_MAP_ID);
        testContainersPsqlStorage.addCollectionToTheStorage(SRC_COLLECTION_ID, SRC_MAP_ID);
        testContainersPsqlStorage.addCollectionToTheStorage(TARGET_COLLECTION_ID, TARGET_MAP_ID);
        return testContainersPsqlStorage;
    }
}
