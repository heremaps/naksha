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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache for circuit breaker lastUpdatedAt timestamps scoped to storageId.
 * Tracks when each storageId's configuration was last updated to detect config changes.
 *
 * <p><b>Size-based Eviction:</b> When cache exceeds 1,000 entries:
 * <ul>
 *   <li>All circuit breakers in CLOSED state are removed from registry
 *   <li>If still full, arbitrary entries are evicted (with warnings)
 * </ul>
 */
public class CircuitBreakerCache {

  private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerCache.class);

  private static final int MAX_CACHE_SIZE = 1000;

  private static final ConcurrentMap<@NotNull String, @NotNull Long> storageCache = new ConcurrentHashMap<>();
  private static final AtomicInteger allocatedSlots = new AtomicInteger(0);
  private static final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

  private CircuitBreakerCache() {
    // Static utility class
  }

  /**
   * Register or update a circuit breaker entry for a storageId.
   *
   * <p><b>Behavior:</b>
   * <ul>
   *   <li>If entry exists with same/newer timestamp: no action (config unchanged)
   *   <li>If entry exists with older timestamp: remove old CB and re-register with new config
   *   <li>If entry not exists: reserve slot (with cleanup if full), then register
   * </ul>
   *
   * <p><b>Thread-safety:</b> Strictly maintains cache size ≤ 1000 via allocatedSlots reservation.
   * All cache reads/writes are atomic via computeIfPresent/putIfAbsent. Slot reservation happens before insertion
   *
   * @param provider the circuit breaker provider instance
   * @param storageId unique storage identifier
   * @param latestLastUpdatedAt timestamp when config was last updated
   * @param config circuit breaker configuration (used only for new/recreated entries)
   */
  public static void registerCircuitBreakerEntry(@NotNull ICircuitBreaker provider, @NotNull String storageId, long latestLastUpdatedAt, @NotNull CircuitBreakerProps config) {
    // Fast path: no config change
    Long cached = getCachedTimestamp(storageId);
    if (cached != null && cached >= latestLastUpdatedAt) {
      provider.getOrCreateCircuitBreaker(storageId, config);
      return;
    }
    while (true) {
      // Try to update existing entry atomically
      if (cached != null) {
        Long updated = storageCache.computeIfPresent(storageId, (key, currentTimestamp) -> {
          if (currentTimestamp >= latestLastUpdatedAt) {
            return currentTimestamp; // Already current, another thread won race
          }
          // Config changed: remove old and re-create
          provider.remove(key);
          provider.getOrCreateCircuitBreaker(key, config);
          return latestLastUpdatedAt;
        });

        if (updated != null) {
          return; // Successfully updated
        }
        // Entry was removed concurrently, treat as new entry below
        cached = null;
      }

      // New entry: reserve slot first (atomically)
      if (cached == null) {
        if (!tryReserveSlot()) {
          cleanupIfNeeded(provider);
          // After cleanup, re-check if entry appeared concurrently
          cached = getCachedTimestamp(storageId);
          if (cached != null && cached >= latestLastUpdatedAt) {
            provider.getOrCreateCircuitBreaker(storageId, config);
            return;
          }
          continue; // Retry slot reservation
        }

        // Atomically insert only if absent (double-check for concurrent insertion)
        Long existing = storageCache.putIfAbsent(storageId, latestLastUpdatedAt);
        if (existing == null) {
          // We won the race, successfully created new entry
          provider.getOrCreateCircuitBreaker(storageId, config);
          return;
        }

        // Another thread inserted while we reserved slot
        allocatedSlots.decrementAndGet(); // Reclaim our unused slot

        // Check if winning entry is current
        cached = existing;
        if (cached >= latestLastUpdatedAt) {
          provider.getOrCreateCircuitBreaker(storageId, config);
          return; // Winning entry is current, done
        }
        // Winning entry is stale, loop to update it
      }
    }
  }

  private static boolean tryReserveSlot() {
    while (true) {
      int slots = allocatedSlots.get();
      if (slots >= MAX_CACHE_SIZE) {
        return false;
      }
      if (allocatedSlots.compareAndSet(slots, slots + 1)) {
        return true;
      }
    }
  }

  private static void cleanupIfNeeded(@NotNull ICircuitBreaker provider) {
    if (!cleanupInProgress.compareAndSet(false, true)) {
      return;
    }
    try {
      // First pass: remove all CLOSED entries
      List<String> storageIds = new ArrayList<>(storageCache.keySet());
      for (String storageId : storageIds) {
        Long timestamp = getCachedTimestamp(storageId);
        if (timestamp != null && provider.isClosed(storageId)) {
          // Atomic remove with value check prevents race with concurrent updates
          if (storageCache.remove(storageId, timestamp)) {
            provider.remove(storageId);
            allocatedSlots.decrementAndGet();
          }
        }
      }

      // Second pass: if still full, evict one arbitrary entry
      if (allocatedSlots.get() >= MAX_CACHE_SIZE) {
        Iterator<String> iterator = storageCache.keySet().iterator();
        if (iterator.hasNext()) {
          String storageId = iterator.next();
          Long timestamp = getCachedTimestamp(storageId);
          if (timestamp != null && storageCache.remove(storageId, timestamp)) {
            provider.remove(storageId);
            allocatedSlots.decrementAndGet();
            logger.warn(
                "Cache full ({}). Evicted circuit breaker for storageId={}", MAX_CACHE_SIZE, storageId);
          }
        }
      }
    } finally {
      cleanupInProgress.set(false);
    }
  }

  /**
   * Get the cached timestamp for a storageId.
   *
   * @param storageId the storage identifier
   * @return the cached timestamp, or null if not found
   */
  @Nullable
  public static Long getCachedTimestamp(@NotNull String storageId) {
    return storageCache.get(storageId);
  }

  /**
   * Clear all cache entries. Intended for testing purposes.
   */
  @TestOnly
  public static void clearCache() {
    storageCache.clear();
    allocatedSlots.set(0);
  }
}
