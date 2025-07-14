package com.here.naksha.cli.copy.service;

import naksha.base.StringList;
import naksha.model.NakshaException;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;

public class CopyService {
    private final CopyElement srcCopyElement;
    private final CopyElement targetCopyElement;
    private final SessionOptions sessionOptions;

    public CopyService(
            @NotNull CopyElement src,
            @NotNull CopyElement target,
            @Nullable SessionOptions sessionOptions
    ) {
        srcCopyElement = src;
        targetCopyElement = target;
        this.sessionOptions = sessionOptions;
    }

    public void copy() throws CopyServiceException {
        List<NakshaFeature> features = readFeaturesFromSrc();
        writeFeaturesToTarget(features);
    }

    private void writeFeaturesToTarget(@NotNull List<NakshaFeature> features) throws CopyServiceException {
        WriteRequest writeRequest = createFeaturesRequest(
                targetCopyElement.getMapId(),
                targetCopyElement.getCollectionId(),
                features
        );

        Response response = targetCopyElement.getStorage().useWriteSession(
                sessionOptions,
                writer -> writer.execute(writeRequest)
        );

        if (response instanceof ErrorResponse errorResponse) {
            throw new CopyServiceException(
                    "Problem with writing to target!",
                    new NakshaException(errorResponse.getError())
            );
        }
    }

    private List<NakshaFeature> readFeaturesFromSrc() throws CopyServiceException {
        ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.setCollectionIds(
                StringList.of(srcCopyElement.getCollectionId())
        );
        readFeatures.setMapId(srcCopyElement.getMapId());

        Response response = srcCopyElement.getStorage().useReadSession(
                sessionOptions,
                reader -> reader.execute(readFeatures)
        );

        return switch (response) {
            case SuccessResponse successResponse -> extractResponseItems(successResponse, NakshaFeature.class);
            case ErrorResponse errorResponse -> throw new CopyServiceException(
                    "Problem with reading from source!",
                    new NakshaException(errorResponse.getError())
            );
            default -> throw new IllegalStateException("Unexpected value: " + response);
        };
    }

}
