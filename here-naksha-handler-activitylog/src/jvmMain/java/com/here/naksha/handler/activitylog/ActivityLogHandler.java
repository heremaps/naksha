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
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SUCCEED_WITHOUT_PROCESSING;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static naksha.model.util.ResultHelper.extractResponseItems;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import java.util.*;
import java.util.stream.Stream;
import naksha.base.JvmBoxingUtil;
import naksha.base.StringList;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.*;
import naksha.model.request.query.POr;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.request.query.StringOp;
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
      @NotNull EventHandlerConfig handlerConfig, @NotNull INaksha hub) {
    super(hub);
    this.properties = Objects.requireNonNull(
        JvmBoxingUtil.box(handlerConfig.getProperties(), ActivityLogHandlerProperties.class));
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (request instanceof ReadFeatures) {
      return PROCESS;
    }
    if (request instanceof WriteRequest && RequestTypesUtil.isOnlyWriteCollections(request)) {
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
    readFeatures.setQueryHistory(true);
    readFeatures.setVersions(Integer.MAX_VALUE);
    ActivityLogRequestTranslationUtil.translatePropertyOperation(readFeatures);
    readFeatures.setCollectionIds(StringList.fromList(List.of(properties.getSpaceId())));
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
    return nakshaHub().getSpaceStorage().useReadSession(SessionOptions.from(context, true),
            reader -> {
              Response response = reader.execute(readFeatures);
              if (!(response instanceof SuccessResponse)) {
                return Collections.emptyList();
              }
              try {
                return extractResponseItems((SuccessResponse) response, NakshaFeature.class);
              } catch (NoSuchElementException e) {
                return Collections.emptyList();
              }

            });
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
      List<NakshaFeature> historyFeatures,
      NakshaContext context
  ) {
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
    PQuery[] matchUuids = missingUuids.stream()
        .map(missingUuid ->
            new PQuery(new Property(NakshaProperties.XYZ_KEY, "uuid"), StringOp.EQUALS, missingUuid))
        .toArray(PQuery[]::new);
    final ReadFeatures readFeatures = new ReadFeatures().addCollectionId(properties.getSpaceId());
    readFeatures.setQueryHistory(true);
    readFeatures.setVersions(Integer.MAX_VALUE);
    readFeatures.getQuery().setProperties(new POr(matchUuids));
    return readFeatures;
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
