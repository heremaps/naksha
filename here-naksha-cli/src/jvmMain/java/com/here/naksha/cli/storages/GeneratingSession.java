package com.here.naksha.cli.storages;

import com.here.naksha.cli.validations.exceptions.FieldValidationException;
import naksha.model.*;
import naksha.model.objects.NakshaCollection;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaMap;
import naksha.model.request.FeatureTuple;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class GeneratingSession implements IReadSession {
    private final GeneratingStorage storage;
    private final SessionOptions sessionOptions;

    GeneratingSession(
            @NotNull GeneratingStorage storage,
            @NotNull SessionOptions sessionOptions
    ) {
        this.storage = storage;
        this.sessionOptions = sessionOptions;
    }

    @NotNull
    @Override
    public Response execute(@NotNull Request request) {
        GeneratingStorageService service = storage.getService();
        GeneratingStorageConfig config = storage.getConfig();
        requireValidConfig(config);
        List<NakshaFeature> generatedFeatures = service.generateFeatures(config.getProperties());
        return new SuccessResponse(generatedFeatures);
    }

    @NotNull
    @Override
    public Response executeParallel(@NotNull Request request) {
        return execute(request);
    }

    @NotNull
    @Override
    public IStorage getStorage() {
        return storage;
    }

    @NotNull
    @Override
    public SessionOptions getOptions() {
        return sessionOptions;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() {
        // nothing to do
    }

    @Override
    public int getSocketTimeout() {
        return 0;
    }

    @Override
    public void setSocketTimeout(int i) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Override
    public int getStmtTimeout() {
        return 0;
    }

    @Override
    public void setStmtTimeout(int i) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Override
    public int getLockTimeout() {
        return 0;
    }

    @Override
    public void setLockTimeout(int i) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Nullable
    @Override
    public NakshaMap getMapById(@NotNull String mapId) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Nullable
    @Override
    public NakshaMap getMapByNumber(int mapNumber) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Nullable
    @Override
    public NakshaCollection getCollectionById(@NotNull NakshaMap map, @NotNull String collectionId) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Nullable
    @Override
    public NakshaCollection getCollectionByNumber(@NotNull NakshaMap map, int collectionNumber) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Override
    public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    @Override
    public void loadTuples(@NotNull List<? extends FeatureTuple> featureTuples, int from, int to, int mode) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "");
    }

    private void requireValidConfig(GeneratingStorageConfig config) {
        try {
            config.validateFields();
        } catch (FieldValidationException e) {
            throw new NakshaException(NakshaError.EXCEPTION, "Generating storage config is invalid!", e);
        }
    }
}
