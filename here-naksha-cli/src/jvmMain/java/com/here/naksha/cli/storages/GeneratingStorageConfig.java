package com.here.naksha.cli.storages;

import com.here.naksha.cli.validations.Validator;
import com.here.naksha.cli.validations.exceptions.FieldValidationException;
import naksha.base.AnyMap;
import naksha.base.JvmBoxingUtil;
import naksha.model.objects.NakshaProperties;
import naksha.model.objects.NakshaStorage;
import org.jetbrains.annotations.NotNull;

import static com.here.naksha.cli.validations.ValidatorUtils.requireValidArgument;
import static com.here.naksha.cli.validations.ValidatorUtils.requireValidField;
import static com.here.naksha.cli.validations.Validators.canBeBoxed;
import static com.here.naksha.cli.validations.Validators.isNotNull;
import static java.util.Objects.requireNonNull;

public final class GeneratingStorageConfig extends NakshaStorage {
    @Override
    @NotNull
    public GeneratingStorageConfigProperties getProperties() {
        return requireNonNull(JvmBoxingUtil.box(super.getProperties(), GeneratingStorageConfigProperties.class));
    }

    @Override
    public void setProperties(@NotNull NakshaProperties generatingStorageConfigProperties) {
        requireValidArgument(propertiesFieldValidator.validate(generatingStorageConfigProperties));
        super.setProperties(generatingStorageConfigProperties);
    }

    @Override
    @NotNull
    public GeneratingStorageConfig withProperties(@NotNull NakshaProperties generatingStorageConfigProperties) {
        setProperties(generatingStorageConfigProperties);
        return this;
    }

    @Override
    public void onCreation() {
        super.onCreation();
        setRaw(PROPERTIES_KEY, new GeneratingStorageConfigProperties());
    }

    public void validateFields() throws FieldValidationException {
        AnyMap map = requireNonNull(JvmBoxingUtil.box(platformObject(), AnyMap.class));
        validatePropertiesField(map);
    }

    private final Validator<Object, GeneratingStorageConfigProperties> propertiesFieldValidator = isNotNull()
            .and(canBeBoxed(GeneratingStorageConfigProperties.class));

    private void validatePropertiesField(AnyMap map) throws FieldValidationException {
        requireValidField(PROPERTIES_KEY, map, propertiesFieldValidator);
        getProperties().validateFields();
    }
}