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
package com.here.naksha.app.service.http.tasks;

import static com.here.naksha.app.service.http.tasks.NoElementsStrategy.FAIL_ON_NO_ELEMENTS;
import static com.here.naksha.common.http.apis.ApiParamsConst.HANDLER_ID;
import static com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS;
import static naksha.model.objects.XyzMembers.XyzId;

import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.apis.ApiParams;
import com.here.naksha.app.service.http.tasks.processor.FeaturePostProcessor;
import com.here.naksha.app.service.http.tasks.processor.MaskingPostProcessor;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import io.vertx.ext.web.RoutingContext;
import naksha.base.JvmJsonUtil;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.WriteRequest;
import naksha.model.request.ops.Equals;
import naksha.model.util.RequestHelper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventHandlerApiTask<T extends XyzResponse> extends AbstractApiTask<XyzResponse> {

  private static final Logger logger = LoggerFactory.getLogger(EventHandlerApiTask.class);
  private static final FeaturePostProcessor<EventHandlerConfig> HANDLER_MASKING = new MaskingPostProcessor<>();

  private final @NotNull EventHandlerApiReqType reqType;

  public EventHandlerApiTask(
      final @NotNull EventHandlerApiReqType reqType,
      final @NotNull NakshaHttpVerticle verticle,
      final @NotNull INaksha nakshaHub,
      final @NotNull RoutingContext routingContext,
      final @NotNull NakshaContext nakshaContext) {
    super(verticle, nakshaHub, routingContext, nakshaContext);
    this.reqType = reqType;
  }

  public enum EventHandlerApiReqType {
    GET_ALL_HANDLERS,
    GET_HANDLER_BY_ID,
    CREATE_HANDLER,
    UPDATE_HANDLER,
    DELETE_HANDLER
  }

  @Override
  protected void init() {
  }

  @Override
  protected @NotNull XyzResponse execute() {
    try {
      return switch (reqType) {
        case CREATE_HANDLER -> executeCreateHandler();
        case GET_ALL_HANDLERS -> executeGetHandlers();
        case GET_HANDLER_BY_ID -> executeGetHandlerById();
        case UPDATE_HANDLER -> executeUpdateHandler();
        case DELETE_HANDLER -> executeDeleteHandler();
        default -> executeUnsupported();
      };
    } catch (Exception ex) {
      if (ex instanceof NakshaException nakshaException) {
        logger.warn("Known exception while processing request. ", ex);
        return verticle.sendErrorResponse(routingContext, nakshaException.getError());
      } else {
        logger.error("Unexpected error while processing request. ", ex);
        return verticle.sendErrorResponse(
            routingContext, NakshaError.EXCEPTION, "Internal error : " + ex.getMessage());
      }
    }
  }

  private @NotNull XyzResponse executeCreateHandler() throws Exception {
    // Read request JSON
    final EventHandlerConfig newHandler = handlerFromRequestBody();
    final WriteRequest writeRequest = RequestHelper.createFeatureRequest(naksha().getAdminMapId(), EVENT_HANDLERS, newHandler);
    // persist new handler in Admin DB (if doesn't exist already)
    Response response = executeWriteRequestFromSpaceStorage(writeRequest);
    return transformResponseToXyzFeatureResponse(response, EventHandlerConfig.class, FAIL_ON_NO_ELEMENTS, HANDLER_MASKING);
  }

  private @NotNull XyzResponse executeGetHandlers() {
    // Create ReadFeatures Request to read all handlers from Admin DB
    final ReadFeatures request = new ReadFeatures(naksha().eventHandlersCollection());
    // Submit request to NH Space Storage
    Response response = executeReadRequestFromSpaceStorage(request);
    // transform Response to Http FeatureCollection response
    return transformResponseToXyzCollectionResponse(response, EventHandlerConfig.class, HANDLER_MASKING);
  }

  private @NotNull XyzResponse executeGetHandlerById() {
    // Create ReadFeatures Request to read the handler with the specific ID from Admin DB
    final String handlerId = routingContext.pathParam(HANDLER_ID);
    final ReadFeatures request = new ReadFeatures(naksha().eventHandlersCollection()).withMemberQuery(new Equals(XyzId, handlerId));
    // Submit request to NH Space Storage
    Response response = executeReadRequestFromSpaceStorage(request);
    return transformResponseToXyzFeatureResponse(
        response,
        EventHandlerConfig.class,
        NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS,
        HANDLER_MASKING
    );
  }

  private @NotNull XyzResponse executeUpdateHandler() {
    String handlerIdFromPath = routingContext.pathParam(HANDLER_ID);
    EventHandlerConfig handlerToUpdate = handlerFromRequestBody();
    if (!handlerIdFromPath.equals(handlerToUpdate.getId())) {
      return verticle.sendErrorResponse(
          routingContext, NakshaError.ILLEGAL_ARGUMENT, mismatchMsg(handlerIdFromPath, handlerToUpdate));
    } else {
      final WriteRequest updateHandlerReq = RequestHelper.nonAtomicUpdateFeatureRequest(naksha().getAdminMapId(), EVENT_HANDLERS,
          handlerToUpdate);
      Response updateHandlerResponse = executeWriteRequestFromSpaceStorage(updateHandlerReq);
      return transformResponseToXyzFeatureResponse(
          updateHandlerResponse,
          EventHandlerConfig.class,
          FAIL_ON_NO_ELEMENTS,
          HANDLER_MASKING
      );
    }
  }

  private @NotNull XyzResponse executeDeleteHandler() {
    final String handlerId = ApiParams.extractMandatoryPathParam(routingContext, HANDLER_ID);
    final WriteRequest wrRequest = RequestHelper.deleteFeatureByIdRequest(naksha().getAdminMapId(), EVENT_HANDLERS, handlerId);
    Response response = executeWriteRequestFromSpaceStorage(wrRequest);
    return transformResponseToXyzFeatureResponse(
        response,
        EventHandlerConfig.class,
        NoElementsStrategy.NOT_FOUND_ON_NO_ELEMENTS,
        HANDLER_MASKING
    );
  }

  private @NotNull EventHandlerConfig handlerFromRequestBody() {
    final String bodyJson = routingContext.body().asString();
    return JvmJsonUtil.readJsonAs(bodyJson, EventHandlerConfig.class);
  }

  private static String mismatchMsg(String handlerIdFromPath, EventHandlerConfig handlerToUpdate) {
    return "Mismatch between event handler ids. Path event handler id: %s, body event handler id: %s"
        .formatted(handlerIdFromPath, handlerToUpdate.getId());
  }
}
