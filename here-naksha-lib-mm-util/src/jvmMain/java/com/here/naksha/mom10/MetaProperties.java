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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Some of Meta-related properties from MOM 10
 */
public class MetaProperties {

  private MetaProperties() {
  }

  /**
   * Renamed in MOM 10 from {@link naksha.model.objects.NakshaProperties#META_KEY}
   */
  public static final String META = "meta";

  /**
   * Source of truth about model version - required since MOM 10.0.0, optional before
   */
  public static final String MODEL_VERSION = "modelVersion";

  private static final Set<String> META_NAMESPACE_PROPERTIES = Set.of(
      "createdTS",
      "hashPayload",
      "lastObservedTS",
      "lastReviewedTS",
      "lastUpdatedBy",
      "lastUpdatedTS",
      "layerId",
      MODEL_VERSION,
      "operation",
      "owner",
      "protectionFlags",
      "sourceId",
      "tid",
      "updatedByApp",
      "updatedByUser");

  /**
   * Renamed from {@link naksha.model.objects.NakshaProperties#DELTA_KEY} and moved under {@link MetaProperties#META}
   */
  public static final String MODERATION_INFO = "moderationInfo";

  // https://here-dev.zoominsoftware.io/docs/bundle/map-object-model-data-specification-10/page/com/here/mom/internal/component/meta/metadata.html
  private static final Set<String> MOM_10_META_PROPERTIES = Set.of(
      "confidence",
      "createdTS",
      "externalIds",
      "keyValues",
      "lastUpdatedBy",
      "lastUpdatedTS",
      "layerId",
      MODEL_VERSION, // "modelVersion"
      MODERATION_INFO, // "moderationInfo"
      "protectionFlags",
      "sourceId",
      "sourceInfo",
      "tags",
      "updatedByApp",
      "updatedByUser");

  static final Set<String> COMMON_META_PROPERTIES;

  static {
    HashSet<String> metaProperties = new HashSet<>(META_NAMESPACE_PROPERTIES);
    metaProperties.retainAll(MOM_10_META_PROPERTIES);
    COMMON_META_PROPERTIES = Collections.unmodifiableSet(metaProperties);
  }
}
