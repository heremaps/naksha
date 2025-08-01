package com.here.naksha.cli.copy.service.psql;

import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.CopyServiceException;
import com.here.naksha.cli.copy.service.StorageProvider;
import com.here.naksha.cli.test_containers.TestContainersPsqlStoragePool;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaMap;
import naksha.model.request.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.here.naksha.cli.test_containers.TestContainersPsqlStoragePool.InstanceIndex;
import static naksha.model.RandomFeatures.randomFeatures;
import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.RequestHelper.createWriteCollectionsRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;

class PsqlCopyTest {
    private CopyService copyService;
    private final String srcCollectionId = "srccolid";
    private final String targetCollectionId = "targetcolid";
    private SessionOptions sessionOptions;

    @BeforeEach
    void beforeEach() {
        NakshaContext.currentContext().withAppId("testapp");
        sessionOptions = SessionOptions.from(NakshaContext.currentContext());
        copyService = new CopyService(new StorageProvider(), sessionOptions);
    }

    @Test
    void shouldCopyFeaturesBetweenMapsOnTheSameStorage() throws CopyServiceException {
        // Given: the same storage for source and target
        IStorage storage = TestContainersPsqlStoragePool.getRunningContainer(InstanceIndex.FIRST_INSTANCE)
                .getStorage();

        // Given: prepared source
        CopyElement source = createMapWithEmptyCollection(storage, srcCollectionId);

        // And: predefined features
        List<NakshaFeature> sourceFeatures = randomFeatures(100);
        addFeatures(storage, source.getMapId(), source.getCollectionId(), sourceFeatures, sessionOptions);

        // And: prepared target
        CopyElement target = createMapWithEmptyCollection(storage, targetCollectionId);

        // When: copying
        copyService.copy(source, target);

        // And
        List<NakshaFeature> targetFeatures = readFeatures(
                storage, target.getMapId(), target.getCollectionId(), sessionOptions
        );

        // Then: target collection contains features from source
        assertSameFeatures(sourceFeatures, targetFeatures);
    }

    @Test
    void shouldCopyFeaturesBetweenStorages() throws CopyServiceException {
        // Given: prepared source
        IStorage sourceStorage = TestContainersPsqlStoragePool.getRunningContainer(InstanceIndex.FIRST_INSTANCE)
                .getStorage();
        CopyElement source = createMapWithEmptyCollection(sourceStorage, srcCollectionId);

        // And: predefined features
        List<NakshaFeature> sourceFeatures = randomFeatures(100);
        addFeatures(sourceStorage, source.getMapId(), source.getCollectionId(), sourceFeatures, sessionOptions);

        // And: prepared target
        IStorage targetStorage = TestContainersPsqlStoragePool.getRunningContainer(InstanceIndex.SECOND_INSTANCE)
                .getStorage();
        CopyElement target = createMapWithEmptyCollection(targetStorage, targetCollectionId);

        // When: copying
        copyService.copy(source, target);

        // And
        List<NakshaFeature> targetFeatures = readFeatures(
                targetStorage, target.getMapId(), target.getCollectionId(), sessionOptions
        );

        // Then: target collection contains features from source
        assertSameFeatures(sourceFeatures, targetFeatures);
    }

    private void assertSameFeatures(List<NakshaFeature> expectedFeatures, List<NakshaFeature> actualFeatures) {
        Assertions.assertEquals(expectedFeatures.size(), actualFeatures.size());
        Assertions.assertIterableEquals(
                expectedFeatures.stream().map(NakshaFeature::getId).sorted().toList(),
                actualFeatures.stream().map(NakshaFeature::getId).sorted().toList()
        );
    }

    private CopyElement createMapWithEmptyCollection(IStorage storage, String collectionId) {
        String mapId = createUniqueMap(storage, sessionOptions);
        addCollectionToTheMap(storage, mapId, collectionId, sessionOptions);
        return new CopyElement.Builder(storage.getConfig(), collectionId)
                .setMapId(mapId)
                .build();
    }

    private void addFeatures(
            IStorage storage,
            String mapId,
            String collectionId,
            List<NakshaFeature> features,
            SessionOptions sessionOptions
    ) {
        WriteRequest writeRequest = createFeaturesRequest(
                mapId,
                collectionId,
                features
        );

        makeWriteRequest(storage, writeRequest, sessionOptions);
    }

    private List<NakshaFeature> readFeatures(
            IStorage storage,
            String mapId,
            String collectionId,
            SessionOptions sessionOptions
    ) {
        ReadFeatures readFeatures = createReadFeaturesRequest(mapId, collectionId);

        Response response = storage.useReadSession(
                sessionOptions,
                reader -> reader.execute(readFeatures)
        );

        Assertions.assertInstanceOf(SuccessResponse.class, response);

        return extractResponseItems((SuccessResponse) response, NakshaFeature.class);
    }

    private ReadFeatures createReadFeaturesRequest(String mapId, String collectionId) {
        ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.setCollectionIds(
                StringList.of(collectionId)
        );
        readFeatures.setMapId(mapId);

        return readFeatures;
    }

    private void addCollectionToTheMap(
            IStorage storage,
            String mapId,
            String collectionId,
            SessionOptions sessionOptions
    ) {
        NakshaCollection collection = new NakshaCollection(collectionId, mapId);
        WriteRequest request = createWriteCollectionsRequest(collection);
        makeWriteRequest(storage, request, sessionOptions);
    }

    private String createUniqueMap(IStorage storage, SessionOptions sessionOptions) {
        String mapId = UUID.randomUUID().toString();
        addMapToTheStorage(storage, mapId, sessionOptions);
        return mapId;
    }

    private void addMapToTheStorage(IStorage storage, String mapId, SessionOptions sessionOptions) {
        WriteRequest writeRequest = new WriteRequest();

        NakshaMap map = new NakshaMap().withId(mapId);
        Write createMap = new Write().createMap(map);
        writeRequest.add(createMap);

        makeWriteRequest(storage, writeRequest, sessionOptions);
    }

    private void makeWriteRequest(IStorage storage, WriteRequest writeRequest, SessionOptions sessionOptions) {
        storage.useWriteSession(sessionOptions, writer -> {
            Response response = writer.execute(writeRequest);
            Assertions.assertInstanceOf(SuccessResponse.class, response);
            writer.commit();
            return response;
        });
    }
}
