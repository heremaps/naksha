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
import static naksha.base.Platform.longToInt64;

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

import naksha.base.Int64;
import naksha.base.StringList;
import naksha.model.*;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParallelQueryExecutor {
  private static final Logger log = LoggerFactory.getLogger(ParallelQueryExecutor.class);
  private final long defaultTimeoutMillis = 1000 * 60 * 10L; // 10 minutes
  private final @NotNull View view;

  public ParallelQueryExecutor(@NotNull View view) {
    this.view = view;
  }

  public @NotNull Map<String, @NotNull List<@NotNull ViewLayerFeature>> queryInParallel(
          @NotNull List<@NotNull LayerReadRequest> requests
  ) {
    @NotNull List<@NotNull Future<@NotNull List<@NotNull ViewLayerFeature>>> futures = new ArrayList<>();

    for (LayerReadRequest layerReadRequest : requests) {
      final QueryTask<@NotNull List<@NotNull ViewLayerFeature>> singleTask = new QueryTask<>(NakshaContext.currentContext());
      final Future<@NotNull List<@NotNull ViewLayerFeature>> futureResult = singleTask.start(() -> executeSingle(
              layerReadRequest.getViewLayer(),
              layerReadRequest.getSession(),
              layerReadRequest.getRequest())
          .collect(toList()));
      futures.add(futureResult);
    }

    // wait for all
    final Long timeout = getTimeout(requests);
    return getCollectedResults(futures, timeout);
  }

  private @NotNull Map<@NotNull String, @NotNull List<@NotNull ViewLayerFeature>> getCollectedResults(
      @NotNull List<@NotNull Future<@NotNull List<@NotNull ViewLayerFeature>>> tasks,
      @NotNull Long timeoutMillis
  ) {
    return tasks.stream()
        .map(future -> {
          try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
          } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw unchecked(e);
          }
        })
        .flatMap(Collection::stream)
        .collect(groupingBy(viewLayerFeature -> viewLayerFeature.getFeatureTuple().getId()));
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
      @NotNull ViewLayer layer,
      @NotNull IReadSession session,
      @NotNull ReadFeatures request
  ) {
    final long startTime = System.currentTimeMillis();
    String status = "OK";
    int featureCnt = 0;
    int layerPriority = view.getViewCollection().priorityOf(layer);
    final String collectionId = layer.getCollectionId();
    final ReadFeatures readRequest = request.copy(false);
    readRequest.setCatalogId(layer.getMapId());
    readRequest.setCollectionId(new StringList(collectionId));

    final @NotNull Response readResponse = session.execute(readRequest);
    final FeatureTupleList featureList = getFeatureTuples(readResponse);
    final Int64 maxMicros = longToInt64(TimeUnit.SECONDS.toMicros(10));
    Naksha.cache.load(featureList, 0, featureList.size(), maxMicros, true, false);
    log.info(
        "[View Request stats => streamId,layerId,method,status,timeTakenMs,fCnt] - ViewReqStats {} {} {} {} {} {}",
        NakshaContext.currentContext().getStreamId(),
        collectionId,
        "READ",
        status,
        System.currentTimeMillis() - startTime,
        featureCnt);
    return featureList.stream().map(featureTuple -> new ViewLayerFeature(featureTuple, layerPriority, layer));
  }

  private static @NotNull FeatureTupleList getFeatureTuples(@NotNull Response response) {
    if (!(response instanceof SuccessResponse)) {
      // TODO: Improve the error handling!
      final @NotNull NakshaError error;
      if (response instanceof ErrorResponse) {
        error = ((ErrorResponse) response).getError();
      } else {
        error = new NakshaError(NakshaError.EXCEPTION, "Response is not successful, unknown reason");
      }
      throw new NakshaException(error);
    }
    final @NotNull SuccessResponse success = (SuccessResponse) response;
    return success.useFeatureTupleList();
  }
}
