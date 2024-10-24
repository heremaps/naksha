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

import com.here.naksha.app.init.TestPsqlStorageConfigs;
import com.here.naksha.app.service.http.auth.NakshaAuthProvider;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.IoHelp.LoadedBytes;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.view.ViewDeserialize;
import com.here.naksha.lib.hub.NakshaHubConfig;
import com.here.naksha.lib.psql.PsqlStorageConfig;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

public class TestUtil {

  private static final String TEST_DATA_FOLDER = "src/test/resources/unit_test_data/";
  public static final String HDR_STREAM_ID = "Stream-Id";

  private TestUtil() {}

  public static String loadFileOrFail(final @NotNull String rootPath, final @NotNull String fileName) {
    try {
      String json = new String(Files.readAllBytes(Paths.get(rootPath + fileName)));
      final PsqlStorageConfig dataDbConfig = TestPsqlStorageConfigs.dataDbConfig;
      json = json.replace("${dataDb.host}", dataDbConfig.host());
      json = json.replace("${dataDb.port}", Integer.toString(dataDbConfig.port()));
      json = json.replace("${dataDb.db}", dataDbConfig.db());
      json = json.replace("${dataDb.storageId}", dataDbConfig.storageId());
      json = json.replace("${dataDb.schema}", dataDbConfig.schema());
      json = json.replace("${dataDb.user}", dataDbConfig.user());
      json = json.replace("${dataDb.password}", dataDbConfig.password());
      json = json.replace("${dataDb.port}", Integer.toString(dataDbConfig.port()));
      final PsqlStorageConfig adminDbConfig = TestPsqlStorageConfigs.adminDbConfig;
      json = json.replace("${adminDb.host}", adminDbConfig.host());
      json = json.replace("${adminDb.port}", Integer.toString(adminDbConfig.port()));
      json = json.replace("${adminDb.db}", adminDbConfig.db());
      json = json.replace("${adminDb.storageId}", adminDbConfig.storageId());
      json = json.replace("${adminDb.schema}", adminDbConfig.schema());
      json = json.replace("${adminDb.user}", adminDbConfig.user());
      json = json.replace("${adminDb.password}", adminDbConfig.password());
      json = json.replace("${adminDb.port}", Integer.toString(dataDbConfig.port()));
      return json;
    } catch (IOException e) {
      Assertions.fail("Unable to read test file " + fileName, e);
      return null;
    }
  }

  public static String loadFileOrFail(final @NotNull String fileName) {
    return loadFileOrFail(TEST_DATA_FOLDER, fileName);
  }

  public static <T> T parseJson(final @NotNull String jsonStr, final @NotNull Class<T> type) {
    T obj = null;
    try (final Json json = Json.get()) {
      obj = json.reader(ViewDeserialize.Storage.class).forType(type).readValue(jsonStr);
    } catch (Exception ex) {
      Assertions.fail("Unable tor parse jsonStr " + jsonStr, ex);
      return null;
    }
    return obj;
  }

  public static <T> T parseJsonFileOrFail(final @NotNull String fileName, final @NotNull Class<T> type) {
    return parseJson(loadFileOrFail(fileName), type);
  }

  public static <T> T parseJsonFileOrFail(
      final @NotNull String rootPath, final @NotNull String fileName, final @NotNull Class<T> type) {
    return parseJson(loadFileOrFail(rootPath, fileName), type);
  }

  public static @NotNull NakshaContext newTestNakshaContext() {
    final NakshaContext nakshaContext = new NakshaContext().withAppId(NakshaHubConfig.defaultAppName());
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
    final LoadedBytes loaded = IoHelp.readBytesFromHomeOrResource(privateKeyPath, false, NakshaHubConfig.APP_NAME);
    final String jwtKey = new String(loaded.getBytes(), StandardCharsets.UTF_8);

    final JWTAuthOptions authOptions = new JWTAuthOptions()
            .setJWTOptions(new JWTOptions().setAlgorithm("RS256"))
            .addPubSecKey(new PubSecKeyOptions().setAlgorithm("RS256").setBuffer(jwtKey))
            ;
    final NakshaAuthProvider nakshaAuthProvider = new NakshaAuthProvider(Vertx.vertx(), authOptions);
    // Sign the following JWT payload
    return nakshaAuthProvider.generateToken(new JsonObject(payload));
  }
}
