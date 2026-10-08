package com.here.naksha.storage.http.cache;

import com.here.naksha.lib.circuitbreaker.CircuitBreakerProvider;
import com.here.naksha.lib.circuitbreaker.ICircuitBreaker;
import com.here.naksha.lib.circuitbreaker.models.CircuitBreakerProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerCacheTest {

  private static final ICircuitBreaker provider = CircuitBreakerProvider.getInstance();

  private static final CircuitBreakerProps CB_CONFIG =
      new CircuitBreakerProps(20, 10, 500L, 50, 30000L, 5);

  private static final String ID_1 = "test_storage_1";
  private static final String ID_2 = "test_storage_2";
  private static final String ID_3 = "test_storage_3";

  @BeforeEach
  void setUp() {
    provider.clear();
    CircuitBreakerCache.clearCache();
  }

  /**
   * Verifies that registering the same storageId with same timestamp is idempotent.
   */
  @Test
  void testSameStorageIdSameTimestampIsIdempotent() {
    long timestamp = 100L;
    
    // First registration
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, timestamp, CB_CONFIG);
    assertTrue(provider.exists(ID_1), "CB should exist after first registration");
    assertEquals(timestamp, CircuitBreakerCache.getCachedTimestamp(ID_1), "Timestamp should be cached");
    
    // Second registration with same timestamp should be no-op
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, timestamp, CB_CONFIG);
    assertTrue(provider.exists(ID_1), "CB should still exist");
    assertEquals(timestamp, CircuitBreakerCache.getCachedTimestamp(ID_1), "Timestamp should remain the same");
  }

  /**
   * Verifies that registering with a newer timestamp recreates the circuit breaker.
   */
  @Test
  void testNewerTimestampRecreatesCircuitBreaker() {
    long oldTimestamp = 100L;
    long newTimestamp = 200L;
    
    // Register with old timestamp
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, oldTimestamp, CB_CONFIG);
    assertTrue(provider.exists(ID_1), "CB should exist after first registration");
    assertEquals(oldTimestamp, CircuitBreakerCache.getCachedTimestamp(ID_1), "Old timestamp should be cached");
    
    // Register with newer timestamp
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, newTimestamp, CB_CONFIG);
    assertTrue(provider.exists(ID_1), "CB should exist after second registration");
    assertEquals(newTimestamp, CircuitBreakerCache.getCachedTimestamp(ID_1), "Timestamp should be updated to new value");
  }

  /**
   * Verifies that registering with an older timestamp is ignored (no downgrade).
   */
  @Test
  void testOlderTimestampIsIgnored() {
    long oldTimestamp = 100L;
    long newerTimestamp = 200L;
    
    // Register with newer timestamp first
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, newerTimestamp, CB_CONFIG);
    assertEquals(newerTimestamp, CircuitBreakerCache.getCachedTimestamp(ID_1), "Newer timestamp should be cached");
    
    // Try to register with older timestamp (should be ignored)
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, oldTimestamp, CB_CONFIG);
    
    // Timestamp should not be downgraded
    assertEquals(newerTimestamp, CircuitBreakerCache.getCachedTimestamp(ID_1), "Timestamp should not be downgraded");
  }

  /**
   * Verifies that different storageIds maintain independent cache entries.
   */
  @Test
  void testMultipleStorageIdsAreIndependent() {
    long timestamp1 = 100L;
    long timestamp2 = 200L;
    long timestamp3 = 300L;
    
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, timestamp1, CB_CONFIG);
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_2, timestamp2, CB_CONFIG);
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_3, timestamp3, CB_CONFIG);
    
    assertTrue(provider.exists(ID_1), "CB for ID_1 should exist");
    assertTrue(provider.exists(ID_2), "CB for ID_2 should exist");
    assertTrue(provider.exists(ID_3), "CB for ID_3 should exist");
    
    // Verify each has the correct timestamp
    assertEquals(timestamp1, CircuitBreakerCache.getCachedTimestamp(ID_1));
    assertEquals(timestamp2, CircuitBreakerCache.getCachedTimestamp(ID_2));
    assertEquals(timestamp3, CircuitBreakerCache.getCachedTimestamp(ID_3));
  }

  /**
   * Verifies that cache respects the 1000 entry limit via size-based cleanup.
   * Fills cache to 1000 entries and verifies cleanup maintains limit.
   */
  @Test
  void testSizeBasedCleanupOnCachedClosedEntries() throws InterruptedException {
    int maxCacheSize = 1000;
    
    // Fill cache to 1000 entries
    for (int i = 0; i < maxCacheSize; i++) {
      String storageId = "storage_" + i;
      long timestamp = 100L + i;
      CircuitBreakerCache.registerCircuitBreakerEntry(provider, storageId, timestamp, CB_CONFIG);
    }
    
    // Small delay to ensure all registrations complete
    Thread.sleep(50);

    // Add one more entry which should trigger cleanup
    CircuitBreakerCache.registerCircuitBreakerEntry(provider, "storage_extra", 200L, CB_CONFIG);

    // After cleanup, verify cache size is still reasonable (≤ 1000 or close to it)
    int finalCacheSize = 0;
    for (int i = 0; i < maxCacheSize + 1; i++) {
      String storageId = (i < maxCacheSize) ? "storage_" + i : "storage_extra";
      if (CircuitBreakerCache.getCachedTimestamp(storageId) != null) {
        finalCacheSize++;
      }
    }
    assertTrue(finalCacheSize <= 1000, "After cleanup, cache should be ≤ 1000 entries, but had: " + finalCacheSize);
  }

  /**
   * Verifies concurrent registrations with same storageId converge correctly.
   */
  @RepeatedTest(5)
  void testConcurrentRegistrationsSameStorageId() throws InterruptedException {
    int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    
    for (int i = 0; i < threadCount; i++) {
      new Thread(() -> {
        try {
          startLatch.await();  // Wait for all threads to be ready
          CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, 100L, CB_CONFIG);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      }).start();
    }
    
    startLatch.countDown();  // Release all threads at once
    endLatch.await();  // Wait for all to complete
    
    assertTrue(provider.exists(ID_1), "CB should exist after concurrent registrations");
  }

  /**
   * Verifies concurrent registrations with different storageIds work correctly.
   */
  @RepeatedTest(5)
  void testConcurrentRegistrationsDifferentStorageIds() throws InterruptedException {
    int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    
    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      new Thread(() -> {
        try {
          startLatch.await();
          String storageId = "storage_" + index;
          CircuitBreakerCache.registerCircuitBreakerEntry(provider, storageId, 100L, CB_CONFIG);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      }).start();
    }
    
    startLatch.countDown();
    endLatch.await();
    
    // Retry loop: wait for CBs to be created (with timeout)
    int maxRetries = 50;
    int retries = 0;
    boolean allExist = false;
    while (retries < maxRetries) {
      allExist = true;
      for (int i = 0; i < threadCount; i++) {
        if (!provider.exists("storage_" + i)) {
          allExist = false;
          break;
        }
      }
      if (allExist) break;
      Thread.sleep(10);
      retries++;
    }
    
    // All 10 CBs should be registered
    for (int i = 0; i < threadCount; i++) {
      String storageId = "storage_" + i;
      assertTrue(provider.exists(storageId), "CB should exist for storageId: " + storageId);
    }
  }

  /**
   * Verifies concurrent updates to same storageId with different timestamps.
   */
  @RepeatedTest(5)
  void testConcurrentUpdatesWithDifferentTimestamps() throws InterruptedException {
    int threadCount = 5;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    AtomicInteger updateCount = new AtomicInteger(0);
    long maxExpectedTimestamp = 100L + (4 * 10);  // Max timestamp: 140L
    
    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      new Thread(() -> {
        try {
          startLatch.await();
          long timestamp = 100L + (index * 10);  // Different timestamps
          CircuitBreakerCache.registerCircuitBreakerEntry(provider, ID_1, timestamp, CB_CONFIG);
          updateCount.incrementAndGet();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      }).start();
    }
    
    startLatch.countDown();
    endLatch.await();
    
    // Retry loop: wait for CB to be created (with timeout)
    int maxRetries = 50;
    int retries = 0;
    while (!provider.exists(ID_1) && retries < maxRetries) {
      Thread.sleep(10);
      retries++;
    }
    
    assertTrue(provider.exists(ID_1), "CB should exist after concurrent updates");
    assertEquals(threadCount, updateCount.get(), "All threads should complete");
    
    // Verify the cached timestamp is the maximum one (140L)
    Long cachedTimestamp = CircuitBreakerCache.getCachedTimestamp(ID_1);
    assertNotNull(cachedTimestamp, "Timestamp should be cached");
    assertEquals(maxExpectedTimestamp, cachedTimestamp.longValue(),"Cached timestamp should be the maximum: " + maxExpectedTimestamp);
  }
}
