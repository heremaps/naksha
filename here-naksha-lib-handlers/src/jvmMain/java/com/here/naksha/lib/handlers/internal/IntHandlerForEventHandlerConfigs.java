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

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.util.CollectionIndexPolicy;
import com.here.naksha.lib.core.util.CustomIndexMappingCompiler;
import com.here.naksha.lib.handlers.DefaultStorageHandler;
import com.here.naksha.lib.handlers.DefaultStorageHandlerProperties;
import com.here.naksha.lib.handlers.DefaultViewHandler;
import com.here.naksha.lib.handlers.DefaultViewHandlerProperties;
import com.here.naksha.lib.handlers.TagFilterHandler;
import com.here.naksha.lib.handlers.TagFilterHandlerProperties;
import naksha.base.JvmBoxingUtil;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import naksha.model.request.query.AnyOp;
import naksha.model.request.query.PQuery;
import naksha.model.request.query.Property;
import naksha.model.util.CustomStoragePropertiesUtil;
import naksha.model.util.RequestHelper;
import naksha.model.util.ResultHelper;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.here.naksha.lib.core.HubInternalIdentifiers.EVENT_HANDLERS;
import static com.here.naksha.lib.core.HubInternalIdentifiers.SPACES;
import static com.here.naksha.lib.core.HubInternalIdentifiers.STORAGES;
import static com.here.naksha.lib.core.models.naksha.EventTarget.EVENT_HANDLER_IDS;
import static com.here.naksha.lib.handlers.TagFilterHandlerProperties.ADD_VALUES;
import static com.here.naksha.lib.handlers.TagFilterHandlerProperties.CONTAINS_VALUES;
import static com.here.naksha.lib.handlers.TagFilterHandlerProperties.REMOVE_W_PREFIXES;
import static com.here.naksha.lib.handlers.internal.IntValidationUtil.SUCCESSFUL_VALIDATION;
import static com.here.naksha.lib.handlers.internal.IntValidationUtil.basicValidationFor;
import static com.here.naksha.lib.handlers.internal.IntValidationUtil.isStorageHandler;
import static naksha.base.Platform.javaProxy;
import static naksha.model.NakshaContext.currentContext;
import static naksha.model.util.RequestHelper.readFeaturesByIdRequest;

public class IntHandlerForEventHandlerConfigs extends AdminFeatureEventHandler<EventHandlerConfig> {

  public IntHandlerForEventHandlerConfigs(final @NotNull INaksha hub) {
    super(hub, EventHandlerConfig.class);
  }

  @Override
  protected @NotNull Response validateDeleteInstruction(Write write) {
    // For DELETE, only the feature ID is needed, other JSON properties are irrelevant
    return noActiveSpaceValidation(write);
  }

  @Override
  protected @NotNull Response validateNonDeleteInstruction(Write write) {
    Response basicValidationResult = basicValidationFor(write);
    if (basicValidationResult instanceof ErrorResponse) {
      return basicValidationResult;
    }
    final EventHandlerConfig eventHandler = JvmBoxingUtil.box(write.getFeature(), EventHandlerConfig.class);
    if (eventHandler == null) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          "Event Handler can't be null"
      );
    }
    final String className = eventHandler.getClassName();
    final Response classNameValidation = validateClassName(className);
    if (classNameValidation instanceof ErrorResponse) {
      return classNameValidation;
    }
    return specificHandlerValidation(eventHandler);
  }

  private Response validateClassName(String className) {
    if (className == null || className.isEmpty()) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          "Storage Config is missing mandatory parameter: '" + NakshaStorage.CLASSNAME_FIELD + "'"
      );
    }
    return SUCCESSFUL_VALIDATION;
  }

  private Response specificHandlerValidation(EventHandlerConfig eventHandler) {
    final String className = eventHandler.getClassName();
    if (DefaultStorageHandler.class.getName().equals(className)) {
      final Response storageValidation = storageValidation(eventHandler, DefaultStorageHandlerProperties.STORAGE_ID);
      if (!(storageValidation instanceof SuccessResponse)) {
        return storageValidation;
      }
      return collectionValidation(eventHandler);
    } else if (DefaultViewHandler.class.getName().equals(className)) {
      return viewHandlerPropertiesValidation(eventHandler);
    } else if (TagFilterHandler.class.getName().equals(className)) {
      return tagFilterHandlerPropertiesValidation(eventHandler);
    } else if (isStorageHandler(nakshaHub(), eventHandler)) {
      // Subclasses inherit the collection behavior of the DefaultStorageHandler.
      return collectionValidation(eventHandler);
    } else {
      return SUCCESSFUL_VALIDATION;
    }
  }

  /**
   * Custom index mappings require auto-creation, because the Hub creates the mapped collection. A Handler collection
   * replaces the collection of its Spaces, so custom index mappings of these Spaces would be ignored.
   */
  private @NotNull Response collectionValidation(EventHandlerConfig eventHandler) {
    final DefaultStorageHandlerProperties properties =
        javaProxy(eventHandler.getProperties(), DefaultStorageHandlerProperties.class);
    final NakshaCollection collection = properties.getCollection();
    final boolean autoCreate = properties.getAutoCreateCollection();
    if (collection != null) {
      try {
        CustomIndexMappingCompiler.validate(collection);
      } catch (NakshaException e) {
        return new ErrorResponse(e.getError());
      }
      if (!autoCreate && CustomIndexMappingCompiler.hasMappings(collection)) {
        return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT,
            "Custom index mappings require autoCreateCollection, but it is disabled for handler " + eventHandler.getId());
      }
    } else if (autoCreate) {
      return SUCCESSFUL_VALIDATION;
    }
    final ReadFeatures readSpacesRequest = new ReadFeatures().withCollectionId(SPACES)
        .withCatalogId(nakshaHub.getAdminMapId());
    final Response response = nakshaHub().getAdminStorage()
        .useReadSession(new SessionOptions(), readSession -> readSession.executeRead(readSpacesRequest));
    if (!(response instanceof SuccessResponse)) {
      return response;
    }
    final List<String> mappedSpaceIds = ResultHelper.extractResponseItems((SuccessResponse) response, Space.class).stream()
        .filter(space -> space.getEventHandlerIds().contains(eventHandler.getId()))
        .filter(space -> CustomIndexMappingCompiler.hasMappings(space.getProperties().getCollection()))
        .map(NakshaFeature::getId)
        .collect(Collectors.toList());
    if (mappedSpaceIds.isEmpty()) {
      return SUCCESSFUL_VALIDATION;
    }
    if (collection != null) {
      return new ErrorResponse(NakshaError.CONFLICT,
          "The event handler defines a collection, but these spaces define custom index mappings: " + mappedSpaceIds);
    }
    return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT,
        "Custom index mappings require autoCreateCollection, but these spaces of the handler define them: " + mappedSpaceIds);
  }

  /**
   * The collections a {@link DefaultStorageHandler}, or a subclass, defines by its configuration are created when the
   * Handler is saved, if they have custom index mappings. If a collection exists, the storage verifies that its schema is
   * unchanged; this is done as well when the mapping is removed.
   */
  @Override
  protected @NotNull Response beforePersist(final @NotNull WriteRequest wr) {
    for (final Write write : wr.getWrites()) {
      if (WriteOp.DELETE.equals(write.getOp())) {
        continue;
      }
      final EventHandlerConfig eventHandler = javaProxy(write.getFeature(), EventHandlerConfig.class);
      final List<NakshaCollection> collections;
      final List<NakshaCollection> savedCollections;
      try {
        collections = configuredCollections(eventHandler);
        if (collections.isEmpty()) {
          continue;
        }
        savedCollections = configuredCollections(savedHandler(eventHandler.getId()));
      } catch (RuntimeException e) {
        return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT,
            "Cannot create handler " + eventHandler.getId() + " to verify its collections: " + e.getMessage());
      }
      if (!javaProxy(eventHandler.getProperties(), DefaultStorageHandlerProperties.class).getAutoCreateCollection()
          && collections.stream().anyMatch(CustomIndexMappingCompiler::hasMappings)) {
        return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT,
            "Custom index mappings require autoCreateCollection, but it is disabled for handler " + eventHandler.getId());
      }
      for (final NakshaCollection collection : collections) {
        final Response response = provisionCollection(eventHandler, collection, savedCollections);
        if (response instanceof ErrorResponse) {
          return response;
        }
      }
    }
    return SUCCESSFUL_VALIDATION;
  }

  private @NotNull Response provisionCollection(
      EventHandlerConfig eventHandler, NakshaCollection collection, List<NakshaCollection> savedCollections) {
    final boolean mapped = CustomIndexMappingCompiler.hasMappings(collection);
    final boolean savedMapped = savedCollections.stream().anyMatch(saved -> collection.getId().equals(saved.getId())
        && CustomIndexMappingCompiler.hasMappings(saved));
    if (!mapped && !savedMapped) {
      return SUCCESSFUL_VALIDATION;
    }
    final DefaultStorageHandlerProperties properties =
        javaProxy(eventHandler.getProperties(), DefaultStorageHandlerProperties.class);
    final IStorage storage = nakshaHub().getStorageById(properties.getStorageId());
    final String catalogId = CustomStoragePropertiesUtil.getSchema(Objects.requireNonNull(storage.getConfig()));
    final NakshaCollection nativeCollection = CollectionIndexPolicy.toNativeCollection(
        collection.copy(true), collection.getId(), catalogId);
    final Write write = mapped
        ? new Write().upsertCollection(nativeCollection)
        : new Write().updateCollection(nativeCollection, false);
    final Response response = storage.useWriteSession(SessionOptions.from(currentContext()), writer -> {
      final Response result = writer.executeWrite(new WriteRequest().add(write));
      if (result instanceof SuccessResponse) {
        writer.commit();
      } else {
        writer.rollback();
      }
      return result;
    });
    if (response instanceof ErrorResponse) {
      final String code = ((ErrorResponse) response).getError().getCode();
      // Nothing to verify, the collection is created lazily on the first write.
      if (NakshaError.COLLECTION_NOT_FOUND.equals(code) || NakshaError.MAP_NOT_FOUND.equals(code)) {
        return SUCCESSFUL_VALIDATION;
      }
    }
    return response;
  }

  /**
   * Returns the collections the given Handler defines by its configuration, if it is a {@link DefaultStorageHandler} or a
   * subclass of it. A Handler save has no Space, so the Handler is created with a placeholder Space.
   */
  private @NotNull List<NakshaCollection> configuredCollections(@Nullable EventHandlerConfig eventHandler) {
    if (eventHandler == null) {
      return List.of();
    }
    if (!isStorageHandler(nakshaHub(), eventHandler)) {
      return List.of();
    }
    final Space placeholder = new Space();
    placeholder.setId(eventHandler.getId());
    placeholder.getEventHandlerIds().add(eventHandler.getId());
    return ((DefaultStorageHandler) eventHandler.newInstance(nakshaHub(), placeholder)).configuredCollections();
  }

  private @Nullable EventHandlerConfig savedHandler(String handlerId) {
    final ReadFeatures readHandler = readFeaturesByIdRequest(nakshaHub.getAdminMapId(), EVENT_HANDLERS, handlerId);
    final Response response = nakshaHub().getAdminStorage()
        .useReadSession(SessionOptions.from(currentContext()), readSession -> readSession.executeRead(readHandler));
    if (!(response instanceof SuccessResponse)) {
      return null;
    }
    return ResultHelper.readFeatureFromResponse((SuccessResponse) response, EventHandlerConfig.class);
  }

  private @NotNull Response viewHandlerPropertiesValidation(EventHandlerConfig eventHandler) {
    Response storageValidation = storageValidation(eventHandler, DefaultViewHandlerProperties.STORAGE_ID);

    if (!(storageValidation instanceof SuccessResponse)) {
      return storageValidation;
    }

    final DefaultViewHandlerProperties viewHandlerProperties = javaProxy(eventHandler.getProperties(), DefaultViewHandlerProperties.class);
    if (viewHandlerProperties == null) {
      return new ErrorResponse(NakshaError.ILLEGAL_STATE, "The handler has no properties");
    }
    final List<String> spaceIds = viewHandlerProperties.getSpaceIds();
    if (spaceIds == null || spaceIds.isEmpty()) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          String.format("Mandatory properties parameter %s empty/blank!", DefaultViewHandlerProperties.SPACE_IDS));
    }

    for (String spaceId : spaceIds) {
      if (StringUtils.isBlank(spaceId)) {
        return new ErrorResponse(
            NakshaError.ILLEGAL_ARGUMENT,
            String.format("Mandatory parameter %s contains space which is empty/blank!",
                DefaultViewHandlerProperties.SPACE_IDS));
      }
    }

    return spaceExistenceValidation(spaceIds);
  }

  private @NotNull Response tagFilterHandlerPropertiesValidation(EventHandlerConfig eventHandler) {

    TagFilterHandlerProperties properties =
        JvmBoxingUtil.box(eventHandler.getProperties(), TagFilterHandlerProperties.class);

    List<String> addList = properties.getAdd();
    List<String> removeWithPrefixesList = properties.getRemoveWithPrefixes();
    List<String> containsList = properties.getContains();
    if (addList == null && removeWithPrefixesList == null && containsList == null) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          String.format("At least one of [%s, %s, %s] parameters must be set",
              ADD_VALUES, REMOVE_W_PREFIXES, CONTAINS_VALUES));
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
          NakshaError.ILLEGAL_ARGUMENT, String.format("The %s parameter cannot be an empty list", listName)));
    }
    if (list.stream().anyMatch(StringUtils::isBlank)) {
      return Optional.of(new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT, String.format("The %s parameter contains blank element", listName)));
    }
    return Optional.empty();
  }

  private Response spaceExistenceValidation(List<String> spaceIds) {
    ReadFeatures readFeaturesRequest = RequestHelper.readFeaturesByIdsRequest(nakshaHub.getAdminMapId(), SPACES, spaceIds);
    return nakshaHub().getAdminStorage().useReadSession(new SessionOptions(), readSession -> {
      final Response readResult = readSession.execute(readFeaturesRequest);
      if (readResult instanceof ErrorResponse) {
        return (ErrorResponse) readResult;
      } else if (readResult instanceof SuccessResponse) {
        SuccessResponse successResponse = (SuccessResponse) readResult;
        List<Space> spaces = ResultHelper.extractResponseItems(successResponse, Space.class);
        if (spaces.size() != spaceIds.size()) {
          return new ErrorResponse(
              NakshaError.ILLEGAL_ARGUMENT,
              String.format("Mandatory parameter %s contains space which is not created!",
                  DefaultViewHandlerProperties.SPACE_IDS));
        }
        return SUCCESSFUL_VALIDATION;
      } else {
        return new ErrorResponse(
            NakshaError.EXCEPTION,
            String.format("Unexpected response while validating space '%s', error: %s",
                DefaultViewHandlerProperties.SPACE_IDS, readResult));
      }
    });
  }

  private @NotNull Response storageValidation(
      @NotNull EventHandlerConfig eventHandler, @NotNull String storagePropertyName) {
    Object storageIdProp = eventHandler.getProperties().getPath(storagePropertyName);
    if (storageIdProp == null) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          String.format("Mandatory properties parameter %s missing!", storagePropertyName));
    }
    String storageId = storageIdProp.toString();
    if (StringUtils.isBlank(storageId)) {
      return new ErrorResponse(
          NakshaError.ILLEGAL_ARGUMENT,
          String.format("Mandatory parameter %s can't be empty/blank!", storagePropertyName));
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
    ReadFeatures findStorageById = readFeaturesByIdRequest(nakshaHub.getAdminMapId(), STORAGES, storageId);
    return nakshaHub().getSpaceStorage().useReadSession(new SessionOptions(), readSession -> {
      Response result = readSession.execute(findStorageById);
      List<String> fetchedIds = ResultHelper.readIdsFromResult(result);
      if (fetchedIds.size() == 1 && storageId.equals(fetchedIds.get(0))) {
        return SUCCESSFUL_VALIDATION;
      } else {
        return new ErrorResponse(NakshaError.NOT_FOUND, "Could not find storage with id: " + storageId);
      }
    });
  }

  private Response noActiveSpaceValidation(Write codec) {
    // Search for active event handlers still using this storage
    String handlerId = codec.getId();
    if (handlerId == null) {
      if (codec.getFeature() == null) {
        return new ErrorResponse(NakshaError.ILLEGAL_ARGUMENT, "No handler ID supplied.");
      }
      handlerId = codec.getFeature().getId();
    }
    // Scan through all spaces with JSON property "eventHandlerIds" containing the targeted handler ID
    final Property pRef = new Property(EVENT_HANDLER_IDS);
    final PQuery activeSpacesPOp = new PQuery(pRef, AnyOp.CONTAINS, handlerId);
    final ReadFeatures readActiveHandlersRequest = new ReadFeatures().withCollectionId(SPACES)
            .withCatalogId(nakshaHub.getAdminMapId())
            .withPropertyQuery(activeSpacesPOp);

    return nakshaHub().getAdminStorage().useReadSession(new SessionOptions(), readSession -> {
      final Response readResult = readSession.execute(readActiveHandlersRequest);
      if (!(readResult instanceof SuccessResponse)) {
        return readResult;
      }
      final List<Space> spaces;
      try {
        spaces = ResultHelper.extractResponseItems((SuccessResponse) readResult, Space.class);
      } catch (NoSuchElementException emptyException) {
        // No active space using the handler, proceed with deleting the handler
        return SUCCESSFUL_VALIDATION;
      }
      if (spaces.isEmpty()) {
        // No active space using the handler, proceed with deleting the handler
        return SUCCESSFUL_VALIDATION;
      }
      final List<String> spaceIds =
          spaces.stream().map(NakshaFeature::getId).collect(Collectors.toList());
      return new ErrorResponse(
          NakshaError.CONFLICT, "The event handler is still in use by these spaces: " + spaceIds);
    });
  }
}
