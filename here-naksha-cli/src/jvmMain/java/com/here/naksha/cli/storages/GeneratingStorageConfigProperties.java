package com.here.naksha.cli.storages;

import naksha.base.ListProxy;
import naksha.base.PlatformType;
import naksha.base.StringList;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static naksha.base.Platform.forClass;

public final class GeneratingStorageConfigProperties extends NakshaProperties {
    public static final PlatformType<GeneratingStorageConfigProperties> TYPE = forClass(GeneratingStorageConfigProperties.class);
    private static final String COUNT_KEY = "count";
    private static final String TILE_IDS_KEY = "tileIds";
    private static final String TILE_IDS_CSV_FILE_PATH_KEY = "tileIdsCsvFilePath";

    public Integer getCount() {
        return (Integer) get(COUNT_KEY);
    }

    public void setCount(Integer count) {
        set(COUNT_KEY, count);
    }

    public GeneratingStorageConfigProperties withCount(Integer count) {
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

    public String getTileIdsCsvFilePath() {
        return (String) getRaw(TILE_IDS_CSV_FILE_PATH_KEY);
    }

    public void setTileIdsCsvFilePath(String path) {
        setRaw(TILE_IDS_CSV_FILE_PATH_KEY, path);
    }

    public GeneratingStorageConfigProperties withTileIdsCsvFilePath(String path) {
        setTileIdsCsvFilePath(path);
        return this;
    }
}
