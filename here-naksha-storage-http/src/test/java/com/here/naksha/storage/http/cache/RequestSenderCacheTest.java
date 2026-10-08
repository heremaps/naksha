package com.here.naksha.storage.http.cache;

import com.here.naksha.lib.circuitbreaker.CircuitBreakerProvider;
import com.here.naksha.storage.http.RequestSender;
import com.here.naksha.storage.http.RequestSender.KeyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestSenderCacheTest {

  @BeforeEach
  void clearProviderState() {
    CircuitBreakerProvider.getInstance().clear();
  }

  public static final String EXAMPLE_URL = "www.example.naksha.com";
  public static final int EXAMPLE_CONNECTION_TIMEOUT = 1;
  public static final int EXAMPLE_SOCKET_TIMEOUT = 1;
  public static final int EXAMPLE_MAX_RETRIES = 1;
  public static final Map<String, String> EXAMPLE_HEADERS = Map.of("Authorization", "******", "Content-Type", "application/json");

  public static final String ID_1 = "id_1";
  public static final String ID_2 = "id_2";
  public static final String ID_3 = "id_3";

  // Same storageId, same updatedAt → should reuse cached sender
  public static final KeyProperties PROP_ID_1 = keyProps(ID_1, 100L);
  public static final KeyProperties PROP_ID_1_COPY = keyProps(ID_1, 100L);

  // Same storageId, newer updatedAt → should create new sender
  public static final KeyProperties PROP_ID_1_NEWER = keyProps(ID_1, 200L);

  // Different storageIds → independent cache entries
  public static final KeyProperties PROP_ID_2 = keyProps(ID_2, 100L);
  public static final KeyProperties PROP_ID_3 = keyProps(ID_3, 100L);

  private static KeyProperties keyProps(String storageId, long updatedAt) {
    return new KeyProperties(
        storageId, EXAMPLE_URL, EXAMPLE_HEADERS, EXAMPLE_CONNECTION_TIMEOUT,
        EXAMPLE_SOCKET_TIMEOUT, EXAMPLE_MAX_RETRIES, updatedAt);
  }

  @Test
  void testOneIdSameUpdatedAtReusesCache() {
    // Setup
    ConcurrentMap<String, RequestSenderCache.RequestSenderWithLastUpdatedAt> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, 8, TimeUnit.HOURS);

    // Tests
    assertEquals(0, senders.size());

    // First lookup creates the sender
    RequestSender senderId1 = cache.getSenderWith(PROP_ID_1);
    assertEquals(1, senders.size());

    // Same storageId + same updatedAt reuses the cached sender
    RequestSender senderId1Copy = cache.getSenderWith(PROP_ID_1_COPY);
    assertEquals(1, senders.size());
    assertSame(senderId1, senderId1Copy, "Should reuse sender when updatedAt is same");
  }

  @Test
  void testOneIdNewerUpdatedAtRefreshesCache() {
    // Setup
    ConcurrentMap<String, RequestSenderCache.RequestSenderWithLastUpdatedAt> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, 8, TimeUnit.HOURS);

    // Tests
    assertEquals(0, senders.size());

    // First lookup creates the sender
    RequestSender senderId1 = cache.getSenderWith(PROP_ID_1);
    assertEquals(1, senders.size());

    // Newer updatedAt replaces the cached sender
    RequestSender senderId1Newer = cache.getSenderWith(PROP_ID_1_NEWER);
    assertEquals(1, senders.size(), "Cache size should remain 1 (same storageId)");
    assertNotSame(senderId1, senderId1Newer, "Should create new sender when updatedAt is newer");
  }

  @Test
  void testMultipleIds() {
    // Setup
    ConcurrentMap<String, RequestSenderCache.RequestSenderWithLastUpdatedAt> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, 8, TimeUnit.HOURS);

    // Tests
    assertEquals(0, senders.size());

    // Different storageIds keep independent cache entries
    RequestSender senderId1 = cache.getSenderWith(PROP_ID_1);
    assertEquals(1, senders.size());

    RequestSender senderId2 = cache.getSenderWith(PROP_ID_2);
    assertEquals(2, senders.size());
    assertNotSame(senderId1, senderId2);

    RequestSender senderId3 = cache.getSenderWith(PROP_ID_3);
    assertEquals(3, senders.size());
    assertNotSame(senderId1, senderId3);

    RequestSender newSenderId1 = cache.getSenderWith(PROP_ID_1);
    assertEquals(3, senders.size());
    assertSame(senderId1, newSenderId1, "Should reuse same storageId when updatedAt matches");
  }

  @Test
  void testCleanup() throws InterruptedException {
    // Setup
    int cleanPeriodMs = 1000;
    ConcurrentMap<String, RequestSenderCache.RequestSenderWithLastUpdatedAt> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, cleanPeriodMs, TimeUnit.MILLISECONDS);

    // Tests
    assertEquals(0, senders.size());

    // Populate cache
    cache.getSenderWith(PROP_ID_1);
    cache.getSenderWith(PROP_ID_2);
    cache.getSenderWith(PROP_ID_3);
    assertEquals(3, senders.size());

    // Wait for cleanup and verify cache is cleared
    Thread.sleep(cleanPeriodMs + 100);
    assertEquals(0, senders.size());

    // Cache should work again after cleanup
    cache.getSenderWith(PROP_ID_1);
    assertEquals(1, senders.size());
  }

  @RepeatedTest(10)
  void testCleanupConcurrency() throws InterruptedException {
    // Setup
    int cleanPeriodMs = 1;
    ConcurrentMap<String, RequestSenderCache.RequestSenderWithLastUpdatedAt> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, cleanPeriodMs, TimeUnit.MILLISECONDS);

    // Tests
    assertEquals(0, senders.size());

    // Rapid cache population should still be removed by cleanup
    cache.getSenderWith(PROP_ID_1);
    cache.getSenderWith(PROP_ID_2);
    cache.getSenderWith(PROP_ID_3);
    Thread.sleep(cleanPeriodMs + 100);
    assertEquals(0, senders.size());
  }

  @RepeatedTest(10)
  void testGetSenderConcurrency() throws InterruptedException {
    // Setup
    ConcurrentMap<String, RequestSenderCache.RequestSenderWithLastUpdatedAt> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, 8, TimeUnit.HOURS);
    AtomicReference<RequestSender> senderId1 = new AtomicReference<>();
    AtomicReference<RequestSender> senderId1Copy = new AtomicReference<>();

    // Tests
    // Concurrent requests with the same version should converge to one cached sender
    List<Thread> threads = List.of(
        new Thread(() -> senderId1.set(cache.getSenderWith(PROP_ID_1))),
        new Thread(() -> senderId1Copy.set(cache.getSenderWith(PROP_ID_1_COPY)))
    );
    threads.forEach(Thread::start);
    for (Thread thread : threads) {
      thread.join();
    }

    assertEquals(senderId1.get(), senderId1Copy.get());
  }
}