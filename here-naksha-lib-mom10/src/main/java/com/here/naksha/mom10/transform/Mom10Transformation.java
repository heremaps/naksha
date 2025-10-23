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
package com.here.naksha.mom10.transform;

import static com.here.naksha.mom10.MetaProperties.CONFIDENCE;
import static com.here.naksha.mom10.MetaProperties.EXTERNAL_IDS;
import static com.here.naksha.mom10.MetaProperties.META;
import static com.here.naksha.mom10.MetaProperties.MODERATION_INFO;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.util.json.JsonObject;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public class Mom10Transformation {
  private Mom10Transformation() {}

  public static void populateMom10Meta(@Nullable XyzFeature feature) {
    if (feature == null) {
      return;
    }
    XyzProperties properties = feature.getProperties();
    if (properties.containsKey(META)) {
      throw new IllegalArgumentException(
          "This feature (id= " + feature.getId() + ") already contains the '" + META + "' property");
    }

    JsonObject oldMetaNs = properties.getMetaNamespace();
    JsonObject mom10Meta;
    if (oldMetaNs != null) {
      mom10Meta = oldMetaNs.deepClone();
      drop(mom10Meta, OutdatedProperties.OUTDATED_META_PROPERTIES);
    } else {
      mom10Meta = new JsonObject();
    }

    JsonObject oldDeltaNs = properties.getDeltaNamespace();
    if (oldDeltaNs != null) {
      JsonObject mom10ModerationInfo = oldDeltaNs.deepClone();
      drop(mom10ModerationInfo, OutdatedProperties.OUTDATED_DELTA_PROPERTIES);
      mom10Meta.put(MODERATION_INFO, mom10ModerationInfo);
    }

    Object confidence = properties.get(CONFIDENCE);
    if (confidence != null) {
      mom10Meta.put(CONFIDENCE, confidence);
    }

    Object externalIds = properties.get(EXTERNAL_IDS);
    if (externalIds != null) {
      mom10Meta.put(EXTERNAL_IDS, externalIds);
    }

    properties.put(META, mom10Meta);
  }

  public static void dropMom10Meta(@Nullable XyzFeature feature) {
    if (feature == null) {
      return;
    }
    XyzProperties properties = feature.getProperties();
    properties.remove(META);
  }

  private static void drop(JsonObject bearer, Set<String> propertyNames) {
    for (String propertyName : propertyNames) {
      bearer.remove(propertyName);
    }
  }
}
