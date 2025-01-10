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
package com.here.naksha.handler.activitylog;

import static com.here.naksha.handler.activitylog.ActivityLogEnhancer.enhanceWithActivityLog;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesFromResult;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SUCCEED_WITHOUT_PROCESSING;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityLogHandler extends AbstractEventHandler {

  private static final Comparator<NakshaFeature> FEATURE_COMPARATOR = new ActivityLogComparator();

  private final @NotNull Logger logger = LoggerFactory.getLogger(ActivityLogHandler.class);
  private final @NotNull ActivityLogHandlerProperties properties;

  // TODO: remove unused 'eventTarget' property as part of MCPODS-7103
  public ActivityLogHandler(
      @NotNull EventHandler handlerConfig, @NotNull INaksha hub, @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.properties = JsonSerializable.convert(handlerConfig.getProperties(), ActivityLogHandlerProperties.class);
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (request instanceof ReadFeatures) {
      return PROCESS;
    }
    if (request instanceof WriteCollections) {
      return SUCCEED_WITHOUT_PROCESSING;
    }
    return NOT_IMPLEMENTED;
  }

  @Override
  protected @NotNull Response process(@NotNull IEvent event) {
    final ErrorResponse validationError = propertiesValidationError();
    if (validationError != null) {
      return validationError;
    }
    final NakshaContext ctx = NakshaContext.currentContext();
    final ReadFeatures request = transformRequest(event.getRequest());
    List<NakshaFeature> activityLogFeatures = activityLogFeatures(request, ctx);
    return new SuccessResponse(NakshaFeatureList.fromList(activityLogFeatures));
  }

  private @NotNull ReadFeatures transformRequest(Request request) {
    final ReadFeatures readFeatures = (ReadFeatures) request;
    readFeatures.withReturnAllVersions(true);
    ActivityLogRequestTranslationUtil.translatePropertyOperation(readFeatures);
    readFeatures.setCollections(List.of(properties.getSpaceId()));
    return readFeatures;
  }

  private @Nullable ErrorResponse propertiesValidationError() {
    if (nullOrEmpty(properties.getSpaceId())) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          "Missing required property: " + ActivityLogHandlerProperties.SPACE_ID);
    }
    return null;
  }

  private List<NakshaFeature> activityLogFeatures(ReadFeatures readFeatures, NakshaContext context) {
    List<NakshaFeature> historyFeatures = fetchHistoryFeatures(readFeatures, context);
    return featuresEnhancedWithActivity(historyFeatures, context);
  }

  private List<NakshaFeature> fetchHistoryFeatures(ReadFeatures readFeatures, NakshaContext context) {
    try (IReadSession readSession =
        nakshaHub().getSpaceStorage().newReadSession(SessionOptions.from(context, true))) {
      try (Response result = readSession.execute(readFeatures)) {
        return readFeaturesFromResult(result, XyzFeature.class);
      }
    } catch (NoCursor | NoSuchElementException e) {
      return Collections.emptyList();
    }
  }

  private List<NakshaFeature> featuresEnhancedWithActivity(
      List<NakshaFeature> historyFeatures, NakshaContext context) {
    List<FeatureWithPredecessor> featuresWithPredecessors = featuresWithPredecessors(historyFeatures, context);
    return featuresWithPredecessors.stream()
        .map(featureWithPredecessor -> enhanceWithActivityLog(
            featureWithPredecessor.feature, featureWithPredecessor.oldFeature, properties.getSpaceId()))
        .sorted(FEATURE_COMPARATOR)
        .toList();
  }

  private List<FeatureWithPredecessor> featuresWithPredecessors(
      List<NakshaFeature> historyFeatures, NakshaContext context) {
    List<NakshaFeature> allNecessaryFeatures = collectAllNecessaryFeatures(historyFeatures, context);
    Map<String, NakshaFeature> allFeaturesByUuid = featuresByUuid(allNecessaryFeatures);
    return historyFeatures.stream()
        .map(feature -> new FeatureWithPredecessor(feature, allFeaturesByUuid.get(puuid(feature))))
        .toList();
  }

  private List<NakshaFeature> collectAllNecessaryFeatures(
      List<NakshaFeature> historyFeatures, NakshaContext context) {
    List<NakshaFeature> missingPredecessors = fetchMissingPredecessors(missingPuuids(historyFeatures), context);
    return combine(historyFeatures, missingPredecessors);
  }

  private List<NakshaFeature> combine(List<NakshaFeature> historyFeatures, List<NakshaFeature> missingPredecessors) {
    if (missingPredecessors.isEmpty()) {
      return historyFeatures;
    }
    return Stream.concat(historyFeatures.stream(), missingPredecessors.stream())
        .toList();
  }

  private Set<String> missingPuuids(List<NakshaFeature> historyFeatures) {
    Set<String> requiredPredecessorsUuids = new HashSet<>();
    Set<String> fetchedUuids = new HashSet<>();
    historyFeatures.forEach(historyFeature -> {
      fetchedUuids.add(uuid(historyFeature));
      String puuid = puuid(historyFeature);
      if (puuid != null) {
        requiredPredecessorsUuids.add(puuid);
      }
    });
    requiredPredecessorsUuids.removeAll(fetchedUuids);
    return requiredPredecessorsUuids;
  }

  private List<NakshaFeature> fetchMissingPredecessors(Set<String> missingUuids, NakshaContext context) {
    if (missingUuids.isEmpty()) {
      return Collections.emptyList();
    }
    return fetchHistoryFeatures(missingPredecessorsRequest(missingUuids), context);
  }

  private ReadFeatures missingPredecessorsRequest(Set<String> missingUuids) {
    POp[] matchUuids = missingUuids.stream()
        .map(missingUuid -> POp.eq(PRef.uuid(), missingUuid))
        .toArray(POp[]::new);
    return new ReadFeatures(properties.getSpaceId())
        .withReturnAllVersions(true)
        .withPropertyOp(POp.or(matchUuids));
  }

  @NotNull
  private static Map<String, NakshaFeature> featuresByUuid(List<NakshaFeature> historyFeatures) {
    return historyFeatures.stream().collect(toMap(ActivityLogHandler::uuid, identity()));
  }

  private static boolean nullOrEmpty(String value) {
    return value == null || value.isBlank();
  }

  private static String uuid(NakshaFeature feature) {
    return xyzNamespace(feature).getUuid();
  }

  private static String puuid(NakshaFeature feature) {
    return xyzNamespace(feature).getPuuid();
  }

  private static XyzNs xyzNamespace(NakshaFeature feature) {
    return feature.getProperties().getXyz();
  }

  private record FeatureWithPredecessor(@NotNull NakshaFeature feature, @Nullable NakshaFeature oldFeature) {}
}
