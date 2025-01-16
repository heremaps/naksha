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
import org.jetbrains.annotations.Nullable;

/**
 * Default variant of EventHandler properties supported by Naksha - for TagFilterHandler
 */
@AvailableSince(NakshaVersion.v2_0_13)
public class TagFilterHandlerProperties extends NakshaProperties {

  @AvailableSince(NakshaVersion.v2_0_13)
  public static final String ADD_VALUES = "add";

  @AvailableSince(NakshaVersion.v2_0_13)
  public static final String REMOVE_W_PREFIXES = "removeWithPrefixes";

  @AvailableSince(NakshaVersion.v2_0_13)
  public static final String CONTAINS_VALUES = "contains";

  /**
   * To specify list of tags to be added to the {@link naksha.model.objects.NakshaFeature} during create/update
   * {@link naksha.model.request.WriteRequest} operations.
   */
  public @Nullable List<String> getAdd() {
    return JvmBoxingUtil.box(get(ADD_VALUES), StringList.class);
  }

  public void setAdd(@Nullable final List<String> add) {
    setRaw(ADD_VALUES, StringList.fromList(add));
  }

  /**
   * To specify prefix-matching tags to be removed from the {@link naksha.model.objects.NakshaFeature} during create/update
   * {@link naksha.model.request.WriteRequest} operations. This is applied before {@link #getAdd()} operation.
   */
  public @Nullable List<String> getRemoveWithPrefixes() {
    return JvmBoxingUtil.box(get(REMOVE_W_PREFIXES), StringList.class);
  }

  public void setRemoveWithPrefixes(final @Nullable List<String> removeWithPrefixes) {
    setRaw(REMOVE_W_PREFIXES, StringList.fromList(removeWithPrefixes));
  }

  /**
   * To specify list of tags to be added as AND filter condition whenever {@link naksha.model.request.ReadFeatures} is processed via this
   * handler.
   */
  public @Nullable List<String> getContains() {
    return JvmBoxingUtil.box(get(CONTAINS_VALUES), StringList.class);
  }

  public void setContains(@Nullable List<String> contains) {
    setRaw(CONTAINS_VALUES, StringList.fromList(contains));
  }
}
