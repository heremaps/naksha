package com.here.naksha.storage.http.cache;

import com.here.naksha.storage.http.RequestSender;
import com.here.naksha.storage.http.RequestSender.KeyProperties;
import com.here.naksha.storage.http.circuitbreaker.CircuitBreakerProps;
import com.here.naksha.storage.http.circuitbreaker.Resilience4jCircuitBreakerProvider;
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

  private Resilience4jCircuitBreakerProvider circuitBreakerProvider;

  @BeforeEach
  void clearProviderState() {
    circuitBreakerProvider = new Resilience4jCircuitBreakerProvider();
    circuitBreakerProvider.clear();
  }

  public static final String EXAMPLE_URL = "www.example.naksha.com";

  public static final int EXAMPLE_CONNECTION_TIMEOUT = 1;

  public static final int EXAMPLE_SOCKET_TIMEOUT = 1;

  public static final int EXAMPLE_MAX_RETRIES = 1;

  public static final Map<String, String> EXAMPLE_HEADERS = Map.of("Authorization", "Bearer exampleToken", "Content-Type", "application/json");

  public static final CircuitBreakerProps EXAMPLE_CB_CONFIG =
          new CircuitBreakerProps(20, 10, 500L, 50, 30000L, 5);

  public static final String ID_1 = "id_1";
  public static final String ID_2 = "id_2";
  public static final String ID_3 = "id_3";

  public static final KeyProperties PROP_ID_1 = keyProps(ID_1, EXAMPLE_HEADERS, EXAMPLE_SOCKET_TIMEOUT, 100L, EXAMPLE_CB_CONFIG);
  public static final KeyProperties PROP_ID_1_COPY = keyProps(ID_1, EXAMPLE_HEADERS, EXAMPLE_SOCKET_TIMEOUT, 100L, EXAMPLE_CB_CONFIG);
  public static final KeyProperties PROP_ID_1_NEWER = keyProps(ID_1, EXAMPLE_HEADERS, EXAMPLE_SOCKET_TIMEOUT, 200L, EXAMPLE_CB_CONFIG);
  public static final KeyProperties PROP_ID_2 = keyProps(ID_2, EXAMPLE_HEADERS, EXAMPLE_SOCKET_TIMEOUT, 100L, EXAMPLE_CB_CONFIG);
  public static final KeyProperties PROP_ID_3 = keyProps(ID_3, EXAMPLE_HEADERS, EXAMPLE_SOCKET_TIMEOUT, 100L, EXAMPLE_CB_CONFIG);
  public static final KeyProperties PROP_ID_1_NO_CB = keyProps(ID_1, EXAMPLE_HEADERS, EXAMPLE_SOCKET_TIMEOUT, 100L, null);

  private static KeyProperties keyProps(
      String storageId,
      Map<String, String> headers,
      long socketTimeout,
      long updatedAt,
      CircuitBreakerProps cbConfig) {
    return new KeyProperties(
        storageId,
        EXAMPLE_URL,
        headers,
        EXAMPLE_CONNECTION_TIMEOUT,
        socketTimeout,
        EXAMPLE_MAX_RETRIES,
        cbConfig,
        updatedAt);
  }


  @Test
  void testOneId() {
    // Setup
    ConcurrentMap<String, RequestSender> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, circuitBreakerProvider, 8, TimeUnit.HOURS);

    // Tests
    assertEquals(0, senders.size());

    // First lookup creates the sender.
    RequestSender senderId1 = cache.getSenderWith(PROP_ID_1);
    assertEquals(1, senders.size());

    // Same storageId + same updatedAt reuses the cached sender.
    RequestSender senderId1Copy = cache.getSenderWith(PROP_ID_1_COPY);
    assertEquals(1, senders.size());
    assertSame(senderId1, senderId1Copy);

    // Higher updatedAt replaces the cached sender.
    RequestSender senderId1Newer = cache.getSenderWith(PROP_ID_1_NEWER);
    assertEquals(1, senders.size());
    assertNotSame(senderId1Copy, senderId1Newer);
  }

  @Test
  void testMoreIds() {
    // Setup
    ConcurrentMap<String, RequestSender> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, circuitBreakerProvider, 8, TimeUnit.HOURS);

    // Tests
    assertEquals(0, senders.size());

    // Different storageIds keep independent cache entries.
    RequestSender senderId1 = cache.getSenderWith(PROP_ID_1);
    assertEquals(1, senders.size());

    RequestSender senderId2 = cache.getSenderWith(PROP_ID_2);
    assertEquals(2, senders.size());
    assertNotEquals(senderId1, senderId2);

    RequestSender senderId3 = cache.getSenderWith(PROP_ID_3);
    assertEquals(3, senders.size());
    assertNotEquals(senderId1, senderId3);

    RequestSender newSenderId1 = cache.getSenderWith(PROP_ID_1);
    assertEquals(3, senders.size());
    assertSame(senderId1, newSenderId1);
  }

  @Test
  void testWithoutCircuitBreakerConfig() {
    ConcurrentMap<String, RequestSender> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, circuitBreakerProvider, 8, TimeUnit.HOURS);

    // Null circuitBreaker config should still create a sender and skip breaker creation.
    RequestSender sender = cache.getSenderWith(PROP_ID_1_NO_CB);

    assertNotNull(sender);
    assertNull(circuitBreakerProvider.findCircuitBreaker(ID_1));
  }

  @Test
  void testCleanup() throws Exception {
    // Setup
    int cleanPeriodMs = 1000;
    ConcurrentMap<String, RequestSender> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, circuitBreakerProvider, cleanPeriodMs, TimeUnit.MILLISECONDS);

    // Tests
    assertEquals(0, senders.size());
    String breakerId = "cleanup-breaker-" + System.nanoTime();
    // Seed an unrelated breaker to verify cleanup clears breaker registry too.
    circuitBreakerProvider.getCircuitBreaker(breakerId, EXAMPLE_CB_CONFIG);
    assertNotNull(circuitBreakerProvider.findCircuitBreaker(breakerId));

    // Populate cache and wait for the scheduled cleanup task.
    cache.getSenderWith(PROP_ID_1);
    cache.getSenderWith(PROP_ID_2);
    cache.getSenderWith(PROP_ID_3);
    assertEquals(3, senders.size());
    Thread.sleep(cleanPeriodMs + 100);
    assertEquals(0, senders.size());
    assertNull(circuitBreakerProvider.findCircuitBreaker(breakerId));
    cache.getSenderWith(PROP_ID_1);
    assertEquals(1, senders.size());
  }


  @RepeatedTest(10)
  void testCleanupConcurrency() throws InterruptedException {
    // Setup
    int cleanPeriodMs = 1;
    ConcurrentMap<String, RequestSender> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, circuitBreakerProvider, cleanPeriodMs, TimeUnit.MILLISECONDS);

    // Tests
    assertEquals(0, senders.size());

    // Rapid cache population should still be removed by cleanup.
    cache.getSenderWith(PROP_ID_1);
    cache.getSenderWith(PROP_ID_2);
    cache.getSenderWith(PROP_ID_3);
    Thread.sleep(cleanPeriodMs + 100);
    assertEquals(0, senders.size());
  }

  @RepeatedTest(10)
  void testGetSenderConcurrency() throws InterruptedException {
    // Setup
    ConcurrentMap<String, RequestSender> senders = new ConcurrentHashMap<>();
    RequestSenderCache cache = new RequestSenderCache(senders, circuitBreakerProvider, 8, TimeUnit.HOURS);
    AtomicReference<RequestSender> senderId1 = new AtomicReference<>();
    AtomicReference<RequestSender> senderId1Copy = new AtomicReference<>();

    // Tests
    // Concurrent requests with the same version should converge to one cached sender.
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