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
package com.here.naksha.lib.view.concurrent;

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.here.naksha.lib.view.View;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerFeature;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import naksha.base.StringList;
import naksha.model.IReadSession;
import naksha.model.NakshaContext;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ResultTuple;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParallelQueryExecutor {
  private static final Logger log = LoggerFactory.getLogger(ParallelQueryExecutor.class);
  private final long defaultTimeoutMillis = 1000 * 60 * 10L; // 10 minutes
  private final View viewRef;

  public ParallelQueryExecutor(@NotNull View viewRef) {
    this.viewRef = viewRef;
  }

  public Map<String, List<ViewLayerFeature>> queryInParallel(@NotNull List<LayerReadRequest> requests) {
    List<Future<List<ViewLayerFeature>>> futures = new ArrayList<>();

    for (LayerReadRequest layerReadRequest : requests) {
      QueryTask<List<ViewLayerFeature>> singleTask = new QueryTask<>(null, NakshaContext.currentContext());

      Future<List<ViewLayerFeature>> futureResult = singleTask.start(() -> executeSingle(
              layerReadRequest.getViewLayer(),
              layerReadRequest.getSession(),
              layerReadRequest.getRequest())
          .collect(toList()));
      futures.add(futureResult);
    }

    // wait for all
    Long timeout = getTimeout(requests);
    return getCollectedResults(futures, timeout);
  }

  @NotNull
  private Map<String, List<ViewLayerFeature>> getCollectedResults(
      List<Future<List<ViewLayerFeature>>> tasks, Long timeoutMillis) {
    return tasks.stream()
        .map(future -> {
          try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
          } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw unchecked(e);
          }
        })
        .flatMap(Collection::stream)
        .collect(groupingBy(viewRow -> viewRow.getTuple().featureId));
  }

  private @NotNull Long getTimeout(@NotNull List<LayerReadRequest> requests) {
    Optional<Integer> maxSessionTimeout =
        requests.stream().map(it -> it.getSession().getStmtTimeout()).max(Integer::compareTo);

    if (maxSessionTimeout.isEmpty() || maxSessionTimeout.get() == 0) {
      return defaultTimeoutMillis;
    } else {
      return Long.valueOf(maxSessionTimeout.get());
    }
  }

  private Stream<ViewLayerFeature> executeSingle(
      @NotNull ViewLayer layer, @NotNull IReadSession session, @NotNull ReadFeatures request) {
    final long startTime = System.currentTimeMillis();
    String status = "OK";
    int featureCnt = 0;
    int layerPriority = viewRef.getViewCollection().priorityOf(layer);
    final String collectionId = layer.getCollectionId();

    // prepare request
    ReadFeatures clonedRequest = request.copy(false);
    final StringList idsList = new StringList();
    idsList.add(collectionId);
    clonedRequest.setCollectionIds(idsList);

    SuccessResponse cursor = (SuccessResponse) session.execute(clonedRequest);

    List<ResultTuple> featureList = cursor.getTuples();
    log.info(
        "[View Request stats => streamId,layerId,method,status,timeTakenMs,fCnt] - ViewReqStats {} {} {} {} {} {}",
        NakshaContext.currentContext().getStreamId(),
        collectionId,
        "READ",
        status,
        System.currentTimeMillis() - startTime,
        featureCnt);
    return featureList.stream().map(row -> new ViewLayerFeature(row, layerPriority, layer));
  }
}
