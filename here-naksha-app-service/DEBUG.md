# Debugging `here-naksha-app-service`

This document describes how the service is bootstrapped during tests, the code path a REST request follows, and where to place breakpoints.

---

## Project Structure (Relevant Modules)

```
here-naksha-app-service/
├── src/jvmMain/
│   └── java/com/here/naksha/app/service/
│       ├── NakshaApp.java              # Main entry point / service bootstrap
│       ├── http/
│       │   ├── NakshaHttpVerticle.java # Vert.x HTTP server + router setup
│       │   ├── apis/                   # REST endpoint handlers (Api subclasses)
│       │   └── tasks/                  # Business logic tasks (per-endpoint)
├── src/jvmTest/
│   └── java/com/here/naksha/app/
│       ├── common/                     # Test infrastructure
│       │   ├── ApiTest.java            # Base class for all API tests
│       │   ├── ApiTestMaintainer.java  # JUnit extension, lifecycle management
│       │   ├── NakshaTestWebClient.java # HTTP client used by tests
│       │   ├── CommonApiTestSetup.java  # Shared storage/handler/space setup
│       │   └── assertions/             # Response assertion helpers
│       ├── init/context/               # Test context (local vs Docker)
│       │   ├── TestContext.java        # Abstract base: start/stop NakshaApp
│       │   └── TestContextEntrypoint.java # Selects Local vs Container context
│       └── service/                    # Actual test classes
```

---

## 1. Test Bootstrap Flow

When you run tests, the following initialization happens **once per test suite**:

### 1.1 JUnit Discovers Test Class

Any test that extends `ApiTest` is annotated with `@ExtendWith(ApiTestMaintainer.class)`.

### 1.2 ApiTestMaintainer.beforeAll()

**File:** `ApiTestMaintainer.java:48`

```
JUnit @BeforeAll  →  ApiTestMaintainer.beforeAll()
```

- A static `TestContext` is loaded at class-load time via `TestContextEntrypoint.loadTestContext()` (line 45)
- `TestContextEntrypoint` (line 19) checks the environment variable `NAKSHA_APP_SERVICE_TEST_CONTEXT`:
  - Unset or blank → `LocalTestContext` (uses existing local PostgreSQL)
  - `"TEST_CONTAINERS"` → `ContainerTestContext` (starts Docker-based PostgreSQL)
  - `"LOCAL_STANDALONE"` → `LocalTestContext`
- If the context state is `NOT_STARTED`, calls `TEST_CONTEXT.start()` (line 52)

### 1.3 TestContext.start()

**File:** `TestContext.java:31`

```
TestContext.start()
  → setupStorage()        // drops/creates test schema (LocalTestContext only)
  → startNaksha()         // creates and starts the NakshaApp
```

The `startNaksha()` method (line 65):
1. Calls `nakshaAppInitializer.get()` — constructs a `NakshaApp`
2. Calls `nakshaApp.start()` — starts the service thread
3. Sleeps **5 seconds** for the HTTP server to come up

### 1.4 NakshaApp Constructor

**File:** `NakshaApp.java:185-253`

1. **Line 201-204**: Reads config file via `ConfigUtil.readConfigFile(configId, appName)`
2. **Line 207**: Creates `NakshaContext` and attaches to current thread
3. **Line 208**: Creates `INaksha hub` via `NakshaHubFactory.getInstance()` — this initializes the core storage/handler infrastructure and connects to PostgreSQL
4. **Line 228**: Creates `Vertx` instance
5. **Line 230-245**: Sets up JWT auth provider with private/public keys
6. **Line 247-251**: Creates `WebClient` for outbound HTTP calls

### 1.5 NakshaApp.run() (deployed verticles)

**File:** `NakshaApp.java:360-405`

1. **Line 372**: Initializes OTel metrics
2. **Line 373-378**: Creates one `NakshaHttpVerticle` per CPU core and deploys each via `vertx.deployVerticle()`

### 1.6 NakshaHttpVerticle.start()

**File:** `NakshaHttpVerticle.java:148-262`

1. **Line 149**: Builds OpenAPI `RouterBuilder` from `swagger/openapi.yaml` resource
2. **Line 158**: Registers JWT security handler for `"Bearer"` scheme
3. **Line 162-168**: Instantiates API controllers:
   - `HealthApi` — health check endpoints
   - `StorageApi` — storage CRUD
   - `SpaceApi` — space CRUD
   - `EventHandlerApi` — event handler CRUD
   - `ReadFeatureApi` — GET feature endpoints
   - `WriteFeatureApi` — POST/PUT/PATCH/DELETE feature endpoints
4. **Line 171-173**: Calls `api.addOperations(rb)` on each → wires handlers to OpenAPI operations
5. **Line 176-179**: Calls `api.addManualRoutes(router)` for non-OpenAPI routes
6. **Line 182-214**: Configures middleware:
   - `CorsHandler` (order -2)
   - `BodyHandler` (order -1) — body parsing, size limits
   - `onNewRequest` (order 1) — access logging, stream info
7. **Line 235-252**: Starts HTTP server on configured port (default 8080)

### 1.7 ApiTest.setupStorage()

**File:** `ApiTest.java:50-56`

After the service is up, JUnit runs `@BeforeAll setupStorage()` once per suite:
- Singleton-guarded via `AtomicBoolean`
- Calls `CommonApiTestSetup.setupCommonStorage()` → POSTs JSON to `hub/storages` endpoint to create the shared data storage

### 1.8 Individual Test @BeforeAll

Each test class (e.g., `ReadFeaturesByTileTest.java:54-58`) typically:
1. Calls `setupHandlerAndSpace(client, "path/to/setup")` → creates event handler + space from JSON files
2. Seeds initial features via POST

### Summary Diagram

```
JUnit @BeforeAll
  └─ ApiTestMaintainer.beforeAll()          [ApiTestMaintainer.java:48]
       └─ TestContext.start()               [TestContext.java:31]
            ├─ setupStorage()                [TestContext.java:57 or subclass]
            └─ startNaksha()                 [TestContext.java:65]
                 ├─ NakshaApp()              [NakshaApp.java:185]
                 │    ├─ ConfigUtil.readConfigFile()
                 │    ├─ NakshaHubFactory.getInstance()  ← core hub + DB
                 │    ├─ Vertx.vertx()
                 │    └─ NakshaAuthProvider()
                 ├─ nakshaApp.start()
                 │    └─ NakshaApp.run()      [NakshaApp.java:360]
                 │         └─ deployVerticle(NakshaHttpVerticle) × N CPUs
                 │              └─ NakshaHttpVerticle.start()  [line 148]
                 │                   ├─ RouterBuilder.create()
                 │                   ├─ new ReadFeatureApi(this), etc.
                 │                   ├─ api.addOperations(rb)  × 6
                 │                   └─ listen(8080)
                 └─ Thread.sleep(5000)
  └─ ApiTest.setupStorage()                  [ApiTest.java:50]
       └─ CommonApiTestSetup.setupCommonStorage()
            └─ POST hub/storages
  └─ TestClass.setup()                       [e.g., ReadFeaturesByTileTest.java:54]
       └─ setupHandlerAndSpace()
            ├─ POST hub/handlers
            └─ POST hub/spaces
```

---

## 2. REST Request Flow

Tracing a typical request: `GET /hub/spaces/{spaceId}/tile/{tileType}/{tileId}`

### 2.1 Test HTTP Client

**File:** `NakshaTestWebClient.java:71-86`

The test calls `nakshaClient.get("hub/spaces/.../tile/quadkey/1?tags=...", streamId)`:
1. Uses standard Java `HttpClient` targeting `http://localhost:8080/`
2. Sets `Stream-Id` header and optional `Authorization` JWT header
3. Builds `HttpRequest` → calls `httpClient.send()`

### 2.2 Vert.x HTTP Server

**File:** `NakshaHttpVerticle.java:235`

The incoming HTTP request is dispatched by Vert.x to the `Router` with the `requestHandler`.

### 2.3 Router Handler Chain

The router processes the request through handlers in priority order:

| Order | Handler | Purpose |
|-------|---------|---------|
| -2 | `CorsHandler` | CORS preflight |
| -1 | `BodyHandler` | Parse request body, enforce size limits |
| 1 | `onNewRequest()` | [NakshaHttpVerticle.java:414] Access logging, attach response/end handlers |
| N | OpenAPI route match | Matches path against `openapi.yaml` → dispatches to API handler |

### 2.4 API Controller (Route Handler)

**File:** `ReadFeatureApi.java:76-78`

For `GET .../tile/...`, the OpenAPI router matches operation `getFeaturesByTile` → calls:

```java
ReadFeatureApi.getFeaturesByTile(RoutingContext)  // line 76
  └─ startReadFeatureApiTask(GET_BY_TILE, ctx)    // line 77
       └─ new ReadFeatureApiTask(...).start()      // line 96-99
```

The `startReadFeatureApiTask()` method:
1. **Line 98**: Calls `verticle.createNakshaContext(routingContext)` → extracts JWT payload, sets streamId, appId, author, URM on the context
2. **Line 96-99**: Creates `ReadFeatureApiTask` with request type, verticle, hub, routing context, and naksha context
3. Calls `.start()` to execute the task

### 2.5 Task Execution

**File:** `ReadFeatureApiTask.java:138-159`

The `execute()` method dispatches on `reqType`:

```java
execute()                                          // line 138
  └─ switch (reqType) → executeFeaturesByTile()    // line 144 → implementation below
```

`executeFeaturesByTile()` (look for it further in the file):
1. Parses path params: `spaceId`, `tileType`, `tileId`
2. Parses query params: `margin`, `limit`, `clip`, `tags`, `p.` (property search), `f.id`, `selection`
3. Builds a `ReadFeatures` request with spatial query (bbox from tile), tag query, property query
4. Calls `executeReadRequestFromSpaceStorage(rdRequest)` → goes to storage layer
5. Post-processes: property selection, geo clipping, MOM10 conversion
6. Returns `XyzResponse`

### 2.6 Storage Layer Call

**File:** `AbstractApiTask.java:248`

```java
executeReadRequestFromSpaceStorage(readFeatures)
  → naksha().getSpaceStorage().useReadSession(..., reader -> reader.execute(readFeatures))
```

For write operations, see `AbstractApiTask.java:252`.

### 2.7 Response Sending

**File:** `NakshaHttpVerticle.java:626`

The task's result flows back to:
1. `transformResponseToXyzCollectionResponse()` or similar → wraps features into `XyzFeatureCollection`
2. `verticle.sendXyzResponse(routingContext, type, response)` [line 626] → serializes to JSON
3. `sendRawResponse(routingContext, status, contentType, buffer)` [line 710] → writes HTTP response
4. `onResponseEnd()` [line 359] → final access logging

### Summary Diagram

```
Test: nakshaClient.get("hub/spaces/.../tile/quadkey/1")
  │  [NakshaTestWebClient.java:71]
  ▼
Vert.x HTTP Server (port 8080)
  │  [NakshaHttpVerticle.java:235]
  ▼
Router Handler Chain:
  ├─ CorsHandler        (order -2)
  ├─ BodyHandler        (order -1)
  ├─ onNewRequest()     (order 1)  [NakshaHttpVerticle.java:414]
  └─ OpenAPI route match → ReadFeatureApi.getFeaturesByTile()  [ReadFeatureApi.java:76]
       │
       └─ startReadFeatureApiTask()  [ReadFeatureApi.java:96]
            ├─ verticle.createNakshaContext()  [NakshaHttpVerticle.java:731]
            │    └─ extract JWT, set streamId, appId, author
            └─ new ReadFeatureApiTask(...).start()
                 │
                 └─ ReadFeatureApiTask.execute()  [ReadFeatureApiTask.java:138]
                      ├─ parse path/query params
                      ├─ build ReadFeatures request
                      ├─ executeReadRequestFromSpaceStorage()  [AbstractApiTask.java:248]
                      │    └─ naksha().getSpaceStorage().useReadSession(...)
                      ├─ post-process (selection, clip, MOM10)
                      └─ transformResponseToXyzCollectionResponse()
                           │
                           └─ verticle.sendXyzResponse()  [NakshaHttpVerticle.java:626]
                                └─ sendRawResponse()  [NakshaHttpVerticle.java:710]
                                     └─ httpResponse.end(content)
```

---

## 3. Breakpoint Reference

### For Debugging Test Bootstrap

| File | Line | What Happens |
|------|------|-------------|
| `ApiTestMaintainer.java` | **48** | `beforeAll()` — test lifecycle entry point |
| `TestContextEntrypoint.java` | **19** | `loadTestContext()` — selects Local vs Container |
| `TestContext.java` | **31** | `start()` — begins setup → startNaksha |
| `TestContext.java` | **65** | `startNaksha()` — creates NakshaApp, sleeps 5s |
| `NakshaApp.java` | **208** | `NakshaHubFactory.getInstance()` — creates hub, connects to DB |
| `NakshaApp.java` | **360** | `run()` — begins verticle deployment |
| `NakshaApp.java` | **375** | `new NakshaHttpVerticle(...)` — verticle per CPU core |
| `NakshaHttpVerticle.java` | **148** | `start()` — builds router, starts HTTP server |
| `NakshaHttpVerticle.java` | **162** | API controller instantiation (Health, Storage, Space, etc.) |
| `NakshaHttpVerticle.java` | **238** | `listen(hubConfig.getHttpPort())` — HTTP server ready |
| `ApiTest.java` | **51** | `setupStorage()` — creates common storage via REST |
| `CommonApiTestSetup.java` | **29** | `setupCommonStorage()` — POSTs storage JSON |

### For Debugging a Read Request (e.g., GetByTile)

| File | Line | What Happens |
|------|------|-------------|
| `NakshaTestWebClient.java` | **71** | `get()` — test client sends HTTP request |
| `NakshaHttpVerticle.java` | **414** | `onNewRequest()` — first handler per request |
| `ReadFeatureApi.java` | **76** | `getFeaturesByTile()` — OpenAPI route handler |
| `ReadFeatureApi.java` | **96** | `startReadFeatureApiTask()` — creates and starts task |
| `NakshaHttpVerticle.java` | **731** | `createNakshaContext()` — builds context from JWT |
| `ReadFeatureApiTask.java` | **138** | `execute()` — task execution entry |
| `ReadFeatureApiTask.java` | **144** | switch case → `executeFeaturesByTile()` |
| `AbstractApiTask.java` | **248** | `executeReadRequestFromSpaceStorage()` → storage layer call |
| `AbstractApiTask.java` | **139** | `transformResponseToXyzCollectionResponse()` — wraps features |
| `NakshaHttpVerticle.java` | **626** | `sendXyzResponse()` — serializes and sends response |
| `NakshaHttpVerticle.java` | **710** | `sendRawResponse()` — writes HTTP response bytes |
| `NakshaHttpVerticle.java` | **359** | `onResponseEnd()` — final access logging |

### For Debugging a Write Request (e.g., Create Feature)

| File | Line | What Happens |
|------|------|-------------|
| `NakshaTestWebClient.java` | **93** | `post()` — test client sends request |
| `WriteFeatureApi.java` | **60** | `createFeatures()` — route handler |
| `WriteFeatureApi.java` | **84** | `startWriteFeatureApiTask()` — creates task |
| `AbstractApiTask.java` | **252** | `executeWriteRequestFromSpaceStorage()` → storage layer |
| `AbstractApiTask.java` | **204** | `transformWriteResultToXyzCollectionResponse()` — transforms write result |

### For Debugging Assertions

| File | Line | What Happens |
|------|------|-------------|
| `ResponseAssertions.java` | **57** | `assertThat()` — fluent assertion entry |
| `ResponseAssertions.java` | **66** | `hasStatus()` — checks HTTP status code |
| `ResponseAssertions.java` | **99** | `hasJsonBody()` — compares JSON response body |

---

## 4. Quick Debug Checklist

When a test fails with an error originating in the service:

1. **Is it a bootstrap failure?** Put a breakpoint at `ApiTestMaintainer.java:48` and step through to `NakshaHttpVerticle.java:148`. Check that the HTTP server starts successfully.

2. **Is it an HTTP-level failure (4xx/5xx)?** Put a breakpoint at `NakshaHttpVerticle.java:414` (`onNewRequest`) to catch the incoming request, then at `NakshaHttpVerticle.java:442` (`sendErrorResponse`) to see what error is being returned.

3. **Is it a business-logic failure?** Put a breakpoint at the specific API handler (e.g., `ReadFeatureApi.java:76`), then step into `ReadFeatureApiTask.execute()` at line 138.

4. **Is it a storage-layer failure?** Put a breakpoint at `AbstractApiTask.java:248` (`executeReadRequestFromSpaceStorage`) or line 252 for writes. This is where the request crosses into the storage subsystem.

5. **Is it a response-serialization failure?** Put a breakpoint at `NakshaHttpVerticle.java:626` (`sendXyzResponse`) to inspect the response object before JSON serialization.

6. **Need to see the full request/response?** The `Stream-Id` header is propagated through the entire pipeline. Check the logs for the stream ID printed by the test client at `NakshaTestWebClient.java:167`.
