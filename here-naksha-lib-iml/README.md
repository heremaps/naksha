# lib-iml

`here-naksha-lib-iml` is a JVM-only Kotlin implementation of the Naksha storage interface for Interactive Map Layers in PostgreSQL, read-only by default with opt-in production writes. It uses the PostgreSQL JDBC driver directly and does not install JavaScript, SQL/server functions, extensions, or upgrade scripts. Explicitly enabled bootstrap and writes may create the documented database structures.

## Usage

```kotlin
import naksha.iml.ImlConfig
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.ops.Equals

NakshaContext.currentContext().withAppId("my-application")
val config = ImlConfig("iml", requireNotNull(System.getenv("IML_DB_URL")))
val storage = Naksha.useStorage(config)
try {
  storage.newReadSession().use { session ->
    val response = session.executeRead(ReadFeatures().apply {
      catalogId = "public"
      collectionId = "logical-layer-id-from-space-meta"
      queryMembers = Equals("id", "feature-id")
      limit = 100
    }) as SuccessResponse
    val features = response.features // Automatically loads the selected exact tuples.
  }
} finally {
  Naksha.removeStorage(config)
}
```

Configure the current `NakshaContext` with an application ID before opening sessions, as with other Naksha storages. Use `Naksha.useStorage`, not `Naksha.setupStorage`: creation requires explicit write opt-in and super-user authorization, and there are no upgrade scripts or metadata migrations. Leave `upgrade = false`. JDBC URLs may contain credentials; supply them externally, percent-encode reserved characters in URL parameter values, and do not log the configuration.

Initialization reads `xyz_config.space_meta` by default and caches layer mappings. The default exposed catalog is `public`; collection IDs are logical layer IDs, not physical hashed table names. `ReadMaps`, `ReadCollections`, and catalog/collection lookup by ID or number expose virtual metadata. `ImlConfig.schema` and `metadataSchema` can override these schemas. Writer commits publish refreshed metadata; write-enabled nontransactional reads check committed IML log revisions. Recreate storage for external discovery changes without those revisions. Numeric identity collisions fail initialization rather than aliasing layers.

Ordinary read connections are opened per read, configured with read-only transactions and session timeouts, then rolled back and closed, serialized per storage. Sticky writer and session-lock connections are additional connections; there is no connection pool. Closing a session is idempotent; removing or replacing a storage closes sessions and clears tuple cache entries and pagination handles. Shutdown attempts all session closures and cache/handle cleanup, aggregating failures rather than stopping at the first one. Read-only database users should also have SELECT-only permissions; writers need the documented DDL and mutation privileges. PostGIS must already be available. Production `IWriteSession` is supported with explicit opt-in; streaming is unsupported.

## Interactive Map Layers Library

`here-naksha-lib-iml` is a JVM-only Kotlin implementation of Naksha's `IStorage` for PostgreSQL-backed Interactive Map Layers (IML). It is read-only by default. Production `ImlWriteSession` support is opt-in through `ImlConfig.writeEnabled = true`; this is not just the `LocalImlWriter` test helper. Use the library through `Naksha.useStorage(config)`. It does not require service or CLI integration, and supporting storage-interface methods does not imply support for every Naksha capability.

### Configuration And Bootstrap

Supply a PostgreSQL JDBC URL externally, for example through `IML_DB_URL`. Do not log or persist URL credentials unprotected. PostgreSQL and PostGIS must already be available; IML installs no extensions, SQL/server functions, JavaScript, or upgrade scripts.

- `config.schema` defaults to `public` and identifies the default catalog's physical layer schema. Without bootstrap, this schema must already exist.
- `config.metadataSchema` defaults to `xyz_config` and must contain a compatible `space_meta` table, unless explicitly bootstrapped.
- `config.writeEnabled` defaults to `false`. Reads do not create missing schemas, metadata, or transaction logs.
- With `writeEnabled = true` and `create = true`, initialization creates the configured layer schema, metadata schema, and `space_meta` with `IF NOT EXISTS`, requiring `NakshaContext.su = true`. This is bootstrap, not migration of an incompatible existing layout. Leave `upgrade = false`; do not use `Naksha.setupStorage`, which forces an upgrade too.
- The first `writer.useTransaction()` (also invoked by writes and transaction-lock acquisition) durably creates `metadataSchema.iml_catalogs`, `iml_transactions`, and `iml_version_seq` if absent. Control-table/sequence creation survives user rollback; allocated manual sequence values are not reused after rollback. The JDBC role needs the corresponding DDL and mutation privileges even for a feature-only writer's first transaction.

### Local Write Example

Run this Kotlin entry point in a JVM application with `here-naksha-lib-iml` and its dependencies on the classpath. Point `IML_DB_URL` only at an intended local PostgreSQL database with PostGIS already enabled, using a database role permitted to create schemas, tables, sequences, and indexes:

```bash
export IML_DB_URL='jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password'
```

The example bootstraps randomly named local schemas, then creates a separate map/catalog, collection, and feature in one ordinary `WriteRequest`. Names are short ASCII identifiers, avoiding collisions and identifier limits. Naksha super-user authorization is separate from PostgreSQL permissions; it is enabled before initialization and writer creation, then restored.

```kotlin
@file:OptIn(naksha.model.v30_experimental::class)

import java.util.UUID
import naksha.geo.SpPoint
import naksha.iml.ImlConfig
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest

fun main() {
  val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
  val config = ImlConfig("iml_demo_$suffix", requireNotNull(System.getenv("IML_DB_URL"))).apply {
    schema = "imld_$suffix"
    metadataSchema = "imlc_$suffix"
    writeEnabled = true
    create = true
    upgrade = false
  }
  val context = NakshaContext.currentContext().withAppId("iml-local-example")
  val previousSu = context.su
  context.su = true
  try {
    val storage = Naksha.useStorage(config)
    val map = NakshaCatalog("imlm_$suffix")
    val collection = NakshaCollection("places", map.id)
    val feature = NakshaFeature("place_$suffix").withGeometry(SpPoint(1.0, 2.0, 3.0))
    feature.properties.setRaw("name", "Local IML example")
    storage.newWriteSession().use { writer ->
      val response = writer.executeWrite(WriteRequest().apply {
        returnResults = true
        add(Write().createMap(map))
        add(Write().createCollection(collection))
        add(Write().createFeature(collection, feature))
      }) as SuccessResponse
      writer.commit()
      println("Committed ${response.length} results in ${map.id}/${collection.id}")
    }
  } finally {
    try {
      Naksha.removeStorage(config)
    } finally {
      context.su = previousSu
    }
  }
}
```

`use` closes the writer and rolls back any uncommitted work on failure. `Naksha.removeStorage` closes library resources and clears caches/handles; it does not drop the committed local schemas or data. Keep this example away from remote inspection databases. For an existing IML database, use its physical `schema` and `metadataSchema`, leave `create = false`, and target registered logical collection IDs rather than hashed table names.

### Supported Contracts

- Read sessions support catalog/collection discovery, HEAD reads, historical snapshots and bounded per-ID revision history, exact tuple loading, supported typed member predicates, ID/author/tag queries, PostGIS intersections/buffers, ordering, result filters, and bounded detached continuation handles. Declared effective custom members are materialized and queryable; arbitrary modern JSON-property paths are not implied.
- Write sessions support `executeWrite` with CREATE, UPDATE, UPSERT, DELETE, and PURGE for features and catalog/collection metadata, subject to ownership and capability checks. They expose `useTransaction`, `getTransaction`, `commit`, `rollback`, member processors, and transaction-local reads. Feature revisions use manual versions and IML validity intervals; `i` remains a physical revision identity, not a stable logical feature number.
- Administrative catalog/collection mutations require the super-user flag captured when the write session is created. Changing the context afterward does not elevate an existing writer. Feature writes do not require that flag, but still require database permissions and explicit write opt-in.
- Catalog creation uses a new physical schema. Names must satisfy the Naksha model's 42-character limit and PostgreSQL's 63-byte identifier limit. Descriptor updates to an externally managed catalog are allowed only for the registered default `config.schema`; `public`, reserved schemas, and externally managed/default catalog schemas cannot be dropped. Collection mutations update `space_meta`; they are not limited to newly created catalogs.
- Writes use a sticky transaction connection with private metadata/tuple state until commit. Successful commits publish metadata and invalidate caches/handles; external discovery changes without an IML transaction-log revision still require recreating storage. Normal read connections are serialized per storage, but sticky writer and session-lock connections are additional connections; there is no connection pool.
- Each write request has a savepoint: a failed batch rolls back that request while preserving earlier pending writes when recovery succeeds. Transaction version and time are frozen at allocation; tampering with either or savepoint recovery failure makes the transaction rollback-only, and commit cannot publish partial work.
- Named native PostgreSQL advisory locks are available through `acquireSessionLock` and `acquireTransactionLock`, with explicit close. Acquire session locks before starting a transaction. The same named key cannot be held across both lifetimes by one writer. Named transaction-lock contention fails fast with `CONFLICT` rather than blocking on a held lock. Transaction locks end at commit/rollback; session locks survive those boundaries until released or the writer closes.
- `SessionOptions.idleTxTimeout` applies to active database transactions, including sticky write transactions. Dedicated session-lock connections use auto-commit and `idle_in_transaction_session_timeout = 0`, so holding a session lock does not leave an idle transaction subject to that timeout. Statement, lock, and socket timeout setters update live connections.
- Storage shutdown attempts all session closures and cache/handle cleanup, aggregating errors with suppressed failures instead of abandoning remaining cleanup after the first error.
- Standard, unmodified XYZ index definitions have supported index DDL. Custom index definitions, unique-index options, and custom index mappings are explicitly rejected, not silently ignored.
- `ReadTransactions` reads the optional `metadataSchema.iml_transactions` log through the normal read dispatch. Commits record transactions atomically with mutations. Reads never create the log and reject transaction reads when it is absent; this is not reconstruction of external IML transaction history.

### Unsupported Capabilities

Partial fetch/projection, dictionary writes, storage classes, collection partitions other than one, custom/unique index definitions and custom index mappings, extended/composed layers, streaming, and catalog/collection metadata revision history remain unsupported. Transaction-local reads reject both publishing continuation handles and consuming `ReadHandle`; detached continuations are only for nontransactional reads. Reference-tile queries, deprecated GUID selection, arbitrary modern property-path operands, general metadata predicates/order, unsupported spatial transformations, and write hash exclusions are also rejected. No full Naksha storage-capability parity is claimed.

## Supported Reads

`limit` must be positive and is capped by `ImlConfig.hardCap`; when omitted it defaults to that cap.

- HEAD, historical snapshots, and bounded per-ID revision histories, optionally including deleted states.
- All 28 modern `Op` types are handled by the compiler, but only on supported target members, not arbitrary fields. Scalar equality, ranges, membership, null/type tests and string prefixes resolve fixed members: `id`, `author`, `fn`, `version`, `nv`, `action`, `gbfn` (always null), `updated_at`, `created_at`, `author_ts`, `hash`, `here_tile`, `cc`, `app_id`, `origin`, `target`, `cv0`-`cv3`, `cs0`-`cs3`, `ft`, `pn`, `pt`, and `gv`, plus supported declared effective custom members. Values must match the declared member type; no fixed standard scalar member is declared boolean. Numeric-looking IDs remain strings. Compose predicates with `And`, `Or`, and `Not`.
- Geometry intersection on `geo`, including bounding-box polygons, through PostGIS `ST_Intersects`. Chained `SpBuffer` transformations support geography (meters) or geometry units and validated buffer styles; other transformations are unsupported.
- Case-sensitive tag-list containment (`TagListContains`, `TagListContainsAllOf`, `TagListContainsAnyOf`) on `XyzMembers.XyzTags`, plus virtual tag-map operations on `tags` (below). Legacy IDs, spatial/buffer queries, and tag queries are converted too; mixing `queryMembers` with legacy conditions is rejected.
- Whitelisted ordering by ID, author, row/tuple number, or manual version, with deterministic row-number tie breaking.
- Ordinary reads are two-phase: `response.featureTupleList` initially contains identities; `session.loadTuples(...)` materializes exact states in batches, or `response.features` invokes Naksha's normal lazy-loading path. Missing states remain missing rather than resolving to newer revisions. Filters and paging materialize eagerly; `FETCH_ALL or NO_CACHE_BIT` forces immediate source loading instead of cache reuse.

Partial fetch modes, reference-tile (`refTiles`) queries, legacy GUID selection, arbitrary modern property paths, and extended layers are unsupported. Use exact tuple loading for known tuple identities. Virtual metadata supports listing/legacy ID selection, filters and paging, not history or general member/spatial queries.

## History

Inside a read session, select the state at a source sequence using a packed manual bound, not a raw IML sequence:

```kotlin
import naksha.base.Action
import naksha.base.Version

val response = session.executeRead(ReadFeatures().apply {
  catalogId = "public"
  collectionId = "logical-layer-id-from-space-meta"
  queryMembers = Equals("id", "feature-id")
  queryHistory = true
  version = Version.manual(42, Action.VERSION).number
  // versions = 3 // Instead select up to three matching revisions per ID.
  // minVersion = Version.manual(10, Action.VERSION).number
  limit = 100
}) as SuccessResponse
val features = response.features
```

- With `queryHistory = true` and `versions = 1` (default), a finite upper bound selects the state valid in `[version, next_version)`; absent/HEAD upper bounds select HEAD. An optional `minVersion` also restricts the selected state's source sequence.
- With `versions > 1`, history must be enabled. Both sequence bounds are inclusive; SQL predicates run before per-ID ranking by source sequence, action, then row number descending. Output ordering is separate from this ranking. `limit` caps total returned revisions, not logical IDs.
- Without history, version bounds only filter current HEAD rows; they never fall back to an older state. Bounds accept manual versions, zero, or `Version.HEAD.number`; action bits do not narrow sequence boundaries. Automatic versions are rejected. Request setters retain the model's bound-normalization behavior.
- Unless `queryDeleted = true`, snapshots omit tombstones without reviving older states. Multi-version reads suppress an ID when its newest matching candidate is deleted, and omit older tombstones without refilling their rank slots.

## Tags And Filters

Tag-list predicates use JSONB containment on `jsondata->'properties'->'@ns:com:here:xyz'->'tags'`, compatible with a GIN expression index on that exact expression. Collection creation/update always ensures unconditional leading-column B-tree coverage for `id` and `i`, GiST on `geo`, and GIN on the tag expression, even with empty optional index declarations. Existing valid ID/row-number primary or unique indexes are reused instead of duplicated. Geometry/tag indexes cannot be removed by omitting optional declarations. Read-only discovery never creates indexes; existing external collections require an explicit write-enabled collection update to install missing indexes. The inspected remote test table has no tag index and was left unchanged.

`ImlIndexPlansTest` verifies actual selection SQL including ordering/limits and second-phase exact loading using `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` on a selective 20,000-row local fixture, without disabling sequential scans. Default plans use ID/row-number B-tree, geometry GiST, and tag GIN indexes before and after optional index updates. PostgreSQL may legitimately prefer sequential scans for tiny tables or broad predicates; index availability does not force every query to use one. Virtual tag-map parsing and negated tag conditions do not have the same direct GIN containment guarantee.

The virtual tag map parses the existing array without rewriting it, following `TagNormalizer.splitTag`: bare tags have null values; `key=value` gives trimmed strings; `key:=true` and `key:=1.5` give typed booleans/numbers. Splitting uses the last `=`. `ref_`, `sourceID_`, and `xyz_source_id_` prefixes remain unsplit null-valued keys; invalid typed values retain the entire tag as the key with a string value. Duplicate parsed keys are last-write-wins. Nonstring array entries are ignored; missing/nonarray tags act as an empty map.

Use `TagMapHasKey`, `TagMapHasAnyOf`, `TagMapHasAllOf`, `TagEquals`, `TagIsNull`, `TagGt/Gte/Lt/Lte`, `TagStartsWith`, and `TagMatches` on `tags`. Comparisons are typed and case-sensitive; missing keys do not match, including null tests. `TagMatches` uses PostgreSQL ARE regex syntax, not Java regex. Virtual-map predicates parse array entries per candidate and do not get the same direct containment-index benefit as tag-list predicates; narrow candidates with ID/spatial/other selective predicates where possible.

`resultFilters` receive materialized exact states after JDBC selection has completed, in reverse registration order (LIFO). Ordinary read connections have closed at this point; a writer retains its sticky transaction connection. Filters may reject or replace entries. Nonpaged reads refill toward `limit` in batches of 128, scanning at most `ImlConfig.hardCap` candidates; selective filters can therefore return fewer results despite later matches in the table.

Legacy `query.properties` (or `withPropertyQuery(...)`) runs client-side through the model's `PropertyFilter`, not SQL. It supports `PAnd/POr/PNot` and property paths, including array indices, with the model's subset of operations: existence, null/boolean tests, membership, containment, string equality/prefix, and numeric comparisons. Other property operations are not implied. These filters run after history ranking and share the candidate hard cap; they cannot be combined with `queryMembers`.

## Pagination

Set `returnHandle = true` on the initial read. It materializes and filters up to `hardCap` candidates once, freezes accepted feature bodies, and returns a page of at most `limit` entries. Paging does not scan beyond that initial candidate set.

This detached pagination is nontransactional only. An active write transaction rejects both `returnHandle = true` and `ReadHandle` consumption. Successful writer commits invalidate retained handles.

```kotlin
import naksha.model.request.ReadHandle

var response = session.executeRead(ReadFeatures().apply {
  catalogId = "public"
  collectionId = "logical-layer-id-from-space-meta"
  limit = 100
  returnHandle = true
}) as SuccessResponse
while (true) {
  val features = response.features // Process this page.
  val handle = response.getRaw("handle") as? String ?: break
  response = session.executeRead(ReadHandle(handle).apply {
    returnHandle = true // Required on every continuation to keep paging.
  }) as SuccessResponse
}
```

`SuccessResponse` has no typed handle property. Tokens are storage-local, single-use, and bound to the session options' `appId` and `authToken` (not a separate database authorization mechanism). A new session on the same storage with matching options may continue after the original session closes. A continuation may override `limit`, but may not add result filters. Omitting `returnHandle = true` consumes the token without retaining further pages. Tokens expire five minutes after initial snapshot creation, even while idle; continuation does not extend the TTL. Invalid, foreign, reused or expired tokens fail.

Per storage, retained snapshots are limited to 128 snapshots, 100,000 tuples total, and 32 MiB of estimated retained bytes. Each snapshot builder is independently capped at 16 MiB, even if it fits in one page; concurrent builders do not reserve storage-wide capacity. Accounting is `4 * serialized body bytes + 512` per tuple, not measured JVM heap. Retention charges the whole snapshot until consumed, expired, or storage shutdown; exceeding a budget fails rather than silently truncating. Frozen pages survive source changes and tuple-cache eviction, but snapshot construction is not one shared database transaction.

## Model Mapping

IML `i` becomes the tuple feature-number and identifies a physical revision rather than a logical feature across versions. `version` is packed as `(sourceVersion << 2) | action`, with `I/U/D` mapping to `0/1/2` and unknown operations to `3`. Only manual versions are supported. IML's `Long.MAX_VALUE` HEAD sentinel translates to Naksha's HEAD sentinel.

Relational ID, author and geometry are authoritative, including null geometry/author. Source JSON properties, unknown namespace fields, tags and timestamps are preserved; UUIDs are generated Naksha GUIDs, and action/version metadata is normalized. Z coordinates survive materialization. Source successor/merge UUIDs are removed: the row-level identity cannot safely generate successor addresses across revisions. Query selection and subsequent materialization are eventually consistent, not one shared database snapshot.

Use the response wrappers or `ImlReadSession.loadTuples` to preserve source XYZ metadata. Calling the model's `Tuple.toNakshaFeature()` directly applies the model's own namespace replacement semantics; `Tuple.decodeFeature(null)` preserves the adapter's encoded representation.

## Tests

Run `./gradlew :here-naksha-lib-iml:jvmTest`. Database-free tests always run; database tests are opt-in. No Docker database is started automatically.

- Local integration tests use `NAKSHA_TEST_IML_DB_URL`, a fixture-capable PostgreSQL database with PostGIS already enabled. Validation requires an explicit single loopback host (`localhost`, `127.0.0.1`, or `[::1]`) and rejects implicit/multiple hosts and routing options. Only `user`, `password`, `ApplicationName`, `connectTimeout`, `socketTimeout`, `ssl=false`, and `sslmode=disable` URL properties are allowed. Tests create, seed and drop only randomly named owned schemas. Never use a remote inspection URL here.
- `LocalImlWriter` lives only in `jvmTest`: `put` inserts/updates revisions and `delete` appends tombstones in the isolated local fixture table, preserving history. It is not an `IWriteSession`, shipped writer, or production write API.
- Remote tests use the separate `NAKSHA_TEST_IML_REMOTE_DB_URL` and only read existing data. `ImlRemoteReadTest` also requires `NAKSHA_TEST_IML_REMOTE_LAYER` (logical layer ID); it checks discovery, 100-feature materialization, metadata and combined queries. `ImlRemoteHistoryTest` uses optional `NAKSHA_TEST_IML_REMOTE_HISTORY_LAYER`, defaulting to `hrn:here-dev:data::olp-here:m499-source-01:data-02`, and requires existing historical rows.

List discovered collections read-only using just `NAKSHA_TEST_IML_REMOTE_DB_URL` (no layer variable required):

```sh
./gradlew :here-naksha-lib-iml:jvmTest --tests 'naksha.iml.ImlListCollectionsTest' --info
```

The listing includes the virtual collections collection and prints IDs and a total. Tests lacking their required environment variables are skipped.

Local tests cover production write sessions and `writeEnabled = true` / `create = true` bootstrap, in addition to read fixtures and the test-only helper. The latest combined local and read-only remote verification passed 213 tests with none skipped; JVM JAR and JaCoCo report generation passed.

## Performance And Edge Cases

### Bounding-Box Experiment

`ImlBoundingBoxPerformanceTest.kt` is a dedicated opt-in, read-only timing test. It is skipped unless `NAKSHA_RUN_IML_PERFORMANCE_TEST=true`, even if remote credentials are present. Set `NAKSHA_TEST_IML_REMOTE_DB_URL` to the inspection JDBC URL; `NAKSHA_TEST_IML_REMOTE_LAYER` optionally overrides the calibrated default layer. The fixed Hyderabad boxes are specific to that dataset and may need recalibration for another layer.

```sh
NAKSHA_RUN_IML_PERFORMANCE_TEST=true ./gradlew :here-naksha-lib-iml:jvmTest \
  --tests naksha.iml.ImlBoundingBoxPerformanceTest --rerun --info
```

Run from the repository root with the remote URL already exported, or set both variables in an IntelliJ Gradle test configuration. Allow several minutes. Console output is also available in `build/reports/tests/jvmTest/classes/naksha.iml.ImlBoundingBoxPerformanceTest.html` under this module.

The test runs ten overlapping boxes in each of two scenarios: clearing only this storage's application tuple cache before every query, then clearing once and retaining cached tuples across the ten queries. Each scenario returns approximately 100000 cumulative features, including duplicates. It verifies at least 70% common feature overlap, complete materialization, and that results were not truncated. Setup and a 100-feature warm-up are excluded from timing. No database or OS cache is flushed; GC, network, JIT, and JaCoCo instrumentation affect the results.

Per-query CSV and scenario summaries report tuple-number selection, normal `loadTuples` materialization, a separate fresh decode pass, cache hits observed immediately before loading, repeated/new identities, encoded tuple-body bytes, totals and medians. Materialization already includes attachment decoding, JSON parsing and JBON construction for misses; fresh decoding is an additional re-decode cost and is not added to the reported two-phase total. Cache hits are observations, not instrumented database-fetch counts, because the default cache uses soft references. Encoded bytes count all result tuples, including cache hits, not network traffic.

One verified run returned 102261 results per scenario, 11898 distinct identities and 8509 identities common to every box (79.94% of the largest result set):

| Scenario | Selection Total | Materialization Total | Fresh Re-Decode Total | Two-Phase Total | Cache Hits |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cache cleared before each query | 19.529 s | 137.378 s | 8.313 s | 156.907 s | 0.00% |
| Overlap reuse | 19.866 s | 37.358 s | 12.095 s | 57.224 s | 88.37% |

Observed aggregate materialization ratio was 3.677x. The second scenario runs after the first, so database cache warmth and JVM effects also differ. There are no timing-based assertions or claims of repeatable speedup; rerun to obtain numbers for the current environment.

Output also reports features/second for each application phase and scenario totals. A subsequent database-only pass uses `EXPLAIN (ANALYZE, FORMAT JSON, TIMING OFF)` for the production bbox identity selection, a direct full-projection bbox query, and production-style exact loading in 500-pair batches. It measures all exact rows and an ideal overlap-miss workload retaining every previously seen identity. The diagnostic pass runs after the application scenarios, so its server caches are warm and it cannot warm pages between their measurements.

The verified database pass produced these aggregate rates across ten boxes:

| Database Query | Rows | Execution Time | Execution Rows/s | Planning + Execution Rows/s |
| --- | ---: | ---: | ---: | ---: |
| Bbox tuple identities | 102261 | 301.188 ms | 339526 | 255291 |
| Bbox full projection | 102261 | 589.620 ms | 173435 | 172642 |
| Exact loading, all rows | 102261 | 1213.498 ms | 84270 | 61260 |
| Exact loading, ideal overlap misses | 11898 | 147.656 ms | 80579 | 59138 |

In that same run, application two-phase throughput was 666 features/s with cache cleared per query and 2330 features/s with overlap reuse. Database rates are executor rows/second from PostgreSQL, not delivered feature throughput. EXPLAIN discards result rows, so normal wire serialization, JDBC/network transfer and JVM decoding are excluded; toasted JSON may not be fully read when it is only projected. Execution includes EXPLAIN overhead, planning is reported separately, and the full-projection diagnostic is not an implemented one-phase API. Ideal overlap misses are computed from previously seen identities, not actual soft-cache survival.

Writer bootstrap and initial source-version reconciliation run once per storage instance, not once per transaction. Subsequent transactions use the durable allocator; independent external version allocation after preparation can cause an explicit conflict. Recreate storage to reconcile imported versions, or ensure writers cooperate with the adapter's allocator and advisory gate.

Row allocation strategies are cached per physical layer and transaction. Identity and sequence-default columns use their server allocator after one maximum/sequence-state check, advancing a stale sequence without rewinding it. Legacy tables read `max(i)` once and increment locally under the writer gate. All consumers of those allocators must cooperate with the gate; independent external sequence draws are not synchronized by the adapter. Legacy tables without sequences retain their cross-transaction `max(i)` behavior, so purging the highest row can allow its number to be reused later.

For a tested batch of 110 legacy inserts, allocation overhead is one metadata probe and one initial maximum query, rather than one of each per insert. Index-plan tests verify generated timestamp and tag expressions match query predicates and can be used as index conditions. These are query-count and index-eligibility checks, not wall-clock benchmark claims. Collection updates remove obsolete adapter-owned standard indexes only on adapter-created tables; external indexes are untouched.

Transaction callbacks cannot commit, roll back, close the session, or initiate nested writes while a write request is executing. Tombstones retain minimal JSON metadata so timestamps survive history and recreation. Extreme finite floating values expanded by JSONB and empty/mixed geometry collections have dedicated round-trip coverage. Transaction lock leases are explicitly unlocked at transaction boundaries. Discovery refresh uses repeatable-read isolation, and duplicate/older generations cannot overwrite a published snapshot.

Remaining performance opportunities include fewer throwaway read connections, compact/incremental transaction statistics, immutable pre-resolved member metadata, bounded in-flight paging allocations, and measured alternatives to batched OR predicates. These have not been changed without dedicated verification.
