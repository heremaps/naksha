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
package com.here.naksha.lib.handlers.internal;

import static com.here.naksha.lib.core.NakshaAdminCollection.SPACES;
import static com.here.naksha.lib.core.models.naksha.EventTarget.EVENT_HANDLER_IDS;
import static com.here.naksha.lib.core.util.storage.ResultHelper.readFeaturesFromResult;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.exceptions.NoCursor;
import com.here.naksha.lib.core.exceptions.StorageNotFoundException;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.geojson.implementation.XyzFeature;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.storage.*;
import com.here.naksha.lib.core.storage.IReadSession;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.lib.handlers.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntHandlerForEventHandlers extends AdminFeatureEventHandler<EventHandler> {

  public IntHandlerForEventHandlers(final @NotNull INaksha hub) {
    super(hub, EventHandler.class);
  }

  @Override
  protected @NotNull Result validateFeature(XyzFeatureCodec codec) {
    final EWriteOp operation = EWriteOp.get(codec.getOp());
    if (operation.equals(EWriteOp.DELETE)) {
      // For DELETE, only the feature ID is needed, other JSON properties are irrelevant
      return noActiveSpaceValidation(codec);
    }
    // For non-DELETE write request
    Result basicValidationResult = super.validateFeature(codec);
    if (basicValidationResult instanceof ErrorResult) {
      return basicValidationResult;
    }
    final EventHandler eventHandler = (EventHandler) codec.getFeature();
    Result pluginValidationResult = PluginPropertiesValidator.pluginValidation(eventHandler);
    if (pluginValidationResult instanceof ErrorResult) {
      return pluginValidationResult;
    }
    return defaultHandlerValidation(eventHandler);
  }

  private Result defaultHandlerValidation(EventHandler eventHandler) {
    if (handlerClassMatches(DefaultStorageHandler.class, eventHandler)) {
      return storageValidation(eventHandler, DefaultStorageHandlerProperties.STORAGE_ID);
    }
    if (handlerClassMatches(DefaultViewHandler.class, eventHandler)) {
      return viewHandlerPropertiesValidation(eventHandler);
    }
    if (handlerClassMatches(TagFilterHandler.class, eventHandler)) {
      return tagFilterHandlerPropertiesValidation(eventHandler);
    }
    return new SuccessResult();
  }

  private @NotNull Result viewHandlerPropertiesValidation(EventHandler eventHandler) {
    Result storageValidation = storageValidation(eventHandler, DefaultViewHandlerProperties.STORAGE_ID);

    if (!(storageValidation instanceof SuccessResult)) {
      return storageValidation;
    }

    DefaultViewHandlerProperties viewHandlerProperties =
        JsonSerializable.convert(eventHandler.getProperties(), DefaultViewHandlerProperties.class);

    List<String> spaceIds = viewHandlerProperties.getSpaceIds();
    if (spaceIds == null || spaceIds.isEmpty()) {
      return new ErrorResult(
          XyzError.ILLEGAL_ARGUMENT,
          "Mandatory properties parameter %s empty/blank!".formatted(DefaultViewHandlerProperties.SPACE_IDS));
    }

    for (String spaceId : spaceIds) {
      if (StringUtils.isBlank(spaceId)) {
        return new ErrorResult(
            XyzError.ILLEGAL_ARGUMENT,
            "Mandatory parameter %s contains space which is empty/blank!"
                .formatted(DefaultViewHandlerProperties.SPACE_IDS));
      }
    }

    return spaceExistenceValidation(spaceIds);
  }

  private @NotNull Result tagFilterHandlerPropertiesValidation(EventHandler eventHandler) {

    TagFilterHandlerProperties properties =
        JsonSerializable.convert(eventHandler.getProperties(), TagFilterHandlerProperties.class);

    List<String> addList = properties.getAdd();
    List<String> removeWithPrefixesList = properties.getRemoveWithPrefixes();
    List<String> containsList = properties.getContains();
    if (addList == null && removeWithPrefixesList == null && containsList == null) {
      return new ErrorResult(
          XyzError.ILLEGAL_ARGUMENT,
          "At least one of [%s, %s, %s] lists must be not null and not empty"
              .formatted(
                  TagFilterHandlerProperties.ADD_VALUES,
                  TagFilterHandlerProperties.REMOVE_W_PREFIXES,
                  TagFilterHandlerProperties.CONTAINS_VALUES));
    }

    return errorIfpresentButHasBlankElement(addList, TagFilterHandlerProperties.ADD_VALUES)
        .or(() -> errorIfpresentButHasBlankElement(
            removeWithPrefixesList, TagFilterHandlerProperties.REMOVE_W_PREFIXES))
        .or(() -> errorIfpresentButHasBlankElement(containsList, TagFilterHandlerProperties.CONTAINS_VALUES))
        .map(Result.class::cast)
        .orElseGet(SuccessResult::new);
  }

  private Optional<ErrorResult> errorIfpresentButHasBlankElement(@Nullable List<String> list, String listName) {
    if (list != null && list.stream().anyMatch(StringUtils::isBlank))
      return Optional.of(new ErrorResult(
          XyzError.ILLEGAL_ARGUMENT, "List %s contains element which is blank!".formatted(listName)));
    else return Optional.empty();
  }

  private Result spaceExistenceValidation(List<String> spaceIds) {

    ReadFeatures readFeaturesRequest = RequestHelper.readFeaturesByIdsRequest(SPACES, spaceIds);

    try (final IReadSession readSession =
        nakshaHub().getAdminStorage().newReadSession(NakshaContext.currentContext(), false)) {

      final Result readResult = readSession.execute(readFeaturesRequest);

      try {
        List<Space> spaces = readFeaturesFromResult(readResult, Space.class);

        if (spaces.size() != spaceIds.size()) {
          return new ErrorResult(
              XyzError.ILLEGAL_ARGUMENT,
              "Mandatory parameter %s contains space which is not created!"
                  .formatted(DefaultViewHandlerProperties.SPACE_IDS));
        }

      } catch (NoCursor | NoSuchElementException e) {
        return new ErrorResult(
            XyzError.ILLEGAL_ARGUMENT,
            "Mandatory parameter %s contains space which is not created!"
                .formatted(DefaultViewHandlerProperties.SPACE_IDS));
      }
    }
    return new SuccessResult();
  }

  private boolean handlerClassMatches(@NotNull Class<?> requestedClass, @NotNull EventHandler eventHandler) {
    return requestedClass.getName().equals(eventHandler.getClassName());
  }

  private @NotNull Result storageValidation(@NotNull EventHandler eventHandler, @NotNull String storagePropertyName) {
    Object storageIdProp = eventHandler.getProperties().get(storagePropertyName);
    if (storageIdProp == null) {
      return new ErrorResult(
          XyzError.ILLEGAL_ARGUMENT,
          "Mandatory properties parameter %s missing!".formatted(storagePropertyName));
    }
    String storageId = storageIdProp.toString();
    if (StringUtils.isBlank(storageId)) {
      return new ErrorResult(
          XyzError.ILLEGAL_ARGUMENT,
          "Mandatory parameter %s can't be empty/blank!".formatted(storagePropertyName));
    }
    return storageExistenceValidation(storageId);
  }

  /**
   * Verifies whether supplied storageId points at existing storage
   *
   * @param storageId
   * @return ErrorResult or null if storage exists
   */
  private @NotNull Result storageExistenceValidation(@NotNull String storageId) {
    try {
      nakshaHub().getStorageById(storageId);
    } catch (StorageNotFoundException snfe) {
      return snfe.toErrorResult();
    }
    return new SuccessResult();
  }

  private Result noActiveSpaceValidation(XyzFeatureCodec codec) {
    // Search for active event handlers still using this storage
    String handlerId = codec.getId();
    if (handlerId == null) {
      if (codec.getFeature() == null) {
        return new ErrorResult(XyzError.ILLEGAL_ARGUMENT, "No handler ID supplied.");
      }
      handlerId = codec.getFeature().getId();
    }
    // Scan through all spaces with JSON property "eventHandlerIds" containing the targeted handler ID
    final PRef pRef = RequestHelper.pRefFromPropPath(new String[] {EVENT_HANDLER_IDS});
    final POp activeSpacesPOp = POp.contains(pRef, handlerId);
    final ReadFeatures readActiveHandlersRequest = new ReadFeatures(SPACES).withPropertyOp(activeSpacesPOp);
    try (final IReadSession readSession =
        nakshaHub().getAdminStorage().newReadSession(NakshaContext.currentContext(), false)) {
      final Result readResult = readSession.execute(readActiveHandlersRequest);
      if (!(readResult instanceof SuccessResult)) {
        return readResult;
      }
      final List<Space> spaces;
      try {
        spaces = readFeaturesFromResult(readResult, Space.class);
      } catch (NoCursor | NoSuchElementException emptyException) {
        // No active space using the handler, proceed with deleting the handler
        return new SuccessResult();
      } finally {
        readResult.close();
      }
      final List<String> spaceIds = spaces.stream().map(XyzFeature::getId).toList();
      return new ErrorResult(XyzError.CONFLICT, "The event handler is still in use by these spaces: " + spaceIds);
    }
  }
}
