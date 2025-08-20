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

import com.here.naksha.lib.core.CollectionRef;
import naksha.base.PlatformType;
import naksha.model.NakshaVersion;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.Nullable;

import static naksha.base.Platform.forClass;

/**
 * Default variant of Space properties supported by Naksha - default storage handler
 */
@AvailableSince(NakshaVersion.v2_0_7)
public class SpaceProperties extends NakshaProperties {

  public static final PlatformType<SpaceProperties> TYPE = forClass(SpaceProperties.class);

  /**
   * The constant string <code>collection</code>.
   * @since 3.0
   */
  public static final String COLLECTION = "collection";

  // TODO: alweber: To review with Jakub

  /**
   * Returns the explicit collection to which this space is bound, if any. Technically a simple JSON object like:
   * <pre>{@code
   * {
   *   "id": {collection-id}
   *   "mapId": {map-id}
   *   "storageId": {storage-id}
   * }
   * }</pre>
   * @return the collection reference or <code>null</code>, if no such information is available.
   */
  // TODO: alweber: Review with Jakub
  public @Nullable CollectionRef getCollectionRef() {
    return getAs(COLLECTION, CollectionRef.TYPE);
  }

  /**
   * Sets the collection reference.
   * @param ref the collection reference to set.
   */
  // TODO: alweber: Review with Jakub
  public void setCollectionRef(final @Nullable CollectionRef ref) {
    set(COLLECTION, ref);
  }

}