package com.here.naksha.cli.testcontainers;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgInstanceConfig;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.lifecycle.Startable;

import static org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode.WITH_MAXIMUM_OUTER_TIMEOUT;

public final class TestContainersPsqlStorage implements Startable {

  private final int exposedPort = 5432;
  private final String postgresImageUri = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r5";
  private final GenericContainer<?> postgres = new GenericContainer<>(postgresImageUri);
  private IStorage storage;
  private boolean isStarted = false;

  public IStorage getStorage() {
    return storage;
  }

  TestContainersPsqlStorage() {
  }

  /**
   * Should be called once before any operation.
   */
  public synchronized void start() {
    if (!isStarted) {
      setUpPostgres();
      postgres.start();
      NakshaContext.currentContext().withAppId("testcontainer");
      storage = Naksha.useStorage(getNakshaStorage());
      isStarted = true;
    }
  }

  /**
   * Should be called once after all operations.
   */
  public synchronized void stop() {
    if (isStarted) {
      postgres.stop();
      isStarted = false;
    }
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
