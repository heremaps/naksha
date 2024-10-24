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

import com.here.naksha.lib.core.storage.IMasterTransaction;

/**
 * A Naksha PostgresQL database transaction that can be used to read and mutate data.
 */
@Deprecated
public abstract class PsqlTxWriter extends PsqlTxReader implements IMasterTransaction {
  //
  //  /**
  //   * Creates a new transaction for the given PostgresQL client.
  //   *
  //   * @param psqlClient the PostgresQL client for which to create a new transaction.
  //   * @param settings   The transaction settings.
  //   * @throws SQLException if creation of the writer failed.
  //   */
  //  PsqlTxWriter(@NotNull PsqlStorage psqlClient, @NotNull ITransactionSettings settings) {
  //    super(psqlClient, settings);
  //  }
  //
  //  @Override
  //  protected boolean naksha_tx_start_write() {
  //    return true;
  //  }
  //
  //  @Override
  //  public @NotNull CollectionInfo createCollection(@NotNull CollectionInfo collection) {
  //    try (final PreparedStatement stmt = preparedStatement("SELECT naksha_collection_upsert(?, ?, ?);")) {
  //      stmt.setString(1, collection.getId());
  //      stmt.setLong(2, collection.getMaxAge());
  //      stmt.setBoolean(3, collection.getHistory());
  //      final ResultSet rs = stmt.executeQuery();
  //      rs.next();
  //      try (final Json json = Json.get()) {
  //        return json.reader(ViewDeserialize.Storage.class)
  //            .forType(CollectionInfo.class)
  //            .readValue(rs.getString(1));
  //      }
  //    } catch (final Throwable t) {
  //      throw unchecked(t);
  //    }
  //  }
  //
  //  @Override
  //  public @NotNull CollectionInfo updateCollection(@NotNull CollectionInfo collection) {
  //    throw new UnsupportedOperationException("updateCollection");
  //  }
  //
  //  @AvailableSince(NakshaVersion.v2_0_0)
  //  public @NotNull CollectionInfo upsertCollection(@NotNull CollectionInfo collection) {
  //    throw new UnsupportedOperationException("updateCollection");
  //  }
  //
  //  @Override
  //  public @NotNull CollectionInfo deleteCollection(@NotNull CollectionInfo collection, long deleteAt) {
  //    throw new UnsupportedOperationException("dropCollection");
  //  }
  //
  //  @Override
  //  @NotNull
  //  public CollectionInfo dropCollection(@NotNull CollectionInfo collection) {
  //    try (final PreparedStatement stmt = preparedStatement("SELECT naksha_collection_drop(?);")) {
  //      stmt.setString(1, collection.getId());
  //      final ResultSet rs = stmt.executeQuery();
  //      rs.next();
  //      try (final var json = Json.get()) {
  //        return json.reader(ViewDeserialize.class)
  //            .forType(CollectionInfo.class)
  //            .readValue(rs.getString(1));
  //      }
  //    } catch (Throwable t) {
  //      throw unchecked(t);
  //    }
  //  }
  //
  //  @Override
  //  public @NotNull CollectionInfo enableHistory(@NotNull CollectionInfo collection) {
  //    try (final PreparedStatement stmt = preparedStatement("SELECT naksha_collection_enable_history(?);")) {
  //      stmt.setString(1, collection.getId());
  //      stmt.executeQuery();
  //      return collection;
  //    } catch (Throwable t) {
  //      throw unchecked(t);
  //    }
  //  }
  //
  //  @Override
  //  public @NotNull CollectionInfo disableHistory(@NotNull CollectionInfo collection) {
  //    try (final PreparedStatement stmt = preparedStatement("SELECT naksha_collection_disable_history(?);")) {
  //      stmt.setString(1, collection.getId());
  //      stmt.executeQuery();
  //      return collection;
  //    } catch (Throwable t) {
  //      throw unchecked(t);
  //    }
  //  }
  //
  //  @SuppressWarnings("rawtypes")
  //  private final ConcurrentHashMap<Class, PsqlFeatureWriter> cachedWriters = new ConcurrentHashMap<>();
  //
  //  @SuppressWarnings("unchecked")
  //  @Override
  //  /* TODO HP_QUERY : Should this function internally decide which type of `collection` to use (for given
  // featureClass)?
  //   * Accepting `collection` as argument has possibility of breaking the behaviour when incorrect value is passed.
  //   */
  //  public @NotNull <F extends XyzFeature> PsqlFeatureWriter<F> writeFeatures(
  //      @NotNull Class<F> featureClass, @NotNull CollectionInfo collection) {
  //    PsqlFeatureWriter<F> writer = cachedWriters.get(featureClass);
  //    if (writer != null) {
  //      return writer;
  //    }
  //    writer = new PsqlFeatureWriter<>(this, featureClass, collection);
  //    final PsqlFeatureWriter<F> existing = cachedWriters.putIfAbsent(featureClass, writer);
  //    if (existing != null) {
  //      return existing;
  //    }
  //    return writer;
  //  }
  //
  //  /**
  //   * Attempts acquiring lock for a given key in database using pg_try_advisory_lock().
  //   *
  //   * @param lockKey     a unique key string to be used for acquiring a lock
  //   * @return            true, if lock could be acquired. false, otherwise.
  //   */
  //  @Override
  //  public boolean acquireLock(final @NotNull String lockKey) {
  //    final String LOCK_SQL = "SELECT pg_try_advisory_lock( ('x' || md5('%s') )::bit(60)::bigint ) AS success";
  //    return _advisory(LOCK_SQL, lockKey);
  //  }
  //
  //  /**
  //   * Attempts releasing lock for a given key in database using pg_advisory_unlock().
  //   *
  //   * @param lockKey     a unique lock key string which is to be released
  //   * @return            true, if lock could be released. false, if there was no lock.
  //   */
  //  @Override
  //  public boolean releaseLock(final @NotNull String lockKey) {
  //    final String UNLOCK_SQL = "SELECT pg_advisory_unlock( ('x' || md5('%s') )::bit(60)::bigint ) AS success";
  //    return _advisory(UNLOCK_SQL, lockKey);
  //  }
  //
  //  private boolean _advisory(final @NotNull String lockStmt, final @NotNull String lockKey) {
  //    boolean success = false;
  //    try (final Statement stmt = createStatement();
  //        final ResultSet rs = stmt.executeQuery(String.format(lockStmt, lockKey)); ) {
  //      if (rs.next()) {
  //        success = rs.getBoolean("success");
  //      }
  //    } catch (final Throwable t) {
  //      throw unchecked(t);
  //    }
  //    return success;
  //  }
  //
  //  /**
  //   * Commit all changes.
  //   *
  //   * @throws SQLException If any error occurred.
  //   */
  //  public void commit() {
  //    try {
  //      conn().commit();
  //    } catch (final Throwable t) {
  //      throw unchecked(t);
  //    } finally {
  //      // start a new transaction, this ensures that the app_id and author are set.
  //      naksha_tx_start();
  //    }
  //  }
  //
  //  /**
  //   * Abort the transaction.
  //   */
  //  public void rollback() {
  //    try {
  //      conn().rollback();
  //    } catch (final Throwable t) {
  //      currentLogger().atWarn("Automatic rollback failed").setCause(t).log();
  //    } finally {
  //      // start a new transaction, this ensures that the app_id and author are set.
  //      naksha_tx_start();
  //    }
  //  }
}
