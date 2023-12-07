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
package com.here.naksha.app.common;

import static com.here.naksha.app.service.NakshaApp.newInstance;

import com.here.naksha.app.service.NakshaApp;
import com.here.naksha.lib.hub.NakshaHubConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InitializedTestNakshaApp {

  private static final String MOCK_CONFIG_ID = "mock-config";

  private static final String TEST_CONFIG_ID = "test-config";

  private static final String TEST_SCHEMA = "naksha_test_schema";

  public final @Nullable String testDbUrl;
  public final @NotNull NakshaApp nakshaApp;

  private InitializedTestNakshaApp(@Nullable String testDbUrl, @NotNull NakshaApp nakshaApp) {
    this.testDbUrl = testDbUrl;
    this.nakshaApp = nakshaApp;
  }

  public static InitializedTestNakshaApp initMockedNakshaApp() {
    return new InitializedTestNakshaApp(null, newInstance(MOCK_CONFIG_ID));
  }

  public static InitializedTestNakshaApp initLocalPsqlBasedNakshaApp() {
    String dbUrl = dbUrl();
    return new InitializedTestNakshaApp(dbUrl, newInstance(TEST_CONFIG_ID, dbUrl));
  }

  private static String dbUrl() {
    String dbUrl = System.getenv("TEST_NAKSHA_PSQL_URL");
    if (dbUrl != null && !dbUrl.isBlank()) {
      return dbUrl;
    }
    String password = System.getenv("TEST_NAKSHA_PSQL_PASS");
    if (password == null || password.isBlank()) {
      password = "password";
    }
    return "jdbc:postgresql://localhost/postgres?user=postgres&password=" + password
        + "&schema=" + TEST_SCHEMA
        + "&app=" + NakshaHubConfig.defaultAppName()
        + "&id=naksha-admin-db";
  }
}
