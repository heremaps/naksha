package com.here.naksha.cli.storages;

import com.here.naksha.cli.validations.Validator;
import com.here.naksha.cli.validations.exceptions.FieldValidationException;
import naksha.base.*;
import naksha.model.objects.NakshaProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.here.naksha.cli.validations.ValidatorUtils.requireValidArgument;
import static com.here.naksha.cli.validations.ValidatorUtils.requireValidField;
import static com.here.naksha.cli.validations.Validators.*;
import static java.util.Objects.requireNonNull;

public final class GeneratingStorageConfigProperties extends NakshaProperties {
    public static final String COUNT_KEY = "count";
    public static final String IDS_PREFIX_KEY = "idsPrefix";
    public static final String TILE_IDS_KEY = "tileIds";
    public static final String TILE_IDS_CSV_FILE_PATH_KEY = "tileIdsCsvFile";
    public static final String FEATURE_TEMPLATE_FILE_PATH_KEY = "featureTemplateFile";

    @Override
    public void onCreation() {
        super.onCreation();
        setRaw(COUNT_KEY, 0);
    }

    public int getCount() {
        return (int) requireNonNull(getRaw(COUNT_KEY));
    }

    public void setCount(int count) {
        requireValidArgument(countFieldValidator.validate(count));
        setRaw(COUNT_KEY, count);
    }

    @NotNull
    public GeneratingStorageConfigProperties withCount(int count) {
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
        PlatformList platformList = (PlatformList) getRaw(TILE_IDS_KEY);
        return JvmBoxingUtil.box(platformList, StringList.class);
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

    public void validateFields() throws FieldValidationException {
        AnyMap map = requireNonNull(JvmBoxingUtil.box(platformObject(), AnyMap.class));
        validateCountField(map);
        validateTileIdsField(map);
        requireStringOrNull(IDS_PREFIX_KEY, map);
        requireStringOrNull(TILE_IDS_CSV_FILE_PATH_KEY, map);
        requireStringOrNull(FEATURE_TEMPLATE_FILE_PATH_KEY, map);
    }

    private void requireStringOrNull(String key, AnyMap map) throws FieldValidationException {
        Validator<Object, String> validator = isNotNull()
                .and(isInstanceOf(String.class))
                .or(isNull());
        requireValidField(key, map, validator);
    }

    private final Validator<Object, Integer> countFieldValidator = isNotNull()
            .and(isInstanceOf(Integer.class))
            .and(fulfillPredicate(count -> count >= 0, "The count should be >= 0, but %s is provided."::formatted));

    private void validateTileIdsField(AnyMap map) throws FieldValidationException {
        Validator<Object, StringList> validator = isNotNull()
                .and(canBeBoxed(AnyList.class))
                .and(allElements(isInstanceOf(String.class)))
                .and(canBeBoxed(StringList.class))
                .or(isNull());
        requireValidField(TILE_IDS_KEY, map, validator);
    }

    private void validateCountField(AnyMap map) throws FieldValidationException {
        requireValidField(COUNT_KEY, map, countFieldValidator);
    }
}