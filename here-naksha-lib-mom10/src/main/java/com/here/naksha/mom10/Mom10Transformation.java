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

import static com.here.naksha.mom10.MetaProperties.CONFIDENCE;
import static com.here.naksha.mom10.MetaProperties.META;
import static com.here.naksha.mom10.MetaProperties.MODERATION_INFO;
import static com.here.naksha.mom10.MetaProperties.SOURCE_INFO;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.EChangeState;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.EReviewState;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.HereDeltaNs;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.HereMetaNs;
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
    if (meta != null) {
      Map<String, Object> moderationInfo = (Map<String, Object>) meta.get(MODERATION_INFO);
      if (moderationInfo != null) {
        HereDeltaNs deltaNs = deltaNsFromModerationInfo(moderationInfo);
        properties.setDeltaNamespace(deltaNs);
      }

      if (!meta.isEmpty()) {
        HereMetaNs metaNs = new HereMetaNs();
        metaNs.putAll(meta);
        // remove properties that before MOM 10 lived in root properties
        metaNs.remove(CONFIDENCE);
        metaNs.remove(MODERATION_INFO);
        metaNs.remove(SOURCE_INFO);
        properties.setMetaNamespace(metaNs);
      }
    }
  }

  private static HereDeltaNs deltaNsFromModerationInfo(@NotNull Map<String, Object> moderationInfo) {
    HereDeltaNs deltaNs = new HereDeltaNs();
    String rawChangeState = (String) moderationInfo.get(HereDeltaNs.CHANGE_STATE_PROPERTY);
    if (rawChangeState != null) {
      deltaNs.setChangeState(EChangeState.get(EChangeState.class, rawChangeState));
    }
    String rawReviewState = (String) moderationInfo.get(HereDeltaNs.REVIEW_STATE_PROPERTY);
    if (rawChangeState != null) {
      deltaNs.setReviewState(EReviewState.get(EReviewState.class, rawReviewState));
    }
    for (Map.Entry<String, Object> entry : moderationInfo.entrySet()) {
      if (!entry.getKey().equals(HereDeltaNs.CHANGE_STATE_PROPERTY)
          && !entry.getKey().equals(HereDeltaNs.REVIEW_STATE_PROPERTY)) {
        deltaNs.put(entry.getKey(), entry.getValue());
      }
    }
    return deltaNs;
  }

  public static void dropPreMom10Namespaces(@Nullable XyzFeature feature) {
    XyzProperties properties = feature.getProperties();
    properties.remove(XyzProperties.HERE_META_NS);
    properties.remove(XyzProperties.HERE_DELTA_NS);
  }
}
