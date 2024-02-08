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
package com.here.naksha.storage.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Http storage configuration as used by the {@link HttpStorage}.
 */
@AvailableSince(NakshaVersion.v2_0_12)
public class HttpStorageProperties extends XyzProperties {

  @JsonCreator
  public HttpStorageProperties(
      @JsonProperty(value = "url", required = true) @NotNull String url,
      @JsonProperty("connectTimeout") @Nullable Long connectTimeout,
      @JsonProperty("socketTimeout") @Nullable Long socketTimeout,
      @JsonProperty("headers") @Nullable Map<String, String> headers) {
    this.url = url;
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.headers = headers;
  }

  // Mandatory fields
  public @NotNull String url;

  // Optional fields
  public @Nullable Long connectTimeout;
  public @Nullable Long socketTimeout;
  public @Nullable Map<String, String> headers;
}
