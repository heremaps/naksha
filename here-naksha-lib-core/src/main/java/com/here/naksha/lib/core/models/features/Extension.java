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
package com.here.naksha.lib.core.models.features;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension is an administrative feature that allows to run proprietary code, outside the Naksha-Hub using proprietary libraries.
 */
@AvailableSince(NakshaVersion.v2_0_3)
@JsonTypeName
public class Extension extends XyzFeature {
  public static final String EXTENSION_ID = "extensionId";
  public static final String URL = "url";
  public static final String VERSION = "version";
  public static final String INIT_CLASS_NAME = "initClassName";
  //  public static final String PROPERTIES = "properties";

  @JsonProperty(EXTENSION_ID)
  String extensionId;

  @JsonProperty(URL)
  String url;

  @JsonProperty(VERSION)
  String version;

  @JsonProperty(INIT_CLASS_NAME)
  String initClassName;

  //  @JsonProperty(PROPERTIES)
  //  Object properties;

  @AvailableSince(NakshaVersion.v2_0_3)
  //  @JsonCreator
  public Extension(
      @JsonProperty(EXTENSION_ID) @NotNull String extensionId,
      @JsonProperty(URL) @NotNull String url,
      @JsonProperty(VERSION) @NotNull String version) {
    this.extensionId = extensionId;
    this.url = url;
    this.version = version;
  }

  /**
   * Create an extension.
   *
   * @param extensionId  Unique identifier of extension.
   * @param url source url of given extension.
   * @param version version of extension.
   * @param initClassName Extension initialisation class.
   * @param properties properties required by initialisation class.
   */
  @AvailableSince(NakshaVersion.v2_0_3)
  @JsonCreator
  public Extension(
      @JsonProperty(EXTENSION_ID) @NotNull String extensionId,
      @JsonProperty(URL) @NotNull String url,
      @JsonProperty(VERSION) @NotNull String version,
      @JsonProperty(INIT_CLASS_NAME) @Nullable String initClassName,
      @JsonProperty(PROPERTIES) @Nullable Object properties) {
    this(extensionId, url, version);
    this.initClassName = initClassName;
    //    this.properties = properties;
  }

  public String getExtensionId() {
    return extensionId;
  }

  public void setExtensionId(String extensionId) {
    this.extensionId = extensionId;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getInitClassName() {
    return initClassName;
  }

  public void setInitClassName(String initClassName) {
    this.initClassName = initClassName;
  }
}
