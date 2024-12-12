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
package com.here.naksha.lib.handlers.util;

import com.here.naksha.lib.core.models.storage.ContextWriteXyzFeatures;
import java.util.ArrayList;
import java.util.List;
import naksha.base.JvmProxyUtil;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.TagList;
import naksha.model.XyzNs;
import naksha.model.mom.MomChangeState;
import naksha.model.mom.MomDeltaNs;
import naksha.model.mom.MomReviewState;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaProperties;
import naksha.model.request.Write;
import naksha.model.request.WriteList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HandlerUtil {

  public static String REVIEW_STATE_PREFIX = "@:review-state:";

  private HandlerUtil() {}

  // TODO: CASL-736 switch to Tuple, wait for change in v3 enabling Tuple creation without storage,
  //  public static @NotNull ContextXyzFeatureResult createContextResultFromFeatureList(
  //      final @NotNull List<NakshaFeature> features,
  //      final @Nullable List<NakshaFeature> context,
  //      final @Nullable List<NakshaFeature> violations) {
  //    // Create list of ResultRow with input features
  //    final List<ResultTuple> resultTuples = new ArrayList<>();
  //    for (final NakshaFeature feature : features) {
  //      //
  //      resultTuples.add(new ResultTuple(ExecutedOp.UPDATED, null, feature));
  //    }
  //    // Create ContextResult with cursor, context and violations
  //    final ContextXyzFeatureResult ctxResult = new ContextXyzFeatureResult(resultTuples);
  //    ctxResult.setContext(context);
  //    ctxResult.setViolations(violations);
  //    return ctxResult;
  //  }

  /**
   * @param collectionIds If the number of collection IDs is smaller than the number of features, the last collection ID
   *                      will be reused for each feature given at the end. Which means a list of only 1 collection ID
   *                      is sufficient if every feature should be written in this same 1 collection.
   */
  public static @NotNull ContextWriteXyzFeatures createContextWriteRequestFromFeatureList(
      final @NotNull List<String> collectionIds,
      final @NotNull List<?> features,
      final @Nullable List<?> context,
      final @Nullable List<?> violations) {
    // generate new ContextWriteFeatures request
    final ContextWriteXyzFeatures cwf = new ContextWriteXyzFeatures();

    // Add features in the request
    for (int i = 0; i < features.size(); i++) {
      final NakshaFeature feature =
          checkInstanceOf(features.get(i), NakshaFeature.class, "Unsupported feature type");
      final Write write = new Write()
          .updateFeature(null, collectionIds.get(Math.min(i, collectionIds.size())), feature, false);
      cwf.add(write);
    }
    // add context to write request
    cwf.setContext(getXyzContextFromGenericList(context));
    // add violations to write request
    cwf.setViolations(getViolationsFromGenericList(violations));
    return cwf;
  }

  public static @NotNull ContextWriteXyzFeatures createContextWriteRequestFromWriteList(
      final @NotNull WriteList writes, final @Nullable List<?> context, final @Nullable List<?> violations) {
    // generate new ContextWriteFeatures request
    final ContextWriteXyzFeatures cwf = new ContextWriteXyzFeatures();

    // Add features in the request
    if (writes.isEmpty())
      throw new NakshaException(new NakshaError(NakshaError.ILLEGAL_ARGUMENT, "No features supplied"));
    for (final Write inputWrite : writes) {
      final NakshaFeature feature = HandlerUtil.checkInstanceOf(
          inputWrite.getFeature(), NakshaFeature.class, "Unsupported feature type");
      final Write write = new Write();
      write.setCollectionId(inputWrite.getCollectionId());
      write.setFeature(feature);
      write.setOp(inputWrite.getOp());
      cwf.add(write);
    }

    // add context to write request
    cwf.setContext(getXyzContextFromGenericList(context));

    // add violations to write request
    cwf.setViolations(getViolationsFromGenericList(violations));

    return cwf;
  }

  public static @NotNull List<NakshaFeature> getFeaturesFromWriteList(final @NotNull WriteList writes) {
    final List<NakshaFeature> outputFeatures = new ArrayList<>();
    for (final Write write : writes) {
      outputFeatures.add(write.getFeature());
    }
    return outputFeatures;
  }

  public static @Nullable List<NakshaFeature> getViolationsFromGenericList(final @Nullable List<?> violations) {
    List<NakshaFeature> outputViolations = null;
    if (violations != null) {
      for (final Object obj : violations) {
        final NakshaFeature violation = checkInstanceOf(
            obj, NakshaFeature.class, NakshaError.EXCEPTION, "Unsupported violation feature type");
        if (outputViolations == null) outputViolations = new ArrayList<>();
        // Add violation to output list
        outputViolations.add(violation);
      }
    }
    return outputViolations;
  }

  public static @Nullable List<NakshaFeature> getXyzContextFromGenericList(final @Nullable List<?> contextList) {
    List<NakshaFeature> outputCtx = null;
    if (contextList != null) {
      for (final Object obj : contextList) {
        final NakshaFeature context = checkInstanceOf(
            obj, NakshaFeature.class, NakshaError.EXCEPTION, "Unsupported context feature type");
        if (outputCtx == null) outputCtx = new ArrayList<>();
        // Add context to output list
        outputCtx.add(context);
      }
    }
    return outputCtx;
  }

  private static @NotNull List<String> tagsWithoutReviewState(@Nullable List<String> tags) {
    if (tags == null) {
      return new ArrayList<>();
    }
    for (int i = 0; i < tags.size(); i++) {
      final String tag = tags.get(i);
      if (tag.startsWith(REVIEW_STATE_PREFIX)) {
        tags.remove(i--);
      }
    }
    return tags;
  }

  public static <T> @NotNull T checkInstanceOf(
      final @Nullable Object input,
      final @NotNull Class<T> returnType,
      final @NotNull String nakshaErrorCode,
      final @NotNull String errDescPrefix) {
    if (input == null) {
      throw new NakshaException(new NakshaError(nakshaErrorCode, errDescPrefix + " - object is null."));
    }
    if (returnType.isAssignableFrom(input.getClass())) {
      return returnType.cast(input);
    }
    throw new NakshaException(new NakshaError(
        nakshaErrorCode, errDescPrefix + " - " + input.getClass().getSimpleName()));
  }

  public static <T> @NotNull T checkInstanceOf(
      final @Nullable Object input, final @NotNull Class<T> returnType, final @NotNull String errDescPrefix) {
    return checkInstanceOf(input, returnType, NakshaError.NOT_IMPLEMENTED, errDescPrefix);
  }

  public static void setDeltaReviewState(
      final @NotNull NakshaFeature feature, final @NotNull MomReviewState reviewState) {
    final NakshaProperties properties = feature.getProperties();
    final XyzNs xyzNs = properties.getXyz();
    final MomDeltaNs deltaNs = properties.getDelta();
    deltaNs.setChangeState(MomChangeState.UPDATED.getText());
    deltaNs.setReviewState(reviewState.getText());
    final @NotNull List<@NotNull String> tags = tagsWithoutReviewState(xyzNs.getTags());
    tags.add(REVIEW_STATE_PREFIX + reviewState);
    TagList tagList = JvmProxyUtil.box(tags, TagList.class);
    xyzNs.setTags(tagList, false);
  }
}
