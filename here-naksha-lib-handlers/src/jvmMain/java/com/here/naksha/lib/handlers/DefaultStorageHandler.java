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
import com.here.naksha.lib.core.lambdas.F1;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import naksha.model.util.CustomStoragePropertiesUtil;
import naksha.base.JvmBoxingUtil;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.StreamInfo;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaCatalog;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.ATTEMPT_AFTER_COLLECTION_CREATION;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.ATTEMPT_AFTER_MAP_CREATION;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.ATTEMPT_AFTER_STORAGE_INITIALIZATION;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.FIRST_ATTEMPT;
import static com.here.naksha.lib.handlers.util.RequestTypesUtil.isOnlyWriteCollections;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static naksha.base.Platform.longToInt64;
import static naksha.model.util.RequestHelper.createWriteCollectionsRequest;

public class DefaultStorageHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DefaultStorageHandler.class);

  protected @NotNull EventHandlerConfig eventHandlerConfig;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull DefaultStorageHandlerProperties properties;

  public DefaultStorageHandler(
      final @NotNull EventHandlerConfig eventHandlerConfig,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandlerConfig = eventHandlerConfig;
    this.eventTarget = eventTarget;
    this.properties = Objects.requireNonNull(
        JvmBoxingUtil.box(eventHandlerConfig.getProperties(), DefaultStorageHandlerProperties.class));
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    final Request request = event.getRequest();
    if (request instanceof ReadFeatures || request instanceof WriteRequest) {
      return PROCESS;
    }
    return NOT_IMPLEMENTED;
  }

  @Override
  public @NotNull Response process(@NotNull IEvent event) {
    final Request request = event.getRequest();

    logger.info("Handler received request {}", request.getClass().getSimpleName());
    // Obtain storageId from EventHandler object
    final String storageId = properties.getStorageId();
    if (storageId == null) {
      logger.error("No storageId configured");
      return new ErrorResponse(NakshaError.NOT_FOUND, "No storageId configured for handler.");
    }

    // Obtain IStorage implementation using NakshaHub
    logger.info("Against Storage id={}", storageId);
    final IStorage storageImpl = nakshaHub().getStorageById(storageId); // TODO: analyze cache potential (CASL-928)
    logger.info("Using storage implementation [{}]", storageImpl.getClass().getName());

    // populate stream info data
    final NakshaContext ctx = NakshaContext.currentContext();
    addStorageIdToStreamInfo(storageId, ctx);
    // prepare session options for storage interactions (default options are fetched from context, then if applicable - they are patched with storage config)
    SessionOptions sessionOptions = CustomStoragePropertiesUtil.mergeSessionOptionsWithStorageConfig(SessionOptions.from(ctx), storageImpl);

    StopWatch storageTimer = new StopWatch();
    try {
      String collectionId = retrieveCollectionIdFromRequest(request);
      String mapId = extractMapIdFromStorageProps(storageImpl);
      normalizeWriteRequest(request, mapId, collectionId);
      OperationData operationData = new OperationData(sessionOptions, storageImpl, mapId, collectionId, request);
      return forwardRequestToStorage(operationData, FIRST_ATTEMPT, storageTimer);
    } catch (NakshaException ne) {
      return new ErrorResponse(ne.getError());
    } finally {
      addStorageTimeToStreamInfo(storageTimer, ctx);
    }
  }

  private String extractMapIdFromStorageProps(@NotNull IStorage storage) {
    NakshaStorage storageConfig = storage.getConfig();
    if(storageConfig == null) {
      throw new NakshaException(NakshaError.ILLEGAL_STATE,
          "Unable to determine 'mapId' for handler '" + eventHandlerConfig.getId() + "', storage '" + storage.getId() + "' has no config.");
    }
    return CustomStoragePropertiesUtil.getSchema(storageConfig);
  }

  private void addStorageTimeToStreamInfo(StopWatch storageTimer, NakshaContext ctx) {
    StreamInfo streamInfo = ctx.getStreamInfo();
    if (streamInfo != null) {
      streamInfo.addTimeInStorage(longToInt64(NANOSECONDS.toMillis(storageTimer.getNanoTime())));
    }
  }

  private <T> T measuredStorageSupplier(Supplier<T> operation, StopWatch stopWatch) {
    try {
      if (stopWatch.isSuspended()) {
        stopWatch.resume();
      } else {
        stopWatch.start();
      }
      return operation.get();
    } finally {
      stopWatch.suspend();
    }
  }

  private void measuredStorageRunnable(Runnable operation, StopWatch stopWatch) {
    try {
      if (stopWatch.isSuspended()) {
        stopWatch.resume();
      } else {
        stopWatch.start();
      }
      operation.run();
    } finally {
      stopWatch.suspend();
    }
  }

  private @NotNull Response forwardRequestToStorage(
      final @NotNull OperationData operationData,
      final @NotNull OperationAttempt currentAttempt,
      final @NotNull StopWatch storageTimer) {
    if (operationData.getRequest() instanceof ReadFeatures) {
      return forwardReadFeatures(operationData, currentAttempt, storageTimer);
    } else if (operationData.getRequest() instanceof WriteRequest) {
      WriteRequest wr = (WriteRequest) operationData.getRequest();
      if (isOnlyWriteCollections(wr)) {
        return forwardWriteCollections(operationData, currentAttempt, storageTimer);
      } else {
        return forwardWriteFeatures(operationData, currentAttempt, storageTimer);
      }
    } else {
      return notImplemented(operationData.getRequest());
    }
  }

  private @NotNull Response forwardReadFeatures(
      final @NotNull OperationData operationData,
      final @NotNull OperationAttempt currentAttempt,
      final @NotNull StopWatch storageTimer) {
    logger.info("Processing ReadFeatures against {}", operationData.getCollectionId());
    Response response = measuredStorageSupplier(
        () -> singleRead(operationData.getSessionOptions(), operationData.getStorageImpl(), (ReadFeatures) operationData.getRequest()), storageTimer);
    if (response instanceof ErrorResponse) {
      ErrorResponse errorResponse = (ErrorResponse) response;
      return reattemptFeatureRequest(operationData, currentAttempt, errorResponse, storageTimer);
    } else {
      return response;
    }
  }

  private @NotNull Response singleRead( @NotNull SessionOptions sessionOptions, @NotNull IStorage storageImpl, @NotNull ReadFeatures rf) {
    return storageImpl.useReadSession(sessionOptions, reader -> reader.execute(rf));
  }

  private @NotNull Response forwardWriteFeatures(
      final @NotNull OperationData operationData,
      final @NotNull OperationAttempt operationAttempt,
      final @NotNull StopWatch storageTimer) {
    logger.info("Processing WriteFeatures for mapId: '{}' collection '{}'", operationData.getMapId(), operationData.getCollectionId());
    return forwardWriteRequest(
        operationData,
        errorResponse -> reattemptFeatureRequest(
            operationData, operationAttempt, errorResponse, storageTimer),
        storageTimer);
  }

  private @NotNull Response forwardWriteCollections(
      final @NotNull OperationData operationData,
      final @NotNull OperationAttempt operationAttempt,
      final @NotNull StopWatch storageTimer) {
    logger.info("Processing WriteCollections against map: '{}' and collection '{}'", operationData.getMapId(), operationData.getCollectionId());
    if (isUpdateCollectionRequest((WriteRequest) operationData.getRequest())) {
      if (properties.getAutoCreateCollection()) {
        return forwardWriteRequest(operationData, errorResponse -> reattemptCollectionRequest(
                operationData, operationAttempt, errorResponse, storageTimer),
            storageTimer);
      } else {
        logger.info(
            "Received update collection request but autoCreate is not enabled, returning success without any action");
        return new SuccessResponse();
      }
    } else if (isDeleteCollectionRequest((WriteRequest) operationData.getRequest())) {
      if (properties.getAutoDeleteCollection()) {
        return forwardWriteRequest(
            operationData,
            re -> reattemptCollectionRequest(operationData, operationAttempt, re, storageTimer),
            storageTimer);
      } else {
        logger.info(
            "Received delete collection request but autoDelete is not enabled, returning success without any action");
        return new SuccessResponse();
      }
    } else {
      logger.info(
          "Handling WriteCollections only with single collection deletion, returning success without any action");
      return new SuccessResponse();
    }
  }

  private boolean isDeleteCollectionRequest(@NotNull WriteRequest wc) {
    return isOnlyWriteCollections(wc)
           && wc.getWrites().size() == 1
           && WriteOp.DELETE.equals(wc.getWrites().get(0).getOp());
  }

  private boolean isUpdateCollectionRequest(@NotNull WriteRequest wc) {
    if (isOnlyWriteCollections(wc) && wc.getWrites().size() == 1) {
      final WriteOp op = wc.getWrites().get(0).getOp();
      return WriteOp.UPDATE.equals(op) || WriteOp.UPSERT.equals(op);
    }
    return false;
  }

  private @NotNull Response forwardWriteRequest(
      @NotNull final OperationData operationData,
      @NotNull final F1<Response, ErrorResponse> reattempt,
      final @NotNull StopWatch storageTimer) {
    Response response = measuredStorageSupplier(
        () -> performAtomicWriteFeatures(operationData.getSessionOptions(), operationData.getStorageImpl(), (WriteRequest) operationData.getRequest()), storageTimer);
    if (response instanceof ErrorResponse) {
      ErrorResponse errorResponse = (ErrorResponse) response;
      return reattempt.call(errorResponse);
    } else {
      return response;
    }
  }

  /**
   * Hook for executing a feature write request atomically.
   */
  protected @NotNull Response performAtomicWriteFeatures(
      @NotNull SessionOptions sessionOptions,
      @NotNull IStorage storageImpl,
      @NotNull WriteRequest wr) {
    return singleWrite(sessionOptions, storageImpl, wr);
  }

  private @NotNull Response singleWrite(@NotNull SessionOptions sessionOptions, @NotNull IStorage storageImpl, @NotNull WriteRequest wr) {
    return storageImpl.useWriteSession(sessionOptions, writer -> {
      final Response result = writer.execute(wr);
      if (result instanceof SuccessResponse) {
        writer.commit();
        return result;
      } else if (result instanceof ErrorResponse) {
        ErrorResponse errorResponse = (ErrorResponse) result;
        logger.warn("Failed executing {}, expected success but got ErrorResponse: {}", wr.getClass(), errorResponse.getError());
        writer.rollback();
        return errorResponse;
      } else {
        logger.warn("Failed executing {}, unexpected result: {}", wr.getClass(), result);
        return new ErrorResponse(NakshaError.EXCEPTION, "Execution unexpectedly failed due to unknown result: " + result);
      }
    });
  }

  private @NotNull Response reattemptFeatureRequest(
      final @NotNull OperationData operationData,
      final @NotNull OperationAttempt previousAttempt,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    switch (previousAttempt) {
      case FIRST_ATTEMPT:
        return reattemptFeatureRequestForTheFirstTime(operationData, previousError, storageTimer);
      case ATTEMPT_AFTER_STORAGE_INITIALIZATION:
        return reattemptAfterStorageInitialization(operationData, previousError, storageTimer);
      case ATTEMPT_AFTER_MAP_CREATION:
        return reattemptAfterMapCreation(operationData, previousError, storageTimer);
      case ATTEMPT_AFTER_COLLECTION_CREATION:
        return previousError;
      default:
        throw new IllegalStateException("Unsupported operation attempt: " + previousAttempt);
    }
  }

  private @NotNull Response reattemptCollectionRequest(
      final @NotNull OperationData operationData,
      final @NotNull OperationAttempt previousAttempt,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    switch (previousAttempt) {
      case FIRST_ATTEMPT:
        return reattemptCollectionRequestForTheFirstTime(operationData, previousError, storageTimer);
      case ATTEMPT_AFTER_STORAGE_INITIALIZATION:
        return reattemptCollectionRequestAfterStorageInit(operationData, previousError, storageTimer);
      case ATTEMPT_AFTER_MAP_CREATION:
      case ATTEMPT_AFTER_COLLECTION_CREATION:
        logger.warn(
                "No further reattempt strategy available for WriteCollections request (collectionId: {}, previous attempt: {}. Rethrowing original exception",
                operationData.getCollectionId(),
                previousAttempt);
        return previousError;
      default:
        throw new IllegalStateException("Unsupported operation attempt: " + previousAttempt);
    }
  }

  private @NotNull Response reattemptCollectionRequestForTheFirstTime(
          final @NotNull OperationData operationData,
          final @NotNull ErrorResponse previousError,
          final @NotNull StopWatch storageTimer) {
    if (indicateStorageNotInitialized(previousError)) {
      return retryDueToUninitializedStorage(operationData, storageTimer);
    } else if (indicatesMissingMap(previousError)) {
      return retryDueToMissingMap(operationData, storageTimer);
    } else {
      return previousError;
    }
  }

  private @NotNull Response reattemptCollectionRequestAfterStorageInit(
          final @NotNull OperationData operationData,
          final @NotNull ErrorResponse previousError,
          final @NotNull StopWatch storageTimer) {
    if (indicatesMissingMap(previousError)) {
      return retryDueToMissingMap(operationData, storageTimer);
    } else {
      return previousError;
    }
  }

  private @NotNull Response reattemptFeatureRequestForTheFirstTime(
      final @NotNull OperationData operationData,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    if (indicateStorageNotInitialized(previousError)) {
      return retryDueToUninitializedStorage(operationData, storageTimer);
    } else if (indicatesMissingMap(previousError)) {
      return retryDueToMissingMap(operationData, storageTimer);
    } else if (indicatesMissingCollection(previousError)) {
      return retryDueToMissingCollection(operationData, storageTimer);
    } else {
      return previousError;
    }
  }

  private @NotNull Response reattemptAfterStorageInitialization(
      final @NotNull OperationData operationData,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    if (indicatesMissingMap(previousError)) {
      return retryDueToMissingMap(operationData, storageTimer);
    } else if (indicatesMissingCollection(previousError)) {
      return retryDueToMissingCollection(operationData, storageTimer);
    } else {
      return previousError;
    }
  }

  private @NotNull Response reattemptAfterMapCreation(
      final @NotNull OperationData operationData,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer
  ) {
    if (indicatesMissingCollection(previousError)) {
      return retryDueToMissingCollection(operationData, storageTimer);
    } else {
      return previousError;
    }
  }

  @NotNull
  private Response retryDueToUninitializedStorage(
      final @NotNull OperationData operationData,
      final @NotNull StopWatch storageTimer
  ) {
    logger.info("Initializing Storage before reattempting write request.");
    measuredStorageRunnable(() -> Naksha.setupStorage(operationData.getStorageImpl().getConfig()), storageTimer);
    logger.info("Storage initialized");
    return forwardRequestToStorage(operationData, ATTEMPT_AFTER_STORAGE_INITIALIZATION, storageTimer);
  }

  private @NotNull Response retryDueToMissingMap(
      final @NotNull OperationData operationData,
      final @NotNull StopWatch storageTimer
  ) {
    logger.info("Creating map '{}'", operationData.getMapId());
    Response createMapResponse = measuredStorageSupplier(
        () -> createMissingMap(operationData.getSessionOptions(), operationData.getStorageImpl(), operationData.getMapId()), storageTimer);
    if (createMapResponse instanceof SuccessResponse) {
      logger.info("Successfully created map '{}'", operationData.getMapId());
      return forwardRequestToStorage(operationData, ATTEMPT_AFTER_MAP_CREATION, storageTimer);
    } else if (createMapResponse instanceof ErrorResponse) {
      ErrorResponse er = (ErrorResponse) createMapResponse;
      logger.info("Failure while creating map '{}': {}", operationData.getMapId(), er.getError());
      return er;
    } else {
      logger.info("Unknown response encountered while creating map '{}': {}", operationData.getMapId(), createMapResponse);
      return new ErrorResponse(new NakshaError(
          NakshaError.EXCEPTION,
          "Unknown response encountered while creating map: '" + operationData.getMapId() + "': " + createMapResponse
      ));
    }
  }

  protected @NotNull Response createMissingMap(
      @NotNull SessionOptions sessionOptions,
      @NotNull IStorage storageImpl,
      @NotNull String mapId) {
    WriteRequest createMapRequest = new WriteRequest().add(new Write().createMap(new NakshaCatalog(mapId)));
    return singleWrite(sessionOptions, storageImpl, createMapRequest);
  }

  private Response retryDueToMissingCollection(
      final @NotNull OperationData operationData,
      final @NotNull StopWatch storageTimer) {
    logger.warn("Collection not found for {}", operationData.getCollectionId());
    if (properties.getAutoCreateCollection()) {
      logger.info(
          "Collection auto creation is enabled, attempting to create collection specified in request: {}",
          operationData.getCollectionId());
      Response createCollectionResp = measuredStorageSupplier(
          () -> createMissingCollection(operationData.getSessionOptions(), operationData.getStorageImpl(), operationData.getMapId(), operationData.getCollectionId()),
          storageTimer);
      if (createCollectionResp instanceof SuccessResponse) {
        logger.info("Created collection {}, forwarding the request once again", operationData.getCollectionId());
        return forwardRequestToStorage(operationData, ATTEMPT_AFTER_COLLECTION_CREATION, storageTimer);
      } else if (createCollectionResp instanceof ErrorResponse) {
        ErrorResponse errorResponse = (ErrorResponse) createCollectionResp;
        if (indicateStorageNotInitialized(errorResponse)) {
          logger.info("Failed to create collection {} because of uninitialized storage", operationData.getCollectionId());
          return retryDueToUninitializedStorage(operationData, storageTimer);
        }
        logger.info("Failed to create collection '{}' because of unhandled reason. Response: {}", operationData.getCollectionId(), createCollectionResp);
        return new ErrorResponse(new NakshaError(
            NakshaError.EXCEPTION,
            "Could not handle request due to missing collection, could not recreate collection: " + operationData.getCollectionId()));
      } else {
        logger.info("Failed to create collection '{}' because of unhandled reason. Response: {}", operationData.getCollectionId(), createCollectionResp);
        return new ErrorResponse(new NakshaError(
            NakshaError.EXCEPTION,
            "Could not handle request due to missing collection, could not recreate collection: " + operationData.getCollectionId()));
      }
    } else {
      logger.warn(
          "Collection auto creation is disabled, failing due to missing collection specified in request: {}",
          operationData.getCollectionId());
      return new ErrorResponse(new NakshaError(
          NakshaError.NOT_FOUND, "Could not find and auto-create collection: " + operationData.getCollectionId()));
    }
  }

  protected @NotNull Response createMissingCollection(
      @NotNull SessionOptions sessionOptions,
      @NotNull IStorage storageImpl,
      @NotNull String mapId,
      @NotNull String collectionId) {
    return createXyzCollection(sessionOptions, storageImpl, mapId, collectionId);
  }

  private boolean indicateStorageNotInitialized(@NotNull ErrorResponse errorResponse) {
    return NakshaError.UNINITIALIZED.equals(errorResponse.getError().getCode());
  }

  private boolean indicatesMissingMap(@NotNull ErrorResponse errorResponse) {
    return NakshaError.MAP_NOT_FOUND.equals(errorResponse.getError().getCode());
  }

  private boolean indicatesMissingCollection(@NotNull ErrorResponse errorResponse) {
    return NakshaError.COLLECTION_NOT_FOUND.equals(errorResponse.getError().getCode());
  }

  private void applyMapIdAndCollectionId(
      Request request,
      @NotNull String mapId,
      @NotNull String collectionId
  ) {
    if (request instanceof ReadFeatures) {
      ReadFeatures rf = (ReadFeatures) request;
      rf.setMapId(mapId);
      rf.setCollectionIds(StringList.of(collectionId));
    } else if (request instanceof WriteRequest) {
      WriteRequest wr = (WriteRequest) request;
      if (isOnlyWriteCollections(wr)) {
        collectionsFrom(wr).forEach(collectionFromRequest -> {
          collectionFromRequest.setCatalogId(mapId);
          collectionFromRequest.setId(collectionId);
        });
      }
      String finalCollectionId = isOnlyWriteCollections(wr) ? Naksha.COLLECTIONS_COL_ID : collectionId;
      wr.getWrites().forEach(write -> {
        write.setMapId(mapId);
        write.setCollectionId(finalCollectionId);
      });
    }
  }

  /**
   * Hook called during {@link #process} to normalize the request before execution.
   */
  protected void normalizeWriteRequest(
      @NotNull Request request,
      @NotNull String mapId,
      @NotNull String collectionId
  ) {
    applyMapIdAndCollectionId(request, mapId, collectionId);
  }

  // TODO: collectionId at handler level can be potentially removed in the future
  private @NotNull String retrieveCollectionIdFromRequest(final Request request) {
    // TODO: check if mapId is present
    final NakshaCollection collectionDefinedInHandler = properties.getCollection();
    if (collectionDefinedInHandler != null) {
      logger.info(
          "Using collection with id {} that is associated with EventHandler(id={})",
          collectionDefinedInHandler.getId(),
          eventHandlerConfig.getId());
      return collectionDefinedInHandler.getId();
    }
    if (eventTarget instanceof Space) {
      Space s = (Space) eventTarget;
      NakshaCollection collectionDefinedInSpace = null;
      if (request instanceof WriteRequest && isUpdateCollectionRequest((WriteRequest) request)) {
        WriteRequest wc = (WriteRequest) request;
        // use newly provided collection in the Update request itself
        // to make sure that the newer collection id (if it has been changed) is used
        collectionDefinedInSpace =
            (NakshaCollection) wc.getWrites().get(0).getFeature();
      } else {
        // use existing Space collection (as it is not an Update request)
        final SpaceProperties spaceProperties = JvmBoxingUtil.box(s.getProperties(), SpaceProperties.class);
        collectionDefinedInSpace = spaceProperties.getCollection();
      }
      if (collectionDefinedInSpace != null) {
        logger.info(
            "Using collection with id {} that is associated with Space(id={})",
            collectionDefinedInSpace.getId(),
            s.getId());
        return collectionDefinedInSpace.getId();
      }
    }
    logger.info(
        "No collection definition found in Handler & Space properties, using default one with event target id: {}",
        eventTarget.getId());
    return eventTarget.getId();
  }

  private @NotNull List<@NotNull NakshaCollection> collectionsFrom(@NotNull WriteRequest wr) {
    final ArrayList<NakshaCollection> collections = new ArrayList<>();
    for (Write write : wr.getWrites()) {
      if (write.getFeature() instanceof NakshaCollection) {
        collections.add((NakshaCollection) write.getFeature());
      }
    }
    return collections;
  }

  private Response createXyzCollection(
      @NotNull SessionOptions sessionOptions,
      @NotNull IStorage storageImpl,
      @NotNull String mapId,
      @NotNull String collectionId
  ) {
    return storageImpl.useWriteSession(sessionOptions, writer -> {
      final Response result = writer.execute(createWriteCollectionsRequest(new NakshaCollection(collectionId, mapId)));
      if (result instanceof SuccessResponse) {
        writer.commit();
        return result;
      } else if (result instanceof ErrorResponse) {
        ErrorResponse errorResponse = (ErrorResponse) result;
        logger.error(
            "Error result while creating collection {}. Error: {}. Executing rollback",
            collectionId,
            errorResponse.getError());
        writer.rollback();
        return new ErrorResponse(errorResponse.getError());
      } else {
        String msg = "Unexpected result while creating collection " + collectionId + ". Result: " + result;
        logger.error(msg);
        writer.rollback();
        return new ErrorResponse(NakshaError.EXCEPTION, msg);
      }
    });
  }

  enum OperationAttempt {
    FIRST_ATTEMPT,
    ATTEMPT_AFTER_STORAGE_INITIALIZATION,
    ATTEMPT_AFTER_MAP_CREATION,
    ATTEMPT_AFTER_COLLECTION_CREATION
  }

  /**
   * Immutable wrapper for data used in each operation attempt
   *
   * @param sessionOptions
   * @param storageImpl
   * @param mapId
   * @param collectionId
   * @param request
   */
  private static final class OperationData {
    private final SessionOptions sessionOptions;
    private final IStorage storageImpl;
    private final String mapId;
    private final String collectionId;
    private final Request request;

    private OperationData(
        SessionOptions sessionOptions,
        IStorage storageImpl,
        String mapId,
        String collectionId,
        Request request
    ) {
      this.sessionOptions = sessionOptions;
      this.storageImpl = storageImpl;
      this.mapId = mapId;
      this.collectionId = collectionId;
      this.request = request;
    }

    private SessionOptions getSessionOptions() {
      return sessionOptions;
    }

    private IStorage getStorageImpl() {
      return storageImpl;
    }

    private String getMapId() {
      return mapId;
    }

    private String getCollectionId() {
      return collectionId;
    }

    private Request getRequest() {
      return request;
    }
  }
}
