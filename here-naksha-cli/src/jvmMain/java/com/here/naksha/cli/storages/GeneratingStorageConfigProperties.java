package com.here.naksha.cli.storages;

import naksha.base.JvmBoxingUtil;
import naksha.base.JvmList;
import naksha.base.StringList;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GeneratingStorageConfigProperties extends NakshaProperties {
    private static final String COUNT_KEY = "count";
    private static final String IDS_PREFIX_KEY = "idsPrefix";
    private static final String TILE_IDS_KEY = "tileIds";
    private static final String TILE_IDS_CSV_FILE_PATH_KEY = "tileIdsCsvFile";
    private static final String FEATURE_TEMPLATE_FILE_PATH_KEY = "featureTemplateFile";

    @Nullable
    public Integer getCount() {
        return (Integer) getRaw(COUNT_KEY);
    }

    public void setCount(@Nullable Integer count) {
        setRaw(COUNT_KEY, count);
    }

    @NotNull
    public GeneratingStorageConfigProperties withCount(@NotNull Integer count) {
        setCount(count);
        return this;
    }

    @Nullable
    public String getIdsPrefix() {
        return (String) getRaw(IDS_PREFIX_KEY);
    }

    public void setIdsPrefix(@Nullable String idsPrefix) {
        setRaw(IDS_PREFIX_KEY, idsPrefix);
    }

    @NotNull
    public GeneratingStorageConfigProperties withIdsPrefix(@Nullable String idsPrefix) {
        setIdsPrefix(idsPrefix);
        return this;
    }

    @Nullable
    public StringList getTileIds() {
        JvmList jvmList = (JvmList) getRaw(TILE_IDS_KEY);
        return JvmBoxingUtil.box(jvmList, StringList.class);
    }

    public void setTileIds(@Nullable StringList tileIds) {
        setRaw(TILE_IDS_KEY, tileIds);
    }

    @NotNull
    public GeneratingStorageConfigProperties withTileIds(@Nullable StringList tileIds) {
        setTileIds(tileIds);
        return this;
    }

    @Nullable
    public String getTileIdsCsvFilePath() {
        return (String) getRaw(TILE_IDS_CSV_FILE_PATH_KEY);
    }

    public void setTileIdsCsvFilePath(@Nullable String path) {
        setRaw(TILE_IDS_CSV_FILE_PATH_KEY, path);
    }

    @NotNull
    public GeneratingStorageConfigProperties withTileIdsCsvFilePath(@Nullable String path) {
        setTileIdsCsvFilePath(path);
        return this;
    }

    @Nullable
    public String getFeatureTemplateFilePath() {
        return (String) getRaw(FEATURE_TEMPLATE_FILE_PATH_KEY);
    }

    public void setFeatureTemplateFilePath(@Nullable String path) {
        setRaw(FEATURE_TEMPLATE_FILE_PATH_KEY, path);
    }

    @NotNull
    public GeneratingStorageConfigProperties withFeatureTemplateFilePath(@Nullable String path) {
        setFeatureTemplateFilePath(path);
        return this;
    }
}