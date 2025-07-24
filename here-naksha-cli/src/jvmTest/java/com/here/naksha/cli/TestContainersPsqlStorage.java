package com.here.naksha.cli;

import com.here.naksha.cli.copy.service.CopyElement;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaMap;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.*;
import naksha.psql.PgInstanceConfig;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.RequestHelper.createWriteCollectionsRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;

public class TestContainersPsqlStorage {
    private final String postgresImageUri = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r4";
    private final GenericContainer<?> postgres = new GenericContainer<>(postgresImageUri);
    private final SessionOptions sessionOptions;
    private IStorage storage;

    public TestContainersPsqlStorage(SessionOptions sessionOptions) {
        this.sessionOptions = sessionOptions;
    }

    /**
     * Should be called before any operation
     */
    public void start() {
        setUpPostgres();
        postgres.start();
        storage = Naksha.useStorage(getNakshaStorage());
    }

    public void stop() {
        postgres.stop();
    }

    public void addFeatures(String mapId, String collectionId, List<NakshaFeature> features) {
        WriteRequest writeRequest = createFeaturesRequest(
                mapId,
                collectionId,
                features
        );

        makeWriteRequest(writeRequest);
    }

    public void addCollectionToTheStorage(String collectionId, String mapId) {
        NakshaCollection collection = new NakshaCollection(collectionId, mapId);
        WriteRequest request = createWriteCollectionsRequest(collection);
        makeWriteRequest(request);
    }

    public void addMapToTheStorage(String mapId) {
        WriteRequest writeRequest = new WriteRequest();

        NakshaMap map = new NakshaMap().withId(mapId);
        Write createMap = new Write().upsertMap(map, false);
        writeRequest.add(createMap);

        makeWriteRequest(writeRequest);
    }

    public List<NakshaFeature> readFeatures(
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

    public NakshaStorage getNakshaStorage() {
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

    private void makeWriteRequest(WriteRequest writeRequest) {
        storage.useWriteSession(sessionOptions, writer -> {
            Response response = writer.execute(writeRequest);
            Assertions.assertInstanceOf(SuccessResponse.class, response);
            writer.commit();
            return response;
        });
    }

    private ReadFeatures createReadFeaturesRequest(CopyElement source) {
        ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.setCollectionIds(
                StringList.of(source.getCollectionId())
        );
        readFeatures.setMapId(source.getMapId());

        return readFeatures;
    }

    private void setUpPostgres() {
        postgres.setPortBindings(List.of("15432:5432"));
        postgres.addEnv("PGPASSWORD", PgInstanceConfig.DEFAULT_PASSWORD);
        postgres.setWaitStrategy(
                new LogMessageWaitStrategy()
                        .withRegEx(".*Future log output will appear in directory.*")
                        .withTimes(2)
                        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))
        );
    }
}
