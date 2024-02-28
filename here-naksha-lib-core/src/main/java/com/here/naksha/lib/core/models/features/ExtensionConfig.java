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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JsonTypeName
public class ExtensionConfig extends XyzFeature {

  public static final String EXPIRY = "expiry";
  public static final String EXTENSIONS = "extensions";
  public static final String WHITELIST_DELEGATE_CLASS = "whitelistDelegateClass";

  @JsonProperty(EXPIRY)
  long expiry;

  @JsonProperty(EXTENSIONS)
  List<Extension> extensions;

  @JsonProperty(WHITELIST_DELEGATE_CLASS)
  List<String> whitelistDelegateClass;

  public ExtensionConfig(
      @JsonProperty(EXPIRY) @NotNull long expiry,
      @JsonProperty(EXTENSIONS) @NotNull List<Extension> extensions,
      @JsonProperty(WHITELIST_DELEGATE_CLASS) @Nullable List<String> whitelistDelegateClass) {
    this.expiry = expiry;
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
}
