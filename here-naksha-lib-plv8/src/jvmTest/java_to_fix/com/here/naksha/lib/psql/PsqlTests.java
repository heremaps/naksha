/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.psql;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.StorageNotInitialized;
import com.here.naksha.lib.jbon.JvmEnv;
import com.here.naksha.lib.psql.PsqlStorage.Params;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

/**
 * Base class for all PostgresQL tests that require some test database.
 */
@SuppressWarnings("unused")
@TestMethodOrder(OrderAnnotation.class)
abstract class PsqlTests {

  static final Logger log = LoggerFactory.getLogger(PsqlTests.class);

  static GenericContainer<?> postgreSQLContainer;

  /**
   * The test database, if any is available.
   * The test database, if any is available.
   */
  @SuppressWarnings("unused")
  static PsqlStorageConfig config;

  static String existingUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL");

  /**
   * Prevents that the test drops the schema at the start.
   */
  static final boolean DROP_INITIALLY = true;

  /**
   * If the test drop the database at the end (false by default, to verify results).
   */
  static final boolean DROP_FINALLY = false;

  /**
   * Logging level.
   */
  static final EPsqlLogLevel LOG_LEVEL = EPsqlLogLevel.VERBOSE;

  abstract boolean enabled();

  /**
   * Can be used to temporarily disable individual tests for debugging. Just do: <pre>{@code
   * @DisabledIf("isTrue")
   * }</pre>
   *
   * @return {@code true}.
   */
  final boolean isTrue() {
    return true;
  }

  final boolean runTest() {
    return enabled();
  }

  final boolean isTestContainerRun() {
    return runTest() && postgreSQLContainer != null;
  }

  final boolean dropInitially() {
    return runTest() && DROP_INITIALLY;
  }

  final boolean dropFinally() {
    return runTest() && DROP_FINALLY;
  }

  static final String TEST_APP_ID = "test_app";
  static final String TEST_AUTHOR = "test_author";
  static @Nullable PsqlStorage storage;
  static @Nullable NakshaContext nakshaContext;
  static @Nullable PsqlWriteSession session;
  static @NotNull PsqlFeatureGenerator fg;
  JvmEnv env = JvmEnv.get();

  /**
   * Prints an arbitrary prefix, followed by the calculation of the features/second.
   *
   * @param prefix   The arbitrary prefix to print.
   * @param START    The start time in nanoseconds.
   * @param END      The end time in nanoseconds.
   * @param features The number features effected.
   */
  static void printResults(final @NotNull String prefix, final long START, final long END, final long features) {
    final long NANOS = END - START;
    final double MS = NANOS / 1_000_000d;
    final double SECONDS = MS / 1_000d;
    final double FEATURES_PER_SECOND = features / SECONDS;
    log.info(String.format(
        "%s %,d features in %2.2f seconds, %6.2f features/seconds\n",
        prefix, features, SECONDS, FEATURES_PER_SECOND));
  }

  @BeforeAll
  static void beforeTest() throws IOException, InterruptedException {
    NakshaContext.currentContext().setAuthor("PsqlStorageTest");
    NakshaContext.currentContext().setAppId("naksha-lib-psql-unit-tests");
    nakshaContext = new NakshaContext().withAppId(TEST_APP_ID).withAuthor(TEST_AUTHOR);
    fg = new PsqlFeatureGenerator();
    final String url;
    if (existingUrl != null) {
      url = existingUrl;
    } else {
      postgreSQLContainer = new GenericContainer("hcr.data.here.com/naksha-devops/naksha-postgres:arm64-v16.2-r1")
          .withExposedPorts(5432);
      String password = "password";
      postgreSQLContainer.addEnv("PGPASSWORD", password);
      postgreSQLContainer.setWaitStrategy(new LogMessageWaitStrategy()
          .withRegEx("Start postgres.*")
          .withTimes(2)
          .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)));
      postgreSQLContainer.start();
      Thread.sleep(1000);
      url = String.format(
          "jdbc:postgresql://localhost:%s/postgres?user=postgres&password=%s&schema=naksha_test&id=com.here.naksha&app=PsqlTests",
          postgreSQLContainer.getMappedPort(5432), password);
    }
    config = new PsqlStorageConfig(url);
  }

  /**
   * The name of the test-collection.
   */
  abstract @NotNull String collectionId();

  /**
   * If the collection should be partitioned.
   */
  abstract boolean partition();

  /**
   * The test-schema to use, can be overridden to switch the schema.
   */
  @NotNull
  String schema() {
    assertNotNull(schema);
    return schema;
  }

  private String schema;

  @Test
  @Order(10)
  @EnabledIf("runTest")
  void createStorage() {
    storage = new PsqlStorage(config).withParams(getParams());
    schema = storage.getSchema();
    if (!schema.equals(schema())) {
      storage.setSchema(schema());
    }
    // Enable this code line to get debug output from the database!
    // storage.setLogLevel(EPsqlLogLevel.DEBUG);
  }

  @Test
  @Order(11)
  @EnabledIf("dropInitially")
  void dropSchemaIfExists() {
    assertNotNull(storage);
    storage.dropSchema();
  }

  @Test
  @Order(12)
  @EnabledIf("dropInitially")
  void testStorageNotInitialized() {
    assertNotNull(storage);
    assertNotNull(nakshaContext);
    assertThrows(StorageNotInitialized.class, () -> {
      try (final PsqlWriteSession session = storage.newWriteSession(nakshaContext, true)) {}
    });
    assertThrows(StorageNotInitialized.class, () -> {
      try (final PsqlReadSession session = storage.newReadSession(nakshaContext, true)) {}
    });
  }

  protected Params getParams() {
    return new Params().pg_hint_plan(false).pg_stat_statements(false);
  }

  @Test
  @Order(13)
  @EnabledIf("runTest")
  void initStorage() {
    assertNotNull(storage);
    storage.initStorage();
  }

  @Test
  @Order(20)
  @EnabledIf("runTest")
  void startWriteSession() {
    assertNotNull(storage);
    session = storage.newWriteSession(nakshaContext, true);
    assertNotNull(session);
  }

  // Custom stuff between 30 and 9000

  @Test
  @Order(9999)
  @EnabledIf("dropFinally")
  void dropSchemaFinally() {
    assertNotNull(storage);
    storage.dropSchema();
  }

  @EnabledIf("runTest")
  @AfterAll
  static void afterTest() {
    if (session != null) {
      try {
        session.close();
      } catch (Exception e) {
        log.atError()
            .setMessage("Failed to close write-session")
            .setCause(e)
            .log();
      } finally {
        session = null;
      }
    }
    if (storage != null) {
      try {
        storage.close();
      } catch (Exception e) {
        log.atError().setMessage("Failed to close storage").setCause(e).log();
      } finally {
        storage = null;
      }
    }
    if (postgreSQLContainer != null) {
      postgreSQLContainer.stop();
    }
  }
}
