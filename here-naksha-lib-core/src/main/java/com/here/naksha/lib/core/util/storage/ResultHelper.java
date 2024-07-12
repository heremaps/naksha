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
package com.here.naksha.lib.core.util.storage;

import static java.util.Collections.emptyList;

import java.util.*;
import naksha.model.NakshaFeatureProxy;
import naksha.model.request.ResultRow;
import naksha.model.response.ExecutedOp;
import naksha.model.response.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResultHelper {

  private ResultHelper() {}

  /**
   * Helper method to fetch features from given Result and return list of features with type T. Returned list is not limited - to set the
   * upper bound, use sibling method with limit argument.
   *
   * @param result      the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param <R>         type of feature
   * @return list of features extracted from ReadResult
   */
  public static <R extends NakshaFeatureProxy> List<R> readFeaturesFromResult(
      SuccessResponse result, Class<R> featureType) throws NoSuchElementException {
    return readFeaturesFromResult(result, featureType, 0, Long.MAX_VALUE);
  }

  /**
   * Helper method to fetch features from given Result and return list of features with type T. Returned list is limited with respect to
   * supplied `limit` parameter.
   *
   * @param result      the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param offset      the offset position (0-based index) in a list from where features to be extracted
   * @param limit       the max number of features to be extracted
   * @param <R>         type of feature
   * @return list of features extracted from ReadResult
   */
  public static <R extends NakshaFeatureProxy> List<R> readFeaturesFromResult(
      SuccessResponse result, Class<R> featureType, long offset, long limit) {
    final List<R> features = new ArrayList<>();
    final Iterator<ResultRow> iterator = result.getRows().iterator();
    int pos = 0;
    int cnt = 0;
    while (iterator.hasNext() && cnt < limit) {
      if (pos++ < offset) {
        continue; // skip initial records till we reach to desired offset
      }
      try {
        features.add(featureType.cast(iterator.next().getFeature()));
        cnt++;
      } catch (ClassCastException | NullPointerException e) {
        throw new RuntimeException(e);
      }
    }
    return features;
  }

  /**
   * Helper method to read single feature from Result
   *
   * @param <T>    the type parameter
   * @param result the Result to read from
   * @param type   the type of feature
   * @return the feature of type T if found, else null
   */
  public static <T> @Nullable T readFeatureFromResult(
      final @NotNull SuccessResponse result, final @NotNull Class<T> type) {
    final List<ResultRow> rows = result.getRows();
    if (rows.isEmpty()) {
      return null;
    }
    return type.cast(rows.get(0).getFeature());
  }

  public static List<String> readIdsFromResult(final @NotNull SuccessResponse result) {
    if (result.getRows().isEmpty()) {
      return emptyList();
    }
    final Iterator<ResultRow> iterator = result.getRows().iterator();
    final List<String> ids = new ArrayList<>();
    while (iterator.hasNext()) {
      ids.add(iterator.next().getFeature().getId());
    }
    return ids;
  }

  /**
   * Helper method to fetch features from given Result and return a map of multiple lists grouped by {@link ExecutedOp} of features with
   * type T. Returned lists are limited with respect to supplied `limit` parameter.
   *
   * @param result      the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param limit       the max number of features to be extracted
   * @param <R>         type of feature
   * @return a map grouping the lists of features extracted from ReadResult
   */
  public static <R extends NakshaFeatureProxy> Map<ExecutedOp, List<R>> readFeaturesGroupedByOp(
      SuccessResponse result, Class<R> featureType, long limit) {
    final Iterator<ResultRow> iterator = result.getRows().iterator();
    if (!iterator.hasNext()) {
      throw new NoSuchElementException("Empty SuccessResponse");
    }
    final List<R> insertedFeatures = new ArrayList<>();
    final List<R> updatedFeatures = new ArrayList<>();
    final List<R> deletedFeatures = new ArrayList<>();
    int cnt = 0;
    while (iterator.hasNext() && cnt++ < limit) {
      ResultRow row = iterator.next();
      if (row.getOp().equals(ExecutedOp.CREATED)) {
        insertedFeatures.add(featureType.cast(row.getFeature()));
      } else if (row.getOp().equals(ExecutedOp.UPDATED)) {
        updatedFeatures.add(featureType.cast(row.getFeature()));
      } else if (row.getOp().equals(ExecutedOp.DELETED)) {
        deletedFeatures.add(featureType.cast(row.getFeature()));
      }
    }
    final Map<ExecutedOp, List<R>> features = new HashMap<>();
    features.put(ExecutedOp.CREATED, insertedFeatures);
    features.put(ExecutedOp.UPDATED, updatedFeatures);
    features.put(ExecutedOp.DELETED, deletedFeatures);
    return features;
  }

  /**
   * Helper method to fetch features from given Result and return a map of multiple lists grouped by {@link ExecutedOp} of features with
   * type T. Returned list is not limited - to set the upper bound, use sibling method with limit argument.
   *
   * @param result      the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param <R>         type of feature
   * @return a map grouping the lists of features extracted from ReadResult
   */
  public static <R extends NakshaFeatureProxy> Map<ExecutedOp, List<R>> readFeaturesGroupedByOp(
      SuccessResponse result, Class<R> featureType) throws NoSuchElementException {
    return readFeaturesGroupedByOp(result, featureType, Long.MAX_VALUE);
  }
}
