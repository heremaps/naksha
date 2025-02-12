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

import static com.here.naksha.lib.core.exceptions.UncheckedException.unchecked;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.ATTEMPT_AFTER_COLLECTION_CREATION;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.ATTEMPT_AFTER_STORAGE_INITIALIZATION;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.FIRST_ATTEMPT;
import static com.here.naksha.lib.handlers.util.RequestTypesUtil.isOnlyWriteCollections;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static naksha.model.util.RequestHelper.createWriteCollectionsRequest;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.lambdas.F1;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import naksha.base.JvmBoxingUtil;
import naksha.base.StringList;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.StreamInfo;
import naksha.model.objects.NakshaCollection;
import naksha.model.request.ErrorResponse;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteOp;
import naksha.model.request.WriteRequest;
import naksha.psql.NakshaExceptionMapper;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultStorageHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DefaultStorageHandler.class);

  protected @NotNull EventHandler eventHandler;
  protected @NotNull EventTarget<?> eventTarget;
  protected @NotNull DefaultStorageHandlerProperties properties;

  public DefaultStorageHandler(
      final @NotNull EventHandler eventHandler,
      final @NotNull INaksha hub,
      final @NotNull EventTarget<?> eventTarget) {
    super(hub);
    this.eventHandler = eventHandler;
    this.eventTarget = eventTarget;
    this.properties = Objects.requireNonNull(
        JvmBoxingUtil.box(eventHandler.getProperties(), DefaultStorageHandlerProperties.class));
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
    final NakshaContext ctx = NakshaContext.currentContext();
    final Request request = event.getRequest();

    logger.info("Handler received request {}", request.getClass().getSimpleName());
    // Obtain storageId from EventHandler object
    final String storageId = properties.getStorageId();
    if (storageId == null) {
      logger.error("No storageId configured");
      return new ErrorResponse(NakshaError.NOT_FOUND, "No storageId configured for handler.");
    }
    logger.info("Against Storage id={}", storageId);
    addStorageIdToStreamInfo(storageId, ctx);

    // Obtain IStorage implementation using NakshaHub
    final IStorage storageImpl = nakshaHub().getStorageById(storageId);
    logger.info("Using storage implementation [{}]", storageImpl.getClass().getName());

    NakshaCollection collection = chooseCollection(request);
    applyCollectionId(request, collection.getId());
    StopWatch storageTimer = new StopWatch();
    try {
      return forwardRequestToStorage(ctx, request, storageImpl, collection, FIRST_ATTEMPT, storageTimer);
    } finally {
      addStorageTimeToStreamInfo(storageTimer, ctx);
    }
  }

  private void addStorageTimeToStreamInfo(StopWatch storageTimer, NakshaContext ctx) {
    StreamInfo streamInfo = ctx.getStreamInfo();
    if (streamInfo != null) {
      streamInfo.increaseTimeInStorage(NANOSECONDS.toMillis(storageTimer.getNanoTime()));
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
      final @NotNull NakshaContext ctx,
      final @NotNull Request request,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull OperationAttempt currentAttempt,
      final @NotNull StopWatch storageTimer) {
    if (request instanceof ReadFeatures rf) {
      return forwardReadFeatures(ctx, storageImpl, collection, rf, currentAttempt, storageTimer);
    } else if (request instanceof WriteRequest wr) {
      if (isOnlyWriteCollections(wr)) {
        return forwardWriteCollections(ctx, storageImpl, collection, wr, currentAttempt, storageTimer);
      } else {
        return forwardWriteFeatures(ctx, storageImpl, collection, wr, currentAttempt, storageTimer);
      }
    } else {
      return notImplemented(request);
    }
  }

  private @NotNull Response forwardReadFeatures(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull ReadFeatures rf,
      final @NotNull OperationAttempt currentAttempt,
      final @NotNull StopWatch storageTimer) {
    logger.info("Processing ReadFeatures against {}", collection.getId());
    Response response = measuredStorageSupplier(() -> singleRead(ctx, storageImpl, rf), storageTimer);
    if (response instanceof ErrorResponse errorResponse) {
      return reattemptFeatureRequest(
          ctx, storageImpl, collection, rf, currentAttempt, errorResponse, storageTimer);
    } else {
      return response;
    }
  }

  private @NotNull Response singleRead(
      final @NotNull NakshaContext ctx, final @NotNull IStorage storageImpl, final @NotNull ReadFeatures rf) {
    try (final IReadSession reader = storageImpl.newReadSession(SessionOptions.from(ctx, false))) {
      return reader.execute(rf);
    }
  }

  private @NotNull Response forwardWriteFeatures(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull WriteRequest wf,
      final OperationAttempt operationAttempt,
      final @NotNull StopWatch storageTimer) {
    logger.info("Processing WriteFeatures against {}", collection.getId());
    return forwardWriteRequest(
        ctx,
        storageImpl,
        wf,
        errorResponse -> reattemptFeatureRequest(
            ctx, storageImpl, collection, wf, operationAttempt, errorResponse, storageTimer),
        storageTimer);
  }

  private @NotNull Response forwardWriteCollections(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull WriteRequest wc,
      final OperationAttempt operationAttempt,
      final @NotNull StopWatch storageTimer) {
    logger.info("Processing WriteCollections against {}", collection.getId());
    if (isUpdateCollectionRequest(wc)) {
      if (properties.getAutoCreateCollection()) {
        return forwardWriteRequest(
            ctx,
            storageImpl,
            wc,
            errorResponse -> reattemptCollectionRequest(
                ctx, storageImpl, collection, wc, operationAttempt, errorResponse, storageTimer),
            storageTimer);
      } else {
        logger.info(
            "Received update collection request but autoCreate is not enabled, returning success without any action");
        return new SuccessResponse();
      }
    } else if (isPurgeCollectionRequest(wc)) {
      if (properties.getAutoDeleteCollection()) {
        return forwardWriteRequest(
            ctx,
            storageImpl,
            wc,
            re -> reattemptCollectionRequest(
                ctx, storageImpl, collection, wc, operationAttempt, re, storageTimer),
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

  private boolean isPurgeCollectionRequest(@NotNull WriteRequest wc) {
    return isOnlyWriteCollections(wc)
        && wc.getWrites().size() == 1
        && WriteOp.PURGE.equals(wc.getWrites().get(0).getOp());
  }

  private boolean isUpdateCollectionRequest(@NotNull WriteRequest wc) {
    if (isOnlyWriteCollections(wc) && wc.getWrites().size() == 1) {
      final WriteOp op = wc.getWrites().get(0).getOp();
      return WriteOp.UPDATE.equals(op) || WriteOp.UPSERT.equals(op);
    }
    return false;
  }

  private @NotNull Response forwardWriteRequest(
      @NotNull NakshaContext ctx,
      @NotNull IStorage storageImpl,
      @NotNull WriteRequest wr,
      @NotNull F1<Response, ErrorResponse> reattempt,
      final @NotNull StopWatch storageTimer) {
    Response response = measuredStorageSupplier(() -> singleWrite(ctx, storageImpl, wr), storageTimer);
    if (response instanceof ErrorResponse errorResponse) {
      return reattempt.call(errorResponse);
    } else {
      return response;
    }
  }

  private @NotNull Response singleWrite(
      @NotNull NakshaContext ctx, @NotNull IStorage storageImpl, @NotNull WriteRequest wr) {
    try (final IWriteSession writer = storageImpl.newWriteSession(SessionOptions.from(ctx, true))) {
      final Response result = writer.execute(wr);
      if (result instanceof SuccessResponse) {
        writer.commit();
      } else {
        logger.warn("Failed executing {}, expected success but got: {}", wr.getClass(), result);
        writer.rollback();
      }
      return result;
    } catch (NakshaException ne) {
      logger.warn("Failed executing {}", wr.getClass(), ne);
      return new ErrorResponse(ne.error);
    } catch (Exception e) {
      logger.warn("Failed executing {}", wr.getClass(), e);
      return new ErrorResponse(NakshaError.EXCEPTION, "Execution unexpectedly failed", e);
    }
  }

  private @NotNull Response reattemptFeatureRequest(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull OperationAttempt previousAttempt,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    return switch (previousAttempt) {
      case FIRST_ATTEMPT -> reattemptFeatureRequestForTheFirstTime(
          ctx, storageImpl, collection, request, previousError, storageTimer);
      case ATTEMPT_AFTER_STORAGE_INITIALIZATION -> reattemptAfterStorageInitialization(
          ctx, storageImpl, collection, request, previousError, storageTimer);
      case ATTEMPT_AFTER_COLLECTION_CREATION -> previousError;
    };
  }

  private @NotNull Response reattemptCollectionRequest(
      NakshaContext ctx,
      IStorage storageImpl,
      NakshaCollection collection,
      WriteRequest wc,
      OperationAttempt previousAttempt,
      ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    if (previousAttempt == FIRST_ATTEMPT && indicateStorageNotInitialized(previousError)) {
      return retryDueToUninitializedStorage(ctx, storageImpl, collection, wc, storageTimer);
    }
    logger.warn(
        "No further reattempt strategy available for WriteCollections request (collectionId: {}, previous attempt: {}. Rethrowing original exception",
        collection.getId(),
        previousAttempt);
    return previousError;
  }

  private @NotNull Response reattemptFeatureRequestForTheFirstTime(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    if (indicateStorageNotInitialized(previousError)) {
      return retryDueToUninitializedStorage(ctx, storageImpl, collection, request, storageTimer);
    } else if (indicatesMissingCollection(previousError)) {
      return retryDueToMissingCollection(ctx, storageImpl, collection, request, storageTimer);
    } else {
      return previousError;
    }
  }

  private @NotNull Response reattemptAfterStorageInitialization(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull ErrorResponse previousError,
      final @NotNull StopWatch storageTimer) {
    if (indicatesMissingCollection(previousError)) {
      return retryDueToMissingCollection(ctx, storageImpl, collection, request, storageTimer);
    } else {
      return previousError;
    }
  }

  @NotNull
  private Response retryDueToUninitializedStorage(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull StopWatch storageTimer) {
    logger.info("Initializing Storage before reattempting write request.");
    measuredStorageRunnable(() -> storageImpl.initStorage(null), storageTimer);
    logger.info("Storage initialized");
    return forwardRequestToStorage(
        ctx, request, storageImpl, collection, ATTEMPT_AFTER_STORAGE_INITIALIZATION, storageTimer);
  }

  private Response retryDueToMissingCollection(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull StopWatch storageTimer) {
    logger.warn("Collection not found for {}", collection.getId());
    if (properties.getAutoCreateCollection()) {
      logger.info(
          "Collection auto creation is enabled, attempting to create collection specified in request: {}",
          collection.getId());
      measuredStorageRunnable(() -> createXyzCollection(ctx, storageImpl, collection), storageTimer);
      logger.info("Created collection {}, forwarding the request once again", collection.getId());
      return forwardRequestToStorage(
          ctx, request, storageImpl, collection, ATTEMPT_AFTER_COLLECTION_CREATION, storageTimer);
    } else {
      logger.warn(
          "Collection auto creation is disabled, failing due to missing collection specified in request: {}",
          collection.getId());
      return new ErrorResponse(new NakshaError(
          NakshaError.EXCEPTION, "Could not find and auto-create collection: " + collection.getId()));
    }
  }

  private boolean indicateStorageNotInitialized(@NotNull ErrorResponse errorResponse) {
    return NakshaError.UNINITIALIZED.equals(errorResponse.getError().getCode());
  }

  private boolean indicatesMissingCollection(@NotNull ErrorResponse errorResponse) {
    return NakshaError.COLLECTION_NOT_FOUND.equals(errorResponse.getError().getCode());
  }

  private void applyCollectionId(Request request, @NotNull String customCollectionId) {
    if (request instanceof ReadFeatures rf) {
      final StringList ids = new StringList();
      ids.add(customCollectionId);
      rf.setFeatureIds(ids);
    } else if (request instanceof WriteRequest wr) {
      if (isOnlyWriteCollections(wr)) {
        collectionsFrom(wr).forEach(collection -> collection.setId(customCollectionId));
      } else {
        wr.getWrites().forEach(write -> write.setCollectionId(customCollectionId));
      }
    }
  }

  // TODO: collectionId at handler level can be potentially removed in the future
  private @NotNull NakshaCollection chooseCollection(final Request request) {
    final NakshaCollection collectionDefinedInHandler = properties.getCollection();
    if (collectionDefinedInHandler != null) {
      logger.info(
          "Using collection with id {} that is associated with EventHandler(id={})",
          collectionDefinedInHandler.getId(),
          eventHandler.getId());
      return collectionDefinedInHandler;
    }
    if (eventTarget instanceof Space s) {
      NakshaCollection collectionDefinedInSpace = null;
      if (request instanceof WriteRequest wc && isUpdateCollectionRequest(wc)) {
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
        return collectionDefinedInSpace;
      }
    }
    logger.info(
        "No collection definition found in Handler & Space properties, using default one with event target id: {}",
        eventTarget.getId());
    return new NakshaCollection(eventTarget.getId());
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

  private void createXyzCollection(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection) {
    final IWriteSession writer = storageImpl.newWriteSession(SessionOptions.from(ctx, true));
    final Response result = writer.execute(createWriteCollectionsRequest(collection));
    if (result instanceof SuccessResponse) {
      writer.commit();
    } else {
      logger.error(
          "Unexpected result while creating collection {}. Result - {}. Executing rollback",
          collection.getId(),
          result);
      writer.rollback();
      throw unchecked(new Exception("Failed creating collection " + collection.getId()));
    }
  }

  enum OperationAttempt {
    FIRST_ATTEMPT,
    ATTEMPT_AFTER_STORAGE_INITIALIZATION,
    ATTEMPT_AFTER_COLLECTION_CREATION
  }
}
