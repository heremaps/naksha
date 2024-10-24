/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package com.here.naksha.lib.psql;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.lambdas.Fe1;
import com.here.naksha.lib.core.models.geojson.implementation.XyzProperties;
import com.here.naksha.lib.core.models.naksha.Storage;
import com.here.naksha.lib.core.storage.IStorage;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.core.util.json.Json;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Naksha PostgresQL storage represents a cluster of Postgres instances that are logically treated as a single Naksha storage. This
 * client does implement low level access to manage collections and the features within these collections. It as well grants access to
 * transactions.
 *
 * <p>If you serialize the storage, you effectively get back the {@link PsqlStorageProperties}.
 */
@SuppressWarnings({"unused", "SqlResolve"})
public final class PsqlStorage implements IStorage, DataSource {

  /**
   * The storage-id of the Naksha-Hub admin storage.
   */
  public static final String ADMIN_STORAGE_ID = "naksha-admin";

  private static final Logger log = LoggerFactory.getLogger(PsqlStorage.class);
  private static final PsqlStorageSlf4jLogWriter DEFAULT_SLF4J_LOG_WRITER = new PsqlStorageSlf4jLogWriter();

  /**
   * Attempts to establish a connection with the master node (mutable) that this DataSource object represents. This connection is
   * initialized for the storage and uses the {@link NakshaContext} attached to the thread invoking this method.
   *
   * @return The PostgresQL connection.
   * @throws SQLException If establishing the connection failed.
   */
  @Override
  public @NotNull PsqlConnection getConnection() throws SQLException {
    return storage().getConnection(true, false, true, NakshaContext.currentContext());
  }

  @Deprecated
  @Override
  public @Nullable Connection getConnection(String username, String password) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public @NotNull PrintWriter getLogWriter() {
    return logWriter;
  }

  @Override
  public void setLogWriter(@NotNull PrintWriter out) {
    logWriter = out;
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    throw new SQLException("The interface " + iface.getName() + " is not wrapped by PsqlInstance");
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  private @NotNull PrintWriter logWriter = DEFAULT_SLF4J_LOG_WRITER;

  public static long MIN_CONN_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(1);
  public static long DEFAULT_CONN_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);

  public static long MIN_STMT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(2);
  public static long DEFAULT_STMT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);

  public static long MIN_LOCK_TIMEOUT_MILLIS = 100;
  public static long DEFAULT_LOCK_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(1);

  private static long value(@Nullable Long value, long minValue, long defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    return Math.max(value, minValue);
  }

  private static @NotNull PsqlStorageProperties p(@NotNull Storage storage) {
    XyzProperties raw = storage.getProperties();
    if (raw instanceof PsqlStorageProperties) {
      return (PsqlStorageProperties) raw;
    }
    try (final Json jp = Json.get()) {
      final PsqlStorageProperties props = jp.convert(raw, PsqlStorageProperties.class);
      storage.setProperties(props);
      return props;
    }
  }

  /**
   * The constructor to create a new PostgresQL storage from a JDBC URL. The URL should have the following format:
   * <pre>{@code
   * jdbc:postgresql://{HOST}[:{PORT}]/{DB}
   *   ?user={USER}
   *   &password={PASSWORD}
   *   &id={STORAGE-ID}
   *   &schema={SCHEMA}
   *   &app={APPLICATION-NAME}
   *   [&readOnly[=true|false]]
   * }</pre>.
   *
   * @param url The JDBC URL that configures the storage.
   */
  public PsqlStorage(@NotNull String url) {
    this(new PsqlStorageConfig(url));
  }

  /**
   * The constructor to create a new PostgresQL storage from a URL configuration. The URL should have the following format:
   * <pre>{@code
   * jdbc:postgresql://{HOST}[:{PORT}]/{DB}
   *   ?user={USER}
   *   &password={PASSWORD}
   *   &id={STORAGE-ID}
   *   &schema={SCHEMA}
   *   &app={APPLICATION-NAME}
   *   [&readOnly[=true|false]]
   * }</pre>.
   *
   * @param config The URL configuration.
   */
  public PsqlStorage(@NotNull PsqlStorageConfig config) {
    this(config.storageId(), config.appName(), config.schema(), config.master(), null, null, null, null, null);
  }

  /**
   * The constructor to create a new PostgresQL storage from a specified storageId, appName and JDBC URL. Note that storageId and appName
   * will take precedence so url's query entries 'id' and 'app' won't be considered. The URL should have the following format:
   * <pre>{@code
   * jdbc:postgresql://{HOST}[:{PORT}]/{DB}
   *   ?user={USER}
   *   &password={PASSWORD}
   *   &schema={SCHEMA}
   *   [&readOnly[=true|false]]
   * }</pre>.
   *
   * @param storageId The storage-id to use.
   * @param appName   The application-name to use when connecting to the storage.
   * @param url       The JDBC URL that configures the rest of the storage.
   */
  public PsqlStorage(@NotNull String storageId, @NotNull String appName, @NotNull String url) {
    this(storageId, appName, new PsqlStorageConfig(url));
  }

  /**
   * The constructor to create a new PostgresQL storage from a specified storageId, appName and configuration object. Note that storageId
   * and appName will take precedence so URLs query entries 'id' and 'app' won't be considered. The URL should have the following format:
   * <pre>{@code
   * jdbc:postgresql://{HOST}[:{PORT}]/{DB}
   *   ?user={USER}
   *   &password={PASSWORD}
   *   &schema={SCHEMA}
   *   [&readOnly[=true|false]]
   * }</pre>.
   *
   * @param storageId The storage-id to use.
   * @param appName   The application-name to use when connecting to the storage.
   * @param config    The configuration of the storage.
   * @deprecated This method does not make much sense, because the URL need to contain the <b>id</b> and <b>appName</b>.
   */
  @Deprecated
  public PsqlStorage(@NotNull String storageId, @NotNull String appName, @NotNull PsqlStorageConfig config) {
    this(storageId, appName, config.schema(), config.master(), null, null, null, null, null);
  }

  /**
   * The constructor to create a new PostgresQL storage from a Naksha {@link Storage storage configuration}.
   *
   * @param storage the storage configuration to use.
   */
  public PsqlStorage(@NotNull Storage storage) {
    this(
        storage.getId(),
        p(storage).appName,
        p(storage).schema,
        p(storage).master,
        p(storage).reader,
        p(storage).connectTimeout == null ? null : SECONDS.toMillis(p(storage).connectTimeout),
        p(storage).stmtTimeout == null ? null : SECONDS.toMillis(p(storage).stmtTimeout),
        p(storage).lockTimeout == null ? null : SECONDS.toMillis(p(storage).lockTimeout),
        null);
  }

  /**
   * Create a new simple PostgresQL storage.
   *
   * @param storageId    The storage identifier, this must match with the storage identifier in the database, if already initialized.
   * @param masterConfig The configuration of the master instance.
   * @param schema       The database schema to use.
   * @param appName      The application name to set, when connecting to the database.
   */
  public PsqlStorage(
      @NotNull String storageId,
      @NotNull String appName,
      @NotNull String schema,
      @Nullable PsqlInstanceConfig masterConfig) {
    this(storageId, appName, schema, masterConfig, null, null, null, null, null);
  }

  /**
   * Create a new PostgresQL configuration.
   *
   * @param storageId     The storage identifier, this must match with the storage identifier in the database, if already initialized.
   * @param masterConfig  The configuration of the master instance.
   * @param readerConfigs The optional reader configurations.
   * @param schema        The database schema to use.
   * @param appName       The application name to set, when connecting to the database.
   * @param connTimeout   The default connection timeout in milliseconds.
   * @param stmtTimeout   The default statement timeout in milliseconds.
   * @param lockTimeout   The default lock timeout in milliseconds.
   * @param logLevel      The log-level for debugging; if any.
   */
  @JsonCreator
  public PsqlStorage(
      @JsonProperty("id") @NotNull String storageId,
      @JsonProperty("appName") @NotNull String appName,
      @JsonProperty("schema") @NotNull String schema,
      @JsonProperty("master") @Nullable PsqlInstanceConfig masterConfig,
      @JsonProperty("reader") @Nullable List<@NotNull PsqlInstanceConfig> readerConfigs,
      @JsonProperty("connTimeout") @Nullable Long connTimeout,
      @JsonProperty("stmtTimeout") @Nullable Long stmtTimeout,
      @JsonProperty("lockTimeout") @Nullable Long lockTimeout,
      @JsonProperty("logLevel") @Nullable EPsqlLogLevel logLevel) {
    this.storage = new PostgresStorage(
        this,
        storageId,
        appName,
        schema,
        masterConfig,
        readerConfigs,
        connTimeout,
        stmtTimeout,
        lockTimeout,
        logLevel);
  }

  /**
   * The implementation.
   */
  private final @NotNull PostgresStorage storage;

  @NotNull
  PostgresStorage storage() {
    return storage.assertNotClosed();
  }

  @JsonGetter("schema")
  public String getSchema() {
    return storage().getSchema();
  }

  @JsonSetter("schema")
  public void setSchema(@NotNull String schema) {
    storage().setSchema(schema);
  }

  public @NotNull PsqlStorage withSchema(@NotNull String schema) {
    setSchema(schema);
    return this;
  }

  @JsonGetter("appName")
  public @NotNull String getAppName() {
    return storage().getAppName();
  }

  @JsonSetter("appName")
  public void setAppName(@NotNull String appName) {
    storage().setAppName(appName);
  }

  public @NotNull PsqlStorage withAppName(@NotNull String appName) {
    setAppName(appName);
    return this;
  }

  @JsonGetter("connTimeout")
  public long getConnTimeoutInMillis() {
    return storage.getConnTimeout(MILLISECONDS);
  }

  @JsonSetter("connTimeout")
  public void setConnTimeoutInMillis(long connTimeout) {
    setConnTimeout(connTimeout, MILLISECONDS);
  }

  public long getConnTimeout(@NotNull TimeUnit timeUnit) {
    return storage().getConnTimeout(timeUnit);
  }

  public void setConnTimeout(long connTimeout, @NotNull TimeUnit timeUnit) {
    storage().setConnTimeout(connTimeout, timeUnit);
  }

  public @NotNull PsqlStorage withConnTimeout(long connTimeout, @NotNull TimeUnit timeUnit) {
    setConnTimeout(connTimeout, timeUnit);
    return this;
  }

  @JsonIgnore
  public long getSocketTimeout(@NotNull TimeUnit timeUnit) {
    return storage().getSocketTimeout(timeUnit);
  }

  @JsonIgnore
  public void setSocketTimeout(long timeout, @NotNull TimeUnit timeUnit) {
    storage().setSocketTimeout(timeout, timeUnit);
  }

  @JsonGetter("stmtTimeout")
  public long getStatementTimeoutInMillis() {
    return storage().getStatementTimeout(MILLISECONDS);
  }

  @JsonSetter("stmtTimeout")
  public void setStatementTimeoutInMillis(long stmtTimeout) {
    storage().setStatementTimeout(stmtTimeout, MILLISECONDS);
  }

  public long getStatementTimeout(@NotNull TimeUnit timeUnit) {
    return storage().getStatementTimeout(timeUnit);
  }

  public void setStatementTimeout(long stmtTimeout, @NotNull TimeUnit timeUnit) {
    storage().setStatementTimeout(stmtTimeout, timeUnit);
  }

  public @NotNull PsqlStorage withStatementTimeout(long stmtTimeout, @NotNull TimeUnit timeUnit) {
    setStatementTimeout(stmtTimeout, timeUnit);
    return this;
  }

  @JsonGetter("lockTimeout")
  public long getLockTimeoutInMillis() {
    return storage().getLockTimeout(MILLISECONDS);
  }

  @JsonSetter("lockTimeout")
  public void setLockTimeoutInMillis(long lockTimeout) {
    storage().setLockTimeout(lockTimeout, MILLISECONDS);
  }

  public long getLockTimeout(@NotNull TimeUnit timeUnit) {
    return storage().getLockTimeout(timeUnit);
  }

  public void setLockTimeout(long lockTimeout, @NotNull TimeUnit timeUnit) {
    storage().setLockTimeout(lockTimeout, timeUnit);
  }

  public @NotNull PsqlStorage withLockTimeout(long lockTimeout, @NotNull TimeUnit timeUnit) {
    setLockTimeout(lockTimeout, timeUnit);
    return this;
  }

  @JsonGetter("logLevel")
  public @NotNull EPsqlLogLevel getLogLevel() {
    return storage().getLogLevel();
  }

  @JsonSetter("logLevel")
  public void setLogLevel(@Nullable EPsqlLogLevel logLevel) {
    storage().setLogLevel(logLevel);
  }

  public @NotNull PsqlStorage withLogLevel(@Nullable EPsqlLogLevel logLevel) {
    setLogLevel(logLevel);
    return this;
  }

  /**
   * Returns the storage identifier.
   *
   * @return the storage identifier.
   */
  @JsonIgnore
  @AvailableSince(NakshaVersion.v2_0_7)
  public @NotNull String getStorageId() {
    return storage().storageId;
  }

  @Override
  public void startMaintainer() {}

  @Override
  public void maintainNow() {}

  @Override
  public void stopMaintainer() {}

  /**
   * The Parameters map that is expected as parameter to {@link #initStorage(Map)}.
   */
  public static class Params extends HashMap<String, Object> {

    /**
     * Create empty default parameters.
     */
    public Params() {}

    /**
     * Create parameters from an arbitrary foreign map.
     *
     * @param otherMap The foreign map to import.
     */
    public Params(@Nullable Map<String, Object> otherMap) {
      if (otherMap != null) {
        this.putAll(otherMap);
      }
    }

    /**
     * Enable or disable {@code pg_hint_plan} extension installation.
     *
     * @param enable If {@code pg_hint_plan} should be enabled.
     * @return this.
     */
    public @NotNull Params pg_hint_plan(boolean enable) {
      put("pg_hint_plan", enable);
      return this;
    }

    /**
     * Tests whether the {@code pg_hint_plan} PostgresQL extension should be installed.
     *
     * @return {@code true} to install {@code pg_hint_plan}; {@code false} otherwise.
     */
    public boolean pg_hint_plan() {
      Object raw = get("pg_hint_plan");
      if (raw instanceof Boolean) {
        return (Boolean) raw;
      }
      return true;
    }

    /**
     * Enable or disable {@code pg_stat_statements} extension installation.
     *
     * @param enable If {@code pg_stat_statements} should be enabled.
     * @return this.
     */
    public @NotNull Params pg_stat_statements(boolean enable) {
      put("pg_stat_statements", enable);
      return this;
    }

    /**
     * Tests whether the {@code pg_stat_statements} PostgresQL extension should be installed.
     *
     * @return {@code true} to install {@code pg_stat_statements}; {@code false} otherwise.
     */
    public boolean pg_stat_statements() {
      Object raw = get("pg_stat_statements");
      if (raw instanceof Boolean) {
        return (Boolean) raw;
      }
      return true;
    }
  }

  @Override
  public void initStorage() {
    initStorage(null);
  }

  /**
   * Ensure that the administration tables exists, and the Naksha extension script installed in the latest version.
   *
   * @param params Parameters special to PostgresQL.
   * @throws SQLException If any error occurred while accessing the database.
   * @throws IOException  If reading the SQL extensions from the resources fail.
   */
  @Override
  public void initStorage(@Nullable Map<String, Object> params) {
    final Params p;
    if (params instanceof Params) {
      p = (Params) params;
    } else {
      p = new Params(params);
    }
    storage().initStorage(p, getIoHelp());
  }

  private @Nullable IoHelp ioHelp;

  /**
   * Returns the IO helper class to be used to load the resources.
   *
   * @return the IO helper class to be used to load the resources.
   */
  public @NotNull IoHelp getIoHelp() {
    return ioHelp != null ? ioHelp : IoHelp.defaultInstance;
  }

  /**
   * Sets the IO helper class to be used to load the resources.
   *
   * @param ioHelp The IO helper class to be used to load the resources.
   * @return the previously set IO helper.
   */
  public @Nullable IoHelp setIoHelp(@Nullable IoHelp ioHelp) {
    final IoHelp old = this.ioHelp;
    this.ioHelp = ioHelp;
    return old;
  }

  /**
   * Sets the IO helper class to be used to load the resources.
   *
   * @param ioHelp The IO helper class to be used to load the resources.
   * @return this.
   */
  public @NotNull PsqlStorage withIoHelp(@Nullable IoHelp ioHelp) {
    setIoHelp(ioHelp);
    return this;
  }

  /**
   * Drop the schema to which the storage is configured.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  public void dropSchema() {
    storage().dropSchema();
  }

  @Override
  public @NotNull PsqlWriteSession newWriteSession(@Nullable NakshaContext context, boolean useMaster) {
    return storage().newWriteSession(context, useMaster);
  }

  @Override
  public @NotNull PsqlReadSession newReadSession(@Nullable NakshaContext context, boolean useMaster) {
    return storage().newReadSession(context, useMaster);
  }

  @Override
  public @NotNull <T> Future<T> shutdown(@Nullable Fe1<T, IStorage> onShutdown) {
    return new PsqlShutdownTask<>(this, onShutdown, null, NakshaContext.currentContext()).start();
  }
}
