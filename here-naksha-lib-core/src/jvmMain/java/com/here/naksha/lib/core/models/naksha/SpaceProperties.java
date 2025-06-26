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
package com.here.naksha.lib.core.models.naksha;

import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.Nullable;

/**
 * Default variant of Space properties supported by Naksha - default storage handler
 */
@AvailableSince(NakshaVersion.v2_0_7)
public class SpaceProperties extends NakshaProperties {

  @AvailableSince(NakshaVersion.v2_0_7)
  public static final String NAKSHA_COLLECTION = "collection";

  /**
   * The backend storage collection details specified at space level
   */
  public @Nullable NakshaCollection getCollection() {
    return getAs(NAKSHA_COLLECTION, NakshaCollection.TYPE);
  }

  public void setCollection(final @Nullable NakshaCollection collection) {
    set(NAKSHA_COLLECTION, collection);
  }
}