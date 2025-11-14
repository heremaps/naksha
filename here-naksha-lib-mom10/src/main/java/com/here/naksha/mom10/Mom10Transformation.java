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
package com.here.naksha.mom10;

import static com.here.naksha.mom10.MetaProperties.COMMON_META_PROPERTIES;
import static com.here.naksha.mom10.MetaProperties.META;
import static com.here.naksha.mom10.MetaProperties.MODERATION_INFO;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.EChangeState;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.EReviewState;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.HereDeltaNs;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.HereMetaNs;
import com.here.naksha.lib.core.util.json.JsonEnum;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Mom10Transformation {

  private Mom10Transformation() {}

  public static void populatePreMom10Namespaces(@Nullable XyzFeature feature) {
    if (feature == null) {
      return;
    }

    XyzProperties properties = feature.getProperties();
    Map<String, Object> meta = (Map<String, Object>) properties.get(META);
    if (meta != null && !meta.isEmpty()) {
      HereDeltaNs deltaNs = deltaNs(meta);
      properties.setDeltaNamespace(deltaNs);
      HereMetaNs metaNs = metaNs(meta);
      properties.setMetaNamespace(metaNs);
    }
  }

  private static @NotNull HereMetaNs metaNs(@NotNull Map<String, Object> meta) {
    HereMetaNs metaNs = new HereMetaNs();
    for (String metaKey : COMMON_META_PROPERTIES) {
      Object value = meta.get(metaKey);
      if (value != null) {
        metaNs.put(metaKey, value);
      }
    }
    return metaNs;
  }

  private static @Nullable HereDeltaNs deltaNs(@NotNull Map<String, Object> meta) {
    Map<String, Object> moderationInfo = (Map<String, Object>) meta.get(MODERATION_INFO);
    if (moderationInfo == null) {
      return null;
    } else {
      HereDeltaNs deltaNs = new HereDeltaNs();
      String rawChangeState = (String) moderationInfo.get(DeltaProperties.CHANGE_STATE);
      if (rawChangeState != null) {
        deltaNs.setChangeState(JsonEnum.get(EChangeState.class, rawChangeState));
      }
      String rawReviewState = (String) moderationInfo.get(DeltaProperties.REVIEW_STATE);
      if (rawChangeState != null) {
        deltaNs.setReviewState(JsonEnum.get(EReviewState.class, rawReviewState));
      }
      String originId = (String) moderationInfo.get(DeltaProperties.ORIGIN_ID);
      if (originId != null) {
        deltaNs.setOriginId(originId);
      }
      String parentLink = (String) moderationInfo.get(DeltaProperties.PARENT_LINK);
      if (parentLink != null) {
        deltaNs.setParentLink(parentLink);
      }
      return deltaNs;
    }
  }

  public static void dropPreMom10Namespaces(@Nullable XyzFeature feature) {
    XyzProperties properties = feature.getProperties();
    properties.remove(XyzProperties.HERE_META_NS);
    properties.remove(XyzProperties.HERE_DELTA_NS);
  }
}
