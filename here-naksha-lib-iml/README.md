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

Connections are opened per read, configured with read-only transactions and session timeouts, then rolled back and closed. Each storage uses at most one connection at a time; there is no connection pool. Closing a session is idempotent; removing or replacing a storage closes its sessions and clears that storage's global tuple cache entries. Production database users should also have SELECT-only permissions. PostGIS must already be available; the storage installs nothing.

## Supported Reads

- HEAD reads with optional deleted states, bounded by `limit` and the storage hard cap.
- ID and author equality, membership, prefix and null predicates, composed with `And`, `Or`, and `Not`. Numeric-looking IDs remain strings.
- Geometry intersection, including bounding-box polygons, through standard PostGIS `ST_Intersects`. No spatial transformations or buffering.
- Case-sensitive tag-list containment (`TagListContains`, `TagListContainsAllOf`, `TagListContainsAnyOf`) on `XyzMembers.XyzTags`. Legacy IDs, intersections, and supported tag conditions are converted too; mixing modern and legacy conditions is rejected.
- Whitelisted ordering by ID, author, row/tuple number, or manual version, with deterministic row-number tie breaking.
- Two-phase reads: `response.featureTupleList` initially contains identities; `session.loadTuples(...)` materializes exact states in batches, or `response.features` invokes Naksha's normal lazy-loading path. Missing states remain missing rather than resolving to newer revisions. `FETCH_ALL or NO_CACHE_BIT` forces immediate source loading instead of cache reuse.

Tuple loading can retrieve an exact selected state even after it moves into history. Historical selection, version ranges, query handles/pagination, result-filter callbacks, partial fetch modes, arbitrary property filters, tag-map operations, extended layers, writes, and streaming are explicitly unsupported.

Tag predicates use JSONB containment on `jsondata->'properties'->'@ns:com:here:xyz'->'tags'`, compatible with a GIN expression index on that expression. The inspected remote test table has no such JSONB index; no index or performance guarantee is implied, and no index is created by the adapter.

## Model Mapping

IML `i` becomes the tuple feature-number and identifies a physical revision rather than a logical feature across versions. `version` is packed as `(sourceVersion << 2) | action`, with `I/U/D` mapping to `0/1/2` and unknown operations to `3`. Only manual versions are supported. IML's `Long.MAX_VALUE` HEAD sentinel translates to Naksha's HEAD sentinel.

Relational ID, author and geometry are authoritative, including null geometry/author. Source JSON properties, unknown namespace fields, tags and timestamps are preserved; UUIDs are generated Naksha GUIDs, and action/version metadata is normalized. Z coordinates survive materialization. Source successor/merge UUIDs are removed: the row-level identity cannot safely generate successor addresses across revisions. Query selection and subsequent materialization are eventually consistent, not one shared database snapshot.

Use the response wrappers or `ImlReadSession.loadTuples` to preserve source XYZ metadata. Calling the model's `Tuple.toNakshaFeature()` directly applies the model's own namespace replacement semantics; `Tuple.decodeFeature(null)` preserves the adapter's encoded representation.

## Tests

Run `./gradlew :here-naksha-lib-iml:jvmTest`. Database-free tests always run. Local integration tests run only when `NAKSHA_TEST_IML_DB_URL` points to a fixture-capable local PostgreSQL database with PostGIS already enabled. These tests create, seed and drop only randomly named owned schemas; never point this variable at the remote inspection database. No Docker database is started automatically.

The separate `ImlRemoteReadTest` is strictly read-only and requires `NAKSHA_TEST_IML_REMOTE_DB_URL` and `NAKSHA_TEST_IML_REMOTE_LAYER` (logical layer ID). It verifies discovery, two-phase materialization of 100 features, GUIDs/metadata, by-ID reads, and combined ID/bbox/author reads. Without its environment variables it is skipped.
