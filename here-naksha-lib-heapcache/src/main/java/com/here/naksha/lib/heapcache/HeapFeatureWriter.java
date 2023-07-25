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
package com.here.naksha.lib.heapcache;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.storage.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HeapFeatureWriter<F extends XyzFeature> extends HeapFeatureReader<F> implements IFeatureWriter<F> {

  HeapFeatureWriter(@NotNull HeapCache cache, @NotNull Class<F> featureClass, @NotNull CollectionInfo collection) {
    super(cache, featureClass, collection);
  }

  @Override
  public @NotNull ModifyFeaturesResp modifyFeatures(@NotNull ModifyFeaturesReq<F> req) {
    for (final F feature : req.insert()) {
      final CacheEntry entry = cache.cache.putWeak(feature.getId());
      entry.setValue(feature);
    }
    for (final F feature : req.update()) {
      final CacheEntry entry = cache.cache.putWeak(feature.getId());
      entry.setValue(feature);
    }
    for (final F feature : req.upsert()) {
      final CacheEntry entry = cache.cache.putWeak(feature.getId());
      entry.setValue(feature);
    }
    for (final @NotNull DeleteOp feature : req.delete()) {
      if (cache.cache.get(feature.id()) != null) {
        cache.cache.remove(feature.id());
      }
    }
    return null;
  }
}
