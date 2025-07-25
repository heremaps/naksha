package com.here.naksha.cli.test_containers;

import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Assertions;

import java.util.List;

import static naksha.model.util.RequestHelper.createFeaturesRequest;
import static naksha.model.util.RequestHelper.createWriteCollectionsRequest;
import static naksha.model.util.ResultHelper.extractResponseItems;

public final class MapController {
    private final IStorage storage;
    private final String mapId;

    public String getMapId() {
        return mapId;
    }

    /**
     * Adds the specified features to the given collection.
     *
     * @param collectionId   The ID of the collection where {@code features} will be created.
     *                       The collection must be created before using {@link #addCollectionToTheMap(String, SessionOptions)}.
     * @param features       The features to be added to the collection.
     * @param sessionOptions Session-specific options for this operation.
     */
    public void addFeatures(String collectionId, List<NakshaFeature> features, SessionOptions sessionOptions) {
        WriteRequest writeRequest = createFeaturesRequest(
                mapId,
                collectionId,
                features
        );

        makeWriteRequest(writeRequest, sessionOptions);
    }

    public void addCollectionToTheMap(String collectionId, SessionOptions sessionOptions) {
        NakshaCollection collection = new NakshaCollection(collectionId, mapId);
        WriteRequest request = createWriteCollectionsRequest(collection);
        makeWriteRequest(request, sessionOptions);
    }

    public List<NakshaFeature> readFeatures(
            String collectionId,
            SessionOptions sessionOptions
    ) {
        ReadFeatures readFeatures = createReadFeaturesRequest(collectionId);

        Response response = storage.useReadSession(
                sessionOptions,
                reader -> reader.execute(readFeatures)
        );

        Assertions.assertInstanceOf(SuccessResponse.class, response);

        return extractResponseItems((SuccessResponse) response, NakshaFeature.class);
    }

    MapController(IStorage storage, String mapId) {
        this.storage = storage;
        this.mapId = mapId;
    }

    private ReadFeatures createReadFeaturesRequest(String collectionId) {
        ReadFeatures readFeatures = new ReadFeatures();
        readFeatures.setCollectionIds(
                StringList.of(collectionId)
        );
        readFeatures.setMapId(mapId);

        return readFeatures;
    }

    private void makeWriteRequest(WriteRequest writeRequest, SessionOptions sessionOptions) {
        storage.useWriteSession(sessionOptions, writer -> {
            Response response = writer.execute(writeRequest);
            Assertions.assertInstanceOf(SuccessResponse.class, response);
            writer.commit();
            return response;
        });
    }
}
