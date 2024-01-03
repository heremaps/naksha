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
package com.here.naksha.lib.view.concurrent;

import static com.here.naksha.lib.core.AbstractTask.State.DONE;
import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.here.naksha.lib.core.SimpleTask;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.storage.FeatureCodec;
import com.here.naksha.lib.core.models.storage.FeatureCodecFactory;
import com.here.naksha.lib.core.models.storage.MutableCursor;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.view.View;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class ParallelQueryExecutor {

  private final View viewRef;

  public ParallelQueryExecutor(@NotNull View viewRef) {
    this.viewRef = viewRef;
  }

  public <FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>>
      Map<String, List<ViewLayerRow<FEATURE, CODEC>>> queryInParallel(
          @NotNull List<LayerRequest> requests, FeatureCodecFactory<FEATURE, CODEC> codecFactory) {
    ConcurrentLinkedQueue<ViewLayerRow<FEATURE, CODEC>> allLayersResults = new ConcurrentLinkedQueue<>();
    List<SimpleTask<?>> tasks = new ArrayList<>();
    for (LayerRequest layerRequest : requests) {
      QueryTask<List<ViewLayerRow<FEATURE, CODEC>>> singleTask = new QueryTask<>();
      singleTask.addListener(allLayersResults::addAll);

      singleTask.start(() -> executeSingle(
              layerRequest.getViewLayer(),
              layerRequest.getSession(),
              codecFactory,
              layerRequest.getRequest())
          .collect(toList()));
      tasks.add(singleTask);
    }

    // wait for all
    while (!tasks.stream().allMatch(task -> task.state() == DONE)) {}
    return allLayersResults.stream()
        .collect(groupingBy(viewRow -> viewRow.getRow().getId()));
  }

  private <FEATURE, CODEC extends FeatureCodec<FEATURE, CODEC>> Stream<ViewLayerRow<FEATURE, CODEC>> executeSingle(
      @NotNull ViewLayer layer,
      @NotNull IReadSession session,
      @NotNull FeatureCodecFactory<FEATURE, CODEC> codecFactory,
      @NotNull ReadFeatures request) {
    int layerPriority = viewRef.getViewCollection().priorityOf(layer);

    // prepare request
    ReadFeatures clonedRequest = request.shallowClone();
    clonedRequest.withCollections(new ArrayList<>());
    clonedRequest.addCollection(layer.getCollectionId());

    Result result = session.execute(clonedRequest);
    try (MutableCursor<FEATURE, CODEC> cursor = result.mutableCursor(codecFactory)) {
      return cursor.asList().stream().map(row -> new ViewLayerRow<>(row, layerPriority, layer));
    } catch (NoCursor e) {
      throw unchecked(e);
    }
  }
}
