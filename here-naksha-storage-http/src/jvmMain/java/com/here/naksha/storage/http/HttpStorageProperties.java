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
package com.here.naksha.storage.http;

import com.here.naksha.lib.circuitbreaker.models.CircuitBreakerProps;
import naksha.base.JvmBoxingUtil;
import naksha.base.JvmMapProxy;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Configuration properties for HTTP storage backends.
 */
@AvailableSince(NakshaVersion.v2_0_12)
public class HttpStorageProperties extends NakshaProperties {

  static final class HeaderMap extends JvmMapProxy<String, String> {
    private HeaderMap() {
      super(String.class, String.class);
    }

    private void putHeaders(@NotNull Map<String, String> headers) {
      headers.forEach(this::put);
    }
  }

  public static final Integer DEF_CONNECTION_TIMEOUT_SEC = 20;
  public static final Integer DEF_SOCKET_TIMEOUT_SEC = 90;
  public static final Integer DEF_MAX_RETRIES = 1;
  public static final Map<String, String> DEFAULT_HEADERS = Map.of(
      "Content-Type", "application/json",
      "Accept-Encoding", "gzip");

  static final String URL = "url";
  private static final String CONNECTION_TIMEOUT = "connectTimeout";
  private static final String SOCKET_TIMEOUT = "socketTimeout";
  private static final String MAX_RETRIES = "maxRetries";
  private static final String HEADERS = "headers";

  private static final String HTTP_INTERFACE = "httpInterface";
  private static final String CIRCUIT_BREAKER = "circuitBreaker";
  private static final HttpInterface DEFAULT_XYZ_PROTOCOL = HttpInterface.ffwAdapter;

  public HttpStorageProperties() {}

  /**
   * The base URL of the remote HTTP storage backend.
   *
   * <p>Required: Yes
   *
   * <p>Points to the instance, not to an endpoint.
   */
  public @NotNull String getUrl() {
    return (String) getRaw(URL);
  }

  public void setUrl(final @Nullable String url) {
    setRaw(URL, url);
  }

  /**
   * Timeout for establishing an HTTP connection to the remote storage, in seconds.
   *
   * <p>Required: Yes
   * <p>Unit: Seconds
   *
   * <p>By default: 20
   */
  public @NotNull Integer getConnectTimeout() {
    return getOrSet(CONNECTION_TIMEOUT, DEF_CONNECTION_TIMEOUT_SEC);
  }

  public void setConnectTimeout(final @Nullable Integer connectTimeout) {
    setRaw(CONNECTION_TIMEOUT, connectTimeout);
  }

  /**
   * Timeout for waiting on a response from the remote storage, in seconds.
   *
   * <p>Required: Yes
   * <p>Unit: Seconds
   *
   * <p>By default: 90
   */
  public @NotNull Integer getSocketTimeout() {
    return getOrSet(SOCKET_TIMEOUT, DEF_SOCKET_TIMEOUT_SEC);
  }

  public void setSocketTimeout(final @Nullable Integer socketTimeout) {
    setRaw(SOCKET_TIMEOUT, socketTimeout);
  }

  /**
   * Number of retry attempts for retryable transport failures.
   *
   * <p>When a request fails due to a retryable error (e.g., connection reset, timeout), Naksha
   * will automatically retry up to this many times before giving up. Non-retryable errors are
   * not retried.
   *
   * <p>By default: 1
   */
  public @NotNull Integer getMaxRetries() {
    return getOrSet(MAX_RETRIES, DEF_MAX_RETRIES);
  }

  public void setMaxRetries(final @Nullable Integer maxRetries) {
    setRaw(MAX_RETRIES, maxRetries);
  }

  /**
   * Default HTTP headers added to every request sent to this storage.
   *
   * <p>These headers are merged with request-specific headers. Common use cases include
   * Authorization tokens, API keys, or custom identifiers.
   *
   * <p>By default: 'Content-Type: application/json' and 'Accept-Encoding: gzip'
   */
  public @NotNull Map<String, String> getHeaders() {
    final Object raw = getPath(HEADERS);
    if (raw instanceof HeaderMap) {
      return (HeaderMap) raw;
    }
    if (raw instanceof Map<?, ?>) {
      HeaderMap headers = toHeaderMap((Map<?, ?>) raw);
      setRaw(HEADERS, headers);
      return headers;
    }
    HeaderMap headers = new HeaderMap();
    headers.putHeaders(DEFAULT_HEADERS);
    setRaw(HEADERS, headers);
    return headers;
  }

  public void setHeaders(final @Nullable Map<String, String> headers) {
    if (headers == null) {
      setRaw(HEADERS, null);
      return;
    }
    HeaderMap headerMap = new HeaderMap();
    headerMap.putHeaders(headers);
    setRaw(HEADERS, headerMap);
  }

  /**
   * HTTP protocol interface mode for communication with the remote storage.
   *
   * <p>Determines how Naksha formats requests and parses responses from the storage backend.
   *
   * <p>See: HttpInterface enum for available modes
   */
  public @NotNull HttpInterface getProtocol() {
    final Object raw = getRaw(HTTP_INTERFACE);
    if (raw instanceof HttpInterface) {
      HttpInterface httpInterface = (HttpInterface) raw;
      return httpInterface;
    }
    if (raw instanceof String) {
      try {
        return HttpInterface.valueOf((String) raw);
      } catch (IllegalArgumentException e) {
        final String errorMessage = String.format(
                "Invalid value for the 'HttpInterface' property. The value '%s' is not supported. Please use one of: %s",
                raw,
                Arrays.toString(HttpInterface.values()));
        throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, errorMessage);
      }
    }
    return DEFAULT_XYZ_PROTOCOL;
  }

  public void setProtocol(final HttpInterface protocol) {setRaw(HTTP_INTERFACE, protocol);}

  /**
   * Optional circuit breaker configuration for this storage.
   *
   * <p>If configured, the circuit breaker monitors requests triggered via this storage.
   * When the slow request rate exceeds the configured threshold, the circuit breaker
   * opens to throttle traffic.
   *
   * <p>This enables per-storage isolation: if one storage times out, only its traffic is
   * throttled. Requests to other storages continue unaffected.
   *
   * <p>Default: null (no circuit breaking)
   *
   * <p>See: CircuitBreakerProps for configuration options
   */
  public @Nullable CircuitBreakerProps getCircuitBreakerConfig() {
    final Object raw = getRaw(CIRCUIT_BREAKER);
    if (raw == null) {
      return null;
    }
    // v2 (Jackson) rejected a non-object circuitBreaker value (e.g. a string or boolean)
    if (!(raw instanceof naksha.base.PlatformMap)) {
      throw new IllegalArgumentException("circuitBreaker must be an object");
    }
    return JvmBoxingUtil.box(raw, CircuitBreakerProps.class);
  }

  public void setCircuitBreakerConfig(@Nullable CircuitBreakerProps config) {
    setRaw(CIRCUIT_BREAKER, config);
  }

  private @NotNull HeaderMap toHeaderMap(@NotNull Map<?, ?> rawHeaders) {
    HeaderMap headers = new HeaderMap();
    rawHeaders.forEach((key, value) -> {
      if (key instanceof String && value instanceof String) {
        headers.put((String) key, (String) value);
      }
    });
    return headers;
  }

}
