package com.here.naksha.cli.copy.service;

import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandResult;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.NakshaError;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaMap;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;

public final class CopyService {
    private final SessionOptions sessionOptions;
    private final StorageProvider storageProvider;

    public CopyService(
            @NotNull StorageProvider storageProvider,
            @NotNull SessionOptions sessionOptions
    ) {
        this.sessionOptions = sessionOptions;
        this.storageProvider = storageProvider;
    }

    @NotNull
    public CommandResult<CopyServiceSuccessResultPayload, CopyServiceException> copy(
            @NotNull CopyElement src,
            @NotNull CopyElement target,
            boolean autoCreateTarget
    ) {
        try {
            List<NakshaFeature> features = readFeaturesFromSrc(src);
            List<String> messages = writeFeaturesToTarget(features, target, autoCreateTarget);
            return new CommandSuccess<>(buildSuccessResultPayload(features, messages));
        } catch (CopyServiceException exception) {
            return new CommandFailure<>(exception);
        }
    }

    private List<NakshaFeature> readFeaturesFromSrc(
            CopyElement source
    ) throws CopyServiceException {
        IStorage storage = useSrcStorage(source);
        ReadFeatures readFeatures = createReadFeaturesRequest(source);
        Response response = performReadRequest(storage, readFeatures);
        SuccessResponse successResponse = requireSourceSuccessResponse(response);
        return extractResponseItems(successResponse, NakshaFeature.class);
    }

    private List<String> writeFeaturesToTarget(
            List<NakshaFeature> features,
            CopyElement target,
            boolean autoCreateTarget
    ) throws CopyServiceException {
        List<String> messages = new ArrayList<>();
        IStorage storage = useTargetStorage(target);
        if (autoCreateTarget) {
            List<String> createTargetMessages = createTarget(storage, target);
            messages.addAll(createTargetMessages);
        }
        Response response = performCreateFeaturesRequest(storage, target, features);
        requireTargetSuccessResponse(response);
        return messages;
    }

    private Response performCreateFeaturesRequest(
            IStorage storage, CopyElement target, List<NakshaFeature> features
    ) throws CopyServiceException {
        WriteRequest addFeaturesRequest = createFeaturesRequest(target.getMapId(), target.getCollectionId(), features);
        return performWriteRequest(storage, addFeaturesRequest);
    }

    private List<String> createTarget(IStorage storage, CopyElement target) throws CopyServiceException {
        String createMapMessage = createMapIfAbsent(storage, target.getMapId());
        String createCollectionMessage = createCollectionIfAbsent(storage, target);
        return List.of(
                createMapMessage,
                createCollectionMessage
        );
    }

    private String createMapIfAbsent(IStorage storage, String mapId) throws CopyServiceException {
        Response response = performCreateMapRequest(storage, mapId);
        return switch (response) {
            case SuccessResponse _ ->
                    "Map(id: \"%s\") was successfully created on storage(id: \"%s\")!".formatted(mapId, storage.getId());
            case ErrorResponse errorResponse -> {
                NakshaError nakshaError = errorResponse.getError();
                if (!nakshaError.getCode().equals(NakshaError.MAP_EXISTS)) {
                    throw new CopyServiceException("Problem with creating map!", new NakshaException(nakshaError));
                }
                yield "Map(id: \"%s\") is already present on storage(id: \"%s\")!".formatted(mapId, storage.getId());
            }
            default -> throw new CopyServiceException("Unexpected response while creating map!");
        };
    }

    private String createCollectionIfAbsent(IStorage storage, CopyElement target) throws CopyServiceException {
        Response response = performCreateCollectionRequest(storage, target);
        return switch (response) {
            case SuccessResponse _ ->
                    "Collection(id: \"%s\") was successfully created in map(id: \"%s\") on storage(id: \"%s\")!"
                            .formatted(target.getCollectionId(), target.getMapId(), storage.getId());
            case ErrorResponse errorResponse -> {
                NakshaError nakshaError = errorResponse.getError();
                if (!nakshaError.getCode().equals(NakshaError.COLLECTION_EXISTS)) {
                    throw new CopyServiceException("Problem with creating collection!", new NakshaException(nakshaError));
                }
                yield "Collection(id: \"%s\") is already present in map(id: \"%s\") on storage(id: \"%s\")!"
                        .formatted(target.getCollectionId(), target.getMapId(), storage.getId());
            }
            default -> throw new CopyServiceException("Unexpected response while creating collection!");
        };
    }

    private Response performCreateMapRequest(IStorage storage, String mapId) throws CopyServiceException {
        WriteRequest createMapRequest = buildCreateMapRequest(mapId);
        return performWriteRequest(storage, createMapRequest);
    }

    private Response performCreateCollectionRequest(IStorage storage, CopyElement target) throws CopyServiceException {
        WriteRequest createCollectionRequest = buildCreateCollectionRequest(target);
        return performWriteRequest(storage, createCollectionRequest);
    }

    private SuccessResponse requireTargetSuccessResponse(Response response) throws CopyServiceException {
        return requireSuccessResponse(
                response,
                "Problem with writing to target!",
                "Unexpected response from target!"
        );
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

    private IStorage useTargetStorage(CopyElement target) throws CopyServiceException {
        try {
            return storageProvider.useStorage(target.getNakshaStorage());
        } catch (Exception e) {
            throw new CopyServiceException("Can not get target storage!", e);
        }
    }

    private Response performWriteRequest(IStorage storage, WriteRequest writeRequest) throws CopyServiceException {
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

    private CopyServiceSuccessResultPayload buildSuccessResultPayload(List<NakshaFeature> features, List<String> messages) {
        return new CopyServiceSuccessResultPayload(features.size(), messages);
    }

    private IStorage useSrcStorage(CopyElement source) throws CopyServiceException {
        try {
            return storageProvider.useStorage(source.getNakshaStorage());
        } catch (Exception e) {
            throw new CopyServiceException("Can not get source storage!", e);
        }
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

    private WriteRequest buildCreateMapRequest(String mapId) {
        WriteRequest writeRequest = new WriteRequest();
        NakshaMap map = new NakshaMap(mapId);
        Write write = new Write().createMap(map);
        writeRequest.add(write);
        return writeRequest;
    }

    private WriteRequest buildCreateCollectionRequest(CopyElement target) {
        WriteRequest writeRequest = new WriteRequest();
        NakshaCollection collection = new NakshaCollection(target.getCollectionId())
                .withMapId(target.getMapId());
        Write write = new Write().createCollection(collection);
        writeRequest.add(write);
        return writeRequest;
    }
}
