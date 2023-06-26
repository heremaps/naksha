/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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

import static com.here.naksha.lib.core.NakshaContext.currentLogger;

import com.here.naksha.lib.core.models.features.StorageCollection;
import com.here.naksha.lib.core.models.geojson.implementation.Feature;
import com.here.naksha.lib.core.storage.IReadTransaction;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Naksha PostgresQL transaction that can be used to read data, optionally using a read-replica, if opened as read-only transaction.
 */
public class PsqlTxReader implements IReadTransaction {

  /**
   * Creates a new transaction for the given PostgresQL client.
   *
   * @param psqlClient the PostgresQL client for which to create a new transaction.
   * @throws SQLException if creation of the reader failed.
   */
  PsqlTxReader(@NotNull PsqlStorage psqlClient) throws SQLException {
    this.psqlClient = psqlClient;
    this.connection = psqlClient.dataSource.getConnection();
    try (final var stmt = connection.createStatement()) {
      stmt.execute("SELECT naksha_tx_init()");
    }
  }

  /**
   * The PostgresQL client to which this transaction is bound.
   */
  protected final @NotNull PsqlStorage psqlClient;

  /**
   * The JDBC connection.
   */
  protected @Nullable Connection connection;

  /**
   * Returns the client to which the transaction is bound.
   *
   * @return the client to which the transaction is bound.
   */
  public @NotNull PsqlStorage getPsqlClient() {
    return psqlClient;
  }

  /**
   * Returns the underlying JDBC connection. Can be used to perform arbitrary queries.
   *
   * @return the underlying JDBC connection.
   * @throws IllegalStateException if the connection is already closed.
   */
  public @NotNull Connection getConnection() {
    final Connection connection = this.connection;
    if (connection == null) {
      throw new IllegalStateException("Connecton closed");
    }
    return connection;
  }

  @Override
  public @NotNull String getTransactionNumber() throws SQLException {
    throw new UnsupportedOperationException("getTransactionNumber");
  }

  @Override
  public @NotNull Iterator<@NotNull StorageCollection> iterateCollections() throws SQLException {
    throw new UnsupportedOperationException("getAllCollections");
  }

  @Override
  public @Nullable StorageCollection getCollectionById(@NotNull String id) throws SQLException {
    throw new UnsupportedOperationException("getCollectionById");
  }

  @Override
  public @NotNull <F extends Feature> PsqlFeatureReader<F> readFeatures(
      @NotNull Class<F> featureClass, @NotNull StorageCollection collection) {
    // TODO: Optimize by tracking the read, no need to create a new instance for every call!
    return new PsqlFeatureReader<>(this, featureClass, collection);
  }

  @Override
  public void close() {
    if (connection != null) {
      try {
        connection.rollback();
      } catch (SQLException e) {
        currentLogger().info("Failed to execute rollback on JDBC connection", e);
      }
      try {
        connection.close();
      } catch (SQLException e) {
        currentLogger().info("Failed to close a JDBC connection", e);
      }
      connection = null;
    }
  }
}
