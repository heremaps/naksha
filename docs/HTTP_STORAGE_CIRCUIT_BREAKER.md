# HTTP Storage Circuit Breaker

This document describes how circuit breaker works for `com.here.naksha.storage.http.HttpStorage`.

## Where it is applied

The circuit breaker is applied when Naksha creates the HTTP storage sender for a storage definition.

Flow:
1. Storage JSON is read from admin storage.
2. `HttpStorage` parses `HttpStorageProperties`.
3. `RequestSenderCache` creates or reuses a `RequestSender` for the `storageId`.
4. The sender executes requests through a per `storageId` circuit breaker when `circuitBreaker` is configured.

The cached sender is refreshed when the storage `updatedAt` value increases.

## Why it exists

The goal is to isolate slow or failing requests by `storageId`.
If one storage starts timing out or responding slowly, only that storage's traffic should be throttled.
Requests for other storages should not be affected.

## What is cached

Naksha caches the resolved sender state for a storage:
- storage id
- host URL
- default headers
- timeouts and retry settings
- circuit breaker configuration

The cache is not refreshed on every feature request.
It is refreshed only when the storage definition changes and the storage `updatedAt` value becomes newer.

## HTTP storage properties

These are the main HTTP storage properties used by Naksha.

| Property | Meaning |
|---|---|
| `url` | Base URL of the remote HTTP storage. |
| `connectTimeout` | Timeout, in seconds, for establishing the HTTP connection. |
| `socketTimeout` | Timeout, in seconds, for waiting on a response. |
| `maxRetries` | Number of retry attempts for retryable transport failures. |
| `headers` | Default headers added to each request. |
| `httpInterface` | HTTP storage interface mode. |
| `circuitBreaker` | Optional circuit-breaker configuration. If omitted, HTTP requests are sent without circuit breaking. |

## Circuit breaker properties

These settings are configured under `properties.circuitBreaker` in the storage JSON.

| Property | Meaning |
|---|---|
| `slidingWindowSize` | Number of recent calls used for breaker decisions. |
| `minimumNumberOfCalls` | Minimum number of observed calls before breaker evaluation starts. |
| `slowCallDurationThresholdMs` | A call slower than this is counted as slow. |
| `slowCallRateThreshold` | Percentage of slow calls that opens the circuit. |
| `waitDurationInOpenStateMs` | How long the breaker stays open before probing half-open. |
| `permittedNumberOfCallsInHalfOpenState` | Number of probe calls allowed while half-open. |

## Behavior

- **CLOSED**: requests flow normally.
- **OPEN**: requests are rejected immediately.
- **HALF_OPEN**: only the configured probe count is allowed.
- If probe calls are fast enough, the breaker closes.
- If probe calls are still slow, the breaker opens again.

## Notes

- The breaker is configured per `storageId`.
- Storage updates are detected through `updatedAt`.
- The storage JSON validation happens when storages are created or updated through the storage API.

