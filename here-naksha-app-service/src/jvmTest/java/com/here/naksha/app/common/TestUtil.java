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

import static java.nio.charset.StandardCharsets.UTF_8;
import static naksha.base.Platform.forClass;

import com.here.naksha.app.service.http.auth.NakshaAuthProvider;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.IoHelp.LoadedBytes;
import com.here.naksha.lib.hub.NakshaHub;
import com.here.naksha.lib.hub.NakshaHubConfig;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import naksha.base.Platform;
import naksha.model.NakshaContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtil {

  private static final @NotNull Logger logger = LoggerFactory.getLogger(TestUtil.class);
  private static final String TEST_DATA_FOLDER = "src/jvmTest/resources/unit_test_data/";
  public static final String HDR_STREAM_ID = "Stream-Id";

/*
Pre-Start databases via:

export NAME=admin_db
export IMG=ghcr.io/naksha-oss/naksha-postgres:v16.2-r4
mkdir -p ~/$NAME
mkdir -p ~/$NAME/pg_data
mkdir -p ~/$NAME/pg_temp
docker pull $IMG
docker run --name $NAME \
       -v ~/$NAME/pg_data:/usr/local/pgsql/data \
       -v ~/$NAME/pg_temp:/usr/local/pgsql/temp \
       -p 0.0.0.0:15432:5432 \
       -e PGPASSWORD=password \
       -d $IMG

export NAME=data_db
export IMG=ghcr.io/naksha-oss/naksha-postgres:v16.2-r4
mkdir -p ~/$NAME
mkdir -p ~/$NAME/pg_data
mkdir -p ~/$NAME/pg_temp
docker pull $IMG
docker run --name $NAME \
       -v ~/$NAME/pg_data:/usr/local/pgsql/data \
       -v ~/$NAME/pg_temp:/usr/local/pgsql/temp \
       -p 0.0.0.0:25432:5432 \
       -e PGPASSWORD=password \
       -d $IMG

Run tests in IntelliJ with env-var:

naksha_admin_db=jdbc:postgresql://localhost:15432/postgres?user=postgres&password=password;test_data_db=jdbc:postgresql://localhost:25432/postgres?user=postgres&password=password

Or externally like:

EXPORT naksha_admin_db=jdbc:postgresql://localhost:15432/postgres?user=postgres&password=password
EXPORT test_data_db=jdbc:postgresql://localhost:25432/postgres?user=postgres&password=password
... run tests

*/

  // Admin-db
  public static final String TEST_ADMIN_DB = "naksha_admin_db";
  public static final String TEST_ADMIN_MAP_ID = NakshaHubConfig.DEFAULT_HUB_ADMIN_MAP_ID;

  // Data-db
  public static final String TEST_DATA_DB = "test_data_db";
  public static final String TEST_DATA_MAP_ID = "test_map";

  private TestUtil() {
  }

  public static String loadFileOrFail(final @NotNull String rootPath, final @NotNull String fileName) {
    try {
      String json = new String(Files.readAllBytes(Paths.get(rootPath + fileName)));
      json = json.replace("${dataDb.storageId}", TEST_DATA_DB);
      json = json.replace("${dataDb.schema}", TEST_DATA_MAP_ID);
//      final TestStorageConfig dataDbConfig = TestStorageConfigs.dataDbConfig;
//      final PgConfig dataPgConfig = dataDbConfig.config().proxy(PgConfig.TYPE);
//      final PgInstanceConfig dataMasterConfig = dataPgConfig.getMaster();
//      json = json.replace("${dataDb.host}", dataMasterConfig.getHost());
//      json = json.replace("${dataDb.port}", Integer.toString(dataMasterConfig.getPort()));
//      json = json.replace("${dataDb.db}", dataMasterConfig.getDb());
//      json = json.replace("${dataDb.storageId}", dataPgConfig.getId());
//      json = json.replace("${dataDb.schema}", dataDbConfig.mapId());
//      json = json.replace("${dataDb.user}", dataMasterConfig.getUser());
//      json = json.replace("${dataDb.password}", dataMasterConfig.getPassword());
//      final TestStorageConfig adminDbConfig = TestStorageConfigs.adminDbConfig;
//      final PgConfig adminPgConfig = adminDbConfig.config().proxy(PgConfig.TYPE);
//      final PgInstanceConfig adminMasterConfig = adminPgConfig.getMaster();
//      json = json.replace("${adminDb.host}", adminMasterConfig.getHost());
//      json = json.replace("${adminDb.port}", Integer.toString(adminMasterConfig.getPort()));
//      json = json.replace("${adminDb.db}", adminMasterConfig.getDb());
//      json = json.replace("${adminDb.storageId}", adminPgConfig.getId());
//      json = json.replace("${adminDb.schema}", adminDbConfig.mapId());
//      json = json.replace("${adminDb.user}", adminMasterConfig.getUser());
//      json = json.replace("${adminDb.password}", adminMasterConfig.getPassword());
      logger.info("Loaded file {}{}: {}", rootPath, fileName, json);
      return json;
    } catch (IOException e) {
      Assertions.fail("Unable to read test file " + fileName, e);
      return null;
    }
  }

  public static String loadFileOrFail(final @NotNull String fileName) {
    return loadFileOrFail(TEST_DATA_FOLDER, fileName);
  }

  /**
   * Should be replaced with <code><pre>
   *   Platform.fromJson(json, ExpectedType.TYPE)
   * or
   *   Platform.fromJson(json, Platform.forClass(ExpectedType.class))
   * </pre></code>
   */
  @Deprecated
  public static <T> T parseJson(final @NotNull String jsonStr, final @NotNull Class<T> type) {
    return Platform.fromJson(jsonStr, forClass(type));
  }

  public static <T> T parseJsonFileOrFail(final @NotNull String fileName, final @NotNull Class<T> type) {
    return parseJson(loadFileOrFail(fileName), type);
  }

  public static <T> T parseJsonFileOrFail(
      final @NotNull String rootPath, final @NotNull String fileName, final @NotNull Class<T> type) {
    return parseJson(loadFileOrFail(rootPath, fileName), type);
  }

  public static @NotNull NakshaContext newTestNakshaContext() {
    final NakshaContext nakshaContext = NakshaContext.newInstance(NakshaHubConfig.defaultAppNameWithVersion());
    nakshaContext.attachToCurrentThread();
    return nakshaContext;
  }

  public static String getHeader(final HttpResponse<?> response, final String header) {
    final List<String> values = response.headers().map().get(header);
    // if list has only one node, return just string element, otherwise toString() of entire list
    return (values == null) ? null : (values.size() > 1 ? values.toString() : values.get(0));
  }

  public static String urlEncoded(String text) {
    return URLEncoder.encode(text, UTF_8);
  }

  public static String getEnvOrDefault(final String envKey, final String defValue) {
    final String envValue = System.getenv(envKey);
    return (envValue == null || envValue.isEmpty()) ? defValue : envValue;
  }

  public static String generateJWT(String payload) {
    return generateJWT(payload, "auth/jwt.key");
  }

  public static String generateJWT(String payload, String privateKeyPath) {
    // Load private key
    final LoadedBytes loaded = IoHelp.readBytesFromHomeOrResource(privateKeyPath, false, NakshaHubConfig.NAKSHA_APP_NAME);
    final String jwtKey = new String(loaded.getBytes(), StandardCharsets.UTF_8);

    final JWTAuthOptions authOptions = new JWTAuthOptions()
        .setJWTOptions(new JWTOptions().setAlgorithm("RS256"))
        .addPubSecKey(new PubSecKeyOptions().setAlgorithm("RS256").setBuffer(jwtKey));
    final NakshaAuthProvider nakshaAuthProvider = new NakshaAuthProvider(Vertx.vertx(), authOptions);
    // Sign the following JWT payload
    return nakshaAuthProvider.generateToken(new JsonObject(payload));
  }
}
