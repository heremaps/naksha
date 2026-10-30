# lib-iml

`here-naksha-lib-iml` is a JVM-only Kotlin implementation of the Naksha storage interface for read-only access to Interactive Map Layers in PostgreSQL. It uses the PostgreSQL JDBC driver directly and does not install JavaScript, SQL functions, extensions, or database structures.

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

Configure the current `NakshaContext` with an application ID before opening sessions, as with other Naksha storages. Use `Naksha.useStorage`, not `Naksha.setupStorage`: create and upgrade requests are rejected. JDBC URLs may contain credentials; supply them externally, percent-encode reserved characters in URL parameter values, and do not log the configuration.

Initialization reads `xyz_config.space_meta` and caches layer mappings for the lifetime of the storage. The default exposed catalog is `public`; collection IDs are logical layer IDs, not physical hashed table names. `ReadMaps`, `ReadCollections`, and catalog/collection lookup by ID or number expose virtual metadata. `ImlConfig.schema` and `metadataSchema` can override these schemas, primarily for isolated tests. Recreate the storage to refresh discovery. Numeric identity collisions fail initialization rather than aliasing layers.

Connections are opened per read, configured with read-only transactions and session timeouts, then rolled back and closed. Each storage uses at most one connection at a time; there is no connection pool. Closing a session is idempotent; removing or replacing a storage closes its sessions and clears its tuple cache entries and pagination handles. Production database users should also have SELECT-only permissions. PostGIS must already be available; the storage installs nothing. Production `IWriteSession` and streaming are unsupported.

## Supported Reads

`limit` must be positive and is capped by `ImlConfig.hardCap`; when omitted it defaults to that cap.

- HEAD, historical snapshots, and bounded per-ID revision histories, optionally including deleted states.
- All 28 modern `Op` types are handled by the compiler, but only on supported target members, not arbitrary fields. Scalar equality, ranges, membership, null/type tests and string prefixes resolve fixed members: `id`, `author`, `fn`, `version`, `nv`, `action`, `gbfn` (always null), `updated_at`, `created_at`, `author_ts`, `hash`, `here_tile`, `cc`, `app_id`, `origin`, `target`, `cv0`-`cv3`, `cs0`-`cs3`, `ft`, `pn`, `pt`, and `gv`. Values must match the declared text/numeric type; no scalar member is declared boolean. Numeric-looking IDs remain strings. Compose predicates with `And`, `Or`, and `Not`.
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

Tag-list predicates use JSONB containment on `jsondata->'properties'->'@ns:com:here:xyz'->'tags'`, compatible with a GIN expression index on that exact expression. No index is created or assumed; the inspected remote test table has no such index.

The virtual tag map parses the existing array without rewriting it, following `TagNormalizer.splitTag`: bare tags have null values; `key=value` gives trimmed strings; `key:=true` and `key:=1.5` give typed booleans/numbers. Splitting uses the last `=`. `ref_`, `sourceID_`, and `xyz_source_id_` prefixes remain unsplit null-valued keys; invalid typed values retain the entire tag as the key with a string value. Duplicate parsed keys are last-write-wins. Nonstring array entries are ignored; missing/nonarray tags act as an empty map.

Use `TagMapHasKey`, `TagMapHasAnyOf`, `TagMapHasAllOf`, `TagEquals`, `TagIsNull`, `TagGt/Gte/Lt/Lte`, `TagStartsWith`, and `TagMatches` on `tags`. Comparisons are typed and case-sensitive; missing keys do not match, including null tests. `TagMatches` uses PostgreSQL ARE regex syntax, not Java regex. Virtual-map predicates parse array entries per candidate and do not get the same direct containment-index benefit as tag-list predicates; narrow candidates with ID/spatial/other selective predicates where possible.

`resultFilters` receive materialized exact states after JDBC selection has closed, in reverse registration order (LIFO). They may reject or replace entries. Nonpaged reads refill toward `limit` in batches of 128, scanning at most `ImlConfig.hardCap` candidates; selective filters can therefore return fewer results despite later matches in the table.

Legacy `query.properties` (or `withPropertyQuery(...)`) runs client-side through the model's `PropertyFilter`, not SQL. It supports `PAnd/POr/PNot` and property paths, including array indices, with the model's subset of operations: existence, null/boolean tests, membership, containment, string equality/prefix, and numeric comparisons. Other property operations are not implied. These filters run after history ranking and share the candidate hard cap; they cannot be combined with `queryMembers`.

## Pagination

Set `returnHandle = true` on the initial read. It materializes and filters up to `hardCap` candidates once, freezes accepted feature bodies, and returns a page of at most `limit` entries. Paging does not scan beyond that initial candidate set.

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
