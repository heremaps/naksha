package com.here.naksha.cli.stream.providers;

import kotlin.reflect.KClass;
import naksha.base.JvmBoxingUtil;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.Platform;
import naksha.model.AbstractStorage;
import naksha.model.IReadSession;
import naksha.model.IStorage;
import naksha.model.IWriteSession;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaProperties;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static naksha.base.NakshaExceptionKt.unsupportedOp;

/** A storage that can only open stream sessions, all other sessions throw. */
public abstract class StreamOnlyStorage extends AbstractStorage<NakshaStorage> {

    /** Returns the storage of the given class for the JSON configuration, the same instance for the same configuration. */
    static synchronized @NotNull IStorage use(@NotNull String provider, @NotNull Class<? extends StreamOnlyStorage> storageClass, @NotNull String config) {
        String id = provider + "-" + Integer.toHexString(config.hashCode());
        try {
            return Naksha.useStorageById(id);
        } catch (NakshaException e) {
            if (!NakshaError.STORAGE_NOT_FOUND.equals(e.getError().getCode())) throw e;
        }
        Object properties;
        try {
            properties = Platform.fromJSON(config.isBlank() ? "{}" : config);
        } catch (Exception e) {
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid " + provider + " configuration: " + e.getMessage());
        }
        NakshaStorage storage = new NakshaStorage(id, storageClass.getName());
        storage.setProperties(JvmBoxingUtil.box(properties, NakshaProperties.class));
        return Naksha.useStorage(storage);
    }

    static @NotNull SessionOptions orDefault(@Nullable SessionOptions options) {
        return options != null ? options : SessionOptions.from(NakshaContext.currentContext());
    }

    @Override
    public @NotNull KClass<NakshaStorage> getConfigKlass() {
        return Platform.klassFor(NakshaStorage.class);
    }

    @Override
    public @NotNull IWriteSession newWriteSession(@Nullable SessionOptions options) {
        throw unsupportedOp("newWriteSession");
    }

    @Override
    public @NotNull IReadSession newReadSession(@Nullable SessionOptions options) {
        throw unsupportedOp("newReadSession");
    }

    @Override
    public int getHardCap() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void initStorage(@NotNull NakshaStorage config, @Nullable Boolean create, @Nullable Boolean upgrade) {
        init(config);
    }

    protected void init(@NotNull NakshaStorage config) {
    }

    @Override
    protected void afterInit() {
    }

    @Override
    protected void shutdownStorage(boolean dropCache) {
    }
}
