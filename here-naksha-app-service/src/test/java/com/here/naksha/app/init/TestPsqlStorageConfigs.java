package com.here.naksha.app.init;

import static com.here.naksha.app.init.TestPsqlConfig.configFromFileOrEnv;
import org.jetbrains.annotations.NotNull;

// TODO CASL-834: replace these with StoragConfig once it is provided
public class TestPsqlStorageConfigs {
  private TestPsqlStorageConfigs(){}

  public static final @NotNull TestPsqlConfig adminDbConfig =
      configFromFileOrEnv("test_admin_db.url", "NAKSHA_TEST_ADMIN_DB_URL", "naksha_admin_schema");

  public static final @NotNull TestPsqlConfig dataDbConfig =
      configFromFileOrEnv("test_data_db.url", "NAKSHA_TEST_DATA_DB_URL", "naksha_data_schema");
}
