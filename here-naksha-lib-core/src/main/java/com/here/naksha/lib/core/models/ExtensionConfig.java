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
package com.here.naksha.lib.core.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.models.features.Extension;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JsonTypeName
public class ExtensionConfig {

  public static final String EXPIRY = "expiry";
  public static final String EXTENSIONS = "extensions";
  public static final String WHITELIST_DELEGATE_CLASS = "whitelistDelegateClass";
  public static final String EXTENSIONS_ROOT_PATH = "extensionsRootPath";

  @JsonProperty(EXPIRY)
  long expiry;

  @JsonProperty(EXTENSIONS)
  List<Extension> extensions;

  @JsonProperty(WHITELIST_DELEGATE_CLASS)
  List<String> whitelistDelegateClass;

  @JsonProperty(EXTENSIONS_ROOT_PATH)
  String extensionsRootPath;

  @JsonCreator
  public ExtensionConfig(
      @JsonProperty(EXPIRY) @NotNull long expiry,
      @JsonProperty(EXTENSIONS_ROOT_PATH) @NotNull String extensionsRootPath) {
    this.expiry = expiry;
    this.extensions = new ArrayList<>();
    this.extensionsRootPath = extensionsRootPath;
    this.whitelistDelegateClass = new ArrayList<>();
  }

  public ExtensionConfig(
      @JsonProperty(EXPIRY) @NotNull long expiry,
      @JsonProperty(EXTENSIONS) @Nullable List<Extension> extensions,
      @JsonProperty(EXTENSIONS_ROOT_PATH) @NotNull String extensionsRootPath,
      @JsonProperty(WHITELIST_DELEGATE_CLASS) @Nullable List<String> whitelistDelegateClass) {
    this(expiry, extensionsRootPath);
    this.extensions = extensions;
    this.whitelistDelegateClass = whitelistDelegateClass;
  }

  public long getExpiry() {
    return expiry;
  }

  public void setExpiry(long expiry) {
    this.expiry = expiry;
  }

  public List<Extension> getExtensions() {
    return extensions;
  }

  public void setExtensions(List<Extension> extensions) {
    this.extensions = extensions;
  }

  public List<String> getWhilelistDelegateClass() {
    return whitelistDelegateClass;
  }

  public void setWhilelistDelegateClass(List<String> whitelistDelegateClass) {
    this.whitelistDelegateClass = whitelistDelegateClass;
  }

  public String getExtensionsRootPath() {
    return extensionsRootPath;
  }

  public void setExtensionsRootPath(String extensionsRootPath) {
    this.extensionsRootPath = extensionsRootPath;
  }
}
