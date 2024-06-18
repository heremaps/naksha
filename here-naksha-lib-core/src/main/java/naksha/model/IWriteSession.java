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
package naksha.model;

import com.here.naksha.lib.core.NakshaVersion;
import com.here.naksha.lib.core.exceptions.StorageLockException;
import com.here.naksha.lib.core.models.storage.Result;

import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.NotThreadSafe;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * A storage session that can perform changes.
 */
@NotThreadSafe
@AvailableSince(NakshaVersion.v2_0_7)
public interface IWriteSession extends IReadSession {

  /**
   * Execute the given write-request.
   *
   * @param writeRequest the write-request to execute.
   * @return the result.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  Result execute(@NotNull WriteRequest<?, ?, ?> writeRequest);

  /**
   * Execute the given write-request in bulk. It doesn't return inserted/modified rows.
   * Important!
   * - If you want to get the best performance out of this method - execute it
   * in parallel in separate threads where each thread process data only from 1 partition (if collection is partitioned)
   * <br>
   * - even if you use partitioned bulk - use head table name without partition suffix as collectionId
   * <br>
   * - you can call this method with mixed IDs (from different partitions) the bulk will be split into
   * n-bulks (one bulk per partition) and then executed, but it's not recommended to use mixed IDs with
   * parallel execution.
   *
   * @param writeRequest the write-request to execute.
   * @return the result.
   */
  @AvailableSince(NakshaVersion.v2_0_13)
  @NotNull
  Result executeBulkWriteFeatures(@NotNull WriteRequest<?, ?, ?> writeRequest);

  /**
   * Acquire a lock to a specific feature in the HEAD state.
   *
   * <p>Any {@link #commit(boolean)}, {@link #rollback(boolean)} or {@link #close(boolean)}ing will release the lock
   * instantly.
   *
   * @param collectionId the collection in which the feature is stored.
   * @param featureId    the identifier of the feature to lock.
   * @param timeout      the maximum time to wait for the lock.
   * @param timeUnit     the time-unit in which the wait-time was provided.
   * @return the lock.
   * @throws StorageLockException if the locking failed.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  IStorageLock lockFeature(
      @NotNull String collectionId, @NotNull String featureId, long timeout, @NotNull TimeUnit timeUnit)
      throws StorageLockException;

  /**
   * Acquire an advisory lock.
   *
   * <p>Any {@link #commit(boolean)}, {@link #rollback(boolean)} or {@link #close(boolean)}ing will release the lock
   * instantly.
   *
   * @param lockId   the unique identifier of the lock to acquire.
   * @param timeout  the maximum time to wait for the lock.
   * @param timeUnit the time-unit in which the wait-time was provided.
   * @return the lock.
   * @throws StorageLockException if the locking failed.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  IStorageLock lockStorage(@NotNull String lockId, long timeout, @NotNull TimeUnit timeUnit)
      throws StorageLockException;

  /**
   * Commit all changes.
   * <p>
   * Beware setting {@code autoCloseCursors} to {@code true} is often very suboptimal. To keep cursors alive, most of the time the
   * implementation requires to read all results synchronously from all open cursors in an in-memory cache and to close the underlying
   * network resources. This can lead to {@link OutOfMemoryError}'s or other issues. It is strictly recommended to first read from all open
   * cursors before closing, committing or rolling-back a session.
   *
   * @param autoCloseCursors If {@code true}, all open cursors are closed; otherwise all pending cursors are kept alive.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  void commit(boolean autoCloseCursors);

  /**
   * Abort the transaction, revert all pending changes.
   * <p>
   * Beware setting {@code autoCloseCursors} to {@code true} is often very suboptimal. To keep cursors alive, most of the time the
   * implementation requires to read all results synchronously from all open cursors in an in-memory cache and to close the underlying
   * network resources. This can lead to {@link OutOfMemoryError}'s or other issues. It is strictly recommended to first read from all open
   * cursors before closing, committing or rolling-back a session.
   *
   * @param autoCloseCursors If {@code true}, all open cursors are closed; otherwise all pending cursors are kept alive.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  void rollback(boolean autoCloseCursors);

  /**
   * Closes the session and, when necessary invokes {@link #rollback(boolean)}.
   * <p>
   * Beware setting {@code autoCloseCursors} to {@code true} is often very suboptimal. To keep cursors alive, most of the time the
   * implementation requires to read all results synchronously from all open cursors in an in-memory cache and to close the underlying
   * network resources. This can lead to {@link OutOfMemoryError}'s or other issues. It is strictly recommended to first read from all open
   * cursors before closing, committing or rolling-back a session.
   *
   * @param autoCloseCursors If {@code true}, all open cursors are closed; otherwise all pending cursors are kept alive.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  void close(boolean autoCloseCursors);

  /**
   * This basically just invokes {@link #close(boolean) close(true)}.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @Override
  default void close() {
    close(true);
  }
}
