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
import com.here.naksha.lib.view.ViewLayerRow;
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
import naksha.model.IReadSession;
import naksha.model.NakshaContext;
import naksha.model.request.ReadFeatures;
import naksha.model.request.ResultRow;
import naksha.model.response.SuccessResponse;
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

  public Map<String, List<ViewLayerRow>> queryInParallel(
      @NotNull List<LayerReadRequest> requests) {
    List<Future<List<ViewLayerRow>>> futures = new ArrayList<>();

    for (LayerReadRequest layerReadRequest : requests) {
      QueryTask<List<ViewLayerRow>> singleTask = new QueryTask<>(null, NakshaContext.currentContext());

      Future<List<ViewLayerRow>> futureResult = singleTask.start(() -> executeSingle(
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
  private Map<String, List<ViewLayerRow>> getCollectedResults(
          List<Future<List<ViewLayerRow>>> tasks, Long timeoutMillis) {
    return tasks.stream()
        .map(future -> {
          try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
          } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw unchecked(e);
          }
        })
        .flatMap(Collection::stream)
        .collect(groupingBy(viewRow -> viewRow.getRow().getFeature().getId()));
  }

  private @NotNull Long getTimeout(@NotNull List<LayerReadRequest> requests) {
    Optional<Long> maxSessionTimeout = requests.stream()
        .map(it -> it.getSession().getStatementTimeout(TimeUnit.MILLISECONDS))
        .max(Long::compareTo);

    if (maxSessionTimeout.isEmpty() || maxSessionTimeout.get() == 0) {
      return defaultTimeoutMillis;
    } else {
      return maxSessionTimeout.get();
    }
  }

  private Stream<ViewLayerRow> executeSingle(
      @NotNull ViewLayer layer,
      @NotNull IReadSession session,
      @NotNull ReadFeatures request) {
    final long startTime = System.currentTimeMillis();
    String status = "OK";
    int featureCnt = 0;
    int layerPriority = viewRef.getViewCollection().priorityOf(layer);
    final String collectionId = layer.getCollectionId();

    // prepare request
    ReadFeatures clonedRequest = request; //TODO shallow clone
    clonedRequest.addCollectionId(collectionId);

    SuccessResponse cursor = (SuccessResponse) session.execute(clonedRequest);

      List<ResultRow> featureList = cursor.rows;
    log.info(
            "[View Request stats => streamId,layerId,method,status,timeTakenMs,fCnt] - ViewReqStats {} {} {} {} {} {}",
            NakshaContext.currentContext().getStreamId(),
            collectionId,
            "READ",
            status,
            System.currentTimeMillis() - startTime,
            featureCnt);
      return featureList.stream().map(row -> new ViewLayerRow(row, layerPriority, layer));
  }
}
