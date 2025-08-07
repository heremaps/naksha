package com.here.naksha.cli.storages;

import naksha.base.JvmList;
import naksha.model.objects.NakshaProperties;

public final class GeneratingStorageConfigProperties extends NakshaProperties {
    private static final String COUNT_KEY = "count";
    private static final String TILE_IDS_KEY = "tileIds";
    private static final String TILE_IDS_CSV_FILE_PATH_KEY = "tileIdsCsvFilePath";
    private static final String FEATURE_TEMPLATE_FILE_PATH_KEY = "featureTemplateFilePath";

    public Integer getCount() {
        return (Integer) getRaw(COUNT_KEY);
    }

    public void setCount(Integer count) {
        setRaw(COUNT_KEY, count);
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

    public String getFeatureTemplateFilePath() {
        return (String) getRaw(FEATURE_TEMPLATE_FILE_PATH_KEY);
    }

    public void setFeatureTemplateFilePath(String path) {
        setRaw(FEATURE_TEMPLATE_FILE_PATH_KEY, path);
    }

    public GeneratingStorageConfigProperties withFetureTemplateFilePath(String path) {
        setFeatureTemplateFilePath(path);
        return this;
    }
}