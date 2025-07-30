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
import static com.here.naksha.handler.activitylog.ActivityLogRequestTranslationUtil.transformOriginalRequest;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SUCCEED_WITHOUT_PROCESSING;
import static naksha.base.Platform.getLogger;
import static naksha.model.util.ResultHelper.extractResponseItems;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import naksha.base.JvmBoxingUtil;
import naksha.base.StringList;
import naksha.model.Action;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.TupleNumber;
import naksha.model.TupleNumberVariant;
import naksha.model.XyzNs;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.WriteRequest;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.MetaColumn;
import naksha.model.request.query.MetaQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ActivityLogHandler extends AbstractEventHandler {

  private static final Comparator<NakshaFeature> FEATURE_COMPARATOR = new ActivityLogComparator();
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
    final ReadFeatures request = (ReadFeatures) event.getRequest();
    transformOriginalRequest(request, properties.getSpaceId());
    try {
      List<NakshaFeature> activityLogFeatures = activityLogFeatures(request, ctx);
      return new SuccessResponse(NakshaFeatureList.fromList(activityLogFeatures));
    } catch (NakshaException e) {
      getLogger().error("Failed to process activity log", e);
      return new ErrorResponse(e.getError());
    } catch (Exception e) {
      getLogger().error("Failed to process activity log", e);
      return new ErrorResponse(NakshaError.EXCEPTION, "Failed to process activity log", e);
    }
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
    Naksha.cache.clear(); // TODO CASL-1107: this effectively kills TupleCache but there's no other way to ensure references to TNs are present
    CollectedFeatures initialFeatures = collectInitialFeatures(readFeatures, context);
    List<FeatureWithPredecessor> featuresWithPredecessors = featuresWithPredecessors(initialFeatures, context);
    return featuresEnhancedWithActivity(featuresWithPredecessors);
  }

  private CollectedFeatures collectInitialFeatures(ReadFeatures readFeatures, NakshaContext context) {
    CollectedFeatures collectedFeatures = new CollectedFeatures();
    collectedFeatures.add(fetchFeatures(readFeatures, context));
    return collectedFeatures;
  }

  // TODO: CASL-1094: in V2 this method was utilizing `properties.xyz.puuid` field to combine subsequent versions of feature
  // During v3 alignment (CASL-1057) it was observed that sometimes puuids of UPDATED & DELETED features are missing
  // Because of that, a bypass that uses `properties.xyz.nuuid` was introduced - logically, it's still correct to combine subsequent versions like that
  // However, this is not expected behavior, as both `puuid` and `nuuid` are expected to be populated in middle features, which is not the case
  // CASL-1094 aims to find the cause and fix missing puuids, then we should consider moving back to the logic that was in place in V2
  private List<FeatureWithPredecessor> featuresWithPredecessors(CollectedFeatures collectedFeatures, NakshaContext context) {
    collectMissingPredecessors(collectedFeatures, context);
    return collectedFeatures.byUuid
        .entrySet().stream()
        .map(uuidAndFeature -> new FeatureWithPredecessor(
            uuidAndFeature.getValue(),
            collectedFeatures.byNuuid.get(uuidAndFeature.getKey())
        ))
        .toList();
  }

  private void collectMissingPredecessors(CollectedFeatures collectedFeatures, NakshaContext context) {
    List<TupleNumber> featuresWithoutPredecessorsTns = collectedFeatures.byUuid.values().stream()
        .filter(f -> !collectedFeatures.byNuuid.containsKey(f.getId()))
        .map(NakshaFeature::getTupleNumber)
        .toList();
    if(!featuresWithoutPredecessorsTns.isEmpty()) {
      List<NakshaFeature> missingPredecessors = fetchFeatures(requestPredecessorsOf(featuresWithoutPredecessorsTns), context);
      collectedFeatures.add(missingPredecessors);
    }
  }

  private ReadFeatures requestPredecessorsOf(List<TupleNumber> tupleNumbers) {
    // we will compare against `next_tn` which is encodded with 96-bit encoding
    byte[][] b96tns = new byte[tupleNumbers.size()][];
    for (int i = 0; i < tupleNumbers.size(); i++) {
      b96tns[i] = tupleNumbers.get(i).toByteArray(TupleNumberVariant.B96);
    }
    MetaQuery nuidQuery = new MetaQuery(MetaColumn.nextVersion(), AnyOp.IS_ANY_OF, b96tns);
    ReadFeatures requestPredecessors = new ReadFeatures();
    requestPredecessors.setCollectionIds(StringList.of(properties.getSpaceId()));
    requestPredecessors.setQueryHistory(true);
    requestPredecessors.getQuery().setMetadata(nuidQuery);
    return requestPredecessors;
  }

  private List<NakshaFeature> fetchFeatures(ReadFeatures readFeatures, NakshaContext context) {
    Response response = nakshaHub().getSpaceStorage().useReadSession(
        SessionOptions.from(context, true),
        readSession -> readSession.execute(readFeatures)
    );
    if (response instanceof SuccessResponse successResponse) {
      return extractResponseItems(successResponse, NakshaFeature.class);
    } else if (response instanceof ErrorResponse errorResponse) {
      throw new NakshaException(errorResponse.getError());
    } else {
      throw new NakshaException(NakshaError.EXCEPTION, "Unexpected response type: " + response.getClass());
    }
  }

  private List<NakshaFeature> featuresEnhancedWithActivity(List<FeatureWithPredecessor> featureWithPredecessors) {
    return featureWithPredecessors.stream()
        .map(featureWithPredecessor -> enhanceWithActivityLog(
            featureWithPredecessor.feature, featureWithPredecessor.oldFeature, properties.getSpaceId()))
        .sorted(FEATURE_COMPARATOR)
        .toList();
  }

  private static boolean nullOrEmpty(String value) {
    return value == null || value.isBlank();
  }

  /**
   * Returns nuuid (uuid of next feature) for all non-DELETE versions. If feature represents DELETED version, we return null. The reason is
   * that `nuuid` is equal to `uuid` when `op` is DELETE - this breaks grouping by nuuid as we can have 2 versions having the same nuuid (ie
   * UPDATE & DELETE)
   */
  private static String nuuidOrNullIfDeleted(XyzNs xyzNs) {
    if (Action.DELETED.equals(xyzNs.getAction())) {
      return null;
    } else {
      return xyzNs.getNuuid();
    }
  }

  private record CollectedFeatures(@NotNull Map<String, NakshaFeature> byUuid, @NotNull Map<String, NakshaFeature> byNuuid) {

    private CollectedFeatures() {
      this(new HashMap<>(), new HashMap<>());
    }

    private void add(List<NakshaFeature> features) {
      for (NakshaFeature feature : features) {
        XyzNs xyzNs = feature.getProperties().getXyz();
        byUuid.put(xyzNs.getUuid(), feature);
        String nuuid = nuuidOrNullIfDeleted(xyzNs);
        if (nuuid != null) {
          byNuuid.put(nuuid, feature);
        }
      }
    }
  }

  private record FeatureWithPredecessor(@NotNull NakshaFeature feature, @Nullable NakshaFeature oldFeature) {

  }
}
