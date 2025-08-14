package com.here.naksha.app.init;

import com.here.naksha.app.common.TestUtil;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.IoHelp.LoadedBytes;
import java.nio.charset.StandardCharsets;

import naksha.base.Platform;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record TestStorageConfig(@NotNull String mapId, @NotNull NakshaStorage config) {

  public TestStorageConfig(String mapId, NakshaStorage config) {
    this.mapId = mapId;
    this.config = config;
    config.setCreate(true);
  }

  private static @Nullable TestStorageConfig testStorageFromString(@NotNull String mapId, @Nullable String raw) {
    if (raw != null) try {
      if (raw.startsWith("jdbc:postgresql://")) {
        PgConfig pgConfig = new PgConfig(TestUtil.TEST_ADMIN_DB).withMasterUri(raw);
        return new TestStorageConfig(mapId, pgConfig);
      }
      // Or, alternatively, a complete configuration.
      final var pgConfig = Platform.fromJson(raw, PgConfig.TYPE);
      if (pgConfig != null && !pgConfig.getId().isEmpty()) return new TestStorageConfig(mapId, pgConfig);
    } catch (Exception ignore) {
    }
    return null;
  }

  /**
   * Reads the configuration from a configuration file from user home directory ({@code ~/.config/naksha/filename}) or from the environment
   * variable, if none is possible, a docker configuration is used that starts a local docker container on demand.
   *
   * @param filename The filename to search for in {@code ~/.config/naksha/}.
   * @param envName  The environment variable to check.
   * @param mapId    The id of map to use
   * @return the PSQL storage configuration.
   */
  @SuppressWarnings("SameParameterValue")
  public static @NotNull TestStorageConfig configFromFileOrEnv(
      final @NotNull String filename,
      final @NotNull String envName,
      final @NotNull String mapId
  ) {
    TestStorageConfig testStorage;
    String raw;
    try {
      final LoadedBytes loadedBytes = IoHelp.readBytesFromHomeOrResource(filename, true, "naksha");
      final byte[] bytes = loadedBytes.getBytes();
      // Allow to store just a URL
      raw = new String(bytes, StandardCharsets.UTF_8);
      testStorage = testStorageFromString(mapId, raw);
      if (testStorage != null) return testStorage;
    } catch (Exception ignore) {
    }

    raw = System.getenv(envName);
    testStorage = testStorageFromString(mapId, raw);
    if (testStorage != null) return testStorage;

    raw = System.getenv("TEST_NAKSHA_PSQL_URL");
    testStorage = testStorageFromString(mapId, raw);
    if (testStorage != null) return testStorage;

    // Eventually, create a docker container as storage.
    final var pgConfig = new PgConfig(TestUtil.TEST_ADMIN_DB);
    pgConfig.setClassName("naksha.psql.PsqlTestStorage");
    final var user = System.getenv("TEST_NAKSHA_PSQL_USER");
    if (user != null) pgConfig.set("user", user);
    final var password = System.getenv("TEST_NAKSHA_PSQL_PASS");
    if (password != null) pgConfig.set("password", password);
    return new TestStorageConfig(mapId, pgConfig);
  }
}
