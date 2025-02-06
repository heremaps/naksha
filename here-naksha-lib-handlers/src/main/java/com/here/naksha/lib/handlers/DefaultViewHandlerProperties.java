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
package com.here.naksha.lib.handlers;

import java.util.List;
import naksha.base.JvmBoxingUtil;
import naksha.base.StringList;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@AvailableSince(NakshaVersion.v2_0_12)
public class DefaultViewHandlerProperties extends NakshaProperties {

  @AvailableSince(NakshaVersion.v2_0_12)
  public static final String STORAGE_ID = "storageId";

  @AvailableSince(NakshaVersion.v2_0_12)
  public static final String SPACE_IDS = "spaceIds";

  @AvailableSince(NakshaVersion.v2_0_15)
  public static final String VIEW_TYPE = "viewType";

  public @Nullable String getStorageId() {
    return (String) getRaw(STORAGE_ID);
  }

  public void setStorageId(@Nullable String storageId) {
    setRaw(STORAGE_ID, storageId);
  }

  public @Nullable List<String> getSpaceIds() {
    return JvmBoxingUtil.box(get(SPACE_IDS), StringList.class);
  }

  public void setSpaceIds(@Nullable List<String> spaceIds) {
    setRaw(SPACE_IDS, StringList.fromList(spaceIds));
  }

  public @NotNull ViewType getViewType() {
    return JvmBoxingUtil.box(get(VIEW_TYPE), ViewType.class);
  }

  public void setViewType(@NotNull ViewType viewType) {
    setRaw(VIEW_TYPE, viewType);
  }

  public enum ViewType {
    LAYERED,
    UNION
  }
}
