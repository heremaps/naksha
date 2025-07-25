package com.here.naksha.cli.test_containers;

import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgInstanceConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

final class TestContainersPsqlStorage {
    private final String postgresImageUri = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r4";
    private final GenericContainer<?> postgres = new GenericContainer<>(postgresImageUri);
    private IStorage storage;

    TestContainersPsqlStorage() {
    }

    /**
     * Should be called once before any operation.
     */
    void start() {
        setUpPostgres();
        postgres.start();
        NakshaContext.currentContext().withAppId("testcontainer");
        storage = Naksha.useStorage(getNakshaStorage());
    }

    /**
     * Should be called once after all operations.
     */
    void stop() {
        postgres.stop();
    }

    StorageController getStorageController() {
        return new StorageController(storage);
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

    private void setUpPostgres() {
        postgres.addExposedPort(5432);
        postgres.addEnv("PGPASSWORD", PgInstanceConfig.DEFAULT_PASSWORD);
        postgres.setWaitStrategy(
                new LogMessageWaitStrategy()
                        .withRegEx(".*Future log output will appear in directory.*")
                        .withTimes(2)
                        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))
        );
    }
}
