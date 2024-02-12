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
package com.here.naksha.lib.handlers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import java.util.List;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

@AvailableSince(NakshaVersion.v2_0_12)
public class DefaultViewHandlerProperties extends XyzProperties {

  @AvailableSince(NakshaVersion.v2_0_12)
  public static final String STORAGE_ID = "storageId";

  @AvailableSince(NakshaVersion.v2_0_12)
  public static final String SPACE_IDS = "spaceIds";

  @AvailableSince(NakshaVersion.v2_0_12)
  @JsonProperty(STORAGE_ID)
  private @NotNull String storageId;

  @AvailableSince(NakshaVersion.v2_0_12)
  @JsonProperty(SPACE_IDS)
  private @NotNull List<String> spaceIds;

  @AvailableSince(NakshaVersion.v2_0_12)
  @JsonCreator
  public DefaultViewHandlerProperties(
      final @JsonProperty(STORAGE_ID) @NotNull String storageId,
      final @JsonProperty(SPACE_IDS) @NotNull List<String> spaceIds) {
    this.storageId = storageId;
    this.spaceIds = spaceIds;
  }

  public @NotNull String getStorageId() {
    return storageId;
  }

  public void setStorageId(@NotNull String storageId) {
    this.storageId = storageId;
  }

  public @NotNull List<String> getSpaceIds() {
    return spaceIds;
  }

  public void setSpaceIds(@NotNull List<String> spaceIds) {
    this.spaceIds = spaceIds;
  }
}
