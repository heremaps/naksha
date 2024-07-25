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
package com.here.naksha.lib.heapcache;

import naksha.model.NakshaFeatureProxy;
import com.here.naksha.lib.core.storage.CollectionInfo;
import com.here.naksha.lib.core.storage.IFeatureWriter;
import com.here.naksha.lib.core.storage.IMasterTransaction;
import org.jetbrains.annotations.NotNull;

public class HeapMasterTx extends HeapReadTx implements IMasterTransaction {
  HeapMasterTx(@NotNull HeapCache cache) {
    super(cache);
  }

  @Override
  public void commit() {}

  @Override
  public void rollback() {}

  @Override
  public boolean acquireLock(final @NotNull String lockKey) {
    return false;
  }

  @Override
  public boolean releaseLock(final @NotNull String lockKey) {
    return false;
  }

  @Override
  public @NotNull CollectionInfo createCollection(@NotNull CollectionInfo collection) {
    // Implement collection creation logic
    return collection;
  }

  @Override
  public @NotNull CollectionInfo updateCollection(@NotNull CollectionInfo collection) {
    // Implement collection update logic
    return collection;
  }

  @Override
  public @NotNull CollectionInfo upsertCollection(@NotNull CollectionInfo collection) {
    // Implement collection update logic
    return collection;
  }

  @Override
  public @NotNull CollectionInfo deleteCollection(@NotNull CollectionInfo collection, long deleteAt) {
    // Implement collection update logic
    return collection;
  }

  @Override
  public @NotNull CollectionInfo dropCollection(@NotNull CollectionInfo collection) {
    // Implement collection update logic
    return collection;
  }

  @Override
  public @NotNull CollectionInfo enableHistory(@NotNull CollectionInfo collection) {
    // Implement collection update logic
    return collection;
  }

  @Override
  public @NotNull CollectionInfo disableHistory(@NotNull CollectionInfo collection) {
    // Implement collection update logic
    return collection;
  }

  @Override
  public @NotNull <F extends NakshaFeatureProxy> IFeatureWriter<F> writeFeatures(
      @NotNull Class<F> featureClass, @NotNull CollectionInfo collection) {
    return new HeapFeatureWriter<>(cache, featureClass, collection);
  }
}
