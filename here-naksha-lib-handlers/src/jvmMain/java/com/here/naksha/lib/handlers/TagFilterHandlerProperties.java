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

import naksha.base.PlatformType;
import naksha.base.StringList;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.Nullable;

import static naksha.base.Platform.forClass;

/**
 * Default variant of EventHandler properties supported by Naksha - for TagFilterHandler
 */
@AvailableSince(NakshaVersion.v2_0_13)
public class TagFilterHandlerProperties extends NakshaProperties {

  public static final PlatformType<TagFilterHandlerProperties> TYPE = forClass(TagFilterHandlerProperties.class);

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
  public @Nullable StringList getAdd() {
    return getAs(ADD_VALUES, StringList.TYPE);
  }

  public void setAdd(@Nullable final List<String> add) {
    set(ADD_VALUES, StringList.fromList(add));
  }

  /**
   * To specify prefix-matching tags to be removed from the {@link naksha.model.objects.NakshaFeature} during create/update
   * {@link naksha.model.request.WriteRequest} operations. This is applied before {@link #getAdd()} operation.
   */
  public @Nullable StringList getRemoveWithPrefixes() {
    return getAs(REMOVE_W_PREFIXES, StringList.TYPE);
  }

  public void setRemoveWithPrefixes(final @Nullable List<String> removeWithPrefixes) {
    set(REMOVE_W_PREFIXES, StringList.fromList(removeWithPrefixes));
  }

  /**
   * To specify list of tags to be added as AND filter condition whenever {@link naksha.model.request.ReadFeatures} is processed via this
   * handler.
   */
  public @Nullable StringList getContains() {
    return getAs(CONTAINS_VALUES, StringList.TYPE);
  }

  public void setContains(@Nullable List<String> contains) {
    set(CONTAINS_VALUES, StringList.fromList(contains));
  }
}
