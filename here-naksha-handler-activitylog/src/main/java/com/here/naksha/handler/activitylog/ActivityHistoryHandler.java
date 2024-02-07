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
package com.here.naksha.handler.activitylog;

import static com.here.naksha.lib.core.models.storage.EWriteOp.DELETE;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesFromResult;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SUCCEED_WITHOUT_PROCESSING;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.here.naksha.handler.activitylog.exceptions.UndefinedStorageIdException;
import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.Original;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzActivityLog;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.storage.ReadFeatures;
import com.here.naksha.lib.core.models.storage.Request;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.WriteCollections;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.util.diff.Difference;
import com.here.naksha.lib.core.util.diff.Patcher;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityHistoryHandler extends AbstractEventHandler {

  private final @NotNull Logger logger = LoggerFactory.getLogger(ActivityHistoryHandler.class);
  private final @NotNull EventTarget<?> eventTarget;
  private final @NotNull EventHandler handlerConfig;
  private final @NotNull ActivityHistoryHandlerProperties properties;

  public ActivityHistoryHandler(
      @NotNull INaksha hub, @NotNull EventHandler handlerConfig, @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventTarget = eventTarget;
    this.handlerConfig = handlerConfig;
    this.properties =
        JsonSerializable.convert(handlerConfig.getProperties(), ActivityHistoryHandlerProperties.class);
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request<?> request = event.getRequest();
    if (request instanceof ReadFeatures) {
      return PROCESS;
    }
    if (isDeleteSingleCollectionRequest(request)) {
      return SUCCEED_WITHOUT_PROCESSING;
    }
    return NOT_IMPLEMENTED;
  }

  private boolean isDeleteSingleCollectionRequest(Request<?> request) {
    if (request instanceof WriteCollections<?, ?, ?> wc) {
      return wc.features.size() == 1
          && DELETE.toString().equals(wc.features.get(0).getOp());
    }
    return false;
  }

  @Override
  protected @NotNull Result process(@NotNull IEvent event) {
    final NakshaContext ctx = NakshaContext.currentContext();
    final ReadFeatures request = (ReadFeatures) event.getRequest();
    try {
      final String storageId = extractStorageId();
      addStorageIdToStreamInfo(storageId, ctx);
      final IStorage storageImpl = nakshaHub().getStorageById(storageId);
      // Obtain IStorage implementation using NakshaHub
      logger.info(
          "Using storage implementation [{}]", storageImpl.getClass().getName());

      List<XyzFeature> activityHistoryFeatures = activityHistoryFeatures(storageImpl, request, ctx);
      return ActivityHistorySuccessResult.forFeatures(activityHistoryFeatures);
    } catch (UndefinedStorageIdException us) {
      return us.toErrorResult();
    }
  }

  private @NotNull String extractStorageId() {
    final String storageId = properties.getStorageId();
    if (storageId == null) {
      logger.error("No storageId configured");
      throw new UndefinedStorageIdException(handlerConfig.getId());
    }
    return storageId;
  }

  private List<XyzFeature> activityHistoryFeatures(
      IStorage storage, ReadFeatures readFeatures, NakshaContext nakshaContext) {
    List<XyzFeature> historyFeatures = fetchHistoryFeatures(storage, readFeatures, nakshaContext);
    return featuresEnhancedWithActivity(historyFeatures);
  }

  private List<XyzFeature> fetchHistoryFeatures(
      IStorage storage, ReadFeatures readFeatures, NakshaContext nakshaContext) {
    readFeatures.withReturnAllVersions(true);
    try (IReadSession readSession = storage.newReadSession(nakshaContext, true)) {
      Result result = readSession.execute(readFeatures);
      return readFeaturesFromResult(result, XyzFeature.class);
    } catch (NoCursor e) {
      return Collections.emptyList();
    }
  }

  private List<XyzFeature> featuresEnhancedWithActivity(List<XyzFeature> historyFeatures) {
    List<FeatureWithPredecessor> featuresWithPredecessors = featuresWithPredecessors(historyFeatures);
    return featuresWithPredecessors.stream()
        .map(this::featureEnhancedWithActivity)
        .toList();
  }

  private List<FeatureWithPredecessor> featuresWithPredecessors(List<XyzFeature> historyFeatures) {
    Map<String, XyzFeature> featuresByUuid = featuresByUuid(historyFeatures);
    return historyFeatures.stream()
        .map(feature -> new FeatureWithPredecessor(feature, featuresByUuid.get(puuid(feature))))
        .toList();
  }

  @NotNull
  private static Map<String, XyzFeature> featuresByUuid(List<XyzFeature> historyFeatures) {
    return historyFeatures.stream().collect(toMap(ActivityHistoryHandler::uuid, identity()));
  }

  private static String uuid(XyzFeature feature) {
    return feature.getProperties().getXyzNamespace().getUuid();
  }

  private static String puuid(XyzFeature feature) {
    return feature.getProperties().getXyzNamespace().getPuuid();
  }

  private XyzFeature featureEnhancedWithActivity(@NotNull FeatureWithPredecessor featureWithPredecessor) {
    XyzActivityLog activityLog = activityLog(featureWithPredecessor);
    XyzFeature feature = featureWithPredecessor.feature;
    feature.getProperties().setXyzActivityLog(activityLog);
    return feature;
  }

  // note (TODO: delete): to jest de facto `@ns:com:here:xyz:log`
  private XyzActivityLog activityLog(@NotNull FeatureWithPredecessor featureWithPredecessor) {
    final XyzNamespace xyzNamespace =
        featureWithPredecessor.feature.getProperties().getXyzNamespace();
    final XyzActivityLog xyzActivityLog = new XyzActivityLog();
    xyzActivityLog.setId(featureWithPredecessor.feature.getId());
    xyzActivityLog.setOriginal(original(xyzNamespace, spaceId()));
    xyzActivityLog.setAction(xyzNamespace.getAction());
    xyzActivityLog.setDiff(calculateDiff(featureWithPredecessor));
    return xyzActivityLog;
  }

  private JsonNode calculateDiff(@NotNull FeatureWithPredecessor featureWithPredecessor) {
    Difference diff = Patcher.getDifference(featureWithPredecessor.feature, featureWithPredecessor.oldFeature);
    return jsonDiff(diff);
  }

  private JsonNode jsonDiff(Difference diff) {
    // TODO: implement
    return BooleanNode.TRUE;
  }

  private Original original(@Nullable XyzNamespace xyzNamespace, @Nullable String spaceId) {
    Original original = new Original();
    if (xyzNamespace != null) {
      original.setPuuid(xyzNamespace.getPuuid());
      //      original.setMuuid(xyzNamespace.getMuuid()); TODO: where to get Muuid from?
      original.setUpdatedAt(xyzNamespace.getUpdatedAt());
      original.setCreatedAt(xyzNamespace.getCreatedAt());
    }
    if (spaceId != null) {
      original.setSpace(spaceId);
    }
    return original;
  }

  // TODO: is this ok?
  private @Nullable String spaceId() {
    if (eventTarget instanceof Space space) {
      return space.getId();
    }
    return null;
  }

  private record FeatureWithPredecessor(@NotNull XyzFeature feature, @Nullable XyzFeature oldFeature) {}
}
