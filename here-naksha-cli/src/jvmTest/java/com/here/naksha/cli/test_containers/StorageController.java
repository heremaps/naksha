package com.here.naksha.cli.test_containers;

import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaMap;
import naksha.model.objects.NakshaStorage;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.junit.jupiter.api.Assertions;

import java.util.UUID;

public final class StorageController {
    private final IStorage storage;

    public MapController getMapControllerOfUniqueMap(SessionOptions sessionOptions) {
        String mapId = createUniqueMap(sessionOptions);
        return new MapController(storage, mapId);
    }

    public NakshaStorage getNakshaStorage() {
        return storage.getConfig();
    }

    StorageController(IStorage storage) {
        this.storage = storage;
    }

    private String createUniqueMap(SessionOptions sessionOptions) {
        String mapId = UUID.randomUUID().toString();
        addMapToTheStorage(mapId, sessionOptions);
        return mapId;
    }

    private void addMapToTheStorage(String mapId, SessionOptions sessionOptions) {
        WriteRequest writeRequest = new WriteRequest();

        NakshaMap map = new NakshaMap().withId(mapId);
        Write createMap = new Write().createMap(map);
        writeRequest.add(createMap);

        makeWriteRequest(writeRequest, sessionOptions);
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
