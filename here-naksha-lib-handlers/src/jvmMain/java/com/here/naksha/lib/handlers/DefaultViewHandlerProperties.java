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

import java.util.Arrays;
import java.util.List;
import naksha.base.PlatformType;
import naksha.base.StringList;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.Platform.forClass;

@AvailableSince(NakshaVersion.v2_0_12)
public class DefaultViewHandlerProperties extends NakshaProperties {

  public static final PlatformType<DefaultViewHandlerProperties> TYPE = forClass(DefaultViewHandlerProperties.class);

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

  public @Nullable StringList getSpaceIds() {
    return getAs(SPACE_IDS, StringList.TYPE);
  }

  public void setSpaceIds(@Nullable List<String> spaceIds) {
    setRaw(SPACE_IDS, StringList.fromList(spaceIds));
  }

  public @NotNull ViewType getViewType() {
    final Object raw = getRaw(VIEW_TYPE);
    if (raw instanceof ViewType) {
      return (ViewType) raw;
    }
    if (raw instanceof String) {
      try {
        return ViewType.valueOf((String) raw);
      } catch (IllegalArgumentException e) {
        final String errorMessage = String.format(
                "Invalid value for the 'viewType' property. The value '%s' is not supported. Please use one of: %s",
                raw,
                Arrays.toString(ViewType.values()));
        throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, errorMessage);
      }
    }
    return ViewType.LAYERED;
  }

  public void setViewType(@NotNull ViewType viewType) {
    set(VIEW_TYPE, viewType);
  }

  public enum ViewType {
    LAYERED,
    UNION
  }
}
