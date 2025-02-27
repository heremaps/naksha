package com.here.naksha.app.init;

import com.here.naksha.app.service.NakshaApp;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.IoHelp.LoadedBytes;
import java.nio.charset.StandardCharsets;
import naksha.model.NakshaVersion;
import naksha.psql.PgConfig;
import org.jetbrains.annotations.NotNull;

public record TestStorageConfig(String mapId, PgConfig pgConfig) {

  public TestStorageConfig(String mapId, PgConfig pgConfig) {
    this.mapId = mapId;
    this.pgConfig = pgConfig;
    pgConfig.setCreate(true);
  }

  /**
   * Reads the configuration from a configuration file from user home directory ({@code ~/.config/naksha/filename}) or from the environment
   * variable, if none is possible, a default localhost configuration is used.
   *
   * @param filename The filename to search for in {@code ~/.config/naksha/}.
   * @param envName  The environment variable to check.
   * @param mapId    The id of map to use
   * @return the PSQL storage configuration.
   */
  @SuppressWarnings("SameParameterValue")
  public static @NotNull TestStorageConfig configFromFileOrEnv(
      @NotNull String filename, @NotNull String envName, @NotNull String mapId) {
    try {
      final LoadedBytes loadedBytes = IoHelp.readBytesFromHomeOrResource(filename, true, "naksha");
      final byte[] bytes = loadedBytes.getBytes();
      String url = new String(bytes, StandardCharsets.UTF_8);
      if (url.startsWith("jdbc:postgresql://")) {
        PgConfig pgConfig = new PgConfig(NakshaApp.HUB_ADMIN_STORAGE_ID).withMasterUri(url);
        return new TestStorageConfig(mapId, pgConfig);
      }
    } catch (Exception ignore) {
    }
    String url = System.getenv(envName);
    if (url != null && url.startsWith("jdbc:postgresql://")) {
      PgConfig pgConfig = new PgConfig(NakshaApp.HUB_ADMIN_STORAGE_ID).withMasterUri(url);
      return new TestStorageConfig(mapId, pgConfig);
    }
    url = System.getenv("TEST_NAKSHA_PSQL_URL");
    if (url != null && url.startsWith("jdbc:postgresql://")) {
      PgConfig pgConfig = new PgConfig(NakshaApp.HUB_ADMIN_STORAGE_ID).withMasterUri(url);
      return new TestStorageConfig(mapId, pgConfig);
    }

    String password = System.getenv("TEST_NAKSHA_PSQL_PASS");
    if (password == null || password.isBlank()) {
      password = "password";
    }
    url = "jdbc:postgresql://localhost:5432/postgres?user=postgres&password=" + password
          + "&schema=" + mapId
          + "&app=" + "Naksha/v" + NakshaVersion.latest;
    PgConfig pgConfig = new PgConfig(NakshaApp.HUB_ADMIN_STORAGE_ID).withMasterUri(url);
    return new TestStorageConfig(mapId, pgConfig);
  }

}
