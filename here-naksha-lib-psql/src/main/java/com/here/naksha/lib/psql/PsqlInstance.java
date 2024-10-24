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

import static com.here.naksha.lib.psql.PostgresInstance.allInstances;
import static com.here.naksha.lib.psql.PostgresInstance.mutex;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A PostgresQL database instance with a connection pool attached. This instance will live as long as there are any references to it, what
 * includes open and idle, pending connections.
 */
public final class PsqlInstance {

  private static final Logger log = LoggerFactory.getLogger(PsqlInstance.class);

  /**
   * Returns the PostgresQL database instance singleton for the given configuration or creates a new one, should there be no one yet.
   *
   * @param config The PostgresQL database instance configuration.
   * @return The PostgresQL database instance singleton.
   */
  public static @NotNull PsqlInstance get(@NotNull PsqlInstanceConfig config) {
    mutex.lock();
    PsqlInstance psqlInstance;
    PostgresInstance instance;
    try {
      instance = allInstances.get(config);
      if (instance != null) {
        psqlInstance = instance.getPsqlInstance();
        if (psqlInstance != null) {
          return psqlInstance;
        }
      }
      psqlInstance = new PsqlInstance(config);
    } finally {
      mutex.unlock();
    }
    return psqlInstance;
  }

  PsqlInstance(@NotNull PsqlInstanceConfig config) {
    this.postgresInstance = new PostgresInstance(this, config);
  }

  final @NotNull PostgresInstance postgresInstance;

  /**
   * Returns a new connection from the pool.
   *
   * @param connTimeoutInMillis         The connection timeout, if a new connection need to be established.
   * @param sockedReadTimeoutInMillis   The socket read-timeout to be used with the connection.
   * @param cancelSignalTimeoutInMillis The signal timeout to be used with the connection.
   * @return The connection.
   * @throws SQLException If acquiring the connection failed.
   */
  public @NotNull PsqlConnection getConnection(
      long connTimeoutInMillis, long sockedReadTimeoutInMillis, long cancelSignalTimeoutInMillis)
      throws SQLException {
    return postgresInstance.getConnection(
        connTimeoutInMillis, sockedReadTimeoutInMillis, cancelSignalTimeoutInMillis);
  }

  /**
   * Returns the medium latency to this instance.
   *
   * @param timeUnit The time-unit in which to return the latency.
   * @return The latency.
   */
  public long getMediumLatency(@NotNull TimeUnit timeUnit) {
    return postgresInstance.getMediumLatency(timeUnit);
  }

  /**
   * Forcefully overrides auto-detected medium latency.
   *
   * @param latency  The latency to set.
   * @param timeUnit The time-unit in which the latency was provided.
   */
  public void setMediumLatency(long latency, @NotNull TimeUnit timeUnit) {
    postgresInstance.setMediumLatency(latency, timeUnit);
  }

  /**
   * Forcefully overrides auto-detected medium latency.
   *
   * @param latency  The latency to set.
   * @param timeUnit The time-unit in which the latency was provided.
   * @return this.
   */
  public @NotNull PsqlInstance withMediumLatency(long latency, @NotNull TimeUnit timeUnit) {
    postgresInstance.setMediumLatency(latency, timeUnit);
    return this;
  }

  /**
   * Returns the maximum bandwidth to the PostgresQL server instance in gigabit.
   *
   * @return The maximum bandwidth to the PostgresQL server instance in gigabit.
   */
  public long getMaxBandwidthInGbit() {
    return postgresInstance.getMaxBandwidthInGbit();
  }

  /**
   * Forcefully set the maximum bandwidth to the PostgresQL server instance in gigabit.
   *
   * @param maxBandwidthInGbit The bandwidth in gigabit.
   */
  public void setMaxBandwidthInGbit(long maxBandwidthInGbit) {
    postgresInstance.setMaxBandwidthInGbit(maxBandwidthInGbit);
  }

  /**
   * Forcefully set the maximum bandwidth to the PostgresQL server instance in gigabit.
   *
   * @param maxBandwidthInGbit The bandwidth in gigabit.
   * @return this.
   */
  public @NotNull PsqlInstance withMaxBandwidthInGbit(long maxBandwidthInGbit) {
    postgresInstance.setMaxBandwidthInGbit(maxBandwidthInGbit);
    return this;
  }
}
