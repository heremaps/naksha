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
import static naksha.base.Base.getLogger;
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

import naksha.base.*;
import naksha.model.*;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.objects.StandardMembers;
import naksha.model.objects.XyzMembers;
import naksha.model.request.*;
import naksha.model.request.ops.And;
import naksha.model.request.ops.Equals;
import naksha.model.request.ops.OpList;
import naksha.model.request.ops.Or;
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
    CollectedFeatures collectedFeatures = collectInitialFeatures(readFeatures, context);
    List<FeatureWithPredecessor> featuresWithPredecessors = featuresWithPredecessors(collectedFeatures, context);
    return featuresEnhancedWithActivity(featuresWithPredecessors);
  }

  private CollectedFeatures collectInitialFeatures(ReadFeatures readFeatures, NakshaContext context) {
    return new CollectedFeatures(fetchFeatures(readFeatures, context));
  }

  // Pairs each root feature with its immediate predecessor.
  //
  // Strategy: find every root that wasn't already paired via `nuuid` walking inside the initial fetch,
  // then issue ONE additional history read that looks up rows whose `next_version` matches the root's
  // own version. That gives us the predecessor — which then gets merged back via `addPredecessors`.
  //
  // (Historical note: this used to have a puuid-based fast path; puuid was removed from XyzNs as part
  // of the prev_tn cleanup, and the next_version-based lookup is the canonical mechanism per spec.)
  private List<FeatureWithPredecessor> featuresWithPredecessors(CollectedFeatures collectedFeatures, NakshaContext context) {
    collectMissingPredecessors(collectedFeatures, context);
    return collectedFeatures.getActivityLogRoots().stream()
        .map(feature -> new FeatureWithPredecessor(
            feature,
            collectedFeatures.getPredecessorOf(feature)
        ))
        .filter(fwp -> !isOrphanTombstone(fwp))
        .toList();
  }

  /**
   * Returns true for a DELETED feature that has no predecessor (history was disabled at delete time).
   * Such tombstones represent features that never participated in activity logging and should be excluded.
   */
  private static boolean isOrphanTombstone(FeatureWithPredecessor fwp) {
    return Action.DELETE.equals(fwp.feature().getProperties().getXyz().getAction())
        && fwp.oldFeature() == null;
  }

  private void collectMissingPredecessors(CollectedFeatures collectedFeatures, NakshaContext context) {
    List<TupleNumber> tnsOfRootsMissingPredecessor = collectedFeatures.activityLogRoots.stream()
        .filter(f -> !collectedFeatures.allByNuuid.containsKey(f.getProperties().getXyz().getUuid()))
        .map(XyzMembers.XyzTn::readTupleNumber)
        .toList();
    if (!tnsOfRootsMissingPredecessor.isEmpty()) {
      List<NakshaFeature> missingPredecessorsByNextVersion =
          fetchFeatures(missingPredecessorFeatures(tnsOfRootsMissingPredecessor), context);
      collectedFeatures.addPredecessors(missingPredecessorsByNextVersion);
    }
  }

  private ReadFeatures missingPredecessorFeatures(List<TupleNumber> tupleNumbers) {
    // next_version is a plain int8 column, so we pass an Int64[] of the version values.
    final Or or = new Or();
    final OpList orClauses = or.getChildren();
      for (TupleNumber tupleNumber : tupleNumbers) {
        //TODO very inefficient, but ISession.loadTuples() currently cannot target next version
          orClauses.add(
                  new And(
                          new Equals(StandardMembers.NextVersionMember.getId(), tupleNumber.version),
                          new Equals(StandardMembers.FeatureNumberMember.getId(), tupleNumber.featureNumber)
                  )
          );
      }
    ReadFeatures requestPredecessors = new ReadFeatures();
    requestPredecessors.setCollectionId(properties.getSpaceId());
    requestPredecessors.setQueryHistory(true);
    requestPredecessors.setMemberQuery(or);
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

  private static class CollectedFeatures {

    private final @NotNull List<NakshaFeature> activityLogRoots;
    private final @NotNull Map<String, NakshaFeature> allByNuuid;

    CollectedFeatures(@NotNull List<NakshaFeature> activityLogRoots) {
      this.activityLogRoots = activityLogRoots;
      this.allByNuuid = new HashMap<>();
      updateAllByNuuid(activityLogRoots);
    }

    @NotNull List<NakshaFeature> getActivityLogRoots() {
      return activityLogRoots;
    }

    @Nullable NakshaFeature getPredecessorOf(NakshaFeature nakshaFeature) {
      return allByNuuid.get(xyzNs(nakshaFeature).getUuid());
    }

    void addPredecessors(List<NakshaFeature> predecessors) {
      updateAllByNuuid(predecessors);
    }

    private void updateAllByNuuid(List<NakshaFeature> features) {
      for (NakshaFeature feature : features) {
        String nuuid = nuuidOrNullIfDeleted(xyzNs(feature));
        if (nuuid != null) {
          allByNuuid.put(nuuid, feature);
        }
      }
    }

    private static XyzNs xyzNs(NakshaFeature feature) {
      return feature.getProperties().getXyz();
    }

    /**
     * Returns nuuid (uuid of next feature) for all non-DELETE versions. If feature represents DELETED version, we return null. The reason
     * is that `nuuid` is equal to `uuid` when `op` is DELETE - this breaks grouping by nuuid as we can have 2 versions having the same
     * nuuid (ie UPDATE & DELETE)
     */
    private static String nuuidOrNullIfDeleted(XyzNs xyzNs) {
      if (Action.DELETE.equals(xyzNs.getAction())) {
        return null;
      } else {
        return xyzNs.getNuuid();
      }
    }
  }

  private record FeatureWithPredecessor(@NotNull NakshaFeature feature, @Nullable NakshaFeature oldFeature) {

  }
}
