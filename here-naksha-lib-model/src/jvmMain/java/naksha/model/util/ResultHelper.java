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
package naksha.model.util;

import static java.util.Collections.emptyList;
import static naksha.base.NakshaBaseKt.String_TYPE;
import static naksha.base.Platform.box;
import static naksha.base.Platform.forClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import naksha.base.MapProxy;
import naksha.base.PlatformType;
import naksha.model.Action;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
public class ResultHelper {

  private ResultHelper() {}

  /**
   * Helper method to fetch features from given Result and return list of features with type T. Returned list is not limited - to set the
   * upper bound, use sibling method with limit argument.
   *
   * @param response    the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param <R>         type of feature
   * @return list of features extracted from ReadResult
   */
  @Deprecated
  public static <R extends NakshaFeature> List<R> extractResponseItems(SuccessResponse response, Class<R> featureType)
      throws NoSuchElementException {
    return extractResponseItems(response, featureType, 0, Long.MAX_VALUE);
  }

  /**
   * Helper method to fetch features from given Result and return list of features with type T. Returned list is limited with respect to
   * supplied `limit` parameter.
   *
   * @param response    the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param offset      the offset position (0-based index) in a list from where features to be extracted
   * @param limit       the max number of features to be extracted
   * @param <R>         type of feature
   * @return list of features extracted from ReadResult
   */
  @Deprecated
  public static <R extends NakshaFeature> List<R> extractResponseItems(
      SuccessResponse response, Class<R> featureType, long offset, long limit) {
    final PlatformType<R> type = forClass(featureType);
    final List<R> features = new ArrayList<>();
    final Iterator<NakshaFeature> iterator = response.getFeatures(NakshaFeatureList.TYPE).iterator();
    int pos = 0;
    int cnt = 0;
    while (iterator.hasNext() && cnt < limit) {
      if (pos++ < offset) {
        iterator.next();
        continue; // skip initial records till we reach to desired offset
      }
      try {
        features.add(box(iterator.next(), type));
        cnt++;
      } catch (ClassCastException | NullPointerException e) {
        throw new RuntimeException(e);
      }
    }
    return features;
  }

  public static <R extends NakshaFeature> @NotNull MapProxy<String, R> extractAndGroupAllFeaturesById(
      final @NotNull SuccessResponse response, final @NotNull Class<R> featureClass
  ) {
    final var featureType = forClass(featureClass);
    final var featuresById = new MapProxy<>(String_TYPE, featureType);
    final var featureList = response.getFeatures(NakshaFeatureList.TYPE);
    for (final NakshaFeature current : featureList) {
      featuresById.put(current.getId(), featureType.proxy(current));
    }
    return featuresById;
  }

  /**
   * Helper method to read single feature from Result
   *
   * @param <T>    the type parameter
   * @param result the Result to read from
   * @param type   the type of feature
   * @return the feature of type T if found, else null
   */
  @Deprecated
  public static <T extends NakshaFeature> @Nullable T readFeatureFromResponse(
      final @NotNull SuccessResponse result,
      final @NotNull Class<T> type
  ) {
    final List<NakshaFeature> features = result.getFeatures(NakshaFeatureList.TYPE);
    if (features.isEmpty()) {
      return null;
    }
    return box(features.get(0), forClass(type));
  }

  @Deprecated
  public static List<String> readIdsFromResult(final @NotNull Response result) {
    if (!(result instanceof SuccessResponse)) {
      return emptyList();
    }
    final var response = (SuccessResponse) result;
    final ArrayList<String> ids = new ArrayList<>(response.resultSize());
    final NakshaFeatureList features = response.getFeatures(NakshaFeatureList.TYPE);
    for (final NakshaFeature feature : features) {
       ids.add(feature.getId());
    }
    return ids;
  }

  /**
   * Helper method to fetch features from given Result and return a map of multiple lists grouped by {@link Action} of features with
   * type T. Returned lists are limited with respect to supplied `limit` parameter.
   *
   * @param result      the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param limit       the max number of features to be extracted
   * @param <T>         type of feature
   * @return a map grouping the lists of features extracted from ReadResult
   */
  @Deprecated
  public static <T extends NakshaFeature> Map<Action, List<T>> readFeaturesGroupedByAction(
          SuccessResponse result, Class<T> featureType, long limit) {
    final PlatformType<T> type = forClass(featureType);
    final NakshaFeatureList features = result.getFeatures(NakshaFeatureList.TYPE);
    if (features.isEmpty()) {
      return Collections.emptyMap();
    }
    final List<T> insertedFeatures = new ArrayList<>();
    final List<T> updatedFeatures = new ArrayList<>();
    final List<T> deletedFeatures = new ArrayList<>();
    int cnt = 0;
    final Iterator<NakshaFeature> iterator = features.iterator();
    while (iterator.hasNext() && cnt++ < limit) {
      final NakshaFeature feature = iterator.next();
      final Action action = feature.getProperties().getXyz().getAction();
      if (action == Action.CREATE) {
        insertedFeatures.add(box(feature, type));
      } else if (action == Action.UPDATE) {
        updatedFeatures.add(box(feature, type));
      } else if (action == Action.DELETE) {
        deletedFeatures.add(box(feature, type));
      }
    }
    final Map<Action, List<T>> featuresByAction = new HashMap<>();
    featuresByAction.put(Action.CREATE, insertedFeatures);
    featuresByAction.put(Action.UPDATE, updatedFeatures);
    featuresByAction.put(Action.DELETE, deletedFeatures);
    return featuresByAction;
  }

  /**
   * Helper method to fetch features from given Result and return a map of multiple lists of features with
   * type T. Returned list is not limited - to set the upper bound, use sibling method with limit argument.
   *
   * @param result      the Result which is to be read
   * @param featureType the type of feature to be extracted from result
   * @param <R>         type of feature
   * @return a map grouping the lists of features extracted from ReadResult (might be Map.empty())
   */
  @Deprecated
  public static <R extends NakshaFeature> Map<Action, List<R>> readFeaturesGroupedByAction(
      SuccessResponse result, Class<R> featureType) {
    return readFeaturesGroupedByAction(result, featureType, Long.MAX_VALUE);
  }
}