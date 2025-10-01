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

import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * A Http storage configuration as used by the {@link HttpStorage}.
 */
@AvailableSince(NakshaVersion.v2_0_12)
public class HttpStorageProperties extends NakshaProperties {

  public static final Integer DEF_CONNECTION_TIMEOUT_SEC = 20;
  public static final Integer DEF_SOCKET_TIMEOUT_SEC = 90;
  public static final Map<String, String> DEFAULT_HEADERS = Map.of(
      "Content-Type", "application/json",
      "Accept-Encoding", "gzip");

  static final String URL = "url";
  private static final String CONNECTION_TIMEOUT = "connectTimeout";
  private static final String SOCKET_TIMEOUT = "socketTimeout";
  private static final String HEADERS = "headers";

  private static final String HTTP_INTERFACE = "httpInterface";
  private static final HttpInterface DEFAULT_XYZ_PROTOCOL = HttpInterface.ffwAdapter;

  public HttpStorageProperties() {}

  /**
   * Points to the instance, not to an endpoint.
   */
  public @NotNull String getUrl() {
    return (String) getRaw(URL);
  }

  public void setUrl(final @Nullable String url) {
    setRaw(URL, url);
  }

  /**
   * The connection timeout in seconds.
   * By default: 20
   */
  public @NotNull Integer getConnectTimeout() {
    return getOrSet(CONNECTION_TIMEOUT, DEF_CONNECTION_TIMEOUT_SEC);
  }

  public void setConnectTimeout(final @Nullable Integer connectTimeout) {
    setRaw(CONNECTION_TIMEOUT, connectTimeout);
  }

  /**
   * The socket timeout in seconds.
   * By default: 90
   */
  public @NotNull Integer getSocketTimeout() {
    return getOrSet(SOCKET_TIMEOUT, DEF_SOCKET_TIMEOUT_SEC);
  }

  public void setSocketTimeout(final @Nullable Integer socketTimeout) {
    setRaw(SOCKET_TIMEOUT, socketTimeout);
  }

  /**
   * The HTTP headers to be sent with each request.
   * By default: 'Content-Type: application/json' and 'Accept-Encoding: gzip'
   */
  public @NotNull Map<String, String> getHeaders() {
    return getOrSet(HEADERS, DEFAULT_HEADERS);
  }

  public void setHeaders(final @Nullable Map<String, String> headers) {
    setRaw(HEADERS, headers);
  }

  public @NotNull HttpInterface getProtocol() {
    final Object raw = getRaw(HTTP_INTERFACE);
    if (raw instanceof HttpInterface) {
      return (HttpInterface) raw;
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

}
