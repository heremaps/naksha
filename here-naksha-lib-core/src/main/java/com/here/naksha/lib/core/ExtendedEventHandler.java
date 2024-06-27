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
package com.here.naksha.lib.core;

import com.here.naksha.lib.core.exceptions.XyzErrorException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.payload.Event;
import com.here.naksha.lib.core.models.payload.events.admin.ModifySubscriptionEvent;
import com.here.naksha.lib.core.models.payload.events.feature.DeleteFeaturesByTagEvent;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByBBoxEvent;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByGeometryEvent;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByIdEvent;
import com.here.naksha.lib.core.models.payload.events.feature.GetFeaturesByTileEvent;
import com.here.naksha.lib.core.models.payload.events.feature.IterateFeaturesEvent;
import com.here.naksha.lib.core.models.payload.events.feature.LoadFeaturesEvent;
import com.here.naksha.lib.core.models.payload.events.feature.ModifyFeaturesEvent;
import com.here.naksha.lib.core.models.payload.events.feature.SearchForFeaturesEvent;
import com.here.naksha.lib.core.models.payload.events.feature.history.IterateHistoryEvent;
import com.here.naksha.lib.core.models.payload.events.info.GetHistoryStatisticsEvent;
import com.here.naksha.lib.core.models.payload.events.info.GetStatisticsEvent;
import com.here.naksha.lib.core.models.payload.events.info.GetStorageStatisticsEvent;
import com.here.naksha.lib.core.models.payload.events.info.HealthCheckEvent;
import com.here.naksha.lib.core.models.payload.events.space.ModifySpaceEvent;
import javax.annotation.Nonnull;
import naksha.model.ErrorResponse;
import naksha.model.XyzResponse;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of an extended event handler that allows to only implement handling for
 * supported events, and optionally of some post-processing.
 */
@Deprecated
public class ExtendedEventHandler<HANDLER extends EventHandler> implements IExtendedEventHandler {

  @Deprecated
  public ExtendedEventHandler(@NotNull HANDLER eventHandler) throws XyzErrorException {
    this.eventHandler = eventHandler;
  }

  // TODO HP_QUERY : Purpose of overriding this function?
  @Deprecated
  @Override
  public @NotNull XyzResponse processEvent(@NotNull IEvent event) {
    return postProcess(IExtendedEventHandler.super.processEvent(event));
  }

  @Deprecated
  @Override
  public void initialize(@NotNull IEvent ctx) {
    this.ctx = ctx;
    this.event = null;
  }

  @Deprecated
  protected final @NotNull HANDLER eventHandler;

  @Deprecated
  protected Event event;

  @Deprecated
  protected IEvent ctx;

  /**
   * Creates an error response to return.
   *
   * @param error the error type.
   * @param message the error message.
   * @return the generated error response.
   */
  @Deprecated
  protected @NotNull XyzResponse errorResponse(@NotNull XyzError error, @NotNull CharSequence message) {
    return new ErrorResponse()
        .withStreamId(event.getStreamId())
        .withError(error)
        .withErrorMessage(message.toString());
  }

  /**
   * Can be overridden to post-process responses.
   *
   * @param response the response.
   * @return the post-processed response.
   */
  @Deprecated
  protected @NotNull XyzResponse postProcess(@NotNull XyzResponse response) {
    return response;
  }

  /**
   * Send the currently processed event upstream towards the storage.
   *
   * @param event the event to send upstream.
   * @return the response returned by the storage and before post-processing.
   */
  @Deprecated
  protected @NotNull XyzResponse sendUpstream(@NotNull Event event) {
    return null;
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processHealthCheckEvent(@NotNull HealthCheckEvent event) {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processGetStatistics(@NotNull GetStatisticsEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Override
  @Deprecated
  public @NotNull XyzResponse processGetHistoryStatisticsEvent(@NotNull GetHistoryStatisticsEvent event)
      throws Exception {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processGetFeaturesByIdEvent(@NotNull GetFeaturesByIdEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processGetFeaturesByGeometryEvent(@NotNull GetFeaturesByGeometryEvent event)
      throws Exception {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processGetFeaturesByBBoxEvent(@Nonnull GetFeaturesByBBoxEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processGetFeaturesByTileEvent(@NotNull GetFeaturesByTileEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processIterateFeaturesEvent(@NotNull IterateFeaturesEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processSearchForFeaturesEvent(@NotNull SearchForFeaturesEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Deprecated
  @Override
  public @NotNull XyzResponse processDeleteFeaturesByTagEvent(@NotNull DeleteFeaturesByTagEvent event)
      throws Exception {
    return sendUpstream(event);
  }

  @Override
  @Deprecated
  public @NotNull XyzResponse processLoadFeaturesEvent(@NotNull LoadFeaturesEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Override
  @Deprecated
  public @NotNull XyzResponse processModifyFeaturesEvent(@NotNull ModifyFeaturesEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Override
  @Deprecated
  public @NotNull XyzResponse processModifySpaceEvent(@NotNull ModifySpaceEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Override
  @Deprecated
  public @NotNull XyzResponse processModifySubscriptionEvent(@NotNull ModifySubscriptionEvent event)
      throws Exception {
    return sendUpstream(event);
  }

  @Override
  @Deprecated
  public @NotNull XyzResponse processIterateHistoryEvent(@NotNull IterateHistoryEvent event) throws Exception {
    return sendUpstream(event);
  }

  @Override
  @Deprecated
  public @NotNull XyzResponse processGetStorageStatisticsEvent(@NotNull GetStorageStatisticsEvent event)
      throws Exception {
    return sendUpstream(event);
  }
}
