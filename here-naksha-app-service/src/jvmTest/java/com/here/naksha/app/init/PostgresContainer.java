package com.here.naksha.app.init;

import static org.testcontainers.containers.InternetProtocol.TCP;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import naksha.model.NakshaVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public class PostgresContainer {

  private static final Logger log = LoggerFactory.getLogger(PostgresContainer.class);
  private static final String NAKSHA_POSTGRES_IMAGE = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r5";
  private static final Integer POSTGRES_CONTAINER_PORT = 5432;
  private static final Integer LOCALHOST_PORT = 5432;
  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);
  private static final String DB_READY_LOG_PATTERN = ".*Future log output will appear in directory.*";

  private final GenericContainer nakshaPostgres;

  private PostgresContainer() {
    nakshaPostgres = new GenericContainer(DockerImageName.parse(NAKSHA_POSTGRES_IMAGE))
        .withEnv(Map.of(
            "PGPASSWORD", "password",
            "POSTGRES_USER", "postgres",
            "POSTGRES_DB", "postgres"
        ));
    nakshaPostgres.setPortBindings(List.of("%s:%s/%s".formatted(LOCALHOST_PORT, POSTGRES_CONTAINER_PORT, TCP.toDockerNotation())));
    nakshaPostgres.setWaitStrategy(
        new LogMessageWaitStrategy()
            .withRegEx(DB_READY_LOG_PATTERN)
            .withTimes(2)
            .withStartupTimeout(STARTUP_TIMEOUT)
    );
  }

  public String getJdbcUrl() {
    return "jdbc:postgresql://localhost:" + LOCALHOST_PORT + "/postgres?user=postgres&password=password"
           + "&schema=" + TestStorageConfigs.dataDbConfig.mapId()
           + "&app=" + "Naksha/v" + NakshaVersion.current;
  }

  public static PostgresContainer startedPostgresContainer() {
    PostgresContainer container = new PostgresContainer();
    container.nakshaPostgres.start();
    container.nakshaPostgres.followOutput(new Slf4jLogConsumer(log));
    return container;
  }

  public void stop() {
    log.info("Stopping Container...");
    nakshaPostgres.stop();
  }
}
