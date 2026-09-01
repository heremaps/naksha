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
package com.here.naksha.app.common;

import naksha.base.Platform;
import naksha.model.XyzFeatureCollection;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static naksha.base.Platform.javaProxy;

public class FeatureUtil {

  private FeatureUtil() {}

  public static @Nullable NakshaFeature featureFromFeatureResponse(String featureCollectionResponseJson) {
    final var raw = Platform.fromJSON(featureCollectionResponseJson);
    return javaProxy(raw, NakshaFeature.class);
  }

  public static @Nullable NakshaFeature featureFromCollectionResponse(String featureCollectionResponseJson) {
    final var raw = Platform.fromJSON(featureCollectionResponseJson);
    final var featureCollection = javaProxy(raw, XyzFeatureCollection.class);
    if (featureCollection == null) return null;
    final var features = featureCollection.getFeatures();
    if (features.isEmpty()) return null;
    return features.getFirst();
  }

  public static @NotNull List<@NotNull NakshaFeature> featuresFromCollectionResponse(String featureCollectionResponseJson) {
    final var raw = Platform.fromJSON(featureCollectionResponseJson);
    final var featureCollection = javaProxy(raw, XyzFeatureCollection.class);
    if (featureCollection == null) return Collections.emptyList();
    return featureCollection.getFeatures();
  }

  public static @NotNull Map<@NotNull String, @NotNull NakshaFeature> featuresByIdFromCollectionResponse(String featureCollectionResponseJson) {
    final var raw = Platform.fromJSON(featureCollectionResponseJson);
    final var featureCollection = javaProxy(raw, XyzFeatureCollection.class);
    final var map = new HashMap<String, NakshaFeature>();
    if (featureCollection == null) return map;
    for (var feature : featureCollection.getFeatures()) {
      if (feature == null) continue;
      final var id = feature.getId();
      map.put(id, feature);
    }
    return map;
  }

//  public static void generateBigFeature(final @NotNull XyzFeature feature,
//                                        final long targetBodySize) {
//    long crtSize = 0;
//    do {
//      final String randomFieldName = UUID.randomUUID().toString();
//      final String randomFieldValue = UUID.randomUUID().toString();
//      feature.getProperties().put(randomFieldName, randomFieldValue);
//      crtSize += randomFieldName.length() + randomFieldValue.length();
//    } while (crtSize < targetBodySize);
//  }

}
