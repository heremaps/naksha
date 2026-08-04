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

import static java.util.stream.Collectors.*;
import static naksha.base.NakshaExceptionKt.illegalState;
import static naksha.base.NakshaExceptionKt.unsupportedOp;
import static naksha.model.util.RequestHelper.readFeaturesByIdsRequest;

import com.here.naksha.lib.view.concurrent.LayerReadRequest;
import com.here.naksha.lib.view.concurrent.ParallelQueryExecutor;
import com.here.naksha.lib.view.merge.MergeByStoragePriority;
import com.here.naksha.lib.view.missing.ObligatoryLayersResolver;
import java.util.*;

import naksha.model.*;
import naksha.model.MemberProcessorMap;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaCatalog;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.IPropertyQuery;
import naksha.model.request.query.PQuery;
import naksha.model.util.CustomStoragePropertiesUtil;
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
public class ViewReadSession implements IReadSession, AutoCloseable {

  protected final View view;
  protected final @NotNull ParallelQueryExecutor parallelQueryExecutor;
  protected final SessionOptions baseOptions;
  private final @NotNull Map<@NotNull ViewLayer, @NotNull IReadSession> subSessions;

  ViewReadSession(@NotNull View view, SessionOptions options) {
    this.view = view;
    this.baseOptions = options;
    this.subSessions = new HashMap<>();
    for (final @NotNull ViewLayer layer : view.getViewCollection().getLayers()) {
      IStorage subStorage = layer.getStorage();
      SessionOptions subSessionOptions = CustomStoragePropertiesUtil.mergeSessionOptionsWithStorageConfig(baseOptions, subStorage);
      subSessions.put(layer, subStorage.newReadSession(subSessionOptions));
    }
    this.parallelQueryExecutor = new ParallelQueryExecutor(view);
  }

  @Override
  public @NotNull Response execute(@NotNull Request readRequest) {
    if (!(readRequest instanceof ReadFeatures)) {
      throw new UnsupportedOperationException("Only ReadFeatures are supported.");
    }
    return executeReadFeatures(
        (ReadFeatures) readRequest,
        new MergeByStoragePriority(),
        new ObligatoryLayersResolver(Set.of(view.getViewCollection().getTopPriorityLayer()))
    );
  }

  public Response executeReadFeatures(
      @NotNull ReadFeatures request,
      @NotNull MergeOperation mergeOperation,
      @NotNull MissingIdResolver missingIdResolver) {
    /*
    Call every layer/storage and get the first result.
    After that we should have featuresById like that:
    [
    <featureId_1, [Layer0_Feature1, Layer1_Feature1, ... LayerN_Feature1]>,
    <featureId_2, [Layer0_Feature2, Layer1_Feature2, ... LayerN_Feature2]>,
    ...
    ]
     */
    List<LayerReadRequest> layerReadRequests = subSessions.entrySet().stream()
        .map(entry -> new LayerReadRequest(request, entry.getKey(), entry.getValue()))
        .collect(toList());
    final var featuresById = parallelQueryExecutor.queryInParallel(layerReadRequests);

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
    ViewLayerFeaturesById fetchedById = isRequestOnlyById(request)
        ? new ViewLayerFeaturesById()
        : getMissingFeatures(featuresById, missingIdResolver);

    /*
    putting all together:
    [ <featureId_1, [Layer0_Feature1, Layer2_Feature1]> ]
    and
    [ <featureId_1, [Layer1_Feature1]> ]
    to get:
    [ <featureId_1, [Layer0_Feature1, Layer1_Feature1, Layer2_Feature1]> ]
     */
    fetchedById.forEach((key, value) -> featuresById.get(key).addAll(value));

    /*
    Merging: [ <featureId_1, [Layer0_Feature1, Layer1_Feature1, Layer2_Feature1]> ]
    into final result:  [ Feature1 ]
     */
    List<NakshaFeature> mergedRows =
        featuresById.values().stream().map(mergeOperation::apply).collect(toList());

    return new ViewSuccessResult(mergedRows, null);
  }

  private @NotNull ViewLayerFeaturesById getMissingFeatures(
      @NotNull ViewLayerFeaturesById resultSet,
      @NotNull MissingIdResolver missingIdResolver
  ) {
    if (missingIdResolver.skip()) return new ViewLayerFeaturesById();

    var idsToFetch = missingIdResolver.getAllMissingIdsByLayer(resultSet);

    // Prepare request by id and query given layers.
    List<LayerReadRequest> missingFeaturesRequests = idsToFetch.entrySet().stream()
        .map(entry -> new LayerReadRequest(
            readFeaturesByIdsRequest(entry.getKey().getMapId(), entry.getKey().getCollectionId(), entry.getValue()),
            entry.getKey(),
            subSessions.get(entry.getKey())))
        .collect(toList());

    return parallelQueryExecutor.queryInParallel(missingFeaturesRequests);
  }

  @Override
  public void close() {
    subSessions.forEach((layer, session) -> session.close());
    subSessions.clear();
  }

  private boolean isRequestOnlyById(ReadRequest request) {
    if (request instanceof ReadFeatures) {
      final ReadFeatures readFeatures = (ReadFeatures) request;
      final IPropertyQuery propertyQuery = readFeatures.getQuery().getProperties();
      if (!readFeatures.getFeatureIds().isEmpty()
          && readFeatures.getQuery().hasNoConditions()) {
        return true;
      }
      if (propertyQuery instanceof PQuery) {
        final PQuery query = ((PQuery) propertyQuery);
        return query.getProperty().getPath().contains("id")
               && query.getOp().equals(AnyOp.IS_ANY_OF);
      }
    }
    return false;
  }

  @Override
  public int getSocketTimeout() {
    return 0;
  }

  @Override
  public void setSocketTimeout(int i) {
  }

  @Override
  public int getStmtTimeout() {
    return 0;
  }

  @Override
  public void setStmtTimeout(int i) {
  }

  @Override
  public int getLockTimeout() {
    throw unsupportedOp("int getLockTimeout()");
  }

  @Override
  public void setLockTimeout(int i) {
    throw unsupportedOp("void setLockTimeout(int i)");
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @NotNull
  @Override
  public Response executeParallel(@NotNull Request request) {
    return execute(request);
  }

  @Override
  public @Nullable Tuple @NotNull [] loadTuples(@NotNull ITupleNumberArray tupleNumbers, boolean cacheOnly) {
    final ViewLayerCollection viewCollection = view.getViewCollection();

    // Group by layer.
    final var tnByLayers = new HashMap<ViewLayer, TupleNumberList>();
    for (int i = 0; i < tupleNumbers.getSize(); i++) {
      var tn = tupleNumbers.getTupleNumber(i);
      var viewLayer = viewCollection.getByTupleNumber(tn);
      if (viewLayer == null) {
        // TODO: Log?
        continue;
      }
      tnByLayers.computeIfAbsent(viewLayer, key -> new TupleNumberList()).add(tn);
    }

    // Load from all layer.
    // TODO: Use virtual threads to load in parallel!
    final var tuplesByLayer = new HashMap<ViewLayer, ArrayList<Tuple>>();
    int totalSize = 0;
    for (var entry : tnByLayers.entrySet()) {
      var layer = entry.getKey();
      var tnArray = entry.getValue();
      var session = subSessions.get(layer);
      if (session == null) {
        // TODO: Log?
        continue;
      }
      var tuples = session.loadTuples(tnArray, cacheOnly);
      var tuplesList = tuplesByLayer.computeIfAbsent(layer, key -> new ArrayList<>());
      Collections.addAll(tuplesList, tuples);
      totalSize += tuples.length;
    }

    // TODO: @AI: We need to guarantee same order as in input, this algo breaks this!
    final var result = new Tuple[totalSize];
    int i = 0;
    for (var entry : tuplesByLayer.entrySet()) {
      var tupleList = entry.getValue();
      for (var tuple :  tupleList) {
        result[i++] = tuple;
      }
    }
    return result;
  }

  @Override
  public @NotNull IStorage getStorage() {
    throw unsupportedOp("IStorage getStorage()");
  }

  @Override
  public @Nullable NakshaCatalog getCatalogByNumber(int catalogNumber, boolean allowTombstone) {
    final var viewCollection = view.getViewCollection();
    final var layers = viewCollection.getLayers();
    for (var layer : layers) {
      var readSession = subSessions.get(layer);
      if (readSession == null) continue;
      var catalog = readSession.getCatalogByNumber(catalogNumber, allowTombstone);
      if (catalog != null) return catalog;
    }
    return null;
  }

  @Override
  public @Nullable NakshaCollection getCollectionByNumber(@NotNull NakshaCatalog catalog, int collectionNumber, boolean allowTombstone) {
    final var viewCollection = view.getViewCollection();
    final var layers = viewCollection.getLayers();
    for (var layer : layers) {
      var readSession = subSessions.get(layer);
      if (readSession == null) continue;
      var collection = readSession.getCollectionByNumber(catalog, collectionNumber, allowTombstone);
      if (collection != null) return collection;
    }
    return null;
  }

  @Override
  public @NotNull SessionOptions getOptions() {
    final var viewCollection = view.getViewCollection();
    final var layers = viewCollection.getLayers();
    for (var layer : layers) {
      var readSession = subSessions.get(layer);
      if (readSession != null) return readSession.getOptions();
    }
    throw illegalState("No valid session options found.");
  }

  @Override
  public @NotNull MemberProcessorMap getProcessors() {
    throw new UnsupportedOperationException();
  }
}
