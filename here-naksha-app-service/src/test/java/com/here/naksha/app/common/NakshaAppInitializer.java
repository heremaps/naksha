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
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;

import com.here.naksha.app.service.NakshaApp;
import com.here.naksha.lib.hub.NakshaHubConfig;
import com.here.naksha.lib.psql.PsqlStorage;
import java.util.Map;
import java.util.stream.Collectors;

public class NakshaAppInitializer {

  private static final String MOCK_CONFIG_ID = "mock-config";

  private static final String TEST_CONFIG_ID = "test-config";

  private static final String TEST_MAINTENANCE = "naksha_test_maintenance";
  private static final String TEST_SCHEMA = "naksha_test_schema";

  private NakshaAppInitializer() {}

  public static NakshaApp mockedNakshaApp() {
    return newInstance(MOCK_CONFIG_ID);
  }

  public static NakshaApp localPsqlBasedNakshaApp() {
    String dbUrl = dbUrl();
    ensureNoTestSchema(dbUrl);
    return newInstance(TEST_CONFIG_ID, dbUrl);
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

  private static void ensureNoTestSchema(String dbUrl) {
    PsqlStorage maintenanceStorage = adminMaintenanceDb(dbUrl);
    maintenanceStorage.dropSchema();
    maintenanceStorage.close();
  }

  private static PsqlStorage adminMaintenanceDb(String testDbUrl) {
    Map<String, String> queryParams = queryParams(testDbUrl);
    queryParams.put("app", TEST_MAINTENANCE);
    queryParams.put("id", TEST_MAINTENANCE);
    String maintenanceUrl = urlWithOverriddenParams(testDbUrl, queryParams);
    return new PsqlStorage(maintenanceUrl);
  }

  private static Map<String, String> queryParams(String url) {
    String[] splitted = url.split("\\?");
    if (splitted.length < 2) {
      return emptyMap();
    }
    String[] queryParts = splitted[1].split("&");
    return stream(queryParts)
        .map(queryPart -> queryPart.split("="))
        .collect(Collectors.toMap(keyAndValue -> keyAndValue[0], keyAndValue -> keyAndValue[1]));
  }

  private static String urlWithOverriddenParams(String originalUrl, Map<String, String> newParams) {
    String newQueryPart = newParams.entrySet().stream()
        .map(keyAndValue -> "%s=%s".formatted(keyAndValue.getKey(), keyAndValue.getValue()))
        .collect(Collectors.joining("&"));
    return "%s?%s".formatted(originalUrl.split("\\?")[0], newQueryPart);
  }
}
