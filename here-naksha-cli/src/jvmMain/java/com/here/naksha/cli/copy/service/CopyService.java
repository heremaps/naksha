package com.here.naksha.cli.copy.service;

import com.here.naksha.cli.results.CommandFailure;
import com.here.naksha.cli.results.CommandResult;
import com.here.naksha.cli.results.CommandSuccess;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;

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
            @NotNull CopyElement target
    ) {
        try {
            List<NakshaFeature> features = readFeaturesFromSrc(src);
            SuccessResponse _ = writeFeaturesToTarget(features, target);
            return new CommandSuccess<>(buildSuccessResultPayload(features));
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

        return switch (response) {
            case SuccessResponse successResponse -> extractResponseItems(successResponse, NakshaFeature.class);
            case ErrorResponse errorResponse -> throw new CopyServiceException(
                    "Problem with reading from source!",
                    new NakshaException(errorResponse.getError())
            );
            default -> throw new CopyServiceException("Unexpected response from source!");
        };
    }

    private SuccessResponse writeFeaturesToTarget(
            List<NakshaFeature> features,
            CopyElement target
    ) throws CopyServiceException {
        IStorage storage = useTargetStorage(target);
        WriteRequest writeRequest = createFeaturesRequest(
                target.getMapId(),
                target.getCollectionId(),
                features
        );
        Response response = performWriteRequest(storage, writeRequest);

        return switch (response) {
            case SuccessResponse successResponse -> successResponse;
            case ErrorResponse errorResponse -> throw new CopyServiceException(
                    "Problem with writing to target!",
                    new NakshaException(errorResponse.getError())
            );
            default -> throw new CopyServiceException("Unexpected response from target!");
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

    private CopyServiceSuccessResultPayload buildSuccessResultPayload(List<NakshaFeature> features) {
        return new CopyServiceSuccessResultPayload(features.size());
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
}
