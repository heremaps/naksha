package com.here.naksha.cli.storages;

import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import naksha.jbon.JbDictionary;
import naksha.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GeneratingStorage extends AbstractStorage<GeneratingStorageConfig> {
    @NotNull
    @Override
    public KClass<GeneratingStorageConfig> getConfigKlass() {
        return JvmClassMappingKt.getKotlinClass(GeneratingStorageConfig.class);
    }

    @NotNull
    @Override
    public IWriteSession newWriteSession(@Nullable SessionOptions options) {
        throw new NakshaException(NakshaError.UNSUPPORTED_OPERATION, "Read-only storage!");
    }

    @NotNull
    @Override
    public IReadSession newReadSession(@Nullable SessionOptions sessionOptions) {
        return new GeneratingSession(
                this,
                sessionOptions
        );
    }

    @Override
    public int getHardCap() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getEncodingFlags(@Nullable Object feature, @Nullable Object context) {
        return Naksha.DEFAULT_FLAGS;
    }

    @Nullable
    @Override
    public JbDictionary getDictionary(@NotNull String id) {
        return null;
    }

    @Override
    protected void initStorage(
            @NotNull GeneratingStorageConfig storageConfig,
            @Nullable Boolean create,
            @Nullable Boolean upgrade
    ) {
        storageConfig.init();
        getConfigRef().set(storageConfig);
    }

    @Override
    protected void afterInit() {
        // nothing to do
    }

    @Override
    protected void shutdownStorage(boolean dropCache) {
        // nothing to do
    }
}
