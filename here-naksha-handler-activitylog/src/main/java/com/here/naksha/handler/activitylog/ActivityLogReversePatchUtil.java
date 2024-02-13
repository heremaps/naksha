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
package com.here.naksha.handler.activitylog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.util.diff.Difference;
import com.here.naksha.lib.core.util.diff.IgnoreKey;
import com.here.naksha.lib.core.util.diff.InsertOp;
import com.here.naksha.lib.core.util.diff.ListDiff;
import com.here.naksha.lib.core.util.diff.MapDiff;
import com.here.naksha.lib.core.util.diff.Patcher;
import com.here.naksha.lib.core.util.diff.PrimitiveDiff;
import com.here.naksha.lib.core.util.diff.RemoveOp;
import com.here.naksha.lib.core.util.diff.UpdateOp;
import java.util.Map;

class ActivityLogReversePatchUtil {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final IgnoreKey IGNORE_ID = (key, sm, m) -> XyzFeature.ID.equals(key);

  private ActivityLogReversePatchUtil() {}

  static JsonNode toJsonNode(ActivityLogReversePatch activityLogReversePatch) {
    return MAPPER.valueToTree(activityLogReversePatch);
  }

  static ActivityLogReversePatch reversePatch(XyzFeature older, XyzFeature younger) {
    Difference difference = Patcher.getDifference(older, younger, IGNORE_ID);
    return fromDifference(difference);
  }

  private static ActivityLogReversePatch fromDifference(Difference difference) {
    if (!(difference instanceof MapDiff rootDiff)) {
      throw new IllegalArgumentException("Expected root Difference to be MapDiff, got "
          + difference.getClass().getName() + " instead");
    }
    ActivityLogReversePatch.Builder diffBuilder = ActivityLogReversePatch.builder();
    handle(rootDiff, diffBuilder, "");
    return diffBuilder.build();
  }

  private static void handle(Difference difference, ActivityLogReversePatch.Builder builder, String currentPath) {
    if (difference instanceof PrimitiveDiff pd) {
      handle(pd, builder, currentPath);
    } else if (difference instanceof MapDiff md) {
      handle(md, builder, currentPath);
    } else if (difference instanceof ListDiff ld) {
      // TODO: ?
    } else {
      throw new UnsupportedOperationException("Unable to  process unknown Difference type: "
          + difference.getClass().getName());
    }
  }

  private static void handle(MapDiff mapDiff, ActivityLogReversePatch.Builder builder, String currentPath) {
    for (Map.Entry<Object, Difference> diffEntry : mapDiff.entrySet()) {
      handle(diffEntry.getValue(), builder, path(currentPath, diffEntry.getKey()));
    }
  }

  private static void handle(
      PrimitiveDiff primitiveDiff, ActivityLogReversePatch.Builder builder, String currentPath) {
    if (primitiveDiff instanceof InsertOp insertOp) {
      builder.reverseInsert(insertOp, currentPath);
    } else if (primitiveDiff instanceof RemoveOp removeOp) {
      builder.reverseRemove(removeOp, currentPath);
    } else if (primitiveDiff instanceof UpdateOp updateOp) {
      builder.reverseUpdate(updateOp, currentPath);
    } else {
      throw new UnsupportedOperationException("Unable to process unknown PrimitiveDifference type: "
          + primitiveDiff.getClass().getName());
    }
  }

  private static String path(String currentPath, Object diffPlacement) {
    if (currentPath.isEmpty()) {
      return diffPlacement.toString();
    }
    return "%s.%s".formatted(currentPath, diffPlacement);
  }
}
