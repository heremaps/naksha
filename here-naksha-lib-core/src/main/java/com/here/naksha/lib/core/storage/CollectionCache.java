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
package com.here.naksha.lib.core.storage;

import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;

/**
 * An in-memory cache for a storage collection. It can be configured to only keep parts of the
 * underlying storage collection in memory using weak references or to keep all features of the
 * storage in memory.
 */
@Deprecated
public abstract class CollectionCache<FEATURE extends NakshaFeature> {
  // TODO: Implement me!

  /**
   * Returns the collection this caches operates on.
   *
   * @return the collection this caches operates on.
   */
  public abstract @NotNull CollectionInfo collection();

  /**
   * Returns the feature reader for this collection.
   *
   * @return the feature reader for this collection.
   */
  public abstract @NotNull IFeatureReader<FEATURE> featureReader();
}
