package com.here.naksha.cli.copy.service;

import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;

public final class CopyService {
    private final SessionOptions sessionOptions;
    private final StorageProvider storageProvider;

    public CopyService(
            @NotNull StorageProvider storageProvider,
            @Nullable SessionOptions sessionOptions
    ) {
        this.sessionOptions = sessionOptions;
        this.storageProvider = storageProvider;
    }

    public void copy(
            @NotNull CopyElement src,
            @NotNull CopyElement target
    ) throws CopyServiceException {
        List<NakshaFeature> features = readFeaturesFromSrc(src);
        writeFeaturesToTarget(features, target);
    }

    private void writeFeaturesToTarget(
            @NotNull List<NakshaFeature> features,
            CopyElement target
    ) throws CopyServiceException {
        IStorage storage;

        try {
            storage = storageProvider.useStorage(target.getNakshaStorage());
        } catch (Exception e) {
            throw new CopyServiceException("Can not get target storage!", e);
        }

        WriteRequest writeRequest = createFeaturesRequest(
                target.getMapId(),
                target.getCollectionId(),
                features
        );

        Response response = storage.useWriteSession(
                sessionOptions,
                writer -> writer.execute(writeRequest)
        );

        switch (response) {
            case SuccessResponse ignored -> { /*do nothing*/ }
            case ErrorResponse errorResponse -> throw new CopyServiceException(
                    "Problem with writing to target!",
                    new NakshaException(errorResponse.getError())
            );
            default -> throw new CopyServiceException("Unexpected response from target!");
        }
    }

    private List<NakshaFeature> readFeaturesFromSrc(
            CopyElement source
    ) throws CopyServiceException {
        IStorage storage;

        try {
            storage = storageProvider.useStorage(source.getNakshaStorage());
        } catch (Exception e) {
            throw new CopyServiceException("Can not get source storage!", e);
        }

        ReadFeatures readFeatures = createReadFeaturesRequest(source);

        Response response = storage.useReadSession(
                sessionOptions,
                reader -> reader.execute(readFeatures)
        );

        return switch (response) {
            case SuccessResponse successResponse -> extractResponseItems(successResponse, NakshaFeature.class);
            case ErrorResponse errorResponse -> throw new CopyServiceException(
                    "Problem with reading from source!",
                    new NakshaException(errorResponse.getError())
            );
            default -> throw new CopyServiceException("Unexpected response from source!");
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
}
