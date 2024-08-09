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
package com.here.naksha.lib.view;

import static com.here.naksha.lib.core.util.storage.RequestHelper.readFeaturesByIdsRequest;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.here.naksha.lib.view.concurrent.LayerReadRequest;
import com.here.naksha.lib.view.concurrent.ParallelQueryExecutor;
import com.here.naksha.lib.view.merge.MergeByStoragePriority;
import com.here.naksha.lib.view.missing.ObligatoryLayersResolver;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link  ViewReadSession} operates on {@link View}, it queries simultaneously all the storages.
 * Then it tries to fetch missing features {@link MissingIdResolver} if needed.
 * At the end {@link MergeOperation} is executed and single result returned.
 * You can provide your own merge operation. The default is "take result from storage on the top". <br>
 *
 * <strong>Important:</strong> {@link ViewReadSession} will always return mutable cursor, this is the only way we can
 * merge results from different storages and fetch missing by ids. Consider this example:
 * Result from Storage A: [F_1, F_2, F_3, F_4]
 * Result from Storage B: [F_2, F_4]
 * Result from Storage C: [F_3, F_5]
 * In this situation using Forward cursor would lead to N+1 issue, as after reading 1st row from each result we'd have
 * to fetch missing F_1 from B and C.
 * To be able to create query that fetches multiple missing features we have to know them first (by caching ahead of time) <br>
 * <p>
 * It might happen that feature has been moved (it's geometry changed). In such case after getting results for bbox
 * query we have to query again for all features (by id) that was missing in a least one storage  result.
 */
public class ViewReadSession implements IReadSession {

  protected final View viewRef;

  protected ParallelQueryExecutor parallelQueryExecutor;

  protected Map<ViewLayer, IReadSession> subSessions;

  protected ViewReadSession(@NotNull View viewRef, @Nullable SessionOptions options) {
    this.viewRef = viewRef;
    this.subSessions = new LinkedHashMap<>();
    for (ViewLayer layer : viewRef.getViewCollection().getLayers()) {
      subSessions.put(layer, layer.getStorage().newReadSession(options));
    }
    this.parallelQueryExecutor = new ParallelQueryExecutor(viewRef);
  }

  @Override
  public @NotNull Response execute(@NotNull Request readRequest) {
    if (!(readRequest instanceof ReadFeatures)) {
      throw new UnsupportedOperationException("Only ReadFeatures are supported.");
    }
    return execute((ReadRequest) readRequest);
  }

  public @NotNull Response execute(@NotNull ReadRequest readRequest) {
    return execute(
        readRequest,
        new MergeByStoragePriority(),
        new ObligatoryLayersResolver(Set.of(viewRef.getViewCollection().getTopPriorityLayer())));
  }

  public Response execute(
      @NotNull ReadRequest request,
      @NotNull MergeOperation mergeOperation,
      @NotNull MissingIdResolver missingIdResolver) {

    if (!(request instanceof ReadFeatures)) {
      throw new UnsupportedOperationException("Only ReadFeatures are supported.");
    }

    /*
    Call every layer/storage and get the first result.
    After that we should have multiLayerRows like that:
    [
    <featureId_1, [Layer0_Feature1, Layer1_Feature1, ... LayerN_Feature1]>,
    <featureId_2, [Layer0_Feature2, Layer1_Feature2, ... LayerN_Feature2]>,
    ...
    ]
     */
    List<LayerReadRequest> layerReadRequests = subSessions.entrySet().stream()
        .map(entry -> new LayerReadRequest((ReadFeatures) request, entry.getKey(), entry.getValue()))
        .collect(toList());
    Map<String, List<ViewLayerFeature>> multiLayerRows = parallelQueryExecutor.queryInParallel(layerReadRequests);

    /*
    If one of the features is missing on one or few layers, we use getMissingFeatures and missingIdResolver to try to fetch it again by id.
    I.e. when we made a request in the first step to Layer0, Layer1 and Layer2, but we got feature only from Layer0 and Layer2:
    [
    <featureId_1, [Layer0_Feature1, Layer2_Feature1]>
    ]
    then missingIdResolver may decide to create another request to Layer1 querying by featureId_1.
    So the result of getMissingFeatures(..) would look like this:
    [
    <featureId_1, [Layer1_Feature1]>
    ]
    or it might be empty if feature is not there
     */
    Map<String, List<ViewLayerFeature>> fetchedById = isRequestOnlyById(request)
        ? Collections.emptyMap()
        : getMissingFeatures(multiLayerRows, missingIdResolver);

    /*
    putting all together:
    [ <featureId_1, [Layer0_Feature1, Layer2_Feature1]> ]
    and
    [ <featureId_1, [Layer1_Feature1]> ]
    to get:
    [ <featureId_1, [Layer0_Feature1, Layer1_Feature1, Layer2_Feature1]> ]
     */
    fetchedById.forEach((key, value) -> multiLayerRows.get(key).addAll(value));

    /*
    Merging: [ <featureId_1, [Layer0_Feature1, Layer1_Feature1, Layer2_Feature1]> ]
    into final result:  [ Feature1 ]
     */
    List<NakshaFeature> mergedRows =
        multiLayerRows.values().stream().map(mergeOperation::apply).collect(toList());

    return new ViewSuccessResult(mergedRows, null);
  }

  private Map<String, List<ViewLayerFeature>> getMissingFeatures(
      @NotNull Map<String, List<ViewLayerFeature>> multiLayerRows, @NotNull MissingIdResolver missingIdResolver) {

    Map<String, List<ViewLayerFeature>> result = new HashMap<>();
    if (!missingIdResolver.skip()) {
      // Prepare map of <Layer_x, [FeatureId_x, ..., FeatureId_z]> features and layers you want to search by id.
      // to query only once each layer
      Map<ViewLayer, List<String>> idsToFetch = multiLayerRows.values().stream()
          .map(missingIdResolver::layersToSearch)
          .filter(Objects::nonNull)
          .flatMap(Collection::stream)
          .collect(groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));

      // Prepare request by id and query given layers.
      List<LayerReadRequest> missingFeaturesRequests = idsToFetch.entrySet().stream()
          .map(entry -> new LayerReadRequest(
              readFeaturesByIdsRequest(entry.getKey().getCollectionId(), entry.getValue()),
              entry.getKey(),
              subSessions.get(entry.getKey())))
          .collect(toList());

      result = parallelQueryExecutor.queryInParallel(missingFeaturesRequests);
    }
    return result;
  }

  @Override
  public void close() {
    subSessions.values().forEach(ISession::close);
  }

  private boolean isRequestOnlyById(ReadRequest request) {
    if (request instanceof ReadFeatures) {
      ReadFeatures readFeatures = (ReadFeatures) request;
      if (readFeatures.op instanceof SOp) {
        return false;
      }
      return isPropertyOpIdOnly((POp) readFeatures.op);
    } else {
      return false;
    }
  }

  private boolean isPropertyOpIdOnly(POp pOp) {
    if (pOp == null) {
      return false;
    }
    return (pOp.getOp() == EQ.INSTANCE) && PRef.id().equals(pOp.getPropertyRef());
  }

  @Override
  public int getSocketTimeout() {
    return 0;
  }

  @Override
  public void setSocketTimeout(int i) {}

  @Override
  public int getStmtTimeout() {
    return 0;
  }

  @Override
  public void setStmtTimeout(int i) {}

  @Override
  public int getLockTimeout() {
    return 0;
  }

  @Override
  public void setLockTimeout(int i) {}

  @Override
  public boolean isClosed() {
    return false;
  }

  @NotNull
  @Override
  public String getMap() {
    return "";
  }

  @Override
  public void setMap(@NotNull String s) {}

  @Override
  public boolean validateHandle(@NotNull String handle, @Nullable Integer ttl) {
    return false;
  }

  @NotNull
  @Override
  public List<Tuple> getLatestTuples(
      @NotNull String mapId, @NotNull String collectionId, @NotNull String[] featureIds, @NotNull String mode) {
    return List.of();
  }

  @NotNull
  @Override
  public List<Tuple> getTuples(@NotNull TupleNumber[] tupleNumbers, @NotNull String mode) {
    return List.of();
  }

  @Override
  public void fetchTuple(@NotNull ResultTuple resultTuple, @NotNull String mode) {}

  @Override
  public void fetchTuples(
      @NotNull List<? extends ResultTuple> resultTuples, int from, int to, @NotNull String mode) {}

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    return IReadSession.super.executeParallel(request);
  }
}
