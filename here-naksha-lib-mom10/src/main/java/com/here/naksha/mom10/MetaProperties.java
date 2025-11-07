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

import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;

/**
 * Some of Meta-related properties from MOM 10
 */
public class MetaProperties {

  private MetaProperties() {}

  /**
   * Renamed in MOM 10 from {@link XyzProperties#HERE_META_NS}
   */
  public static final String META = "meta";

  /**
   * Renamed from {@link XyzProperties#HERE_DELTA_NS} and moved under {@link MetaProperties#META}
   */
  public static final String MODERATION_INFO = "moderationInfo";

  /**
   * Moved from {@link XyzProperties#HERE_DELTA_NS} to {@link MetaProperties#META}
   */
  public static final String CONFIDENCE = "confidence";

  public static final String SOURCE_INFO = "sourceInfo";

  /**
   * Source of truth about model version - required since MOM 10.0.0
   */
  public static final String MODEL_VERSION = "modelVersion";
}
