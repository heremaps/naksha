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
import com.here.naksha.lib.core.models.storage.Notification;
import com.here.naksha.lib.core.models.storage.Result;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.NotThreadSafe;
import org.jetbrains.annotations.ApiStatus.AvailableSince;
import org.jetbrains.annotations.NotNull;

/**
 * A storage session that can only read. Each session is backed by a single storage connection with a single transaction.
 */
@NotThreadSafe
@AvailableSince(NakshaVersion.v2_0_7)
public interface IReadSession extends ISession {

  /**
   * Tests whether this session is connected to the master-node.
   *
   * @return {@code true}, if this session is connected to the master-node; {@code false} otherwise.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  boolean isMasterConnect();

  /**
   * Returns the Naksha context bound to this read-connection.
   *
   * @return the Naksha context bound to this read-connection.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  NakshaContext getNakshaContext();

  /**
   * Returns the amount of features to fetch at ones.
   *
   * @return the amount of features to fetch at ones.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  int getFetchSize();

  /**
   * Changes the amount of features to fetch at ones.
   *
   * @param size The amount of features to fetch at ones.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  void setFetchSize(int size);

  /**
   * Returns the statement timeout.
   *
   * @param timeUnit The time-unit in which to return the timeout.
   * @return The timeout.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  long getStatementTimeout(@NotNull TimeUnit timeUnit);

  /**
   * Sets the statement timeout.
   *
   * @param timeout  The timeout to set.
   * @param timeUnit The unit of the timeout.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  void setStatementTimeout(long timeout, @NotNull TimeUnit timeUnit);

  /**
   * Returns the lock timeout.
   *
   * @param timeUnit The time-unit in which to return the timeout.
   * @return The timeout.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  long getLockTimeout(@NotNull TimeUnit timeUnit);

  /**
   * Sets the lock timeout.
   *
   * @param timeout  The timeout to set.
   * @param timeUnit The unit of the timeout.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  void setLockTimeout(long timeout, @NotNull TimeUnit timeUnit);

  /**
   * Execute the given read-request.
   *
   * @return the result.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  Result execute(@NotNull ReadRequest<?> readRequest);

  /**
   * Process the given notification.
   *
   * @return the result.
   */
  @AvailableSince(NakshaVersion.v2_0_7)
  @NotNull
  Result process(@NotNull Notification<?> notification);
}
