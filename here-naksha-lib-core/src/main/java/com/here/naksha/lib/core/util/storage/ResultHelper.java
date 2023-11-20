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
package com.here.naksha.lib.core.util.storage;

public class ResultHelper {

  private ResultHelper() {}
  //
  //  /**
  //   * Helper method to fetch features from given Result and return list of features with type T.
  //   * Returned list is not limited - to set the upper bound, use sibling method with limit argument.
  //   *
  //   * @param result      the Result which is to be read
  //   * @param featureType the type of feature to be extracted from result
  //   * @param <R>         type of feature
  //   * @return list of features extracted from ReadResult
  //   */
  //  public static <R extends XyzFeature> List<R> readFeaturesFromResult(Result result, Class<R> featureType)
  //      throws NoCursor, NoSuchElementException {
  //    return readFeaturesFromResult(result, featureType, Long.MAX_VALUE);
  //  }
  //
  //  /**
  //   * Helper method to fetch features from given Result and return list of features with type T.
  //   * Returned list is limited with respect to supplied `limit` parameter.
  //   *
  //   * @param result      the Result which is to be read
  //   * @param featureType the type of feature to be extracted from result
  //   * @param limit       the max number of features to be extracted
  //   * @param <R>         type of feature
  //   * @return list of features extracted from ReadResult
  //   */
  //  public static <R extends XyzFeature> List<R> readFeaturesFromResult(Result result, Class<R> featureType, long
  // limit)
  //      throws NoCursor, NoSuchElementException {
  //    try (ResultCursor<R> resultCursor = result.cursor(featureType)) {
  //      if (!resultCursor.hasNext()) {
  //        throw new NoSuchElementException("Result Cursor is empty");
  //      }
  //      List<R> features = new ArrayList<>();
  //      int cnt = 0;
  //      while (resultCursor.hasNext() && cnt++ < limit) {
  //        if (!resultCursor.next()) {
  //          throw new RuntimeException("Unexpected invalid result");
  //        }
  //        features.add(resultCursor.getFeature());
  //      }
  //      return features;
  //    }
  //  }
  //
  //  /**
  //   * Helper method to read single feature from ReadResult
  //   *
  //   * @param <T>    the type parameter
  //   * @param result the Result to read from
  //   * @param type   the type of feature
  //   * @return the feature of type T if found, else null
  //   */
  //  public static <T> @Nullable T readFeatureFromResult(final @NotNull Result result, final @NotNull Class<T> type)
  // {
  //    try (ResultCursor<T> resultCursor = result.cursor(type)) {
  //      if (resultCursor.hasNext() && resultCursor.next()) {
  //        return resultCursor.getFeature();
  //      }
  //      return null;
  //    } catch (NoCursor e) {
  //      return null;
  //    }
  //  }
}
