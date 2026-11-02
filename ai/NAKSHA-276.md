# NAKSHA-276: Interactive Map Layers Storage

Implement a PostgreSQL-backed `IStorage` for Interactive Map Layers from Interactive Map Service. Its database layout differs from the existing PostgreSQL storage. Reading is the default; production writing is explicitly opt-in. Do not install JavaScript, SQL/server functions, extensions, or upgrade scripts. Opt-in bootstrap and transactional writes may create the documented schemas, metadata/control tables, sequences, layer tables, and supported standard indexes.

- [x] Create and register the Kotlin/JVM `here-naksha-lib-iml` module, short name `lib-iml`, following existing module conventions.
- [x] Review the `IStorage` contract and existing PostgreSQL storage for reusable infrastructure.
- [x] Add `ImlConfig`, read-only JDBC connections, and storage/read-session lifecycle infrastructure.
- [x] Add unit and opt-in integration tests; verify creation and read-session opening against the supplied temporary local database.
- [x] Inspect the actual Interactive Map Layers database layout and confirm the required read operations. Keep credentials out of version control.
- [x] Implement IML layer reads, metadata lookup, and tuple loading against the confirmed layout.
- [x] Add tests for supported layer read operations.

The temporary local test database is supplied externally using `NAKSHA_TEST_IML_DB_URL`. The separate read-only remote smoke test uses `NAKSHA_TEST_IML_REMOTE_DB_URL` and `NAKSHA_TEST_IML_REMOTE_LAYER`. The initial read-only scope was expanded to opt-in production write sessions, verified only against the intended local test database. Remote inspection and smoke tests remain strictly read-only.

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
- Read and cache `metadataSchema.space_meta` in memory during storage initialization (defaults: `metadataSchema = xyz_config`, `schema = public`). Expose schema as catalog ID and logical layer ID as collection ID. Writer commits publish refreshed metadata and invalidate caches/handles; write-enabled nontransactional reads check committed IML log revisions for refresh. External changes without those revisions still require recreating storage; no background refresh.
- Support standalone layers only. Composition and extended-layer behavior belong to Naksha Views.
- Support manual versions only: encode `(imlVersion << 2) | action`, mapping `I` to CREATE (0), `U` to UPDATE (1), `D` to DELETE (2), and unknown operations to VERSION (3). Translate the Naksha HEAD sentinel to/from IML's `9223372036854775807` sentinel.
- Use `i` as the tuple feature-number, accepting that it identifies a row rather than a logical feature across revisions. ID predicates always use the source `id` column, including numeric-looking IDs. Bare tuple reloading must work without relying on the ID inside a GUID.
- Required priority: HEAD reads, ID filtering, bounding-box intersection, author filtering, and two-phase queries selecting tuple identities then loading exact tuples. Return synthetic catalog/collection metadata through the normal storage interfaces.
- Preserve source feature properties, XYZ timestamps, tags, and unrelated namespace metadata. Override relational ID/author/geometry and generated Naksha identity/version/action metadata, including GUID-based UUIDs. Preserve Z coordinates. Do not retain source IML UUIDs as identities.
- Exact states that disappear remain missing; never substitute the newest revision. Eventual consistency between selection and tuple materialization is acceptable.
- Reject unsupported query capabilities explicitly rather than ignoring them. HEAD support is required; tag search is optional afterward, using JSONB-compatible queries, and history is optional only after tags and all required behavior/tests are working.
- The remote database stays strictly read-only, with sequential tests and bounded connection usage. The inspection role has a four-connection limit.
- Synthetic fixtures may create/delete isolated schemas and tables in the supplied local test database. Production remains nonmutating by default; `writeEnabled = true` explicitly permits documented bootstrap and write DDL/data mutations. No production code installs scripts, functions, or extensions.
- Acceptance is a tested reusable `lib-iml` library, usable like `lib-psql`. No application integration is required.

## Database Inspection Results

TLS connectivity and read-only access were verified. The first 100 rows ordered by `i` contain 100 distinct IDs and row numbers, all operation `I`, version `1`, and HEAD `next_version`. All JSON IDs match the relational IDs. Geometry is separate from JSON: all sampled geometries are 3D LineStrings in SRID 4326, and XYZ creation/update timestamps are present. No historical or update/delete rows were found in the test table. The metadata table maps its physical table name to a logical layer HRN. Naksha feature bytes require JBON encoding rather than raw JSONB bytes.

## Initial Read Results

- Cached layer discovery and virtual catalog/collection metadata are implemented, including identity collision checks and safe SQL identifier quoting.
- HEAD selection supports modern and legacy ID/spatial conditions, author predicates, logical composition, whitelisted sorting, limits, and optional deleted rows. Source IDs remain strings even when numeric-looking.
- Two-phase selection and batched exact tuple loading are implemented, including null entries, range bounds, duplicates, cache hits, cross-session lazy loading, row movement into history, missing states, and cache invalidation when a storage is replaced. Forced no-cache reads materialize immediately and do not retry source misses through the cache.
- Source properties and XYZ metadata are preserved through the adapter's response and loading paths. Geometry and author overrides, GUIDs, manual version/action encoding, 3D coordinates, and bodyless tombstones are tested.
- Optional tag-list containment/all/any queries are implemented using JSONB containment. The inspected remote test table has no JSONB expression index for tags; no database indexes are installed, and indexed tag performance is not claimed.
- Local PostgreSQL was prepared with a public schema and the already available PostGIS extension. Fixtures create/drop only random owned schemas. Remote operations remain strictly read-only.
- Initial read-only verification passed all 60 module tests with zero failures and zero skipped tests, including the remote smoke test selecting/materializing 100 rows and checking ID/bbox/author reads. JVM JAR generation and the module's JaCoCo coverage verification also passed. These are historical results, not current production-write totals.
- Remote verification exposed an index-planning problem with null-safe ID equality. Predicates now retain ordinary index-friendly comparisons while preserving explicit null semantics.

## Remaining Limitations

The initial read-only limitations were partly resolved by the follow-up results and production write iteration. Current remaining gaps are partial fetch/projection, dictionary writes, storage classes, multiple collection partitions, custom/unique index definitions and custom index mappings, extended layers, streaming, and catalog/collection metadata revision history. Transaction-local reads explicitly reject continuation-handle publication and consumption. Reference-tile and deprecated GUID selection, arbitrary modern property-path operands, general metadata query predicates/order, unsupported spatial transformations, and write hash exclusions remain rejected. Exact tuple loading can retrieve a selected revision after it becomes historical; the row-level `i` compromise still does not provide stable same-feature identity or successor GUIDs across revisions. Interface-method support is not a claim of full storage-capability parity.

## Follow-Up Plan

The read follow-up below remained strictly read-only against remote IML. Its results are retained as historical context. Production writes are a subsequent opt-in iteration; this documentation update makes no commit.

1. Add a standalone collection-listing test and inspect retained source history.
2. Expand typed member comparisons, virtual XYZ tag-map predicates, and standard PostGIS buffer transformations.
3. Implement historical snapshots, version ranges, and latest-N revisions per logical source ID, with local fixtures and remote history checks.
4. Add result-filter processing and bounded continuation handles where compatible with the model, document unsupported capabilities, and assess separate opt-in local-only writes without enabling remote mutation.

Remote discovery found 1,153 public layer mappings. Retained history was confirmed in `m499-source-01:data-02` (physical table `f84ff9888bc5b30fdd69ddbc0c460797`), including successive `I`/`U` rows around source versions 441-451. History also exists in `org644368758:catalog01:layer01`. Catalog estimates identify other much larger history tables; avoid scanning them during routine tests.

Step 1 completed: `ImlListCollectionsTest` passed remotely and listed 1,154 collections, including the virtual collections collection.

Step 2 completed: typed scalar comparisons and null/boolean type tests resolve fixed standard/XYZ member paths; all modern virtual tag-map operations support parsed keys, typed values, last-write-wins duplicates, legacy conversion, and PostgreSQL regex predicates. Spatial intersections support ordered buffer transformations with geometry/geography units and validated style options. Local integration tests cover numeric tag parsing, duplicate/invalid tags, malformed JSON, deserialized operations and spatial buffers. The full local suite passed; remote-only tests skip when remote credentials are not supplied.

Step 3 completed: historical snapshots and latest-N candidates per logical ID are supported. Bounds are transaction-inclusive manual source sequences (action bits do not narrow boundaries). Without queryHistory, bounds filter only HEAD rows. With one history version, the half-open source validity interval determines the snapshot; multiple versions rank matching candidates by source version/action/row number. Unless queryDeleted is set, a newest matching tombstone suppresses its ID group. Older tombstones are omitted without refilling rank slots. Filters run before ranking. Source retention remains external; missing revisions stay missing. Local history tests and a remote retained-history smoke test pass, including exact materialization and author filtering. Layer descriptors advertise history/deletion read capabilities without guaranteeing retention.

Step 4 completed: LIFO result filters materialize candidate batches and refill accepted results up to the configured candidate hard cap. Legacy property queries run through the model's client-side PropertyFilter, with structural depth/cycle limits. Bounded detached continuation snapshots support ReadHandle across sessions; tokens are single-use, app-ID/auth-token-bound, expire after five minutes even while idle, and are cleared on storage shutdown. Retention is limited to 128 snapshots, 100,000 tuples, and 32 MiB estimated bytes; each snapshot builder has a separate 16 MiB limit. Request another token by setting returnHandle on continuation requests, and read it through response.getRaw("handle"). Capacity is checked after filtering. Byte accounting is an estimate, and concurrent builders are not reserved storage-wide.

The earlier read follow-up added optional local writing only as a test helper. `LocalImlWriter` transactionally appends insert/update/delete revisions in fixture-owned tables, maintains validity intervals, allocates row identities under an advisory lock, rolls back failures, and invalidates cache entries. It remains test-only and is distinct from the subsequent production `ImlWriteSession`. Fixture connections centrally require an explicit loopback endpoint and allowlisted connection parameters before mutations. This cannot prove a local endpoint is not a tunnel; use only the intended local test database. Remote verification remains read-only, and production storage rejects writes unless explicitly enabled.

The earlier read follow-up's combined verification passed 112 tests with no failures or skipped tests, including collection listing, remote bbox/ID/author reads, retained remote history, local advanced queries, filters/continuations, and test-helper rollback/history tests. JVM JAR and coverage report generation passed. These are historical results, not the final production-write verification totals. No other modules were changed in that iteration.

## Production Write Results

- `ImlConfig.writeEnabled` defaults to `false`. When enabled, `newWriteSession` returns a real production `ImlWriteSession` using normal `WriteRequest`/`Write` operations, not the fixture helper. CREATE, UPDATE, UPSERT, DELETE, and PURGE are supported for features and catalog/collection metadata, subject to explicit validation and ownership restrictions.
- Administrative authorization uses `NakshaContext.su` captured at write-session creation. Enable it before creating an administrative writer; later context changes cannot elevate that writer. Feature writes require write opt-in and PostgreSQL permissions, but not Naksha super-user authorization.
- Transactions use sticky JDBC connections, private discovery/tuple state, transaction-local reads, manual versions, relational row identities, and commit/rollback/close lifecycle handling. Successful commits publish discovery and invalidate caches/continuations. Ordinary read connections remain serialized per storage; writer and session-lock connections are additional connections, with no pool.
- The first `useTransaction` durably bootstraps `metadataSchema.iml_catalogs`, `iml_transactions`, and `iml_version_seq` under a native writer lock before the user's transaction. Bootstrap and sequence allocation survive rollback, avoiding reuse of returned identities. No extension, SQL/server function, or JavaScript installation is involved.
- Each request uses a savepoint. Failed requests restore their own database/discovery/statistics changes while retaining prior pending work when savepoint recovery succeeds. Transaction version and time are frozen at allocation. Tampering with either or savepoint recovery failure marks the transaction rollback-only; commit rejects unsafe partial work.
- Named native PostgreSQL advisory locks support explicit release and session/transaction lifetimes. Session locks must be acquired before a transaction starts. Same-key locks across both lifetimes are rejected within one writer. Named transaction-lock contention fails fast with `CONFLICT`. Transaction locks release at commit/rollback; session locks release explicitly or at session close.
- `SessionOptions.idleTxTimeout` applies to active database transactions, including sticky write transactions. Dedicated session-lock connections use auto-commit and an idle-in-transaction timeout of zero, avoiding idle transactions while retaining session locks. Live statement/lock/socket timeout updates are supported.
- Storage shutdown attempts every session closure and cache/handle cleanup, aggregating failures with suppressed errors rather than stopping at the first failure.
- Existing registered layers support feature and collection metadata mutations through external `space_meta`. Descriptor updates of externally managed catalogs are allowed only for the registered default `config.schema`. New catalogs create physical schemas, with both the model's 42-character name limit and PostgreSQL's 63-byte identifier limit enforced. Reserved, public, externally managed, and default catalog schema drops are prevented; owned schema deletion uses RESTRICT against unexpected external objects.
- Declared effective custom members are typed, processed, materialized, and queried. Standard member paths/types remain fixed. Supported standard XYZ index definitions have real index DDL; custom/unique definitions and custom index mappings are explicitly rejected. These rejections are intentional capability boundaries, not ignored configuration.
- `ReadTransactions` is implemented in normal read dispatch over the optional committed `metadataSchema.iml_transactions` log. Commit records and data mutations are atomic. Read-only access never creates the log and rejects transaction-log reads when absent; external IML transaction history is not reconstructed. Catalog/collection descriptor revision history remains unsupported.

## Bootstrap And Usage

`config.schema` defaults to `public`, which must physically exist when bootstrap is not requested. `config.metadataSchema` defaults to `xyz_config`, with an existing compatible `space_meta` required unless bootstrapped. Initialization bootstrap with `config.writeEnabled = true` plus `config.create = true` is implemented and locally tested: it creates the configured layer schema, metadata schema, and `space_meta` with `IF NOT EXISTS`, requiring Naksha super-user authorization and PostgreSQL DDL privileges. PostGIS must already be installed. This does not upgrade incompatible existing metadata; upgrade scripts and metadata migration are unsupported. Leave `upgrade = false` and use `Naksha.useStorage`, not `Naksha.setupStorage`, which forces upgrade as well as creation.

The module [README local write example](../here-naksha-lib-iml/README.md#local-write-example) is a Kotlin JVM entry point using an externally supplied local JDBC URL, short randomly named schemas/IDs, `Write.createMap`, `createCollection`, and `createFeature` in one normal `WriteRequest`, `writer.use`, explicit commit, and `try/finally` removal of storage. Storage removal closes resources but does not delete committed schemas/data. Administrative context is set before initialization/session creation and restored afterward. Do not run mutation examples against remote inspection databases.

## Verification Status

Final verification passed 169 tests with no failures and no skipped tests, including fresh writable setup, normal write-session map/collection/feature mutations, rollback/savepoints, lock lifetimes and loss detection, typed members, transaction-log reads, metadata/cache generation fencing, and existing strictly read-only remote tests. JVM JAR and JaCoCo report generation passed. Mutations were tested only against the local PostgreSQL database; remote writeEnabled remained false. Test-owned fixture schemas were cleaned up. Full capability parity is not claimed: partial projection/fetch, dictionary mutation, partitioned collections, arbitrary custom/unique indices, descriptor revision history, transaction-local continuation handles, reference-tile/GUID selection, and streaming remain explicit limitations. Changes are confined to lib-iml and this work log.

## Exotic Tests And Performance Review

Added 44 tests covering Unicode/quoted IDs, large nested documents, floating-point extremes, empty/mixed geometry collections, same-transaction recreation, tombstone timestamps, overlapping lock leases, callback boundary attacks, allocation variants and stale sequences, initial reconciliation/restart, query counts, and index-plan eligibility. The combined suite now passes 213 tests with zero failures and zero skipped tests; JAR and JaCoCo report generation pass. Mutations remain local-only during verification.

The tests exposed and fixed extreme floating-value loss in JSONB parsing, geometry-collection encoding failures, asynchronous transaction-lock release, callback-induced partial commits, tombstone timestamp loss, and stale sequence-default allocations. Discovery now uses repeatable-read snapshots and rejects equal-generation replacement.

Performance improvements cache allocation strategies per transaction, honor identity and sequence defaults after safe reconciliation, reduce 110 legacy inserts from 110 metadata/maximum queries each to one each, run durable bootstrap/global source-version reconciliation once per storage instance, reuse transaction metadata at commit, align standard timestamp/tag index expressions with query predicates, and remove obsolete adapter-owned indexes conservatively. EXPLAIN checks use enable_seqscan=false to establish eligibility, not default planner preference or timing gains.

Remaining reviewed opportunities are connection validation/refresh round trips, quadratic transaction-statistics rebuilding, mutable lazily resolved shared member metadata, transient paging-builder memory, and OR-query planning overhead. These are documented follow-ups rather than unverified optimizations. Writer startup reconciliation assumes subsequent writers cooperate with the adapter allocator; externally advanced target versions fail explicitly. Legacy row allocation can reuse a purged highest row across transactions and independent external sequence draws are not synchronized by the adapter gate.

## Mandatory Index Verification

Collection creation/update now always ensures valid unconditional leading-column B-tree coverage for id and i, GiST geometry, and expression GIN tag containment. Existing primary/unique indexes are reused for id/i. Required geo/tags indexes survive empty or reduced optional declarations. Read-only discovery never modifies external collections; explicit writable collection update is the repair path for missing indexes.

Four ImlIndexPlansTest cases verify null/empty/subset index declarations, update protection, legacy repair, and default query plans on 20,000 selective rows. Actual request SQL with ordering/limits and exact second-phase tuple SQL use the expected indexes under EXPLAIN ANALYZE without planner overrides. Local ID uses the composite unique index, i/exact pairs use the primary key, geometry uses GiST, and tag containment uses GIN. Remote read-only EXPLAIN confirms ID, i, and geometry indexes; its test table has no tag index and remains unchanged. Broad/negated predicates and virtual tag-map parsing are not guaranteed to use these indexes.

Combined verification passes all 217 tests with zero failures/skips; JVM JAR generation passes. No remote mutations were performed.

## Bounding-Box Performance Experiment

Added standalone `ImlBoundingBoxPerformanceTest`, gated by `NAKSHA_RUN_IML_PERFORMANCE_TEST=true` and requiring the separate remote JDBC URL. Ten fixed overlapping Hyderabad boxes run in two scenarios (20 measured queries): clear application cache before each query, then retain it across overlaps. Both return 102261 cumulative features, 11898 distinct identities, and 8509 identities common to all boxes (79.94% of the largest result set). No source rows are modified.

Verified skip behavior without the opt-in flag and a complete remote performance run. Cold-application-cache selection/materialization totals were 19.529/137.378 seconds (156.907 seconds combined); overlap-reuse totals were 19.866/37.358 seconds (57.224 seconds combined), with 88.37% observed cache hits. Aggregate materialization ratio was 3.677x. Additional fresh-decode totals were 8.313 and 12.095 seconds respectively; normal materialization already decodes during attachment, so fresh decode is reported separately rather than double-counted. The full test took about four minutes under JaCoCo. Results are descriptive, not performance regression thresholds, and include uncontrolled database cache/JVM/GC/network effects. Invocation and measurement caveats are documented in the module README. Production code is unchanged.

The test now prints per-phase features/second and a separate PostgreSQL EXPLAIN ANALYZE diagnostic pass after the application workloads. A verified run measured 339526 executor rows/s for bbox identities, 173435 rows/s for a full-projection bbox query, 84270 rows/s for exact loading of all 102261 rows, and 80579 rows/s for the 11898 ideal overlap misses. Including planning, rates were 255291, 172642, 61260 and 59138 rows/s respectively. Application two-phase throughput in that run was 666 features/s cold and 2330 features/s with overlap reuse. Database-only execution excludes wire serialization, JDBC/network transfer and decoding, uses warm server caches, and may not detoast merely projected JSON. Exact loads use the production batch size and paired predicates; ideal miss counts are identity-based rather than soft-cache observations. The extended opt-in test passed in about five minutes. Production code remains unchanged.
