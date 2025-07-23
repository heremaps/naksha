package com.here.naksha.cli.copy.service.psql;

import com.here.naksha.cli.copy.service.CopyElement;
import com.here.naksha.cli.copy.service.CopyService;
import com.here.naksha.cli.copy.service.CopyServiceException;
import com.here.naksha.cli.copy.service.StorageProvider;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaMap;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.*;
import naksha.psql.PgInstanceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.RequestHelper.createWriteCollectionsRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;

class PsqlCopyTest {
    static private CopyService copyService;
    static private final String POSTGRES_IMAGE_URI = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r4";
    static private final GenericContainer<?> postgres = new GenericContainer<>(POSTGRES_IMAGE_URI);
    static private SessionOptions sessionOptions;

    @BeforeAll
    static void beforeAll() {
        setUpPostgres();
        postgres.start();
        NakshaContext.currentContext().withAppId("app");
        sessionOptions = SessionOptions.from(NakshaContext.currentContext());
        copyService = new CopyService(new StorageProvider(), sessionOptions);
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Test
    void shouldCopyBetweenPostgresStorages() throws CopyServiceException {
        // Given: src storage
        NakshaStorage srcNakshaStorage = getNakshaStorage();
        IStorage srcStorage = Naksha.useStorage(srcNakshaStorage);

        // And: src copy element
        CopyElement srcCopyElement = new CopyElement.Builder(srcNakshaStorage, "srccolid")
                .setMapId("srcmapid")
                .build();

        // And: src test map
        addMapToStorage(srcStorage, srcCopyElement.getMapId());

        // And: src test collection
        addCollectionToStorage(srcStorage, srcCopyElement.getCollectionId(), srcCopyElement.getMapId());

        // And: initial features in src test collection
        List<NakshaFeature> features = List.of(
                new NakshaFeature("1"),
                new NakshaFeature("2")
        );
        addFeatures(srcStorage, srcCopyElement.getMapId(), srcCopyElement.getCollectionId(), features);

        // And: target storage
        NakshaStorage targetNakshaStorage = getNakshaStorage();
        IStorage targetStorage = Naksha.useStorage(targetNakshaStorage);

        // And: target copy element
        CopyElement targetCopyElement = new CopyElement.Builder(targetNakshaStorage, "targetcolid")
                .setMapId("targetmapid")
                .build();

        // And: target test map
        addMapToStorage(targetStorage, targetCopyElement.getMapId());

        // And: target test collection
        addCollectionToStorage(targetStorage, targetCopyElement.getCollectionId(), targetCopyElement.getMapId());

        // When: copying
        copyService.copy(srcCopyElement, targetCopyElement);

        // Then: target test contains features from source
        List<NakshaFeature> actualFeatures = readFeatures(targetStorage, targetCopyElement);
        Assertions.assertEquals(features.size(), actualFeatures.size());
    }

    private void makeWriteRequest(IStorage storage, WriteRequest writeRequest) {
        storage.useWriteSession(sessionOptions, writer -> {
            Response response = writer.execute(writeRequest);
            Assertions.assertInstanceOf(SuccessResponse.class, response);
            writer.commit();
            return response;
        });
    }

    private void addMapToStorage(IStorage storage, String mapId) {
        WriteRequest writeRequest = new WriteRequest();

        NakshaMap map = new NakshaMap().withId(mapId);
        Write createMap = new Write().upsertMap(map, false);
        writeRequest.add(createMap);

        makeWriteRequest(storage, writeRequest);
    }

    private void addFeatures(IStorage storage, String mapId, String collectionId, List<NakshaFeature> features) {
        WriteRequest writeRequest = createFeaturesRequest(
                mapId,
                collectionId,
                features
        );

        makeWriteRequest(storage, writeRequest);
    }

    private void addCollectionToStorage(IStorage storage, String collectionId, String mapId) {
        NakshaCollection collection = new NakshaCollection(collectionId, mapId);
        WriteRequest request = createWriteCollectionsRequest(collection);
        makeWriteRequest(storage, request);
    }

    private List<NakshaFeature> readFeatures(
            IStorage storage,
            CopyElement copyElement
    ) {
        ReadFeatures readFeatures = createReadFeaturesRequest(copyElement);

        Response response = storage.useReadSession(
                sessionOptions,
                reader -> reader.execute(readFeatures)
        );

        Assertions.assertInstanceOf(SuccessResponse.class, response);

        return extractResponseItems((SuccessResponse) response, NakshaFeature.class);
    }

    private ReadFeatures createReadFeaturesRequest(CopyElement source) {
        ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.setCollectionIds(
                StringList.of(source.getCollectionId())
        );
        readFeatures.setMapId(source.getMapId());

        return readFeatures;
    }

    private static void setUpPostgres() {
        postgres.setPortBindings(List.of("15432:5432"));
        postgres.addEnv("PGPASSWORD", PgInstanceConfig.DEFAULT_PASSWORD);
        postgres.setWaitStrategy(
                new LogMessageWaitStrategy()
                        .withRegEx(".*Future log output will appear in directory.*")
                        .withTimes(2)
                        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))
        );
    }

    private NakshaStorage getNakshaStorage() {
        return NakshaStorage.fromJSON(
                """
                        {
                          "id": "storage",
                          "type": "Storage",
                          "create": true,
                          "upgrade": true,
                          "className": "naksha.psql.PsqlStorage",
                          "master": {
                            "host": "%s",
                            "database": "%s",
                            "port": %s,
                            "user": "%s",
                            "password": "%s",
                            "readOnly": false
                          }
                        }
                        """.formatted(
                        postgres.getHost(),
                        PgInstanceConfig.DEFAULT_DB,
                        postgres.getMappedPort(5432),
                        PgInstanceConfig.DEFAULT_USER,
                        PgInstanceConfig.DEFAULT_PASSWORD
                )
        );
    }

}
