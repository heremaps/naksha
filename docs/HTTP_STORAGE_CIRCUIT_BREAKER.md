# HTTP Storage Circuit Breaker

This document describes how circuit breaker works for [`com.here.naksha.storage.http.HttpStorage`](../here-naksha-storage-http/src/main/java/com/here/naksha/storage/http/HttpStorage.java).

## Where it is applied

The CircuitBreaker is applied whenever Naksha wants to forward a request to HttpStorage.

**Key Points:**
- The CB config is read from the individual HTTP Storage definition.
- At runtime, the CB logic is executed against individual `storageId`.
- Different Storage definitions can imply different CB behavior based on their respective configurations.
- Whenever a Storage definition changes (via storage `updatedAt` value), the internal CB cache for that `storageId` is invalidated and a new CB is created with the updated configuration.
- This ensures that configuration changes are immediately reflected in the CB behavior without requiring an application restart.

## Why it exists

The goal is to isolate slow or failing requests by `storageId`.
If one storage starts timing out or responding slowly, only that storage's traffic should be throttled.
Requests for other storages should not be affected.


## HTTP Storage Configuration

For detailed configuration properties and their validation rules, refer to:
- [HttpStorageProperties](../here-naksha-storage-http/src/main/java/com/here/naksha/storage/http/HttpStorageProperties.java) - HTTP storage-specific settings (URL, timeouts, headers, etc.)
- [CircuitBreakerProps](../here-naksha-lib-circuit-breaker/src/main/java/com/here/naksha/lib/circuitbreaker/models/CircuitBreakerProps.java) - Circuit breaker configuration

### Example Storage JSON

```json
{
  "id": "my_storage",
  "type": "Storage",
  "className": "com.here.naksha.storage.http.HttpStorage",
  "properties": {
    "type": "http",
    "url": "https://example.com/api",
    "protocol": "json",
    "connectTimeout": 10,
    "socketTimeout": 30,
    "maxRetries": 3,
    "headers": {
      "Authorization": "Bearer token123"
    },
    "circuitBreaker": {
      "slidingWindowSize": 20,
      "minimumNumberOfCalls": 10,
      "slowCallDurationThresholdMs": 30000,
      "slowCallRateThreshold": 50,
      "waitDurationInOpenStateMs": 5000,
      "permittedNumberOfCallsInHalfOpenState": 4
    }
  }
}
```

**Note:** The `circuitBreaker` configuration is optional. If omitted, HTTP requests are sent without circuit breaking.


## CircuitBreaker Behavior

- **CLOSED**: This is default state during start of application where requests flow normally.
- **OPEN**: Requests are rejected immediately when circuit is in OPEN state. Circuit moves to or remains in OPEN state whenever the slow call rate exceeds `slowCallRateThreshold` (after minimum `minimumNumberOfCalls` have been observed).
- **HALF_OPEN**: Circuit transitions from OPEN to HALF_OPEN after waiting `waitDurationInOpenStateMs`. Limited number of probe calls (`permittedNumberOfCallsInHalfOpenState`) are allowed. It moves back to OPEN state if slow call rate exceeds `slowCallRateThreshold`. It moves to CLOSED state if slow call rate drops below `slowCallRateThreshold`.
- If probe calls are fast enough, the breaker closes.
- If probe calls are still slow, the breaker opens again.

## Notes

- The breaker is configured per `storageId`.
- Storage updates are detected through `updatedAt`.
- The storage JSON validation happens when storages are created or updated through the storage API.

