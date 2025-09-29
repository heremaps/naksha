package com.here.naksha.cli.testcontainers;

import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgInstanceConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode.WITH_MAXIMUM_OUTER_TIMEOUT;

public final class TestContainersPsqlStorage {
    private final int exposedPort = 5432;
    private final String postgresImageUri = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r5";
    private final GenericContainer<?> postgres = new GenericContainer<>(postgresImageUri);
    private IStorage storage;

    public IStorage getStorage() {
        return storage;
    }

    public static TestContainersPsqlStorage getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Should be called once before any operation.
     */
    private void start() {
        setUpPostgres();
        postgres.start();
        NakshaContext.currentContext().withAppId("testcontainer");
        storage = Naksha.useStorage(getNakshaStorage());
    }

    /**
     * Should be called once after all operations.
     */
    private void stop() {
        postgres.stop();
    }

    private static final class Holder {
        private static final TestContainersPsqlStorage INSTANCE = new TestContainersPsqlStorage();

        static {
            INSTANCE.start();
            Runtime.getRuntime().addShutdownHook(
                    new Thread(INSTANCE::stop)
            );
        }
    }

    private TestContainersPsqlStorage() {
    }

    private NakshaStorage getNakshaStorage() {
        return NakshaStorage.fromJSON(
                """
                        {
                          "id": "psql_storage",
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
                        postgres.getMappedPort(exposedPort),
                        PgInstanceConfig.DEFAULT_USER,
                        PgInstanceConfig.DEFAULT_PASSWORD
                )
        );
    }

    private void setUpPostgres() {
        postgres.addExposedPort(exposedPort);
        postgres.addEnv("PGPASSWORD", PgInstanceConfig.DEFAULT_PASSWORD);
        postgres.setWaitStrategy(
                new WaitAllStrategy(WITH_MAXIMUM_OUTER_TIMEOUT)
                        .withStartupTimeout(Duration.of(120, ChronoUnit.SECONDS))
                        .withStrategy(Wait.forLogMessage(".*Future log output will appear in directory.*", 2))
                        .withStrategy(Wait.forListeningPort())
        );
    }
}
