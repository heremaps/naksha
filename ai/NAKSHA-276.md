# NAKSHA-276: Interactive Map Layers Storage

Implement a PostgreSQL-backed `IStorage` for reading Interactive Map Layers from Interactive Map Service. Its database layout differs from the existing PostgreSQL storage. Do not install JavaScript, SQL functions, or database structures.

- [x] Create and register the Kotlin/JVM `here-naksha-lib-iml` module, short name `lib-iml`, following existing module conventions.
- [x] Review the `IStorage` contract and existing PostgreSQL storage for reusable infrastructure.
- [x] Add `ImlConfig`, read-only JDBC connections, and storage/read-session lifecycle infrastructure.
- [x] Add unit and opt-in integration tests; verify creation and read-session opening against the supplied temporary local database.
- [x] Inspect the actual Interactive Map Layers database layout and confirm the required read operations. Keep credentials out of version control.
- [x] Implement IML layer reads, metadata lookup, and tuple loading against the confirmed layout.
- [x] Add tests for supported layer read operations.

The temporary local test database is supplied externally using `NAKSHA_TEST_IML_DB_URL`. The separate read-only remote smoke test uses `NAKSHA_TEST_IML_REMOTE_DB_URL` and `NAKSHA_TEST_IML_REMOTE_LAYER`. Initial scope is reading Interactive Map Layers; write support is not planned.

## Database
The test-database is available like following:

- host: `read.db3.eu-west-1.iml.sit.iddev.aws.in.here.com`
- port: `5432`
- user: `ai_inspect`
- password: [read from here](../vault/.pgpassword)
- database: `xyz`
- schema: `public`
- table: `1242e83b25c1e30c24afafb62e98af4b`

## Model Mapping
We will map the columns like following:

- `id` —> `StandardMembers.Id`
- `version` —> `StandardMembers.Version`
- `next_version` —> `StandardMembers.NextVersion`
- `operation` —> `StandardMembers.Action`
- `author` —> `XyzMember.Author`
- `jsondata` —> `StandardMembers.Feature`
- `geo` —> `StandardMembers.Geometry`
- `i` —> `StandardMembers.FeatureNumber`

There are some issue. For example, `i` is a sequence, so a unique row number. This does not make it very useful as real feature-number, but for our use-case it should be sufficient. So, we can create `TupleNumber` with this mapping. When we receive identifiers, we will have to rely upon `id` and when we receive a `TupleNumber` we translate the feature-number to `i`, which allows us to find the row.

As said, this mapping is not perfect, but should allow us a read-only implementation very well. Specifically, because the `Guid` as well contains the feature-id.

## Agreed Implementation Scope

- Change only `lib-iml`, its tests/documentation, and this work log. Do not integrate the service or CLI or change other Naksha modules.
- Read and cache `xyz_config.space_meta` in memory during storage initialization. Expose mapped layers from `public`, with schema as catalog ID and logical layer ID as collection ID. Refresh metadata by recreating the storage; no background refresh.
- Support standalone layers only. Composition and extended-layer behavior belong to Naksha Views.
- Support manual versions only: encode `(imlVersion << 2) | action`, mapping `I` to CREATE (0), `U` to UPDATE (1), `D` to DELETE (2), and unknown operations to VERSION (3). Translate the Naksha HEAD sentinel to/from IML's `9223372036854775807` sentinel.
- Use `i` as the tuple feature-number, accepting that it identifies a row rather than a logical feature across revisions. ID predicates always use the source `id` column, including numeric-looking IDs. Bare tuple reloading must work without relying on the ID inside a GUID.
- Required priority: HEAD reads, ID filtering, bounding-box intersection, author filtering, and two-phase queries selecting tuple identities then loading exact tuples. Return synthetic catalog/collection metadata through the normal storage interfaces.
- Preserve source feature properties, XYZ timestamps, tags, and unrelated namespace metadata. Override relational ID/author/geometry and generated Naksha identity/version/action metadata, including GUID-based UUIDs. Preserve Z coordinates. Do not retain source IML UUIDs as identities.
- Exact states that disappear remain missing; never substitute the newest revision. Eventual consistency between selection and tuple materialization is acceptable.
- Reject unsupported query capabilities explicitly rather than ignoring them. HEAD support is required; tag search is optional afterward, using JSONB-compatible queries, and history is optional only after tags and all required behavior/tests are working.
- The remote database stays strictly read-only, with sequential tests and bounded connection usage. The inspection role has a four-connection limit.
- Synthetic fixtures may create/delete isolated schemas and tables in the supplied local test database. No production code installs scripts, functions, extensions, or schema objects.
- Acceptance is a tested reusable `lib-iml` library, usable like `lib-psql`. No application integration is required.

## Database Inspection Results

TLS connectivity and read-only access were verified. The first 100 rows ordered by `i` contain 100 distinct IDs and row numbers, all operation `I`, version `1`, and HEAD `next_version`. All JSON IDs match the relational IDs. Geometry is separate from JSON: all sampled geometries are 3D LineStrings in SRID 4326, and XYZ creation/update timestamps are present. No historical or update/delete rows were found in the test table. The metadata table maps its physical table name to a logical layer HRN. Naksha feature bytes require JBON encoding rather than raw JSONB bytes.

## Implementation Results

- Cached layer discovery and virtual catalog/collection metadata are implemented, including identity collision checks and safe SQL identifier quoting.
- HEAD selection supports modern and legacy ID/spatial conditions, author predicates, logical composition, whitelisted sorting, limits, and optional deleted rows. Source IDs remain strings even when numeric-looking.
- Two-phase selection and batched exact tuple loading are implemented, including null entries, range bounds, duplicates, cache hits, cross-session lazy loading, row movement into history, missing states, and cache invalidation when a storage is replaced. Forced no-cache reads materialize immediately and do not retry source misses through the cache.
- Source properties and XYZ metadata are preserved through the adapter's response and loading paths. Geometry and author overrides, GUIDs, manual version/action encoding, 3D coordinates, and bodyless tombstones are tested.
- Optional tag-list containment/all/any queries are implemented using JSONB containment. The inspected remote test table has no JSONB expression index for tags; no database indexes are installed, and indexed tag performance is not claimed.
- Local PostgreSQL was prepared with a public schema and the already available PostGIS extension. Fixtures create/drop only random owned schemas. Remote operations remain strictly read-only.
- Final verification passed all 60 module tests with zero failures and zero skipped tests, including the remote smoke test selecting/materializing 100 rows and checking ID/bbox/author reads. JVM JAR generation and the module's JaCoCo coverage verification also passed.
- Remote verification exposed an index-planning problem with null-safe ID equality. Predicates now retain ordinary index-friendly comparisons while preserving explicit null semantics.

## Remaining Limitations

Historical query selection, version ranges, pagination/handles, result-filter callbacks, partial fetch modes, arbitrary JSON-property filters, tag-map operations, spatial transformations, extended layers, writes, and streaming remain explicitly unsupported. Exact tuple loading can still retrieve a selected revision after it becomes historical. Metadata is refreshed only by recreating storage. Connections are serialized per storage; no connection pool is implemented. The row-level `i` compromise does not provide stable same-feature identity or successor GUIDs across revisions. The module README documents usage and supported behavior.
