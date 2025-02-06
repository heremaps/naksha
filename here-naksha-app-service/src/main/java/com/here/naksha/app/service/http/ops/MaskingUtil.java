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
package com.here.naksha.app.service.http.ops;

import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import naksha.model.objects.NakshaFeature;

public class MaskingUtil {

  static final String MASK = "xxxxxx";

  private MaskingUtil() {
  }

  public static void maskProperties(NakshaFeature feature, Set<String> propertiesToMaskLowercase) {
    maskProperties(feature.getProperties(), propertiesToMaskLowercase);
  }

  private static void maskProperties(Map<String, Object> propertiesAsMap, Set<String> propertiesToMaskLowercase) {
    for (Entry<String, Object> entry : propertiesAsMap.entrySet()) {
      if (propertiesToMaskLowercase.contains(entry.getKey().toLowerCase())) {
        entry.setValue(MASK);
      } else if (entry.getValue() instanceof Map) {
        maskProperties((Map<String, Object>) entry.getValue(), propertiesToMaskLowercase);
      } else if (entry.getValue() instanceof ArrayList array) {
        // recursive call to the nested array json
        for (Object arrayEntry : array) {
          maskProperties((Map<String, Object>) arrayEntry, propertiesToMaskLowercase);
        }
      }
    }
  }
}
