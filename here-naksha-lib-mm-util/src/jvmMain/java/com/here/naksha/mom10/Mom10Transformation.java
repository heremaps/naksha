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

import java.util.Map;
import naksha.model.mom.MomDeltaNs;
import naksha.model.mom.MomMetaNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Mom10Transformation {

  private Mom10Transformation() {
  }

  public static void populatePreMom10Namespaces(@Nullable NakshaFeature feature) {
    if (feature == null) {
      return;
    }

    NakshaProperties properties = feature.getProperties();
    Map<String, Object> meta = (Map<String, Object>) properties.get(META);
    if (meta != null && !meta.isEmpty()) {
      MomDeltaNs deltaNs = deltaNs(meta);
      properties.setDelta(deltaNs);
      MomMetaNs metaNs = metaNs(meta);
      properties.setMeta(metaNs);
    }
  }

  private static @NotNull MomMetaNs metaNs(@NotNull Map<String, Object> meta) {
    MomMetaNs metaNs = new MomMetaNs();
    for (String metaKey : COMMON_META_PROPERTIES) {
      Object value = meta.get(metaKey);
      if (value != null) {
        metaNs.put(metaKey, value);
      }
    }
    return metaNs;
  }

  private static @Nullable MomDeltaNs deltaNs(@NotNull Map<String, Object> meta) {
    Map<String, Object> moderationInfo = (Map<String, Object>) meta.get(MODERATION_INFO);
    if (moderationInfo == null) {
      return null;
    } else {
      MomDeltaNs deltaNs = new MomDeltaNs();
      String changeState = (String) moderationInfo.get(DeltaProperties.CHANGE_STATE);
      if (changeState != null) {
        deltaNs.setChangeState(changeState);
      }
      String reviewState = (String) moderationInfo.get(DeltaProperties.REVIEW_STATE);
      if (reviewState != null) {
        deltaNs.setReviewState(reviewState);
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

  public static void dropPreMom10Namespaces(@Nullable NakshaFeature feature) {
    if(feature != null) {
      NakshaProperties properties = feature.getProperties();
      properties.remove(NakshaProperties.META_KEY);
      properties.remove(NakshaProperties.DELTA_KEY);
    }
  }
}
