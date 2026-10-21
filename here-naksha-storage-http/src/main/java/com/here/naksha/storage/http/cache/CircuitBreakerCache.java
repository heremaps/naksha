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
package com.here.naksha.storage.http.cache;

import com.here.naksha.lib.circuitbreaker.ICircuitBreaker;
import com.here.naksha.lib.circuitbreaker.models.CircuitBreakerProps;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache for circuit breaker lastUpdatedAt timestamps scoped to storageId.
 * Tracks when each storageId's configuration was last updated to detect config changes.
 *
 * <p><b>Size-based Eviction:</b> When cache exceeds {@link #MAX_SIZE} entries:
 * <ul>
 *   <li>All circuit breakers in CLOSED state are removed from registry
 *   <li>If still full, arbitrary entries are evicted (with warnings)
 * </ul>
 */
public class CircuitBreakerCache {

  private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerCache.class);
  private static final int MAX_SIZE = 1000;
  private static final Random random = new Random();

  private static final ConcurrentMap<@NotNull String, @NotNull Long> circuitBreakerMap = new ConcurrentHashMap<>();

  /**
   * Register or invalidate circuit breaker entry for a storageId in {@link #circuitBreakerMap}.
   * If configuration changed (detected by lastUpdatedAt), removes old CB.
   * Check or invalidate the cached timestamp for a storageId, recreating its circuit breaker if needed.
   *
   * Compares provided timestamp against cached value. If no cache entry exists or timestamp is newer,
   * updates cache and removes any stale CB from provider (harmless no-op for new storageIds).
   * Caller must call {@link ICircuitBreaker#getOrCreateCircuitBreaker(String, CircuitBreakerProps)} afterward.
   *
   * @param provider circuit breaker provider instance
   * @param storageId unique storage identifier
   * @param lastUpdatedAt timestamp when config was last updated
   */
  public static void checkOrInvalidateCircuitBreakerCache(
      @NotNull ICircuitBreaker provider, @NotNull String storageId, long lastUpdatedAt) {
    final Long cached = getCachedTimestamp(storageId);
    if (cached == null || lastUpdatedAt > cached) {
      boolean updated = updateInternalCache(storageId, lastUpdatedAt, provider);
      if (updated) {
        // Remove old CB; harmless for new storageIds (no-op since CB doesn't exist yet)
        provider.remove(storageId);
      }
    }
  }

  /**
   * Get cached timestamp for a storageId.
   *
   * @param storageId storage identifier
   * @return cached timestamp or null if not found
   */
  @Nullable
  static Long getCachedTimestamp(@NotNull String storageId) {
    return circuitBreakerMap.get(storageId);
  }

  /**
   * Update cache with new timestamp, removing old entries if needed.
   *
   * @param storageId storage identifier
   * @param lastUpdatedAt new timestamp value
   * @param provider circuit breaker provider for cleanup
   * @return true if the cache was updated, false otherwise
   */
  private static synchronized boolean updateInternalCache(
      @NotNull String storageId, long lastUpdatedAt, @NotNull ICircuitBreaker provider) {
    // Double check to prevent concurrent threads from creating duplicate entries
    final Long cached = circuitBreakerMap.get(storageId);
    if (cached != null && lastUpdatedAt <= cached) {
      return false; // Another thread already updated, nothing to do
    }

    // Check if cache size exceeds max size
    if (circuitBreakerMap.size() >= MAX_SIZE) {
      removeClosedEntries(provider);
      if (circuitBreakerMap.size() >= MAX_SIZE) {
        removeRandomEntry(provider);
      }
    }

    // Add new or replace existing cache entry
    circuitBreakerMap.put(storageId, lastUpdatedAt);
    return true;
  }

  /**
   * Remove entries with CLOSED circuit breakers due to cache size limit.
   * Iterates through all entries and removes those with CLOSED circuit breakers.
   *
   * @param provider circuit breaker provider to check CB state
   */
  private static synchronized void removeClosedEntries(@NotNull ICircuitBreaker provider) {
    List<String> storageIds = new ArrayList<>(circuitBreakerMap.keySet());
    for (String storageId : storageIds) {
      Long timestamp = getCachedTimestamp(storageId);
      if (timestamp != null && provider.isClosed(storageId)) {
        if (circuitBreakerMap.remove(storageId, timestamp)) {
          provider.remove(storageId);
          logger.info(
              "Removed CLOSED CircuitBreaker for storageId: {} due to max cache size limit", storageId);
        }
      }
    }
  }

  /**
   * Remove an arbitrary entry from cache due to size limit.
   *
   * @param provider circuit breaker provider to remove the entry from
   */
  private static synchronized void removeRandomEntry(@NotNull ICircuitBreaker provider) {
    if (!circuitBreakerMap.isEmpty()) {
      int randomIndex = random.nextInt(circuitBreakerMap.size());
      String storageId = (String) circuitBreakerMap.keySet().toArray()[randomIndex];
      Long removed = circuitBreakerMap.remove(storageId);
      provider.remove(storageId);
      if (removed != null) {
        logger.info("Cache full ({}). Evicted circuit breaker for storageId={}", MAX_SIZE, storageId);
      }
    }
  }

  /**
   * Clear all cache entries. Intended for testing purposes.
   */
  @TestOnly
  public static void clearCache() {
    circuitBreakerMap.clear();
  }
}
