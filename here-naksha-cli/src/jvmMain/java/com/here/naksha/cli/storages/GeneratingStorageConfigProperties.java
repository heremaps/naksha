package com.here.naksha.cli.storages;

import naksha.base.PlatformType;
import naksha.model.objects.NakshaProperties;

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

    public JvmList getTileIds() {
        return (JvmList) getRaw(TILE_IDS_KEY);
    }

    public void setTileIds(JvmList tileIds) {
        setRaw(TILE_IDS_KEY, tileIds);
    }

    public GeneratingStorageConfigProperties withTileIds(JvmList tileIds) {
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
