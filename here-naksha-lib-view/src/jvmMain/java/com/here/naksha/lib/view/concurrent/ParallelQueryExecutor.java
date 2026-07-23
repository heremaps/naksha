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
import static naksha.base.Platform.longToInt64;
import static naksha.base.NakshaError.EXCEPTION;
import static naksha.base.NakshaError.INTERNAL_ERROR;

import com.here.naksha.lib.view.View;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerFeature;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import naksha.base.Int64;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
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

  /**
   * Execute the given read requests in parallel.
   * @param requests the requests to execute.
   * @return the merged result as map where the key is the identifier of the feature, and the value are all states.
   */
  public @NotNull Map<String, @NotNull List<@NotNull ViewLayerFeature>> queryInParallel(
          @NotNull List<@NotNull LayerReadRequest> requests
  ) {
    // Execute all requests in parallel.
    // Note: Each request has an individual timeout defined in it's read-session.
    //       Therefore, we do not have to add a timeout later, when collecting results,
    //       the future will simply return a timeout exception, when the request timed-out.
    final var futures = new ArrayList<@NotNull Future<@NotNull List<@NotNull ViewLayerFeature>>>(requests.size());
    for (final var layerReadRequest : requests) {
      final QueryTask<@NotNull List<@NotNull ViewLayerFeature>> singleTask = new QueryTask<>(NakshaContext.currentContext());
      final var futureResult = singleTask.start(() -> executeSingle(
              layerReadRequest.getViewLayer(),
              layerReadRequest.getSession(),
              layerReadRequest.getRequest()
          )
      );
      futures.add(futureResult);
    }
    try {
      final var result = new HashMap<@NotNull String, @NotNull List<@NotNull ViewLayerFeature>>();
      for (var future : futures) {
        final List<@NotNull ViewLayerFeature> features = future.get();
        for (final ViewLayerFeature feature : features) {
          final FeatureTuple tuple = feature.getFeatureTuple();
          final String id = tuple.getId();
          var versions = result.computeIfAbsent(id, v -> new ArrayList<>());
          versions.add(feature);
        }
      }
      return result;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw unchecked(e);
    }
  }

  private List<@NotNull ViewLayerFeature> executeSingle(
      @NotNull ViewLayer layer,
      @NotNull IReadSession session,
      @NotNull ReadFeatures request
  ) {
    final long startTime = System.currentTimeMillis();
    int featureCnt = 0;
    int layerPriority = view.getViewCollection().priorityOf(layer);
    final String collectionId = layer.getCollectionId();
    final ReadFeatures readRequest = request.copy(false);
    readRequest.setCatalogId(layer.getMapId());
    readRequest.setCollectionId(collectionId);

    final @NotNull Response readResponse = session.execute(readRequest);
    final FeatureTupleList featureTupleList = getFeatureTuples(readResponse);
    final Int64 maxMicros = longToInt64(TimeUnit.SECONDS.toMicros(10));
    Naksha.cache.load(featureTupleList, 0, featureTupleList.size(), maxMicros, true, false);
    log.info(
        "[View Request stats => streamId,layerId,method,status,timeTakenMs,fCnt] - ViewReqStats {} {} {} {} {} {}",
        NakshaContext.currentContext().getStreamId(),
        collectionId,
        "READ",
        "OK",
        System.currentTimeMillis() - startTime,
        featureCnt);
    final int SIZE = featureTupleList.getSize();
    final var viewFeatures = new ArrayList<ViewLayerFeature>(SIZE);
    for (int i = 0; i < SIZE; i++) {
      final var featureTuple = featureTupleList.get(i);
      if (featureTuple == null) continue;
      final var viewFeature = new ViewLayerFeature(featureTuple, layerPriority, layer);
      viewFeatures.add(viewFeature);
    }
    return viewFeatures;
  }

  private static @NotNull FeatureTupleList getFeatureTuples(@NotNull Response response) {
    if (response instanceof SuccessResponse) {
      final var success = (SuccessResponse) response;
      try {
        return success.getFeatureTupleList();
      } catch (NakshaException e) {
        throw e;
      } catch (Exception e) {
        final var MSG = "Failed to invoke getFeatureTupleList() on success response";
        log.error(MSG, e);
        throw new NakshaException(INTERNAL_ERROR, MSG);
      }
    }
    // TODO: Improve the error handling!
    final @NotNull NakshaError error;
    if (response instanceof ErrorResponse) {
      error = ((ErrorResponse) response).getError();
    } else {
      error = new NakshaError(EXCEPTION, "Response is not successful, unknown reason");
    }
    throw new NakshaException(error);
  }
}
