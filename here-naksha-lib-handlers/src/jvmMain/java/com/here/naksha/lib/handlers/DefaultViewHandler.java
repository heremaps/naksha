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
package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.handlers.DefaultViewHandlerProperties.ViewType;
import com.here.naksha.lib.handlers.util.RequestTypesUtil;
import com.here.naksha.lib.view.IView;
import com.here.naksha.lib.view.MissingIdResolver;
import com.here.naksha.lib.view.ViewLayer;
import com.here.naksha.lib.view.ViewLayerCollection;
import com.here.naksha.lib.view.ViewReadSession;
import com.here.naksha.lib.view.merge.MergeByStoragePriority;
import com.here.naksha.lib.view.missing.IgnoreMissingResolver;
import com.here.naksha.lib.view.missing.ObligatoryLayersResolver;
import naksha.base.JvmBoxingUtil;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.SUCCEED_WITHOUT_PROCESSING;

public class DefaultViewHandler extends AbstractEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultViewHandler.class);

    private final @NotNull EventHandlerConfig eventHandler;
    private final @NotNull EventTarget<?> eventTarget;
    private final @NotNull DefaultViewHandlerProperties properties;

    public DefaultViewHandler(
            final @NotNull EventHandlerConfig eventHandler,
            final @NotNull INaksha hub,
            final @NotNull EventTarget<?> eventTarget) {
        super(hub);
        this.eventHandler = eventHandler;
        this.eventTarget = eventTarget;
        this.properties = JvmBoxingUtil.box(eventHandler.getProperties(), DefaultViewHandlerProperties.class);
    }

    @Override
    protected EventProcessingStrategy processingStrategyFor(IEvent event) {
        final Request request = event.getRequest();
        if (request instanceof WriteRequest && RequestTypesUtil.isOnlyWriteCollections((WriteRequest) request)) {
            return SUCCEED_WITHOUT_PROCESSING;
        } else if (request instanceof ReadFeatures || request instanceof WriteRequest) {
            return PROCESS;
        }
        return NOT_IMPLEMENTED;
    }

    @Override
    public @NotNull Response process(@NotNull IEvent event) {
        final NakshaContext ctx = NakshaContext.currentContext();
        final Request request = event.getRequest();
        logger.info("Handler received request {}", request.getClass().getSimpleName());

        final String storageId = properties.getStorageId();

        if (storageId == null) {
            logger.error("No storageId configured");
            return new ErrorResponse(NakshaError.NOT_FOUND, "No storageId configured for handler.");
        }
        logger.info("Against Storage id={}", storageId);
        addStorageIdToStreamInfo(storageId, ctx);

        final IStorage storageImpl = nakshaHub().getStorageById(storageId);
        logger.info("Using storage implementation [{}]", storageImpl.getClass().getName());

        if (storageImpl instanceof IView) {
          final IView view = (IView) storageImpl;
          final var spaceIds = properties.getSpaceIds();
          if (spaceIds == null || spaceIds.isEmpty()) {
                logger.error("No spaces present in view's properties - unable to process this request");
                return new ErrorResponse(NakshaError.NOT_FOUND, "No spaces defined in properties of handler: '" + eventHandler.getId() + "'");
            } else {
                view.setViewLayerCollection(prepareViewLayerCollection(nakshaHub().getSpaceStorage(), spaceIds));
                // TODO: MCPODS-7046 Replace the way how view is created. Should be immutable without need to use set method.
                return processRequest(ctx, view, request);
            }
        } else {
            logger.error("Associated storage doesn't implement View, so can't process this request");
            return new ErrorResponse(NakshaError.EXCEPTION, "Associated storage doesn't implement View");
        }
    }

    private Response processRequest(NakshaContext ctx, IView view, Request request) {
        if (request instanceof ReadFeatures) {
          final ReadFeatures rf = (ReadFeatures) request;
          return forwardReadFeatures(ctx, view, rf);
        } else if (request instanceof WriteRequest) {
          final WriteRequest wr = (WriteRequest) request;
          return forwardWriteFeatures(ctx, view, wr);
        } else {
          return notImplemented(request);
        }
    }

    private Response forwardWriteFeatures(NakshaContext ctx, IView view, WriteRequest wr) {
      try (final IWriteSession writeSession = view.newWriteSession(SessionOptions.from(ctx, null, true))) {
        return writeSession.execute(wr);
      }
    }

    private Response forwardReadFeatures(NakshaContext ctx, IView view, ReadFeatures rf) {

        final MissingIdResolver resolver;
        if (properties.getViewType() == ViewType.UNION) {
            resolver = new IgnoreMissingResolver();
        } else {
            final Set<ViewLayer> obligatoryLayers = getObligatoryLayers(view.getViewCollection());
            resolver = new ObligatoryLayersResolver(obligatoryLayers);
        }
        try (final var session = (ViewReadSession) view.newReadSession(SessionOptions.from(ctx))) {
          return session.executeReadFeatures(rf, new MergeByStoragePriority(), resolver);
        }
    }

    private ViewLayerCollection prepareViewLayerCollection(IStorage nhStorage, List<String> spaceIds) {
        final List<ViewLayer> viewLayerList = new ArrayList<>();
        for (final String spaceId : spaceIds) {
            viewLayerList.add(new ViewLayer(nhStorage, spaceId));
        }
        return new ViewLayerCollection("", viewLayerList);
    }

    private Set<ViewLayer> getObligatoryLayers(ViewLayerCollection viewLayerCollection) {

        int layerCollectionSize = viewLayerCollection.getLayers().size();

        if (layerCollectionSize >= 2) {
            List<ViewLayer> obligatoryLayers = viewLayerCollection.getLayers().subList(0, layerCollectionSize - 1);
            return new HashSet<>(obligatoryLayers);
        } else {
            return Set.of(viewLayerCollection.getTopPriorityLayer());
        }
    }
}
