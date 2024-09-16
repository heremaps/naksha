/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.lib.view;

import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.psql.PgPlatform;
import naksha.psql.PgStorage;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Base class for all PostgresQL tests that require some test database.
 */
@SuppressWarnings("unused")
@TestMethodOrder(OrderAnnotation.class)
abstract class PsqlTests {

  static final Logger log = LoggerFactory.getLogger(PsqlTests.class);

  /**
   * Prevents that the test drops the schema at the start.
   */
  static final boolean DROP_INITIALLY = true;

  /**
   * If the test drop the database at the end (false by default, to verify results).
   */
  static final boolean DROP_FINALLY = false;

  abstract boolean enabled();

  final boolean runTest() {
    return enabled();
  }

  static final String TEST_APP_ID = "test_app";
  static final String TEST_AUTHOR = "test_author";
  static PgStorage storage;
  static @Nullable NakshaContext nakshaContext;
  static @Nullable IWriteSession session;

  @BeforeAll
  static void beforeTest() {
    NakshaContext.currentContext().setAuthor("PsqlStorageTest");
    NakshaContext.currentContext().setAppId("naksha-lib-view-unit-tests");
    nakshaContext = NakshaContext.currentContext().withAppId(TEST_APP_ID).withAuthor(TEST_AUTHOR);
    storage = PgPlatform.newTestStorage();
    storage.initStorage(null);
    session = storage.newWriteSession(new SessionOptions());
    assertNotNull(storage);
    assertNotNull(session);
  }

  // Custom stuff between 50 and 9000

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
  }
}
