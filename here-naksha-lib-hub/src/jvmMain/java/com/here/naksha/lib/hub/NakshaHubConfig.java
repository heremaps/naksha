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
package com.here.naksha.lib.hub;

import static naksha.base.JvmAnyObjectUtil.getOrSetProperty;
import static naksha.base.JvmAnyObjectUtil.getProperty;
import static naksha.base.JvmAnyObjectUtil.getPropertyOrReturnDefault;

import com.here.naksha.lib.core.util.json.JsonSerializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import naksha.base.AnyObject;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NakshaHubConfig extends NakshaFeature {

  private static final Logger logger = LoggerFactory.getLogger(NakshaHubConfig.class);

  /**
   * The default application name, used for example as identifier when accessing the PostgresQL database and to read the configuration file
   * using the <a href="https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html">XGD</a> standard, therefore from
   * directory ({@code ~/.config/<APP_NAME>/...}).
   */
  public static final @NotNull String NAKSHA_APP_NAME = "naksha";

  private static final String NAKSHA_ENV = "NAKSHA_ENV";

  /**
   * The default Http request body limit in MB.
   */
  public static final Integer DEF_REQ_BODY_LIMIT = 25;

  /**
   * The maximum Http request body limit in MB.
   */
  public static final Integer MAX_REQ_BODY_LIMIT = Math.max(25, DEF_REQ_BODY_LIMIT);

  /**
   * Returns a default application name used at many placed.
   *
   * @return The default application name.
   */
  public static @NotNull String defaultAppName() {
    return NAKSHA_APP_NAME + "/v" + NakshaVersion.current;
  }

  /**
   * Returns a className of default NakshaHub instance
   *
   * @return The default NakshaHub className
   */
  public static @NotNull String defaultHubClassName() {
    return NakshaHub.class.getName();
  }


  // property names
  public static final String HUB_CLASS_NAME = "hubClassName";
  public static final String USER_AGENT = "userAgent";
  public static final String APP_ID = "appId";
  public static final String AUTHOR = "author";
  public static final String HTTP_PORT = "httpPort";
  public static final String HOSTNAME = "hostname";
  public static final String ENDPOINT = "endpoint";
  public static final String ENV = "env";
  public static final String WEB_ROOT = "webRoot";
  public static final String NAKSHA_AUTH = "authMode";
  public static final String JWT_NAME = "jwtName";
  public static final String DEBUG = "debug";
  public static final String STORAGE_PARAMS = "storageParams";
  public static final String EXTENSION_CONFIG_PARAMS = "extensionConfigParams";
  public static final String REQUEST_BODY_LIMIT = "requestBodyLimit";
  public static final String MAX_PARALLEL_REQUESTS_PER_CPU = "maxParallelRequestsPerCPU";
  public static final String MAX_PCT_PARALLEL_REQUESTS_PER_ACTOR = "maxPctParallelRequestsPerActor";

  @Override
  public void onCreation() {
    setEndpointDetailsIfInvalid();
    resolveEnv();
    Integer requestBodyLimit = getProperty(this, REQUEST_BODY_LIMIT, Integer.class);
    if (requestBodyLimit != null && requestBodyLimit > MAX_REQ_BODY_LIMIT) {
      logger.warn(
          "Configured request body limit {} MB not supported. Falling back to default limit of {} MB",
          requestBodyLimit,
          DEF_REQ_BODY_LIMIT);
      setRequestBodyLimit(DEF_REQ_BODY_LIMIT);
    }
  }

  private void setEndpointDetailsIfInvalid() {
    String endpoint = getProperty(this, ENDPOINT, String.class);
    if (endpoint == null || endpoint.isEmpty()) {
      resolveInvalidEndpoint();
    } else {
      try {
        URL validEndpoint = new URL(endpoint);
        populateEndpointDetails(validEndpoint);
      } catch (MalformedURLException e) {
        logger.error("Invalid endpoint URL {}, resolving endpoint via 'hostname', 'port' and defaults...", endpoint, e);
        resolveInvalidEndpoint();
      }
    }
  }

  private void populateEndpointDetails(URL validEndpoint) {
    setHostname(validEndpoint.getHost());
    setHttpPort(validEndpoint.getPort());
  }

  private void resolveInvalidEndpoint() {
    int httpPort = getOrSetProperty(this, HTTP_PORT, 8080);
    if (httpPort < 0 || httpPort > 65535) {
      logger.atError()
          .setMessage("Invalid port in Naksha configuration: {}, changing to default 8080")
          .addArgument(httpPort)
          .log();
      httpPort = 8080;
      setHttpPort(8080);
    }
    String hostname = getOrSetProperty(this, HOSTNAME, "localhost");
    if (hostname.isBlank()) {
      try {
        hostname = InetAddress.getLocalHost().getHostAddress();
        logger.error("Naksha hostname is blank, changing to local host address: {}", hostname);
        setHostname(hostname);
      } catch (UnknownHostException e) {
        logger.error("Unable to resolve the hostname using Java's API, changing to 'localhost'");
        hostname = "localhost";
        setHostname(hostname);
      }
    }
    String rawEndpoint = null;
    try {
      rawEndpoint = "http://" + hostname + ":" + httpPort;
      new URL(rawEndpoint);
      setEndpoint(rawEndpoint);
    } catch (MalformedURLException e) {
      logger.error("Unable to parse ULR for endpoint {} because of invalid hostname {}. Will use 'localhost' instead", rawEndpoint,
          hostname);
      setHostname("localhost");
      setEndpoint("http://localhost:" + httpPort);
    }
  }

  private void resolveEnv() {
    // This is only to be backward compatible to support EC2 based deployment
    String envVal = System.getenv(NAKSHA_ENV);
    if (envVal != null && !envVal.isEmpty() && !"null".equalsIgnoreCase(envVal)) {
      setRaw(ENV, envVal);
    }
    String propEnv = getProperty(this, ENV, String.class);
    if (propEnv == null || propEnv.isEmpty() || "null".equalsIgnoreCase(propEnv)) {
      setRaw(ENV, "local");
    }
  }

  /**
   * The port at which to listen for HTTP requests.
   */
  public @NotNull Integer getHttpPort() {
    return getOrSetProperty(this, HTTP_PORT, 8080);
  }

  private void setHttpPort(@NotNull Integer httpPort) {
    setRaw(HTTP_PORT, httpPort);
  }

  /**
   * The hostname to use to refer to this instance, if {@code null}, then auto-detected.
   */
  public @NotNull String getHostname() {
    return getProperty(this, HOSTNAME, String.class);
  }

  private void setHostname(@NotNull String hostname) {
    setRaw(HOSTNAME, hostname);
  }

  /**
   * The application-id to be used when modifying the admin-database.
   */
  public @NotNull String getAppId() {
    return getOrSetProperty(this, APP_ID, "naksha");
  }


  /**
   * The author to be used when modifying the admin-database.
   */
  public @Nullable String getAuthor() {
    return getOrSetProperty(this, AUTHOR, defaultAppName());
  }

  /**
   * The public endpoint, for example "https://naksha.foo.com/".
   */
  public @NotNull String getEndpoint() {
    return getProperty(this, ENDPOINT, String.class);
  }

  private void setEndpoint(@NotNull String endpoint) {
    setRaw(ENDPOINT, endpoint);
  }

  /**
   * The environment, for example "local", "dev", "e2e" or "prd".
   */
  public @NotNull String getEnv() {
    return getProperty(this, ENV, String.class);
  }

  /**
   * If set, then serving static files from this directory.
   */
  public @Nullable String getWebRoot() {
    return getProperty(this, WEB_ROOT, String.class);
  }

  /**
   * The JWT key files to be read from the disk ({@code "~/.config/naksha/auth/$<jwtName>.(key|pub)"}).
   */
  public @NotNull String getJwtName() {
    return getOrSetProperty(this, JWT_NAME, "jwt");
  }

  /**
   * The user-agent to be used for external communication.
   */
  public @NotNull String getUserAgent() {
    return getOrSetProperty(this, USER_AGENT, defaultAppName());
  }

  /**
   * If debugging mode is enabled.
   */
  public boolean isDebug() {
    return getPropertyOrReturnDefault(this, DEBUG, false);
  }

  public void setDebug(boolean debug) {
    setRaw(DEBUG, debug);
  }

  /**
   * The fully qualified class name to be used to initiate NakshaHub instance
   */
  public @NotNull String getHubClassName() {
    return getOrSetProperty(this, HUB_CLASS_NAME, defaultHubClassName());
  }

  /**
   * Optional storage-specific parameters
   */
  public Map<String, Object> getStorageParams() {
    return getProperty(this, STORAGE_PARAMS, AnyObject.class);
  }

  /**
   * Optional extension-manager parameters
   */
  public @Nullable ExtensionConfigParams getExtensionConfigParams() {
    return getProperty(this, EXTENSION_CONFIG_PARAMS, ExtensionConfigParams.class);
  }

  /**
   * Optional Http request body limit in MB. Default is {@link #DEF_REQ_BODY_LIMIT}.
   */
  public Integer getRequestBodyLimit() {
    return getOrSetProperty(this, REQUEST_BODY_LIMIT, DEF_REQ_BODY_LIMIT);
  }

  private void setRequestBodyLimit(@NotNull Integer bodyLimit) {
    setRaw(REQUEST_BODY_LIMIT, bodyLimit);
  }

  /**
   * Optional Total Concurrency Limit
   */
  public Integer getMaxParallelRequestsPerCPU() {
    return getProperty(this, MAX_PARALLEL_REQUESTS_PER_CPU, Integer.class);
  }

  /**
   * Optional Total Author Concurrency Threshold
   */
  public Integer getMaxPctParallelRequestsPerActor() {
    return getProperty(this, MAX_PCT_PARALLEL_REQUESTS_PER_ACTOR, Integer.class);
  }

  /**
   * The authorization mode.
   */
  public @NotNull AuthorizationMode getAuthMode() {
    String raw = getOr(NAKSHA_AUTH, AuthorizationMode.JWT.name()); // proxy model does not play well with JVM enums
    return AuthorizationMode.fromString(raw);
  }

  /**
   * Returns a default threshold per processor for concurrency
   *
   * @return the default threshold per processor
   */
  private static int defaultMaxParallelRequestsPerCPU() {
    return 30;
  }

  /**
   * Returns a default percentage threshold per principal for concurrency
   *
   * @return the default percentage threshold per principal
   */
  private static int defaultMaxPctParallelRequestsPerActor() {
    return 25;
  }

  public enum AuthorizationMode {
    DUMMY,
    JWT;

    static AuthorizationMode fromString(String mode) {
      return AuthorizationMode.valueOf(mode.toUpperCase());
    }
  }
}
