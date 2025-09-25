package com.here.naksha.cli.copy.service.psql;

import com.here.naksha.cli.copy.service.*;
import com.here.naksha.cli.copy.service.executors.OneShotFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.ParallelFeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.results.CommandResult;
import com.here.naksha.cli.results.CommandSuccess;
import com.here.naksha.cli.storages.GeneratingStorage;
import com.here.naksha.cli.storages.GeneratingStorageConfig;
import com.here.naksha.cli.testcontainers.TestContainersPsqlStorage;
import com.here.naksha.lib.core.models.geojson.WebMercatorTile;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaMap;
import naksha.model.request.*;
import naksha.model.request.query.SpIntersects;
import naksha.model.request.query.SpOr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static naksha.model.RandomFeatures.randomFeatures;
import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.RequestHelper.createWriteCollectionsRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;
import static org.junit.jupiter.api.Assertions.*;

class PsqlCopyTest {
    private final String srcCollectionId = "srccolid";
    private final String targetCollectionId = "targetcolid";
    private SessionOptions sessionOptions;

    @BeforeEach
    void beforeEach() {
        NakshaContext.currentContext().withAppId("testapp");
        sessionOptions = SessionOptions.from(NakshaContext.currentContext());
    }

    @ParameterizedTest
    @MethodSource("featuresWriteExecutors")
    void shouldCopyFeaturesBetweenGeneratingStorageAndPostgres(FeaturesWriteExecutor featuresWriteExecutor) {
        // Given: prepared source
        int countOfFeatures = 10_000;
        StringList tileIds = new StringList("122013100013", "122013100020");
        IStorage sourceStorage = generatingStorageWithGivenCountOfFeaturesAndTilesIds(countOfFeatures, tileIds);
        CopyElement source = copyElementForGeneratingStorage(sourceStorage);

        // And: prepared target
        IStorage targetStorage = TestContainersPsqlStorage.getInstance().getStorage();
        CopyElement target = createMapWithEmptyCollection(targetStorage, targetCollectionId);

        // And: copy service
        CopyService copyService = new CopyService(featuresWriteExecutor, new StorageProvider(), sessionOptions);

        // When: copying
        assertCommandSuccessResult(copyService.copy(source, target, false));

        // And
        List<NakshaFeature> targetFeatures = readFeaturesInTheGivenTiles(
                targetStorage, target.getMapId(), target.getCollectionId(), sessionOptions, tileIds
        );

        // Then
        assertEquals(countOfFeatures, targetFeatures.size());
    }

    @ParameterizedTest
    @MethodSource("featuresWriteExecutors")
    void shouldCopyFeaturesBetweenMapsOnTheSameStorage(FeaturesWriteExecutor featuresWriteExecutor) {
        // Given: the same storage for source and target
        IStorage storage = TestContainersPsqlStorage.getInstance().getStorage();

        // Given: prepared source
        CopyElement source = createMapWithEmptyCollection(storage, srcCollectionId);

        // And: predefined features
        List<NakshaFeature> sourceFeatures = randomFeatures(10_000);
        addFeatures(storage, source.getMapId(), source.getCollectionId(), sourceFeatures, sessionOptions);

        // And: prepared target
        CopyElement target = createMapWithEmptyCollection(storage, targetCollectionId);

        // And: copy service
        CopyService copyService = new CopyService(featuresWriteExecutor, new StorageProvider(), sessionOptions);

        // When: copying
        assertCommandSuccessResult(copyService.copy(source, target, false));

        // And
        List<NakshaFeature> targetFeatures = readFeatures(
                storage, target.getMapId(), target.getCollectionId(), sessionOptions
        );

        // Then: target collection contains features from source
        assertSameFeatures(sourceFeatures, targetFeatures);
    }

    @ParameterizedTest
    @MethodSource("featuresWriteExecutors")
    void shouldCreateMapAndCollectionThenCopy(FeaturesWriteExecutor featuresWriteExecutor) {
        // Given: the same storage for source and target
        IStorage storage = TestContainersPsqlStorage.getInstance().getStorage();

        // Given: prepared source
        CopyElement source = createMapWithEmptyCollection(storage, srcCollectionId);

        // And: predefined features
        List<NakshaFeature> sourceFeatures = randomFeatures(10_000);
        addFeatures(storage, source.getMapId(), source.getCollectionId(), sourceFeatures, sessionOptions);

        // And: prepared target with no existing map and collection
        CopyElement target = createCopyElementWithNoExistingMapAndCollection(storage);

        // And: copy service
        CopyService copyService = new CopyService(featuresWriteExecutor, new StorageProvider(), sessionOptions);

        // When: copying with auto create target
        assertCommandSuccessResult(copyService.copy(source, target, true));

        // And
        List<NakshaFeature> targetFeatures = readFeatures(
                storage, target.getMapId(), target.getCollectionId(), sessionOptions
        );

        // Then: target collection contains features from source
        assertSameFeatures(sourceFeatures, targetFeatures);
    }

    private static Stream<Arguments> featuresWriteExecutors() {
        return Stream.of(
                Arguments.of(new ParallelFeaturesWriteExecutor()),
                Arguments.of(new OneShotFeaturesWriteExecutor())
        );
    }

    private CopyElement createCopyElementWithNoExistingMapAndCollection(IStorage storage) {
        String mapId = UUID.randomUUID().toString();
        String collectionId = UUID.randomUUID().toString();
        return new CopyElement.Builder(storage.getConfig())
                .setMapId(mapId)
                .setCollectionId(collectionId)
                .build();
    }

    private CopyElement copyElementForGeneratingStorage(IStorage storage) {
        return new CopyElement.Builder(storage.getConfig()).build();
    }

    private IStorage generatingStorageWithGivenCountOfFeaturesAndTilesIds(int count, StringList tileIds) {
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.setId("test_generating_storage");
        config.setClassName(GeneratingStorage.class.getCanonicalName());
        config.getProperties()
                .withCount(count)
                .withTileIds(tileIds);

        return Naksha.useStorage(config);
    }

    private void assertSameFeatures(List<NakshaFeature> expectedFeatures, List<NakshaFeature> actualFeatures) {
        assertEquals(expectedFeatures.size(), actualFeatures.size());
        assertIterableEquals(
                expectedFeatures.stream().map(NakshaFeature::getId).sorted().toList(),
                actualFeatures.stream().map(NakshaFeature::getId).sorted().toList()
        );
    }

    private CopyElement createMapWithEmptyCollection(IStorage storage, String collectionId) {
        String mapId = createUniqueMap(storage, sessionOptions);
        addCollectionToTheMap(storage, mapId, collectionId, sessionOptions);
        return new CopyElement.Builder(storage.getConfig())
                .setMapId(mapId)
                .setCollectionId(collectionId)
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

    private List<NakshaFeature> readFeaturesInTheGivenTiles(
            IStorage storage,
            String mapId,
            String collectionId,
            SessionOptions sessionOptions,
            StringList tileIds
    ) {
        SpOr spOr = new SpOr();
        tileIds.forEach(tileId -> spOr.add(
                new SpIntersects(
                        WebMercatorTile.forQuadkey(tileId).getBBox(false).toPolygon()
                )
        ));

        RequestQuery requestQuery = new RequestQuery();
        requestQuery.setSpatial(spOr);

        ReadFeatures readRequest = createReadFeaturesRequest(mapId, collectionId);
        readRequest.setQuery(requestQuery);

        SuccessResponse response = makeReadRequest(storage, readRequest, sessionOptions);

        return extractResponseItems(response, NakshaFeature.class);
    }

    private List<NakshaFeature> readFeatures(
            IStorage storage,
            String mapId,
            String collectionId,
            SessionOptions sessionOptions
    ) {
        ReadFeatures readFeatures = createReadFeaturesRequest(mapId, collectionId);

        SuccessResponse response = makeReadRequest(storage, readFeatures, sessionOptions);

        return extractResponseItems(response, NakshaFeature.class);
    }

    private SuccessResponse makeReadRequest(IStorage storage, Request request, SessionOptions sessionOptions) {
        Response response = storage.useReadSession(
                sessionOptions,
                reader -> reader.execute(request)
        );

        return assertInstanceOf(SuccessResponse.class, response);
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
            assertInstanceOf(SuccessResponse.class, response);
            writer.commit();
            return response;
        });
    }

    private void assertCommandSuccessResult(CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> commandResult) {
        assertInstanceOf(CommandSuccess.class, commandResult);
    }
}
