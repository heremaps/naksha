package com.here.naksha.cli.copy.service;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorInfo;
import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandResult;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.model.IStorage;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaCatalog;
import naksha.model.objects.NakshaDatabase;
import naksha.model.objects.NakshaFeatureList;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static naksha.base.NakshaExceptionKt.illegalState;
import static naksha.base.NakshaExceptionKt.internalError;

public final class CopyService {
    private final FeaturesWriteExecutor featuresWriteExecutor;
    private final Logger logger = LoggerFactory.getLogger(CopyService.class);
    private final SessionOptions sessionOptions;
    private final StorageProvider storageProvider;

    public CopyService(
            @NotNull FeaturesWriteExecutor featuresWriteExecutor,
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions
    ) {
        this.storageProvider = storageProvider;
        this.sessionOptions = sessionOptions;
        this.featuresWriteExecutor = featuresWriteExecutor;
    }

    @NotNull
    public CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copy(
            @NotNull CopyElement src,
            @NotNull CopyElement target,
            boolean autoCreateTarget
    ) {
        try {
            IStorage targetStorage = useTargetStorage(target);
            if (autoCreateTarget) {
                createTarget(targetStorage, target);
            }
            FeatureTupleList featureTuples = readFeaturesTuplesFromSrc(src);
            FeaturesWriteExecutorInfo featuresWriteExecutorInfo = writeFeaturesToTarget(featureTuples, target, targetStorage);
            CopyServiceSuccessResultPayload successResultPayload = buildSuccessResultPayload(featuresWriteExecutorInfo);
            return new CommandSuccess<>(successResultPayload);
        } catch (CopyServiceException exception) {
            return new CommandFailure<>(exception);
        }
    }

    private FeaturesWriteExecutorInfo writeFeaturesToTarget(
            FeatureTupleList featureTuples,
            CopyElement target,
            IStorage storage
    ) throws CopyServiceException {
        try {
            logger.info("Writing to {}", target);
            return featuresWriteExecutor.write(storage, target, featureTuples, sessionOptions);
        } catch (Exception exception) {
            throw new CopyServiceException("Problem with writing to target!", exception);
        }
    }

    private IStorage useSrcStorage(CopyElement source) throws CopyServiceException {
        try {
            return storageProvider.useStorage(source.getNakshaStorage());
        } catch (Exception e) {
            throw new CopyServiceException("Can not get source storage!", e);
        }
    }

    private FeatureTupleList readFeaturesTuplesFromSrc(
            CopyElement source
    ) throws CopyServiceException {
        logger.info("Reading from {}", source);
        IStorage storage = useSrcStorage(source);
        ReadFeatures readFeatures = createReadFeaturesRequest(source);
        Response response = performReadRequest(storage, readFeatures);
        SuccessResponse successResponse = requireSourceSuccessResponse(response);
        FeatureTupleList featureTuples = successResponse.getFeatureTupleList();
        logger.info("Successfully read {} feature's tuples from {}!", featureTuples.size(), source);
        return successResponse.getFeatureTupleList();
    }

    private SuccessResponse requireSourceSuccessResponse(Response response) throws CopyServiceException {
        return requireSuccessResponse(
                response,
                "Problem with reading from source!",
                "Unexpected response from source!"
        );
    }

    private SuccessResponse requireSuccessResponse(
            Response response,
            String errorResponseExceptionMessage,
            String unexpectedResponseExceptionMessage
    ) throws CopyServiceException {
        return switch (response) {
            case SuccessResponse successResponse -> successResponse;
            case ErrorResponse errorResponse -> throw new CopyServiceException(
                    errorResponseExceptionMessage,
                    new NakshaException(errorResponse.getError())
            );
            default -> throw new CopyServiceException(unexpectedResponseExceptionMessage);
        };
    }

    private ReadFeatures createReadFeaturesRequest(CopyElement source) {
        ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.setCollectionId(source.getCollectionId());
        readFeatures.setCatalogId(source.getMapId());

        return readFeatures;
    }

    private Response performReadRequest(IStorage storage, Request request) throws CopyServiceException {
        try {
            return storage.useReadSession(
                    sessionOptions,
                    reader -> reader.execute(request)
            );
        } catch (Exception e) {
            throw new CopyServiceException("Problem while reading features from source!", e);
        }
    }

    private IStorage useTargetStorage(CopyElement target) throws CopyServiceException {
        try {
            return storageProvider.useStorage(target.getNakshaStorage());
        } catch (Exception e) {
            throw new CopyServiceException("Can not get target storage!", e);
        }
    }

    private void createTarget(@NotNull IStorage storage, @NotNull CopyElement target) throws CopyServiceException {
        if (target.getMapId() == null) {
            throw new CopyServiceException("Target's mapId should not be null!");
        }
        if (target.getCollectionId() == null) {
            throw new CopyServiceException("Target's collectionId should not be null!");
        }
        createMapIfAbsent(storage, target);
        createCollectionIfAbsent(storage, target);
    }

    private void createMapIfAbsent(
        @NotNull IStorage storage,
        @NotNull CopyElement target
    ) throws CopyServiceException {
      final var mapId = target.getMapId();
      try (final var session = storage.newWriteSession(sessionOptions)) {
        final var req = new WriteRequest().add(new Write().createCatalog(new NakshaCatalog(mapId, new NakshaDatabase(storage))));
        final var response = session.execute(req);
        if (response instanceof SuccessResponse) {
          session.commit();
          logger.info("Map(id: \"{}\") was successfully created on storage(id: \"{}\")!", mapId, storage.getId());
        } else if (response instanceof ErrorResponse errorResponse) {
          final var err = errorResponse.getError();
          if (!err.getCode().equals(NakshaError.CATALOG_EXISTS)) {
            throw new NakshaException(err);
          }
          logger.info("Map(id: \"{}\") is already present on storage(id: \"{}\")!", mapId, storage.getId());
        } else {
          throw internalError("Unexpected response while creating map");
        }
        final var catalog = session.getCatalogById(target.getMapId());
        if (catalog == null) throw illegalState("The session does not return the map");
        target.catalog = catalog;
      } catch (Exception e) {
        throw new CopyServiceException("Failed to create map: " + mapId, e);
      }
    }

    private void createCollectionIfAbsent(
        @NotNull IStorage storage,
        @NotNull CopyElement target
    ) throws CopyServiceException {
      final var collectionId = target.getCollectionId();
      try (final var session = storage.newWriteSession(sessionOptions)) {
        final var req = new WriteRequest().add(new Write().createCollection(new NakshaCollection(collectionId, target.catalog())));
        final var response = session.execute(req);
        if (response instanceof SuccessResponse) {
          session.commit();
          logger.info("Collection(id: \"{}\") was successfully created in map(id: \"{}\") on storage(id: \"{}\")!",
              target.getCollectionId(),
              target.getMapId(),
              storage.getId()
          );
        } else if (response instanceof ErrorResponse errorResponse) {
          session.rollback();
          NakshaError nakshaError = errorResponse.getError();
          if (!nakshaError.getCode().equals(NakshaError.COLLECTION_EXISTS)) {
            throw new NakshaException(nakshaError);
          }
          logger.info(
              "Collection(id: \"{}\") is already present in map(id: \"{}\") on storage(id: \"{}\")!",
              target.getCollectionId(),
              target.getMapId(),
              storage.getId()
          );
        } else {
          throw internalError("Unexpected response while creating collection!");
        }
        final var collection = session.getCollectionById(target.catalog(), collectionId);
        if (collection == null) throw illegalState("The session does not return the collection");
        target.collection = collection;
      } catch (Exception e) {
        throw new CopyServiceException("Failed to create collection: "+collectionId, e);
      }
    }

    private CopyServiceSuccessResultPayload buildSuccessResultPayload(FeaturesWriteExecutorInfo featuresWriteExecutorInfo) {
        return new CopyServiceSuccessResultPayload(featuresWriteExecutorInfo.numberOfWrittenElements());
    }
}
