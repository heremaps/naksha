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
package com.here.naksha.lib.core.util;

import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertyPathUtil {

  private PropertyPathUtil() {}

  public static @NotNull Map<String, Object> extractPropertyMapFromFeature(
      final @NotNull XyzFeature feature, final @Nullable Set<@Nullable String> paths) {
    final Map<String, Object> tgtMap = new HashMap<>();
    PropertyPathUtil.populatePropertyMapFromMap(feature.asMap(), tgtMap, paths);
    return tgtMap;
  }

  public static void populatePropertyMapFromMap(
      final @NotNull Map<String, Object> srcMap,
      final @NotNull Map<String, Object> tgtMap,
      final @Nullable Set<@Nullable String> paths) {
    if (paths == null || paths.size() == 0) return;
    // iterate through all property paths and merge the extracted fields into tgtMap
    for (final String path : paths) {
      if (path == null || path.isEmpty()) continue;
      // we pass tokenized paths using "." delimiter
      // e.g. convert "properties.xyz.tags" to ["properties","xyz","tags"]
      extractPropertyPathFromMap(srcMap, tgtMap, path.split("\\."), 0);
    }
    // Remove unwanted (null) list nodes from the tgtMap
    deepRemoveUnusedListNodes(tgtMap);
  }

  @SuppressWarnings({"unchecked"})
  private static @Nullable Map<String, Object> extractPropertyPathFromMap(
      final @NotNull Map<String, Object> srcMap,
      final @NotNull Map<String, Object> tgtMap,
      final @NotNull String[] path,
      final int pathIdx) {
    if (pathIdx >= path.length) {
      return srcMap; // we reached to end of json path, so we return entire source object itself
    }
    final String key = path[pathIdx]; // property key to be searched in srcMap
    final Object value = srcMap.get(key); // property value from srcMap
    if (value instanceof Map) {
      // property is a map, so we need to go further down the map
      final Map<String, Object> crtMap = (Map<String, Object>) value;
      // create new map, if it doesn't already exist in tgtMap
      final Object tObj = tgtMap.get(key);
      final Map<String, Object> newMap = (tObj == null) ? new HashMap<>() : (Map<String, Object>) tObj;
      // recursively look into crtMap to search for next property in json path
      final Object retVal = extractPropertyPathFromMap(crtMap, newMap, path, pathIdx + 1);
      if (retVal != null) {
        // Add extracted map to a tgtMap and then return.
        tgtMap.put(key, retVal);
        return tgtMap;
      }
    } else if (value instanceof List) {
      // property is a list, so we need to go further down the list
      final List<Object> crtList = (List<Object>) value;
      // create new list, if it doesn't already exist in tgtMap
      final Object tObj = tgtMap.get(key);
      final List<Object> newList = (tObj == null) ? new ArrayList<>(crtList.size()) : (List<Object>) tObj;
      // recursively look into crtList to search for next property in json path
      final Object retVal = extractPropertyPathFromList(crtList, newList, path, pathIdx + 1);
      if (retVal != null) {
        // Add extracted list to a tgtMap and then return.
        tgtMap.put(key, retVal);
        return tgtMap;
      }
    } else if (value != null && pathIdx == path.length - 1) {
      // Neither Object, nor Array. So, we got to the desired field value. Add directly.
      tgtMap.put(key, value);
      return tgtMap;
    }
    return null; // in all other cases, we return null (as no property found)
  }

  @SuppressWarnings("unchecked")
  private static @Nullable List<Object> extractPropertyPathFromList(
      final @NotNull List<Object> srcList,
      final @NotNull List<Object> tgtList,
      final @NotNull String[] path,
      final int pathIdx) {
    if (pathIdx >= path.length) {
      return srcList; // we reached to end of json path, so we return entire source list itself
    }
    int arrIdx = 0;
    try {
      arrIdx = Integer.parseInt(path[pathIdx]); // we need integer value to retrieve field from the list
    } catch (NumberFormatException ne) {
      return null;
    }
    if (arrIdx < 0 || arrIdx >= srcList.size()) return null; // avoid ArrayIndexOutOfBounds
    final Object value = srcList.get(arrIdx); // property value from srcList
    if (value instanceof Map) {
      // property is a map, so we need to go further down the map
      final Map<String, Object> crtMap = (Map<String, Object>) value;
      // create new map, if it doesn't already exist in tgtList
      final Object tObj = (arrIdx >= tgtList.size()) ? null : tgtList.get(arrIdx);
      final Map<String, Object> newMap = (tObj == null) ? new HashMap<>() : (Map<String, Object>) tObj;
      // recursively look into crtMap to search for next property in json path
      final Object retVal = extractPropertyPathFromMap(crtMap, newMap, path, pathIdx + 1);
      if (retVal != null) {
        // Add extracted map to a tgtList and then return.
        return addToTargetListAtPosition(tgtList, arrIdx, retVal);
      }
    } else if (value instanceof List) {
      // property is a list, so we need to go further down the list
      final List<Object> crtList = (List<Object>) value;
      // create new list, if it doesn't already exist in tgtList
      final Object tObj = (arrIdx >= tgtList.size()) ? null : tgtList.get(arrIdx);
      final List<Object> newList = (tObj == null) ? new ArrayList<>(crtList.size()) : (List<Object>) tObj;
      // recursively look into crtList to search for next property in json path
      Object retVal = extractPropertyPathFromList(crtList, newList, path, pathIdx + 1);
      if (retVal != null) {
        // Add extracted list to a tgtList and then return.
        return addToTargetListAtPosition(tgtList, arrIdx, retVal);
      }
    } else if (value != null && pathIdx == path.length - 1) {
      // Neither Object, nor Array. So, we got to the desired field value. Add directly.
      return addToTargetListAtPosition(tgtList, arrIdx, value);
    }
    return null; // in all other cases, we return null (as no property found)
  }

  private static @NotNull List<Object> addToTargetListAtPosition(
      final @NotNull List<Object> tgtList, final int arrIdx, final @NotNull Object retVal) {
    final int crtSize = tgtList.size();
    for (int i = crtSize; i < arrIdx; i++) {
      // we intentionally add temporary holes (null),
      // to retain element order within tgtList same as original srcList
      tgtList.add(i, null);
    }
    if (arrIdx < crtSize) tgtList.set(arrIdx, retVal);
    else tgtList.add(arrIdx, retVal);
    return tgtList;
  }

  @SuppressWarnings({"unchecked"})
  private static void deepRemoveUnusedListNodes(final @NotNull Map<String, Object> tgtMap) {
    for (final Object obj : tgtMap.values()) {
      if (obj instanceof Map) {
        deepRemoveUnusedListNodes((Map<String, Object>) obj);
      } else if (obj instanceof List) {
        ((List<Object>) obj).removeIf(Objects::isNull);
      }
    }
  }
}
