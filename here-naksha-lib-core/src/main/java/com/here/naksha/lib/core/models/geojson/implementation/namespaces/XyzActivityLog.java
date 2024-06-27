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
package com.here.naksha.lib.core.models.geojson.implementation.namespaces;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.util.json.JsonObject;
import naksha.model.EXyzAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class XyzActivityLog extends JsonObject {
  public static final ObjectMapper mapper = new ObjectMapper();
  public static final String ID = "id";
  public static final String ORIGINAL = "original";
  public static final String ACTION = "action";
  public static final String INVALIDATED_AT = "invalidatedAt";
  public static final String DIFF = "diff";

  public XyzActivityLog() {
    this.original = new Original();
    this.diff = mapper.createObjectNode();
  }

  /** The Original tag. */
  @JsonProperty(ORIGINAL)
  private @NotNull Original original;

  /** The Difference tag. */
  @JsonInclude(Include.NON_EMPTY)
  @JsonProperty(DIFF)
  private JsonNode diff;

  /** The space ID the feature belongs to. */
  @JsonProperty(ID)
  private String id;

  /**
   * The operation that lead to the current state of the namespace. Should be a value from {@link
   * EXyzAction}.
   */
  @JsonProperty(ACTION)
  private String action;

  public @Nullable String getAction() {
    return action;
  }

  public void setAction(@Nullable String action) {
    this.action = action;
  }

  public @Nullable JsonNode getDiff() {
    return diff;
  }

  public void setDiff(@Nullable JsonNode diff) {
    this.diff = diff;
  }

  public void setAction(@NotNull EXyzAction action) {
    this.action = action.toString();
  }

  public @NotNull XyzActivityLog withAction(@Nullable String action) {
    setAction(action);
    return this;
  }

  public @NotNull XyzActivityLog withAction(@NotNull EXyzAction action) {
    setAction(action);
    return this;
  }

  public boolean isDeleted() {
    return EXyzAction.DELETE.equals(getAction());
  }

  public void setDeleted(boolean deleted) {
    if (deleted) {
      setAction(EXyzAction.DELETE);
    }
  }

  public @NotNull XyzActivityLog withDeleted(boolean deleted) {
    setDeleted(deleted);
    return this;
  }

  public @Nullable String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id;
  }

  public @NotNull Original getOriginal() {
    return original;
  }

  public void setOriginal(@NotNull Original original) {
    this.original = original;
  }
}
