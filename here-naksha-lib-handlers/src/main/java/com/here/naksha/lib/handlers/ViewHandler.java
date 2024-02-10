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
package com.here.naksha.lib.handlers;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SEND_UPSTREAM_WITHOUT_PROCESSING;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.view.IView;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerCollection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(ViewHandler.class);

  private @NotNull EventHandler eventHandler;
  private @NotNull EventTarget<?> eventTarget;
  private @NotNull DefaultStorageHandlerProperties properties;

  public ViewHandler(
      final @NotNull EventHandler eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties = JsonSerializable.convert(eventHandler.getProperties(), DefaultStorageHandlerProperties.class);
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request<?> request = event.getRequest();
    if (request instanceof ReadFeatures
        || request instanceof WriteXyzFeatures
        || request instanceof WriteXyzCollections) {
      return PROCESS;
    }
    return SEND_UPSTREAM_WITHOUT_PROCESSING;
  }

  @Override
  public @NotNull Result process(@NotNull IEvent event) {

    final NakshaContext ctx = NakshaContext.currentContext();
    final Request<?> request = event.getRequest();
    logger.info("Handler received request {}", request.getClass().getSimpleName());

    final String storageId = properties.getStorageId();

    if (storageId == null) {
      logger.error("No storageId configured");
      return new ErrorResult(XyzError.NOT_FOUND, "No storageId configured for handler.");
    }
    logger.info("Against Storage id={}", storageId);
    addStorageIdToStreamInfo(storageId, ctx);

    final IStorage storageImpl = nakshaHub().getStorageById(storageId);
    logger.info("Using storage implementation [{}]", storageImpl.getClass().getName());

    if (storageImpl instanceof IView view) {

      List<String> spaceIds = getSpaceIds(properties);
      if (spaceIds.isEmpty()) {
        return new ErrorResult(XyzError.NOT_FOUND, "No spaceIds configured for handler.");
      }
      view.setViewLayerCollection(prepareViewLayerCollection(nakshaHub().getSpaceStorage(), spaceIds));
      //TODO Replace the way how view is created. Should be immutable without need to use set method.
      return processRequest(ctx, view, request);
    } else {
      logger.info("Storage is not and instance of IView. Processing event to next handler.");
      return new ErrorResult(XyzError.EXCEPTION, "Storage is not instance of IView");
    }
  }

  private Result processRequest(NakshaContext ctx, IView view, Request<?> request) {

    if (request instanceof ReadFeatures rf) {
      return forwardReadFeatures(ctx, view, rf);
    } else if (request instanceof WriteFeatures<?, ?, ?> wf) {
      return forwardWriteFeatures(ctx, view, wf);
    } else if (request instanceof WriteCollections<?, ?, ?> wc) {
      return forwardWriteFeatures(ctx, view, wc);
    } else {
      return notImplemented(request);
    }
  }

  private Result forwardWriteFeatures(NakshaContext ctx, IView view, WriteRequest<?, ?, ?> wr) {
    try (final IWriteSession writeSession = view.newWriteSession(ctx, false)) {
      return writeSession.execute(wr);
    }
  }

  private Result forwardReadFeatures(NakshaContext ctx, IView view, ReadFeatures rf) {

    try (final IReadSession reader = view.newReadSession(ctx, false)) {
      return reader.execute(rf);
    }
  }

  private List<String> getSpaceIds(DefaultStorageHandlerProperties properties) {

    Object spaceIds = properties.get("spaceIds");
    if (spaceIds != null && spaceIds instanceof List<?>) {

      try {
        return (List<String>) spaceIds;
      } catch (ClassCastException castException) {
        logger.warn("spaceIds collection can't be casted to List of String");
        throw castException;
      }
    }
    return List.of();
  }

  private ViewLayerCollection prepareViewLayerCollection(IStorage nhStorage, List<String> storageIds) {

    List<ViewLayer> viewLayerList = storageIds.stream()
        .map(storageId -> new ViewLayer(nhStorage, storageId))
        .toList();

    return new ViewLayerCollection("", viewLayerList);
  }
}
