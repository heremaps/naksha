package com.here.naksha.app.init;

import static com.here.naksha.lib.psql.PsqlStorageConfig.configFromFileOrEnv;

import com.here.naksha.lib.psql.PsqlStorageConfig;
import org.jetbrains.annotations.NotNull;

// TODO (Jakub): next step from here:
public class TestPsqlStorageConfigs {
  private TestPsqlStorageConfigs(){}

  public static final @NotNull PsqlStorageConfig adminDbConfig =
      configFromFileOrEnv("test_admin_db.url", "NAKSHA_TEST_ADMIN_DB_URL", "naksha_admin_schema");

  public static final @NotNull PsqlStorageConfig dataDbConfig =
      configFromFileOrEnv("test_data_db.url", "NAKSHA_TEST_DATA_DB_URL", "naksha_data_schema");
}
