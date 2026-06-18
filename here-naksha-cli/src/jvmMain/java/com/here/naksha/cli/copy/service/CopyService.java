package com.here.naksha.cli.copy.service;

import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutor;
import com.here.naksha.cli.copy.service.executors.model.FeaturesWriteExecutorInfo;
import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandResult;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaCatalog;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        readFeatures.setCollectionIds(
                StringList.of(source.getCollectionId())
        );
        readFeatures.setMapId(source.getMapId());

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

    private void createTarget(IStorage storage, CopyElement target) throws CopyServiceException {
        if (target.getMapId() == null) {
            throw new CopyServiceException("Target's mapId should not be null!");
        }
        if (target.getCollectionId() == null) {
            throw new CopyServiceException("Target's collectionId should not be null!");
        }
        createMapIfAbsent(storage, target.getMapId());
        createCollectionIfAbsent(storage, target);
    }

    private void createMapIfAbsent(IStorage storage, String mapId) throws CopyServiceException {
        Response response = performCreateMapRequest(storage, mapId);
        switch (response) {
            case SuccessResponse _ -> logger.info(
                    "Map(id: \"{}\") was successfully created on storage(id: \"{}\")!", mapId, storage.getId()
            );
            case ErrorResponse errorResponse -> {
                NakshaError nakshaError = errorResponse.getError();
                if (!nakshaError.getCode().equals(NakshaError.CATALOG_EXISTS)) {
                    throw new CopyServiceException("Problem with creating map!", new NakshaException(nakshaError));
                }
                logger.info("Map(id: \"{}\") is already present on storage(id: \"{}\")!", mapId, storage.getId());
            }
            default -> throw new CopyServiceException("Unexpected response while creating map!");
        }
    }

    private void createCollectionIfAbsent(IStorage storage, CopyElement target) throws CopyServiceException {
        Response response = performCreateCollectionRequest(storage, target);
        switch (response) {
            case SuccessResponse _ -> logger.info(
                    "Collection(id: \"{}\") was successfully created in map(id: \"{}\") on storage(id: \"{}\")!",
                    target.getCollectionId(),
                    target.getMapId(),
                    storage.getId()
            );
            case ErrorResponse errorResponse -> {
                NakshaError nakshaError = errorResponse.getError();
                if (!nakshaError.getCode().equals(NakshaError.COLLECTION_EXISTS)) {
                    throw new CopyServiceException("Problem with creating collection!", new NakshaException(nakshaError));
                }
                logger.info(
                        "Collection(id: \"{}\") is already present in map(id: \"{}\") on storage(id: \"{}\")!",
                        target.getCollectionId(),
                        target.getMapId(),
                        storage.getId()
                );
            }
            default -> throw new CopyServiceException("Unexpected response while creating collection!");
        }
    }

    private Response performCreateMapRequest(IStorage storage, String mapId) throws CopyServiceException {
        WriteRequest createMapRequest = buildCreateMapRequest(mapId);
        return performWriteRequest(storage, createMapRequest);
    }

    private Response performCreateCollectionRequest(IStorage storage, CopyElement target) throws CopyServiceException {
        WriteRequest createCollectionRequest = buildCreateCollectionRequest(target);
        return performWriteRequest(storage, createCollectionRequest);
    }

    private Response performWriteRequest(
            IStorage storage,
            WriteRequest writeRequest
    ) throws CopyServiceException {
        try {
            return storage.useWriteSession(
                    sessionOptions,
                    writer -> {
                        Response r = writer.execute(writeRequest);
                        if (r instanceof SuccessResponse) {
                            writer.commit();
                        } else {
                            writer.rollback();
                        }
                        return r;
                    });
        } catch (Exception e) {
            throw new CopyServiceException("Problem while writing features to target!", e);
        }
    }

    private WriteRequest buildCreateMapRequest(String mapId) {
        WriteRequest writeRequest = new WriteRequest();
        NakshaCatalog map = new NakshaCatalog(mapId);
        Write write = new Write().createMap(map);
        writeRequest.add(write);
        return writeRequest;
    }

    private WriteRequest buildCreateCollectionRequest(CopyElement target) {
        WriteRequest writeRequest = new WriteRequest();
        NakshaCollection collection = new NakshaCollection(target.getCollectionId())
                .withCatalogId(target.getMapId());
        Write write = new Write().createCollection(collection);
        writeRequest.add(write);
        return writeRequest;
    }

    private CopyServiceSuccessResultPayload buildSuccessResultPayload(FeaturesWriteExecutorInfo featuresWriteExecutorInfo) {
        return new CopyServiceSuccessResultPayload(featuresWriteExecutorInfo.numberOfWrittenElements());
    }
}
