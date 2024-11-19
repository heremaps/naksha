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
import static com.here.naksha.lib.core.util.storage.RequestHelper.createWriteCollectionsRequest;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.NOT_IMPLEMENTED;
import static com.here.naksha.lib.handlers.AbstractEventHandler.EventProcessingStrategy.PROCESS;
import static com.here.naksha.lib.handlers.DefaultStorageHandler.OperationAttempt.*;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.exceptions.StorageNotInitialized;
import com.here.naksha.lib.core.lambdas.F1;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.naksha.Space;
import com.here.naksha.lib.core.models.naksha.SpaceProperties;
import com.here.naksha.lib.handlers.exceptions.MissingCollectionsException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import naksha.base.JvmProxyUtil;
import naksha.base.StringList;
import naksha.model.*;
import naksha.model.objects.NakshaCollection;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultStorageHandler extends AbstractEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(DefaultStorageHandler.class);
  private static final Set<String> MISSING_COLLECTION_SQL_ERROR_STATES =
      Set.of(UNDEFINED_TABLE.toString(), COLLECTION_DOES_NOT_EXIST.toString());

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
    this.properties = JvmProxyUtil.box(eventHandler.getProperties(), DefaultStorageHandlerProperties.class);
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
      return new ErrorResponse(NakshaError.NOT_FOUND, "No storageId configured for handler.", null, null);
    }
    logger.info("Against Storage id={}", storageId);
    addStorageIdToStreamInfo(storageId, ctx);

    // Obtain IStorage implementation using NakshaHub
    final IStorage storageImpl = nakshaHub().getStorageById(storageId);
    logger.info("Using storage implementation [{}]", storageImpl.getClass().getName());

    NakshaCollection collection = chooseCollection(request);
    applyCollectionId(request, collection.getId());
    return forwardRequestToStorage(ctx, request, storageImpl, collection, FIRST_ATTEMPT);
  }

  private @NotNull Response forwardRequestToStorage(
      final @NotNull NakshaContext ctx,
      final @NotNull Request request,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull OperationAttempt currentAttempt) {
    if (request instanceof ReadFeatures rf) {
      return forwardReadFeatures(ctx, storageImpl, collection, rf, currentAttempt);
    } else if (request instanceof WriteRequest wr) {
      if (wr.getWrites().stream().map(Write::getCollectionId).allMatch(Naksha.VIRT_COLLECTIONS::equals)) {
        return forwardWriteCollections(ctx, storageImpl, collection, wr, currentAttempt);
      } else return forwardWriteFeatures(ctx, storageImpl, collection, wr, currentAttempt);
    } else {
      return notImplemented(request);
    }
  }

  private @NotNull Response forwardReadFeatures(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull ReadFeatures rf,
      final @NotNull OperationAttempt currentAttempt) {
    logger.info("Processing ReadFeatures against {}", collection.getId());
    final IReadSession reader = storageImpl.newReadSession(SessionOptions.from(ctx, false));
    Response response = reader.execute(rf);
    if (response instanceof ErrorResponse er) {
      return reattemptFeatureRequest(ctx, storageImpl, collection, rf, currentAttempt, new RuntimeException(er.getError().getCause()));
    }
    return response;
  }

  private @NotNull Response forwardWriteFeatures(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull WriteRequest wf,
      final OperationAttempt operationAttempt) {
    logger.info("Processing WriteFeatures against {}", collection.getId());
    return forwardWriteRequest(
        ctx,
        storageImpl,
        wf,
        re -> reattemptFeatureRequest(ctx, storageImpl, collection, wf, operationAttempt, re));
  }

  private @NotNull Response forwardWriteCollections(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull WriteRequest wc,
      final OperationAttempt operationAttempt) {
    logger.info("Processing WriteCollections against {}", collection.getId());
    if (isUpdateCollectionRequest(wc)) {
      if (properties.getAutoCreateCollection()) {
        return forwardWriteRequest(
            ctx,
            storageImpl,
            wc,
            re -> reattemptCollectionRequest(ctx, storageImpl, collection, wc, operationAttempt, re));
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
            re -> reattemptCollectionRequest(ctx, storageImpl, collection, wc, operationAttempt, re));
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
    final WriteOp op = wc.getWrites().get(0).getOp();
    return wc.getWrites().size() == 1
            && wc.getWrites().get(0).getCollectionId().equals(Naksha.VIRT_COLLECTIONS)
            && WriteOp.PURGE.equals(op);
  }

  private boolean isUpdateCollectionRequest(@NotNull WriteRequest wc) {
    final WriteOp op = wc.getWrites().get(0).getOp();
    return wc.getWrites().size() == 1
            && wc.getWrites().get(0).getCollectionId().equals(Naksha.VIRT_COLLECTIONS)
            && (WriteOp.UPDATE.equals(op) || WriteOp.UPSERT.equals(op));
  }

  private @NotNull Response forwardWriteRequest(
      @NotNull NakshaContext ctx,
      @NotNull IStorage storageImpl,
      @NotNull WriteRequest wr,
      @NotNull F1<Response, RuntimeException> reattempt) {
    final IWriteSession writer = storageImpl.newWriteSession(SessionOptions.from(ctx, true));
    final Response result = writer.execute(wr);
    if (result instanceof SuccessResponse) {
      writer.commit();
    } else {
      reattempt.call(new RuntimeException(
              "Failed executing " + wr.getClass() + ", expected success but got: " + result));
      logger.warn("Failed executing {}, expected success but got: {}", wr.getClass(), result);
      writer.rollback();
    }
    return result;
  }

  private @NotNull Response reattemptFeatureRequest(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull OperationAttempt previousAttempt,
      final @NotNull RuntimeException re) {
    return switch (previousAttempt) {
      case FIRST_ATTEMPT -> reattemptFeatureRequestForTheFirstTime(ctx, storageImpl, collection, request, re);
      case ATTEMPT_AFTER_STORAGE_INITIALIZATION -> reattemptAfterStorageInitialization(
          ctx, storageImpl, collection, request, re);
      case ATTEMPT_AFTER_COLLECTION_CREATION -> throw re;
    };
  }

  private @NotNull Response reattemptCollectionRequest(
      NakshaContext ctx,
      IStorage storageImpl,
      NakshaCollection collection,
      WriteRequest wc,
      OperationAttempt previousAttempt,
      RuntimeException re) {
    if (previousAttempt == FIRST_ATTEMPT && re instanceof StorageNotInitialized) {
      return retryDueToUninitializedStorage(ctx, storageImpl, collection, wc);
    }
    logger.warn(
        "No further reattempt strategy available for WriteCollections request (collectionId: {}, previous attempt: {}. Rethrowing original exception",
        collection.getId(),
        previousAttempt);
    throw re;
  }

  private @NotNull Response reattemptFeatureRequestForTheFirstTime(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull RuntimeException re) {
    if (re instanceof StorageNotInitialized) {
      return retryDueToUninitializedStorage(ctx, storageImpl, collection, request);
    } else if (indicatesMissingCollection(re)) {
      try {
        return retryDueToMissingCollection(ctx, storageImpl, collection, request);
      } catch (MissingCollectionsException mce) {
        logger.info("Retrying due to missing collection failed", mce);
        return mce.toErrorResult();
      }
    } else {
      throw re;
    }
  }

  private @NotNull Response reattemptAfterStorageInitialization(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request,
      final @NotNull RuntimeException re) {
    if (indicatesMissingCollection(re)) {
      try {
        return retryDueToMissingCollection(ctx, storageImpl, collection, request);
      } catch (MissingCollectionsException mce) {
        logger.info("Retrying due to missing collection failed", mce);
        return mce.toErrorResult();
      }
    } else {
      throw re;
    }
  }

  private boolean indicatesMissingCollection(RuntimeException re) {
    if (re.getCause() instanceof SQLException sqe) {
      return MISSING_COLLECTION_SQL_ERROR_STATES.contains(sqe.getSQLState());
    }
    return false;
  }

  @NotNull
  private Response retryDueToUninitializedStorage(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request) {
    logger.info("Initializing Storage before reattempting write request.");
    storageImpl.initStorage(null);
    logger.info("Storage initialized");
    return forwardRequestToStorage(ctx, request, storageImpl, collection, ATTEMPT_AFTER_STORAGE_INITIALIZATION);
  }

  private Response retryDueToMissingCollection(
      final @NotNull NakshaContext ctx,
      final @NotNull IStorage storageImpl,
      final @NotNull NakshaCollection collection,
      final @NotNull Request request) {
    logger.warn("Collection not found for {}", collection.getId());
    if (properties.getAutoCreateCollection()) {
      logger.info(
          "Collection auto creation is enabled, attempting to create collection specified in request: {}",
          collection.getId());
      createXyzCollection(ctx, storageImpl, collection);
      logger.info("Created collection {}, forwarding the request once again", collection.getId());
      return forwardRequestToStorage(ctx, request, storageImpl, collection, ATTEMPT_AFTER_COLLECTION_CREATION);
    } else {
      logger.warn(
          "Collection auto creation is disabled, failing due to missing collection specified in request: {}",
          collection.getId());
      throw new MissingCollectionsException(collection);
    }
  }

  private void applyCollectionId(Request request, @NotNull String customCollectionId) {
    if (request instanceof ReadFeatures rf) {
      final StringList ids = new StringList();
      ids.add(customCollectionId);
      rf.setFeatureIds(ids);
    } else if (request instanceof WriteRequest wr) {
      if (wr.getWrites().stream().map(Write::getCollectionId).allMatch(Naksha.VIRT_COLLECTIONS::equals)) {
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
        final SpaceProperties spaceProperties = JvmProxyUtil.box(s.getProperties(), SpaceProperties.class);
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
