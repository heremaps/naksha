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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import naksha.base.PAnyMap;
import naksha.model.objects.NakshaFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Mom10Verification {

  private static final Pattern SHORT_SEM_VER = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\..+$");

  private Mom10Verification() {
  }

  public static boolean isMom10OrGreater(PAnyMap rawFeature) {
    Map properties = nestedMapOrNull(rawFeature, NakshaFeature.PROPERTIES_KEY);
    if (properties != null) {
      Map meta = nestedMapOrNull(properties, MetaProperties.META);
      if (meta != null) {
        Object modelVersion = meta.get(MetaProperties.MODEL_VERSION);
        if (modelVersion instanceof String) {
          return specifiesAtLeastMom10((String) modelVersion);
        }
      }
    }
    return false;
  }

  private static boolean specifiesAtLeastMom10(@NotNull String modelVersion) {
    Integer major = getMajorFrom(modelVersion);
    return major != null && major >= 10;
  }

  private static @Nullable Integer getMajorFrom(@NotNull String modelVersion) {
    Matcher matcher = SHORT_SEM_VER.matcher(modelVersion);
    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private static @Nullable Map<String, Object> nestedMapOrNull(Map rawFeature, String propertyName) {
    Object property = rawFeature.get(propertyName);
    if (property instanceof Map) {
      return (Map<String, Object>) property;
    }
    return null;
  }
}
