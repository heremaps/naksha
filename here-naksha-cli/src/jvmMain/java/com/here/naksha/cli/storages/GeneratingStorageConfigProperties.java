package com.here.naksha.cli.storages;

import naksha.base.JvmBoxingUtil;
import naksha.base.ListProxy;
import naksha.base.PlatformType;
import naksha.base.StringList;
import naksha.base.StringList;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static naksha.base.Platform.forClass;

public final class GeneratingStorageConfigProperties extends NakshaProperties {
    public static final PlatformType<GeneratingStorageConfigProperties> TYPE = forClass(GeneratingStorageConfigProperties.class);
    private static final String COUNT_KEY = "count";
    private static final String IDS_PREFIX_KEY = "idsPrefix";
    private static final String TILE_IDS_KEY = "tileIds";
    private static final String TILE_IDS_CSV_FILE_PATH_KEY = "tileIdsCsvFile";
    private static final String FEATURE_TEMPLATE_FILE_PATH_KEY = "featureTemplateFile";

    @Nullable
    public Integer getCount() {
        return (Integer) get(COUNT_KEY);
    }

    public void setCount(@Nullable Integer count) {
        set(COUNT_KEY, count);
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

    @NotNull
    public GeneratingStorageConfigProperties withCount(@NotNull Integer count) {
        setCount(count);
        return this;
    }

    public @Nullable StringList getTileIds() {
        return getAs(TILE_IDS_KEY, StringList.TYPE);
    }

    public void setTileIds(@Nullable List<String> tileIds) {
        set(TILE_IDS_KEY, ListProxy.toNullable(StringList.TYPE, tileIds));
    }

    public GeneratingStorageConfigProperties withTileIds(@Nullable List<String> tileIds) {
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
