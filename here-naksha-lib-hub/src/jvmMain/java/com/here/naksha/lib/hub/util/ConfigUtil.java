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
package com.here.naksha.lib.hub.util;

import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.view.ViewDeserialize;
import com.here.naksha.lib.hub.NakshaHubConfig;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import naksha.base.AnyObject;
import naksha.base.Platform;
import naksha.base.PlatformType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class ConfigUtil {

  private static final Logger logger = LoggerFactory.getLogger(ConfigUtil.class);

  public static final String DEF_CFG_PATH_ENV = "NAKSHA_CONFIG_PATH";

  private ConfigUtil() {}

  /**
   * Read the Naksha configuration from the file-system.
   *
   * <p>This method will search for the configuration file in this order:
   * <li>Search for <code>${configId}.json</code> in the current working directory.
   * <li>If the environment variable <code>NAKSHA_CONFIG_PATH</code> is defined, search for <code>$NAKSHA_CONFIG_PATH/${configId}.json</code>
   * <li>Searches for <code>$XDG_CONFIG_HOME/$appName/${configId}.json</code> with <code>XDG_CONFIG_HOME</code> defaulting to <code>~/.config/</code>.
   * <li>Finally, search in the resources of the <code>naksha.jar</code> for a <code>${configId}.json</code></li>
   * @param configId The identifier of configuration file to load.
   * @param appName The application name to use for searching.
   * @param type The type of the configuration file to load.
   * @return the configuration file, if found anywhere along the search paths.
   * @throws IOException if the file was not found.
   */
  public static <CONFIG extends AnyObject> @NotNull CONFIG readConfigFile(
      final @NotNull String configId,
      final @NotNull String appName,
      final @NotNull PlatformType<CONFIG> type
  ) {
    // use the path provided in NAKSHA_CONFIG_PATH (if it is set)
    final String envVal = System.getenv(DEF_CFG_PATH_ENV);
    final String customPath = envVal == null || envVal.isEmpty() || "null".equalsIgnoreCase(envVal) ? null : envVal;
    // attempt loading config from file
    final var loaded = IoHelp.readBytesFromHomeOrResource(configId + ".json", true, appName, customPath);
    logger.info("Fetched supplied server config from {}", loaded.getPath());
    final var bytes = loaded.getBytes();
    final var json = new String(bytes, StandardCharsets.UTF_8);
    final var config = requireNonNull(Platform.fromJson(json, type));
    logger.info("Loaded config: {}", json);
    return config;
  }

  /**
   * Read the Naksha configuration from the file-system.
   *
   * <p>This method will search for the configuration file in this order:
   * <li>Search for <code>${configId}.json</code> in the current working directory.
   * <li>If the environment variable <code>NAKSHA_CONFIG_PATH</code> is defined, search for <code>$NAKSHA_CONFIG_PATH/${configId}.json</code>
   * <li>Searches for <code>$XDG_CONFIG_HOME/naksha/${configId}.json</code> with <code>XDG_CONFIG_HOME</code> defaulting to <code>~/.config/</code>.
   * <li>Finally, search in the resources of the <code>naksha.jar</code> for a <code>${configId}.json</code></li>
   * @param configId The identifier of configuration file to load.
   * @param type The type of the configuration file to load.
   * @return the configuration file, if found anywhere along the search paths.
   */
  public static <CONFIG extends AnyObject> CONFIG readConfigFile(
      final @NotNull String configId,
      final @NotNull PlatformType<CONFIG> type
  ) {
    return readConfigFile(configId, NakshaHubConfig.NAKSHA_APP_NAME, type);
  }

  public static String readAuthKeyFile(final @NotNull String keyFilePath, final @NotNull String appName) {
    // use the path provided in NAKSHA_CONFIG_PATH (if it is set)
    final String envVal = System.getenv(DEF_CFG_PATH_ENV);
    final String path = envVal == null || envVal.isEmpty() || "null".equalsIgnoreCase(envVal) ? null : envVal;
    // attempt loading key from file
    final IoHelp.LoadedBytes loaded = IoHelp.readBytesFromHomeOrResource(keyFilePath, false, appName, path);
    logger.info("Loaded auth key file {}", loaded.getPath());
    return new String(loaded.getBytes(), StandardCharsets.UTF_8);
  }
}
