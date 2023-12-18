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
package com.here.naksha.lib.handlers.util;

import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.EChangeState;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.EReviewState;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.HereDeltaNs;
import com.here.naksha.lib.core.models.geojson.implementation.namespaces.XyzNamespace;
import com.here.naksha.lib.core.models.storage.*;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HandlerUtil {

  public static String REVIEW_STATE_PREFIX = "@:review-state:";

  public static @NotNull ContextResult<XyzFeature, XyzFeature, XyzFeature, XyzFeatureCodec>
      createContextResultFromCodecList(
          final @NotNull List<?> inputCodecs,
          final @Nullable List<?> context,
          final @Nullable List<XyzFeature> violations) {
    // Create ForwardCursor with input features
    final List<XyzFeatureCodec> codecs = new ArrayList<>();
    final XyzFeatureCodecFactory codecFactory = XyzFeatureCodecFactory.get();
    for (final Object inputCodec : inputCodecs) {
      if (!(inputCodec instanceof XyzFeatureCodec xyzCodec)) {
        throw new XyzErrorException(
            XyzError.NOT_IMPLEMENTED,
            "Unsupported feature codec type during validation - "
                + inputCodec.getClass().getSimpleName());
      }
      codecs.add(codecFactory.newInstance().copy(xyzCodec));
    }
    final ListBasedForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
        new ListBasedForwardCursor<>(codecFactory, codecs);

    // TODO : Create list of contextual features based on input context
    final List<XyzFeature> ctxFeatures = null;

    // Create ContextResult with cursor, context and violations
    final ContextXyzFeatureResult ctxResult = new ContextXyzFeatureResult(cursor);
    ctxResult.setContext(ctxFeatures);
    ctxResult.setViolations(violations);
    return ctxResult;
  }

  public static @NotNull ContextResult<XyzFeature, XyzFeature, XyzFeature, XyzFeatureCodec>
      createContextResultFromFeatureList(
          final @NotNull List<XyzFeature> features,
          final @Nullable List<?> context,
          final @Nullable List<XyzFeature> violations) {
    // Create ForwardCursor with input features
    final List<XyzFeatureCodec> codecs = new ArrayList<>();
    final XyzFeatureCodecFactory codecFactory = XyzFeatureCodecFactory.get();
    for (final XyzFeature feature : features) {
      final XyzFeatureCodec codec = codecFactory.newInstance();
      codec.setOp(EExecutedOp.UPDATED);
      codec.setFeature(feature);
      codecs.add(codec);
    }
    final ListBasedForwardCursor<XyzFeature, XyzFeatureCodec> cursor =
        new ListBasedForwardCursor<>(codecFactory, codecs);

    // TODO : Create list of contextual features based on input context
    final List<XyzFeature> ctxFeatures = null;

    // Create ContextResult with cursor, context and violations
    final ContextXyzFeatureResult ctxResult = new ContextXyzFeatureResult(cursor);
    ctxResult.setContext(ctxFeatures);
    ctxResult.setViolations(violations);
    return ctxResult;
  }

  public static @NotNull Request<?> createWriteContextRequestFromResult(
      final @NotNull String collectionId, final @NotNull Result result) {
    if (result instanceof ErrorResult er) throw new XyzErrorException(er.reason, er.message);

    if (!(result instanceof ContextResult<?, ?, ?, ?> ctxResult))
      throw new XyzErrorException(
          XyzError.NOT_IMPLEMENTED,
          "Unsupported result type " + result.getClass().getSimpleName());
    // generate new ContextWriteFeatures request
    final ContextWriteXyzFeatures cwf = new ContextWriteXyzFeatures(collectionId);

    // add features to write request
    try (final ForwardCursor<XyzFeature, XyzFeatureCodec> cursor = ctxResult.cursor(XyzFeatureCodecFactory.get())) {
      while (cursor.hasNext()) {
        if (!cursor.next()) {
          throw new XyzErrorException(
              XyzError.EXCEPTION, "Unexpected failure while iterating through result");
        }
        final XyzFeature feature = cursor.getFeature();
        if (feature == null)
          throw new XyzErrorException(
              XyzError.EXCEPTION, "Unexpected empty feature while creating endorsement request");
        cwf.add(EWriteOp.PUT, feature);
      }
    } catch (NoCursor e) {
      throw new RuntimeException(e);
    }

    // add context to write request
    final List<?> contextList = ctxResult.getContext();
    List<XyzFeature> wrtCtxList = null;
    if (contextList != null) {
      for (final Object obj : contextList) {
        if (!(obj instanceof XyzFeature ctx))
          throw new XyzErrorException(
              XyzError.EXCEPTION,
              "Unexpected context type while creating endorsement request - "
                  + obj.getClass().getSimpleName());
        if (wrtCtxList == null) wrtCtxList = new ArrayList<>();
        wrtCtxList.add(ctx);
      }
    }
    cwf.setContext(wrtCtxList);

    // add violations to write request
    final List<?> violationList = ctxResult.getViolations();
    List<XyzFeature> wrtViolationList = null;
    if (violationList != null) {
      for (final Object obj : violationList) {
        if (!(obj instanceof XyzFeature violation))
          throw new XyzErrorException(
              XyzError.EXCEPTION,
              "Unexpected violation type while creating endorsement request - "
                  + obj.getClass().getSimpleName());
        if (wrtViolationList == null) wrtViolationList = new ArrayList<>();
        wrtViolationList.add(violation);
      }
    }
    cwf.setViolations(wrtViolationList);

    return cwf;
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

  public static void setDeltaReviewState(final @NotNull XyzFeature feature, final @NotNull EReviewState reviewState) {
    final XyzProperties properties = feature.getProperties();
    final XyzNamespace xyzNs = properties.getXyzNamespace();
    final HereDeltaNs deltaNs = properties.useDeltaNamespace();
    deltaNs.setChangeState(EChangeState.UPDATED);
    deltaNs.setReviewState(reviewState);
    final @NotNull List<@NotNull String> tags = tagsWithoutReviewState(xyzNs.getTags());
    tags.add(REVIEW_STATE_PREFIX + reviewState);
    xyzNs.setTags(tags, false);
  }
}
