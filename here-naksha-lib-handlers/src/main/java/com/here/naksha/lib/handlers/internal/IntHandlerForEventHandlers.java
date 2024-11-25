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
package com.here.naksha.lib.handlers.internal;

import static com.here.naksha.lib.core.NakshaAdminCollection.SPACES;
import static com.here.naksha.lib.core.NakshaAdminCollection.STORAGES;
import static com.here.naksha.lib.core.models.naksha.EventTarget.EVENT_HANDLER_IDS;
import static com.here.naksha.lib.core.util.storage.RequestHelper.readFeaturesByIdRequest;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesFromResult;
import static com.here.naksha.lib.handlers.TagFilterHandlerProperties.*;
import static naksha.model.NakshaContext.currentContext;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.storage.EWriteOp;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.lib.core.util.storage.ResultHelper;
import com.here.naksha.lib.handlers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import naksha.base.JvmProxyUtil;
import naksha.model.IReadSession;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntHandlerForEventHandlers extends AdminFeatureEventHandler<EventHandler> {

  public IntHandlerForEventHandlers(final @NotNull INaksha hub) {
    super(hub, EventHandler.class);
  }

  @Override
  protected @NotNull Response validateFeature(Write codec) {
    final EWriteOp operation = EWriteOp.get(codec.getOp());
    if (operation.equals(EWriteOp.DELETE)) {
      // For DELETE, only the feature ID is needed, other JSON properties are irrelevant
      return noActiveSpaceValidation(codec);
    }
    // For non-DELETE write request
    Response basicValidationResult = super.validateFeature(codec);
    if (basicValidationResult instanceof ErrorResponse) {
      return basicValidationResult;
    }
    final EventHandler eventHandler = (EventHandler) codec.getFeature();
    Response pluginValidationResult = PluginPropertiesValidator.pluginValidation(eventHandler);
    if (pluginValidationResult instanceof ErrorResponse) {
      return pluginValidationResult;
    }
    return defaultHandlerValidation(eventHandler);
  }

  private Response defaultHandlerValidation(EventHandler eventHandler) {
    if (handlerClassMatches(DefaultStorageHandler.class, eventHandler)) {
      return storageValidation(eventHandler, DefaultStorageHandlerProperties.STORAGE_ID);
    }
    if (handlerClassMatches(DefaultViewHandler.class, eventHandler)) {
      return viewHandlerPropertiesValidation(eventHandler);
    }
    if (handlerClassMatches(TagFilterHandler.class, eventHandler)) {
      return tagFilterHandlerPropertiesValidation(eventHandler);
    }
    return new SuccessResponse();
  }

  private @NotNull Response viewHandlerPropertiesValidation(EventHandler eventHandler) {
    Response storageValidation = storageValidation(eventHandler, DefaultViewHandlerProperties.STORAGE_ID);

    if (!(storageValidation instanceof SuccessResponse)) {
      return storageValidation;
    }

    DefaultViewHandlerProperties viewHandlerProperties =
            JvmProxyUtil.box(eventHandler.getProperties(), DefaultViewHandlerProperties.class);

    List<String> spaceIds = viewHandlerProperties.getSpaceIds();
    if (spaceIds == null || spaceIds.isEmpty()) {
      return new ErrorResponse(
              NakshaError.ILLEGAL_ARGUMENT,
              "Mandatory properties parameter %s empty/blank!".formatted(DefaultViewHandlerProperties.SPACE_IDS),
              null,
              null);
    }

    for (String spaceId : spaceIds) {
      if (StringUtils.isBlank(spaceId)) {
        return new ErrorResponse(
                NakshaError.ILLEGAL_ARGUMENT,
            "Mandatory parameter %s contains space which is empty/blank!"
                    .formatted(DefaultViewHandlerProperties.SPACE_IDS),
                null,
                null);
      }
    }

    return spaceExistenceValidation(spaceIds);
  }

  private @NotNull Response tagFilterHandlerPropertiesValidation(EventHandler eventHandler) {

    TagFilterHandlerProperties properties =
            JvmProxyUtil.box(eventHandler.getProperties(), TagFilterHandlerProperties.class);

    List<String> addList = properties.getAdd();
    List<String> removeWithPrefixesList = properties.getRemoveWithPrefixes();
    List<String> containsList = properties.getContains();
    if (addList == null && removeWithPrefixesList == null && containsList == null) {
      return new ErrorResponse(
              NakshaError.ILLEGAL_ARGUMENT,
          "At least one of [%s, %s, %s] parameters must be set"
                  .formatted(ADD_VALUES, REMOVE_W_PREFIXES, CONTAINS_VALUES),
              null,
              null);
    }

    return errorIfInvalidList(addList, ADD_VALUES)
        .or(() -> errorIfInvalidList(removeWithPrefixesList, REMOVE_W_PREFIXES))
        .or(() -> errorIfInvalidList(containsList, CONTAINS_VALUES))
            .map(Response.class::cast)
            .orElseGet(SuccessResponse::new);
  }

  /**
   * @return appropriate {@link ErrorResponse} if the list is not null and:
   * <ul><li>is empty</li>OR<li>contains at least one null/blank element</li></ul>
   * Otherwise returns {@link Optional#empty()}
   */
  private Optional<ErrorResponse> errorIfInvalidList(@Nullable List<String> list, String listName) {
    if (list == null) {
      return Optional.empty();
    }
    if (list.isEmpty()) {
      return Optional.of(new ErrorResponse(
              NakshaError.ILLEGAL_ARGUMENT,
              "The %s parameter cannot be an empty list".formatted(listName),
              null,
              null));
    }
    if (list.stream().anyMatch(StringUtils::isBlank)) {
      return Optional.of(new ErrorResponse(
              NakshaError.ILLEGAL_ARGUMENT,
              "The %s parameter contains blank element".formatted(listName),
              null,
              null));
    }
    return Optional.empty();
  }

  private Response spaceExistenceValidation(List<String> spaceIds) {

    ReadFeatures readFeaturesRequest = RequestHelper.readFeaturesByIdsRequest(SPACES, spaceIds);

    final IReadSession readSession =
            nakshaHub().getAdminStorage().newReadSession(new SessionOptions());

      final Response readResult = readSession.execute(readFeaturesRequest);

      try {
        if (readResult instanceof ErrorResponse errorResponse) {
          throw new NoSuchElementException(errorResponse.getError().getCause());
        }
        List<Space> spaces = readFeaturesFromResult((SuccessResponse) readResult, Space.class);

        if (spaces.size() != spaceIds.size()) {
          return new ErrorResponse(
                  NakshaError.ILLEGAL_ARGUMENT,
              "Mandatory parameter %s contains space which is not created!"
                      .formatted(DefaultViewHandlerProperties.SPACE_IDS),
                  null,
                  null);
        }

      } catch (NoSuchElementException e) {
        return new ErrorResponse(
                NakshaError.ILLEGAL_ARGUMENT,
            "Mandatory parameter %s contains space which is not created!"
                    .formatted(DefaultViewHandlerProperties.SPACE_IDS),
                null,
                null);
      }

    return new SuccessResponse();
  }

  private boolean handlerClassMatches(@NotNull Class<?> requestedClass, @NotNull EventHandler eventHandler) {
    return requestedClass.getName().equals(eventHandler.getClassName());
  }

  private @NotNull Response storageValidation(
          @NotNull EventHandler eventHandler, @NotNull String storagePropertyName) {
    Object storageIdProp = eventHandler.getProperties().get(storagePropertyName);
    if (storageIdProp == null) {
      return new ErrorResponse(
              NakshaError.ILLEGAL_ARGUMENT,
              "Mandatory properties parameter %s missing!".formatted(storagePropertyName),
              null,
              null);
    }
    String storageId = storageIdProp.toString();
    if (StringUtils.isBlank(storageId)) {
      return new ErrorResponse(
              NakshaError.ILLEGAL_ARGUMENT,
              "Mandatory parameter %s can't be empty/blank!".formatted(storagePropertyName),
              null,
              null);
    }
    return storageExistenceValidation(storageId);
  }

  /**
   * Verifies whether supplied storageId points at existing storage
   *
   * @param storageId
   * @return ErrorResult or null if storage exists
   */
  private @NotNull Response storageExistenceValidation(@NotNull String storageId) {
    ReadFeatures findStorageById = readFeaturesByIdRequest(STORAGES, storageId);
    IReadSession readSession = nakshaHub().getSpaceStorage().newReadSession(new SessionOptions());
      Response result = readSession.execute(findStorageById);
      List<String> fetchedIds = ResultHelper.readIdsFromResult(result);
      if (fetchedIds.size() == 1 && fetchedIds.get(0).equals(storageId)) {
        return new SuccessResponse();
      }
      return new ErrorResponse(NakshaError.NOT_FOUND, "Could not find storage with id: " + storageId, null, null);

  }

  private Response noActiveSpaceValidation(Write codec) {
    // Search for active event handlers still using this storage
    String handlerId = codec.getId();
    if (handlerId == null) {
      if (codec.getFeature() == null) {
        return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT, "No handler ID supplied.", null, null);
      }
      handlerId = codec.getFeature().getId();
    }
    // Scan through all spaces with JSON property "eventHandlerIds" containing the targeted handler ID
    final Property pRef = new Property(EVENT_HANDLER_IDS);
    final PQuery activeSpacesPOp = new PQuery(pRef, AnyOp.CONTAINS, new String[]{handlerId});
    final ReadFeatures readActiveHandlersRequest = new ReadFeatures(SPACES);
    readActiveHandlersRequest.getQuery().setProperties(activeSpacesPOp);
    final IReadSession readSession =
            nakshaHub().getAdminStorage().newReadSession(new SessionOptions());
      final Response readResult = readSession.execute(readActiveHandlersRequest);
      if (!(readResult instanceof SuccessResponse)) {
        return readResult;
      }
      final List<Space> spaces;
      try {
        spaces = readFeaturesFromResult((SuccessResponse) readResult, Space.class);
      } catch (NoSuchElementException emptyException) {
        // No active space using the handler, proceed with deleting the handler
        return new SuccessResponse();
      } finally {
        readSession.close();
      }
      final List<String> spaceIds =
              spaces.stream().map(NakshaFeature::getId).toList();
      return new ErrorResponse(
              NakshaError.CONFLICT, "The event handler is still in use by these spaces: " + spaceIds, null, null);

  }
}
