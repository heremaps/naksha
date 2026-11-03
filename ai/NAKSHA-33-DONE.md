# PgDatabase migration — completed tickets

Completed tickets for [JIRA: NAKSHA-33](https://here-technologies.atlassian.net/browse/NAKSHA-33).
These are moved out of [NAKSHA-33.md](NAKSHA-33.md) so the active workboard stays
flat; they remain listed (struck out) in its overview table. Every ticket here is
`DONE`.

### PLV8 storage attachment and shared maintenance increment

**Commit:** Attach PLV8 storage from schema comments and share Kotlin maintenance.

**Completion:** user-approved increment, `DONE`. Includes credential-free, versioned schema-comment configuration (`readFromSchemaComment`/`writeToSchemaComment`), fresh attach-existing storage/write sessions, the exported Kotlin/JS maintenance entry point, runtime version requirements, native PLV8 API naming, import-alias isolation and member-sort initialization fixes. The user explicitly included staged instruction-file changes in this commit. No pg_cron scheduling or permanent SQL maintenance wrapper is part of this increment.

**Verification:** 13 focused tests passed without skips, including live PostgreSQL 18.6 PLV8 maintenance parity, bigint precision, rollback after success and partial failure, retry, invalid-comment rejection, session-setting preservation and crossed module aliases. Production JS compilation and diff checks passed; temporary databases were removed. General JS tuple-cache operations remain unfinished, and concurrent-session/module-hot-reload policies remain outside this acceptance.

### JVM creation, bootstrap and registry sweep increment

**Commit:** Implement shared Kotlin collection creation and partition maintenance.

**Completion:** user-approved implementation increment across db-04-b/c/d/f/g/h. Parent tickets remain open for remaining storage policies, logical preparation/drop integration, concurrency coverage, PLV8 attachment/parity and scheduling. Do not mark the entire migration DONE.

Shared Kotlin now implements permanent collection creation, mandatory/custom indexes, owner/member comments, HEAD/history/fn topology, immutable registration, retained-leaf repair and deterministic registry-wide sweeps. JVM calls execute directly through JDBC without installed maintenance functions. Storage policy is HEAD fillfactor 50/history 100; unsupported storage classes fail explicitly. Fixed JDBC constructor initialization and prerequisite configuration/member validation defects; clean public-factory bootstrap creates the committed admin roots, identity and sequence. A real write session can run maintenance without a logical feature transaction. Historical PL/pgSQL remains under ai/ only.

**Verification:** eight JDBC maintenance tests, five model regression tests, focused naming/schema/index/config tests, separate clean-bootstrap integration and JS production compilation pass. PostgreSQL 18.6 disposable databases were removed. Three previously documented MemberTest collection-builder failures remain unchanged. No PLV8 runtime parity or concurrency stress is claimed.

**Scheduling decision:** defer pg_cron until its implementation step. Plan one job per Naksha database; respect the cluster-designated cron metadata database while keeping each database's admin/runtime schema local. The current increment does not install cron or require cross-database administrative access.

### db-04-b/d — Index execution and schema immutability increment

**Commit:** Centralize index writes and enforce immutable collection schemas.

**Completion:** approved increment only; db-04-b and db-04-d remain open for collection/maintainer integration and live partition-index verification.

Moved index CREATE/DROP execution into PgDatabase and removed PgIndex execution methods. Legacy table traversal passes an explicit database and delegates writes, retaining bookkeeping until hierarchy removal. PgIndex keeps validation and definition fragments; root-index builds stay synchronous and caller-transaction-owned. Added hidden virtual Index.id with source-identity cache invalidation and serialization exclusion; model and physical descriptors reject invalid custom names. Physical index aliases use a fixed 13-byte budget and prefix i; complete names use dollar separators. Shared naming extensions and constants live in PgDatabase.kt, not PgNames.kt. The 63-byte budget reserves 29 root bytes, 16 history suffix bytes, four performance suffix bytes and 14 index suffix bytes; oversized complete names fail rather than truncate.

Existing collection members and indices are create-only. PgWriter compares corresponding definitions in their declared order using loops and early failure, without serialization/default expansion/sorting/list copies. Nested path/key/order/include changes and reordering are rejected. Removed Write.force and its fluent setter. Normalized PgWriter.kt and PgDatabase.kt indentation to two spaces, preserving embedded SQL strings; added project-wide EditorConfig indentation defaults.

**Verification:** final combined run of IndexIdTest, PgIndexTest, PgDatabaseIndexTest, PgNamingTest and PgCollectionSchemaTest passed, together with JS production compilation. Model JS test-source compilation also passed during implementation. No live database index propagation or concurrent metadata-write tests were run. Full collection creation, shared maintainer integration and transaction/cache migration remain separate outstanding work. No database objects were modified.

### db-04-a — Implement schema operations

**Commit:** Implement transactional schema lookup and creation with logical-name comments.

`PgDatabase` namespace lookup binds the physical name, reads unsigned OID text into Long, and returns -1 for absence. Creation validates final physical identifier size/NUL, obtains a transaction-scoped advisory lock by physical schema name, then returns negative existing OID without modifications or creates/comments the schema and returns positive OID. Writer CREATE/UPSERT callers supply the original logical name. Cursors close; connection and transaction ownership remain with the caller. Schema drop remains db-04-e, including registry cleanup.

**Verification:** three database-free `PgDatabaseSchemaTest` JVM tests pass, covering missing/high OIDs through 4294967295, bindings, create/reuse SQL sequencing, comments, cursor closure, caller transaction preservation and invalid physical identifiers. JVM/JS production compilation and diff checks pass. No database/concurrency integration was run. Serialization covers cooperating creators in open READ COMMITTED transactions; external creators and higher-isolation stale snapshots may raise propagated errors. Logical ownership/collision checks remain with administrative metadata writes.

### db-03 — Finish the physical row and naming contract

**Commit:** Unify identifier aliases and physical naming across storage boundaries.

All children db-03-a through db-03-e are approved and complete. Physical creation options, ordinal conversion/range routing, non-null HEAD next-version and logical/physical naming contracts are established. Production DDL, mutation and read migration remain tracked in db-04..09. Authoritative routing and naming examples remain in the active workboard for implementation reference.

### db-03-e — Establish the root naming rule

**Commit:** Unify identifier aliases and physical naming across storage boundaries.

Added implementation-neutral `IdTextType` and `Id.textAlias`: cumulative classifications, lowercase ALPHABET_ONLY/SAFE, URL-safe tilde, internal dollar naming, strict RESERVED pattern, illegal-name rejection, UTF-8 preservation budgets, and deliberately unchecked fallback prefixes/budgets. Storage mappings use URL_SAFE/25 bytes for schemas/roots with `s$`/`t$` aliases and directly preserve RESERVED names; database names use UNSAFE/42 bytes with `d$`, columns SAFE/42 bytes with `m$`.

`Member.id` is a hidden virtual cache keyed by source name identity, reusing `_id_org` / `_id_val`; Member setters/constructors and collection insertion accept ordinary legal names through UNSAFE. Raw names remain book keys and paths. `PgColumn.id` delegates to the member; `columnName`, quoted identifiers and row SQL labels preserve physical/logical separation. Creation/bootstrap, wrappers, query predicates/projections/order and qualified read/mutation references now share physical mapping. Fixed quoting for case, keywords, embedded quotes and backslashes, DELETE descriptor registration, and cursor descriptor lookup. Documented the general virtual read-only cache pattern in the naksha-proxy skill.

**Verification:** focused Id classification/routing, Member cache/name, physical naming/row/cursor and query-placeholder tests pass; JVM/JS production and model/base JS test compilation pass. Direct Node Id classification/alias checks passed with supplied numeric inputs (environment lacks a text-ID MD5 provider). No database objects were changed. Known legacy member-list validation and end-to-end migration gaps remain explicit. Logical entity comments, transactional alias ownership and final suffix-byte checks are acceptance requirements of db-04 creation/provisioning, not implemented DDL in this naming ticket.

### db-03-d — Make HEAD nv non-null

**Commit:** Preserve non-null HEAD next-version values at the row boundary.

`PgRows.setRow` writes `tuple.nextVersion` explicitly, so unset model next-version becomes the numeric HEAD sentinel and finite successors remain unchanged. `getTuple` no longer replaces a stored NULL with HEAD. `PgColumn.NextVersionColumn` declares `STORAGE PLAIN NOT NULL` and documents HEAD/finite successor semantics. The existing root DDL sketch already declares non-null nv with a HEAD default.

**Verification:** all four `PgRowsTest` JVM tests pass; JS main/production compilation passes. The database-free test covers unset and explicit HEAD, finite successor binding, NULL access without translation, and the NOT NULL declaration. Full tuple reconstruction testing encountered the existing `MemberList.verify` duplicate-mandatory-member failure while constructing `PgCollection`; that unrelated model issue was not changed, so reconstruction is not runtime-covered here. No database changes were made.

Legacy `PgHeadTable` still checks `nv IS NULL`, and UPDATE/UPSERT/DELETE SQL still manufactures NULL HEAD values. These conflict with the new shared column contract and must be replaced in db-04/db-06..09; this ticket does not claim that legacy physical creation/writes are ready. No compatibility mode or schema migration was added.

### db-03-c — Keep feature numbers logical

**Commit:** Keep feature identities logical and store feature-ordinals with aligned partition ranges.

All physical fn storage uses feature-ordinals, regardless of partition count. `PgRowsColumn` keeps raw ordinals internally; normal set/get operators encode/decode logical feature-numbers using `Long.toOrdinalFeatureNumber()` / `Long.fromOrdinalFeatureNumber()` in `LibBase`. Removed redundant `PgUtil.fnToInt8` / `int8ToFn`. `PgRows.values()` and cursor ingestion transfer raw ordinals without conversion. Descriptor-backed fn aliases participate, while other INT64/version values remain unchanged. Tuple reconstruction and `_id` materialization use logical access.

`Id` defines feature-ordinals, inclusive ordinal bounds per partition-number, and inclusive partition-number/ordinal bounds per partition-index, with companion and instance accessors. `Id.partitionIndex` and `TupleNumber.partitionIndex` use contiguous groups, not modulo, validating counts 1..256 and returning 0 for one partition. The agreed seven-partition example remains in [the active routing contract](NAKSHA-33.md#agreed-routing-contract), including `{name}_h{nv >> shift}_p{partitionIndex}` leaf naming.

This supersedes conditional storage statements in db-03-b. Legacy raw-fn sign/modulo DDL, writer SQL, and standalone query/cache bindings still require the db-04/db-06..09 migrations; no end-to-end storage rollout is claimed. Query builders must encode fn search arguments and avoid double conversion of raw bindings.

**Verification:** all 11 `IdTest` tests and the full base JVM suite passed; all three `PgRowsTest` and three `PgFeatureNumberTest` tests passed after switching to the LibBase extensions. Tests cover all partition-number/count combinations, contiguous bounds, signed extremes, ordinal round trips, raw bindings and logical access, aliases and nulls. JS main/production compilation passed. Psql JS test compilation was blocked by JVM-only references in untouched `TupleCacheReadPathTest.kt`. No database objects were changed.

### db-03-a — Validate create-only partition options

**Commit:** Validate collection creation options and add physical feature-number conversion helpers.

`PgCollectionCreateOptions` validates partition count 1..256, the existing model shift contract 12..41, retain/backward/forward relationships, checked retention multiplication, TOAST bounds, tablespace strings and storage class before DDL. Validation also runs on data-class copies and history-OFF settings. Aligned finite range start/end helpers exclude HEAD from finite inputs and clip the final exclusive upper bound at HEAD.

Retain/backward/forward remain model settings, not an automatic deletion policy; production maintainer coverage/version anchoring remains db-04-g.

**Verification:** all three `PgCollectionCreateOptionsTest` tests pass on JVM and `:here-naksha-lib-psql:compileKotlinJs` passes. No database changes were needed.

### db-03-b — Add fn conversion helpers and init SQL

**Commit:** Validate collection creation options and add physical feature-number conversion helpers.

Implemented `PgUtil.fnToInt8` and `int8ToFn` using `Platform.swapLong` and `PlatformUtil.swapSign`, plus schema-qualified immutable/strict/parallel-safe equivalents in `naksha.sql`, installed by the existing bootstrap step. No separate SQL resource or bootstrap call is needed. Corrected the original SQL sketch in `partman.md`, which reconstructed int8send bytes instead of reversing them.

Conversion ownership: apply once when binding logical fn to a performance-partitioned physical column (writes, ID/GUID selections, predicates, cache loads); invert once when decoding that column or physical result aliases. PgRows and the persistence boundary own transport conversion, not the model/tuple/cache identity. Do not also wrap already-converted bindings in SQL conversion functions. SQL helpers serve direct SQL callers. Unpartitioned fn storage remains logical. Wiring writers/readers is db-03-c/db-06..09; these helpers alone do not change storage or cached tuples.

`Platform.swapLong/swapInt/swapShort` use JVM reverseBytes intrinsics and the existing shared JS DataView buffer with reversed endianness. JS `swapLong` uses native `setBigInt64`/`getBigInt64`. `PlatformUtil.swapSign` overloads cover Long/Int/Short/Byte, with distinct JS names `swapSignLong/Int/Short/Byte`. No per-call JS buffers are allocated.

**Verification:** three fn tests, three option tests and the primitive swap tests pass on JVM; JS main and test compilation pass. Fn tests cover known vectors, signed extremes, 10,000 seeded round trips, all routing bytes and partition counts 1..256. The rollback-only `NAKSHA-33-fn-test.sql` passed against PostgreSQL 18.6 before SQL resource consolidation; it now checks installed functions and requires bootstrap first. No retained database objects or sample data were changed. The configured Gradle Node test task is skipped; direct production-export checks passed 10,000 independent vectors and exhaustive short/byte sign flips. After the native JS Long refactor, the production JS build and 10,000 reference/round-trip vectors passed again.

## db-00 — Restore a compilable baseline

**Commit:** Restore compilable psql baseline and rebase import fixes.

- **db-00-a:** Adapted stale identifier/member/transaction APIs in psql writers, row helpers,
  tests, and downstream Java callers. Added a test-only `adminConnection` helper
  using `newConnection`; existing tests remain included.
- **db-00-b:** Temporarily excluded superseded implementations through the psql Gradle source
  sets, preserving the source files for reference:
  - JVM: `PgAdminCatalogGc.jvm.kt`, `PsqlAdminCatalog.kt`, `PsqlCatalog.kt`,
    `PsqlCollection.kt`, `PsqlStorageListener.kt`.
  - JS: `PgAdminCatalogManager.js.kt`, `Plv8Cluster.kt`, `Plv8Cursor.kt`,
    `Plv8Plan.kt`, `Plv8Storage.kt`.
- **db-00-c:** Legacy writers temporarily construct HEAD/history descriptors locally rather
  than restoring removed fields on `PgCollection`. Remove these adapters in
  db-06..db-09 together with the legacy hierarchy and Gradle exclusions.
- **db-00-d:** Legacy table DDL temporarily uses the existing 2048-byte TOAST default and no
  explicit tablespace. Restore supported physical settings in db-04.
- **db-00-e:** Added required database accessors to storage implementations/test doubles.
  `NHAdminStorage` delegates to its physical storage; virtual/remote/generated
  storages explicitly throw unsupported-operation until their database semantics
  are implemented.
- **db-00-f:** Corrected `MemberType` initialization validation: byte alignments are positive
  powers of two shared by multiple types, not contiguous sort-order identifiers.
  The old check prevented even the database-independent `PgRowsTest` from starting.

**Verification:** all configured JVM main/test Kotlin compilation tasks and JS
main compilation tasks pass (including Java compilation dependencies):

```bash
NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew compileKotlinJvm compileTestKotlinJvm compileKotlinJs :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgRowsTest' --tests 'naksha.psql.PsqlInstanceTest' --tests 'naksha.psql.PsqlQueryParsingTest' --tests 'naksha.psql.PgExceptionMapperTest' --console=plain
```

Result: **BUILD SUCCESSFUL**, all **21 selected tests passed**. Database-backed
integration tests were not run: the schema/row methods in `PgDatabase` remain
unfinished, and bootstrap/write functionality requires the tickets in
`NAKSHA-33.md`.

### db-01 — Agree the database boundary and operation contracts

Parent ticket; the work is decomposed into `db-01-a` … `db-01-g`, each of which
depends on this parent. **Done when:** the revised tuple contract, error details,
rollback ownership, and behavior matrix are approved; immutable wrapper/model and
API changes compile. Implementation starts only after review of this revised plan
and resolution of the relevant decisions below. db-01-a/b remain historical
completed steps; their mutation contract is superseded by db-01-c..db-01-g.

**Completion:** `DONE`; all children db-01-a through db-01-g are complete.
**Commit:** Archive completed database contract and partition-maintenance verification tickets.

### db-01-a — Finalize operation signatures

**Historical contract:** the row-mutation signatures/results below describe the
completed db-01-a implementation, not the new target. The tuple-based contract
in db-01-c supersedes its `PgRows`/`NakshaError?` mutation API. Schema/index and
query preparation contracts remain applicable unless explicitly revised.

All operations receive the caller's `PgConnection`. Schema/table arguments are
unquoted physical names; quoting and qualification belong to `PgDatabase`.
Operations do not commit or roll back the caller's transaction. Logical
identifier collision checks and internal collection orchestration stay in the
caller; creating a schema does not implicitly create Naksha metadata tables.

| Operation | Input beyond connection | Result |
| --- | --- | --- |
| `catalog_get_schema_oid` | schema name | `Long`: unsigned OID, `-1` when absent |
| `catalog_get_or_create_schema` | schema name | `Long`: positive OID when created, negative OID when already present (existing convention retained) |
| `catalog_drop_schema` | schema name | `Unit`; cascading, absent schema is allowed |
| `collection_create_table_if_not_exists` | schema, root table, `Array<PgColumn>`, `Array<PgIndex>`, physical creation options | `Unit`; existing root is left in place |
| `collection_drop_if_exists` | schema, root table | `Unit`; cascading, absent table is allowed |
| `collection_create_index` / `collection_drop_index` | schema, root table, `PgIndex` | `Unit`; includes configuration for future partitions; drop allows an absent index |
| `rows_insert` / `rows_upsert` / `rows_update` | schema, root table, `PgRows`, `storeHistory` | `NakshaError?`: `null` on success; updates supplied rows in place |
| `rows_purge` | schema, root table, selection `PgRows`, transaction version, `storeHistory` | `NakshaError?`; updates supplied rows with previous states/outcomes |
| `rows_delete_keep_old_state` | schema, root table, selection `PgRows`, payload `Array<PgColumn>`, transaction version, `storeHistory` | `NakshaError?`; updates supplied rows, including preserved payload |
| `rows_delete_with_new_state` | schema, root table, new-state `PgRows`, `storeHistory` | `NakshaError?`; updates supplied rows; support decision remains in db-01-e |
| `rows_delete_without_state` | schema, root table, selection `PgRows`, transaction version, `storeHistory` | `NakshaError?`; updates supplied rows; support decision remains in db-01-e |
| `rows_query` | schema/table error context, SQL text, argument type names | `PgPlan`; caller binds arguments and consumes/closes cursors and plan |

Mutation methods take explicit selection rows rather than `Array<Id>` or nullable
selection. This leaves room for expected-version/correlation columns without
putting identifiers, `Write`, or `Tuple` business logic in the physical API.
Mutation results are materialized into the supplied `PgRows`, so a writer can
validate outcomes and rebuild tuples without retaining a database cursor. All
row mutations return `null` on success or `NakshaError` on failure, allowing the
caller to choose whether to throw. Errors may follow partial SQL/row-buffer
changes: the caller must roll back the operation before continuing and must not
interpret partially updated rows as successful results. Returning an error does
not repair a failed PostgreSQL transaction. Query results remain streamed through
`PgPlan`. Mutation input rows contain successor versions where applicable;
selection-only delete/purge operations receive their transaction version explicitly.

CREATE first purges matching HEAD states only if their action is DELETE, then
inserts the new states. Live HEAD states are not purged and produce a conflict.
History-enabled operations archive predecessors by changing `nv`; history-OFF
operations discard purged states or replace existing HEAD rows in place.
The function KDoc describes each operation, every argument, in-place result
handling, and caller-owned transaction/error handling. Remaining delete-policy
decisions are still tracked under db-01-e.

Scope:

- Corrected the creation-method typo at the declaration and all three callers.
- Added the missing index entry points and made all unused row-operation stubs
  explicitly throw `TODO` instead of silently doing nothing.
- Defined return types and input categories before operation writers switch over.
  Existing writers continue using their current SQL pending db-06..db-08.
- db-01-b expands the original `shift`/`partitions` parameters to the full option
  set described below. Exact row columns, expected-version representation,
  diagnostics, and behavior rules remain db-01-c..db-01-e.

Verification: **BUILD SUCCESSFUL** for the following command; `git diff --check`
also passes. No runtime tests were needed for these declarations and unused stubs.

```bash
./gradlew :here-naksha-lib-psql:compileKotlinJvm :here-naksha-lib-psql:compileKotlinJs :here-naksha-lib-psql:compileTestKotlinJvm --console=plain
```

### db-01-b — Pass physical creation options

`collection_create_table_if_not_exists` now takes explicit column/index arrays
and an immutable, internal `PgCollectionCreateOptions` value containing:

- `storeHistory`, `partitions`, `shift`, `retain`, `backward`, and `forward`;
- `storageClass`, an unquoted nullable `tablespace`, and `toastTupleTarget`.

`PgCollection.creationOptions` snapshots collection settings and maps the storage
class to `PgConfig.ephemeral_tablespace`, `brittle_tablespace`, or
`temp_tablespace`; other classes use PostgreSQL's default tablespace. The helper
keeps logical-to-physical configuration mapping outside `PgDatabase`. The options
value contains only settings, with no hierarchy, connection, or executable SQL.

Writer CREATE/UPSERT and JVM admin bootstrap use the same mapping and the
initialized database's maximum tuple size. No transaction version is passed to
collection creation. pg_partman owns history and performance partition
provisioning; Naksha configures its interval, coverage, retention, and
subpartitioning. The supported pg_partman configuration is verified in db-02,
including how backward coverage is expressed without a caller-selected start.

The creation KDoc documents history-OFF, initial coverage, retention, and
create-only semantics. Validation/range arithmetic remain in db-03-a; physical
DDL, persistence support, tablespace/TOAST application to leaves, and future
partition provisioning remain in db-04. The creation method is still a DDL stub.

Verification: **BUILD SUCCESSFUL** for JVM/JS main and JVM test-source compilation
using the db-01-a command above; `git diff --check` passes. No runtime tests were
run for this parameter-wiring change.

### db-01-c — Revise the tuple persistence contract

This section records the user's new direction. It changes the planned API, not
production code in this documentation change.

#### Confirmed direction

- Remove the mutable `previousTupleNumber` field from `Tuple`. Cached tuples
  represent immutable states and must not carry mutable operation expectations.
- Introduce an immutable model wrapper (working shape):

  ```kotlin
  data class NewTuple(
      val tuple: Tuple,
      val previousTupleNumber: TupleNumber? = null
  )
  ```

  The wrapper holds a tuple reference plus the expected predecessor. It has no
  mutable fields; additional operation metadata can be added later. It is not a
  new persisted tuple format or a cache entry. Migration must cover the existing
  `Tuple` constructor/copy usages as well as future encoding paths; current
  UPDATE/UPSERT writers attach predecessor information through `Tuple.copy`.
- Change the new-state encoding entry points `Tuple.encodeFeature`,
  `Tuple.encodeObject`, and `Tuple.encodeRaw` to return `NewTuple`. The encoder
  already extracts and checks the source state's tuple number (`prevTn`); it
  must return that information in `NewTuple.previousTupleNumber` together with
  the encoded `tuple`, rather than attaching it to a cached/persisted `Tuple`.
  Forwarding overloads preserve the wrapper from the underlying encoder.
  Existing decoding/loading APIs continue returning `Tuple` because they read
  persisted states, not new mutation instructions. Callers that only need the
  encoded state (for example admin bootstrap) explicitly use `.tuple`.
  Resolve the existing `atomic` hint's effect on predecessor metadata in db-01-d:
  unconditional encoding must not accidentally become atomic merely because
  the source feature contains a valid old tuple number.
- A non-null `NewTuple.previousTupleNumber` requests an atomic UPDATE and must
  be valid for the same database, catalog, collection, and feature number.
  Reject an invalid supplied predecessor as an illegal argument; only null
  selects unconditional UPDATE. No separate atomic flag is needed at this
  persistence boundary. Request adapters may still accept their existing flags
  and translate them into wrapper metadata.
- Atomic version comparisons ignore the two action bits by default:
  `(expected.version and -4L) == (actual.version and -4L)`. UPDATE/UPSERT accept
  either CREATE or UPDATE HEAD states at that version; a client need not know
  which live action was stored. This does not relax the requirement for a live
  HEAD, nor alter action bits stored in tuples or reported in result/error data.
  Check actions separately when operation semantics require it. In particular,
  PURGE requires DELETE action and has no atomic/expected-version variant;
  live states are left unchanged and reported to the caller. Any additional action-sensitive
  exception must be explicitly specified for that operation.
- Operation failures construct a `NakshaError`, wrap it in `NakshaException`,
  and throw. No nullable-error return and no success/failure union result.
  SQL causes/context must survive exception mapping. Extend `NakshaError` only
  with details needed to describe actionable conflicts.
- `PgDatabase` takes new states and returns final persisted states, with
  immutable `NewTuple` result wrappers for UPDATE/UPSERT. `PgRows`, helper columns, statement results, and row
  correlation are internal. Inputs and cached tuples are never mutated.
- INSERT requires every input `previousTupleNumber` to be null; otherwise throw
  an illegal-argument `NakshaException` carrying `NakshaError.ILLEGAL_ARGUMENT`.
  Validate this before purging or inserting. INSERT returns the given tuple states.
- UPDATE returns `NewTuple` with `previousTupleNumber` set to the actual live
  HEAD state replaced, including for unconditional UPDATE. The returned `tuple`
  is the supplied fully encoded new state. Input predecessor metadata is an expectation; output metadata
  describes the predecessor actually replaced.
- UPSERT returns `NewTuple` with `previousTupleNumber` set when UPDATE was
  performed, and null when INSERT was performed. Re-creation after purging a
  DELETE HEAD counts as INSERT, so the result predecessor is null in that case.
  The final tuple may differ from input due to branch-specific state changes.
  Returned tuples/wrappers may reuse input references only when all values match;
  inputs and cached tuples are never modified to populate result metadata.
- UPSERT has two phases. First validate any supplied predecessor identifies the
  same feature in the same target; an invalid/foreign predecessor is an illegal
  argument even if insertion would succeed. Then attempt INSERT, replacing any
  DELETE HEAD state, without using the predecessor as a condition. Only when
  insertion cannot proceed because HEAD is live (CREATE/UPDATE) does the UPDATE
  phase run. That phase is unconditional without a predecessor, or conditional
  on its version when supplied, ignoring action bits as agreed. Other insertion
  errors must propagate rather than triggering the UPDATE fallback.
- Schema/index APIs continue receiving physical names/settings/descriptors.
  The tuple persistence boundary still needs target columns/configuration;
  tuples alone do not describe the collection's full physical schema.
- Tuple persistence methods accept schema/catalog and table/collection as `Id`
  objects instead of plain strings. Reuse these identifiers and `PgDatabase.id`
  to verify any supplied predecessor belongs to the same database, catalog, and
  collection using the existing infix comparisons. Compare the database's full
  numeric identity and the catalog/collection identities at their tuple-number
  widths. `Id` caches numeric derivation, so validation must not reconstruct
  identifiers or repeatedly hash their text. The new tuple and its predecessor
  must also address the same feature. A valid predecessor for a different target
  is an illegal argument, not an unconditional update. Resolve physical relation
  naming/quoting inside `PgDatabase` using the agreed naming policy; accepting
  `Id` does not itself settle literal versus mapped relation names.

#### Operation sequence

| Operation             | Planned behavior                                                                                                                                                                                                                                                             |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| INSERT | Require null input `previousTupleNumber`, otherwise throw illegal argument before mutation. Purge matching HEAD states only when DELETE, then insert. A live HEAD causes an error. Return the supplied tuple states. |
| UPDATE, unconditional | Require an existing live HEAD and replace it. Return `NewTuple` with the new state and the actual replaced HEAD's tuple number as `previousTupleNumber`. |
| UPDATE, atomic | Require a matching live HEAD according to the expected predecessor. On mismatch throw a structured error. Return `NewTuple` with the new state and the actual replaced HEAD's tuple number. |
| UPSERT | Validate supplied predecessor identity first. Phase 1 inserts into absent HEAD or replaces DELETE HEAD without a predecessor condition. Phase 2 runs only for live CREATE/UPDATE HEAD and updates unconditionally or conditionally on the supplied predecessor's masked version. Return `NewTuple` with predecessor set only for UPDATE, null for INSERT (including re-creation). |
| PURGE | Accept database/catalog/collection IDs and ordered feature IDs. Purge only DELETE HEAD states. Return one nullable GUID per input: DELETE means purged; live actions mean unchanged HEAD; null means no HEAD exists. No expected-version/atomic variant. |
| DELETE by ID | Delete live HEAD without an expected-version condition; return GUIDs or deleted `NewTuple` states according to the variant. Optional empty tombstones for the without-result variant. |
| DELETE by GUID | Conditional delete of live HEAD; mismatching expected state throws. An already-DELETE HEAD is accepted and returned even when its version differs from the expectation. |
| DELETE explicit state | Accept `NewTuple` tombstones. Require a live HEAD when no predecessor is supplied; when supplied, require exactly that HEAD state. Missing/mismatching HEAD fails; return created tombstones as `NewTuple`. |

Archival remains the agreed root-table behavior: change predecessor `nv` when
history is enabled; replace/remove current state without retaining history when
disabled. pg_partman owns partition provisioning. The UPSERT sequence is a
behavioral contract, not yet a choice of one SQL statement versus several;
locking and concurrent INSERT/UPDATE races must preserve it.

#### Proposed interface shape for review

```kotlin
// Schematic: connection and collection configuration are omitted, not removed.
// Database identity is available as PgDatabase.id.
internal fun rows_insert(schema: Id, table: Id, /* config */, tuples: List<NewTuple>): List<Tuple>
internal fun rows_update(schema: Id, table: Id, /* config */, tuples: List<NewTuple>): List<NewTuple>
internal fun rows_upsert(schema: Id, table: Id, /* config */, tuples: List<NewTuple>): List<NewTuple>
internal fun rows_purge(db: Id, catalog: Id, collection: Id, featureIds: List<Id>): List<Guid?>
internal fun rows_delete_by_id_without_result(
    db: Id, catalog: Id, collection: Id, featureIds: List<Id>, emptyTombstone: Boolean = false
): List<Guid>
internal fun rows_delete_by_id_with_result(
    db: Id, catalog: Id, collection: Id, featureIds: List<Id>
): List<NewTuple>
internal fun rows_delete_by_guid_without_result(
    db: Id, catalog: Id, collection: Id, guids: List<Guid>, emptyTombstone: Boolean = false
): List<Guid>
internal fun rows_delete_by_guid_with_result(
    db: Id, catalog: Id, collection: Id, guids: List<Guid>
): List<NewTuple>
internal fun delete_with_new_state(
    db: Id, catalog: Id, collection: Id, tuples: List<NewTuple>
): List<NewTuple>
// Failure: throw NakshaException(error: NakshaError).
```

Use the common wrapper for new-state operations. INSERT rejects non-null
predecessor metadata; UPDATE/UPSERT results expose the actual predecessor only
when a live HEAD was updated.
Proposed result ordering is input order, including mixed CREATE/UPDATE UPSERT
batches; SQL result order must never be assumed.

#### Settled decisions

- **Complete new states:** callers provide fully encoded tuples, including all
   payload/member values. Mutations replace the entire state; no column is
   implicitly retained from HEAD. Remove the `undefined` preservation use-case,
   sentinel handling, byte-array merge SQL, and related reconstruction logic.
   INSERT/UPDATE persist the supplied state rather than resolving partial inputs
   against old data. UPSERT may still produce a different tuple for the agreed
   CREATE/UPDATE branch-specific control values; it never merges old payload.
   This supersedes `partman.md`'s requirement to preserve `undefined` behavior.

1. **Expected predecessor validity:** target matching is settled: accept `Id`
   schema/catalog and table/collection arguments and use the existing infix
   comparisons with `PgDatabase.id` and the new tuple's feature identity. Reject
    any supplied predecessor that is invalid or addresses another database,
    catalog, collection, or feature number as an illegal argument. This applies
    equally to UPDATE and UPSERT; never downgrade a non-null predecessor to an
    unconditional operation. Do not rehash identifier strings. Only a null
    predecessor selects unconditional UPDATE. No separate numeric version-validity
    policy is required; version equality is checked against HEAD using the agreed
    action-bit rules.
2. **Version comparison:** mask the low two action bits for atomic
   version equality by default. UPDATE/UPSERT accept CREATE or UPDATE at the
   expected version and independently reject a DELETE HEAD. PURGE has no atomic
   version comparison: only DELETE HEAD states are eligible, and live states
   are returned unchanged rather than rejected. Preserve complete actual tuple numbers in results/errors;
   masking is only for comparison. Document any other operation-specific
   exception explicitly in db-01-e.
3. **Atomic UPSERT insert branch:** a supplied predecessor must be
   valid for the same feature/target, but it never conditions phase 1. Missing
   or DELETE HEAD permits INSERT regardless of the predecessor's version/action.
   Only a live CREATE/UPDATE HEAD selects phase 2, where the supplied predecessor
   conditions UPDATE using masked version equality. Null selects unconditional
   UPDATE. A supplied invalid predecessor is an illegal argument, not permission
   to downgrade to an unconditional UPSERT. Unrelated SQL/constraint failures
   are errors, not reasons to retry as UPDATE.
4. **Error payload:** add `expectedVersion: Long?` and `actualVersion: Long?`
   to `NakshaError`, rather than storing redundant complete tuple numbers.
   Existing `database`, `catalog`, `collection`, and `id` fields identify the
   feature; each new field supplies the respective state version. Null means
   no expectation or no actual HEAD, rather than a fabricated version zero.
   Keep full versions including action bits in error data; masking applies only
   during operation-specific comparison.
   Add `getExpectedTupleNumber(): TupleNumber?` and
   `getActualTupleNumber(): TupleNumber?` helpers that reconstruct the address
   using existing `Id` conversions and the corresponding version. Return null
   when the version or any required identity field is absent. Use the existing
   base-layer `TupleNumber`; no dependency on the model module is needed.
   Operation/batch-index/reason fields and duplicate tuple-number fields are not
   part of this error-payload change. Existing error code/message and identity
   fields remain available for diagnosis.

5. **PURGE contract:** provide one method accepting `db: Id`, `catalog: Id`,
   `collection: Id`, and `featureIds: List<Id>`, returning `List<Guid?>` in exactly
   the input feature-ID order. The connection remains caller-owned as for other
   database operations; the schematic signature omits it. Validate the supplied
   database ID against the database instance using cached numeric identities.
   No `NewTuple`, expected version, or atomic PURGE variant is needed.
   Only HEAD states explicitly in DELETE action are purged. A live CREATE/UPDATE
   HEAD is left untouched; this is a successful per-feature outcome, not a
   conflict or an operation failure. Return its current HEAD GUID instead.
   A returned DELETE-action GUID identifies the tombstone that was purged, even
   though it no longer exists in HEAD. Other returned action values identify
   states that remain in HEAD. The caller interprets/checks these GUIDs rather
   than expecting every requested feature to be purged. Associate results with
   input positions explicitly, never by SQL result order. Genuine database
   failures still throw `NakshaException`. This supersedes the earlier atomic
   PURGE proposal and any requirement to purge live HEAD states.
   This is an internal API with a positional result contract: the result list
   has exactly the same size as `featureIds`, and `result[i]` always describes
   `featureIds[i]`. Return null at that position when no HEAD state exists;
   do not omit the entry, shift subsequent results, or synthesize a GUID.
   Callers distinguish all three outcomes explicitly:
   - null: no HEAD state was found, so nothing was purged;
   - DELETE-action GUID: that tombstone was purged;
   - CREATE/UPDATE-action GUID: that live HEAD was not purged and remains unchanged.
   Document this correspondence and nullable element type in the method KDoc.
6. **DELETE variants:** use separate internal methods rather than one method
   with ambiguous input/result modes. All receive database/catalog/collection
   `Id` values and use internal row transport. Their schematic signatures above
   omit connection, transaction-version, and collection configuration parameters;
   generated tombstones still need the caller's deletion transaction version.
   - `rows_delete_by_id_without_result` accepts feature IDs and
     `emptyTombstone: Boolean = false`. It returns `List<Guid>` identifying
     successfully deleted features. "Without result" means no full tuple payload,
     not a Unit return. By default retain the old payload in the new tombstone.
     When `emptyTombstone` is true, every column except mandatory primary-key
     columns (`fn`, `version`, `nv`) is SQL NULL. The new tombstone has DELETE
     action and HEAD `nv`; history-enabled deletion archives the old state.
     An already-deleted HEAD is returned as its existing GUID without rewriting
     it. A feature with no HEAD is omitted from the result list without error.
   - `rows_delete_by_id_with_result` accepts feature IDs and returns
     `List<NewTuple>` containing deleted states and references to their actual
     previous states. Retain the old payload in the generated tombstone.
     Missing HEAD is omitted without error. An already-deleted HEAD is returned
     unchanged with `previousTupleNumber = null`, because nothing was replaced.
   - `rows_delete_by_guid_without_result` and
     `rows_delete_by_guid_with_result` mirror the ID variants (including the
     default-false `emptyTombstone` option for the without-result variant), but
     use GUIDs as expected states. For live HEAD, require the expected version,
     ignoring action bits under the general rule; mismatch/missing expected
     state throws `NakshaException` with the agreed error details. Exception:
     if HEAD is already DELETE, accept and return that deleted state even when
     its tuple number differs from the expected one. Do not rewrite an existing
     tombstone merely to make it match the expectation. The caller decides
     whether this equivalent deleted outcome is acceptable.
     When the with-result variant returns an existing tombstone, its wrapper
     has `previousTupleNumber = null`; this is not the caller's expectation and
     must not falsely imply that an UPDATE/DELETE replaced a state.
   - `delete_with_new_state` is the agreed name for the explicit-state variant.
     It accepts `List<NewTuple>` describing the exact
     tombstones to create and returns the created states as `List<NewTuple>`
     with predecessor references. Without a supplied predecessor, require an
     existing live HEAD (CREATE/UPDATE); missing or already-deleted HEAD fails.
     With a predecessor, require exactly that HEAD state; mismatch or absence
     fails, with no permissive already-deleted shortcut. "Exactly" is an
     action-sensitive exception to masked version comparison: compare the full
     expected state including action. An exactly matching DELETE predecessor
     may be replaced when explicitly supplied; the live-HEAD-only requirement
     applies to the branch without a predecessor.
     The supplied new state must encode DELETE action. Failure to create the
     requested state throws; never report an unrelated existing tombstone as
     successful creation of the explicit new state.
   - Empty tombstones deliberately contain no retained payload or non-key
     identity column. Update physical nullability/check constraints and tuple
     decoding/encoding paths so key-only tombstones are valid, including hashed
     feature IDs. Preserve full old states in history when history is enabled.
     This supersedes any plan to enforce non-key payload constraints on tombstones.
   - DELETE results preserve relative input order when missing features are
     omitted: `[A, missing-B, C]` returns `[result-A, result-C]`. Do not insert
     null placeholders for missing ID-based DELETE results; this differs from
     PURGE's fixed-size positional `List<Guid?>` contract. Associate SQL results
     with inputs explicitly rather than relying on database row order.

7. **Caller-owned transaction and input preconditions:** all mutation methods
   are `internal` trusted entry points. They assume the caller has established
   a savepoint, validated transaction-wide feature uniqueness, and sorted inputs
   into the common deterministic locking order. Do not duplicate those checks,
   reorder inputs, or create/commit/roll back transactions inside `PgDatabase`.
   Document these preconditions in every mutation method's KDoc.
   - `PgWriter` establishes the savepoint before the operation and rolls back to
     it when a `NakshaException` escapes. A method may have changed database rows
     before throwing; no partial success list is returned. The exception reports
     the encountered failure rather than an aggregated batch-error result.
   - Duplicate feature identifiers are forbidden within a batch **and across
     all writes in a single transaction/version**. A feature may have at most
     one newly produced state in a version, regardless of which request or
     operation would produce it. Use full database/catalog/collection/feature
     identity, not feature text alone across unrelated collections.
   - The session checks this invariant using transaction-object modification
     tracking; `PgWriter` validates the incoming writes against each other and
     the session's previously modified features before passing them to the
     database. Caller-side checks cover catalog/collection feature mutations as
     applicable, not only user-feature writes.
   - Reserve/update modification tracking consistently with savepoint success:
     failed writes must not leave phantom modified features in session state,
     while earlier successful requests in the transaction remain tracked.
   - Callers sort features before invocation to prevent row-lock deadlocks.
     `PgDatabase` relies on this ordering. Internal result correlation preserves
     the supplied order (and agreed DELETE omissions/PURGE null positions).
     `PgWriter` may separately restore original request order in its response.
   - SQL failures remain `NakshaException` carrying `NakshaError`, including
     target identity and expected/actual versions where relevant. No additional
     aggregated errors, batch-index fields, or new error-code taxonomy are
     required by this contract; preserve the existing applicable codes.

#### Ordered implementation after plan approval

1. Obtain user approval of the revised plan. Implement the settled contract
   details and operation matrix in db-01-d/e.
2. Implement the immutable model migration in db-01-f, including `NewTuple`
   return types and predecessor population across all three encoding entry
   points. Check encoder forwarding, bootstrap, writers, cache users, and JVM/JS
   exports; keep the project compiling at each step.
3. Implement revised declarations/KDoc in db-01-g. Keep internal row-layout
   choices within the database implementation rather than exposing helper columns.
4. Implement request-level rollback/error context in db-05, then move INSERT,
   UPDATE/UPSERT, and DELETE/PURGE SQL/reconstruction in db-06..db-08.

Plan verification: reviewed current `Tuple`, `TupleNumber`, `NakshaError`, and all
production `previousTupleNumber` usages. Approved by the user; the model migration
(db-01-f) is implemented and compiles on JVM and JS (main and test).

Implementation decisions folded in:
1. All row methods take a uniform `(schema: Id, table: Id)` (= catalog/collection); the
   database comes from `PgDatabase.id`. Final signatures land in db-01-g.
2. `rows_purge` takes the caller's transaction version (elided), like the DELETE variants,
   because history-ON purges archive the removed tombstone.
3. `rows_insert` returns `List<Tuple>` (predecessor always null); only UPDATE/UPSERT return
   `NewTuple` results.
4. The explicit-state delete is named `rows_delete_with_new_state`.

### db-01-d — Document identity validation and preconditions

Implement/document the settled identity validation, expected/actual error
versions and reconstruction helpers, and caller-owned savepoint,
mutation-uniqueness, and sorting preconditions. Specify cursor/plan ownership and
SQL logging/EXPLAIN hooks.

Identity validation uses the cached numeric values of the supplied `Id` objects:
compare `PgDatabase.id.number` to `TupleNumber.databaseNumber`, `schema.intValue`
to `catalogNumber`, `table.intValue` to `collectionNumber`, and the new state's
`featureNumber` to its supplied predecessor's `featureNumber`. Do not hash strings
again or use `Id.equals` for catalog/collection-width comparisons. No dedicated
infix identity comparisons exist in this checkout; use these explicit comparisons
rather than assuming an unavailable helper. Foreign predecessors are illegal
arguments before UPSERT branch selection, not unconditional operations. Atomic
UPDATE/UPSERT compare versions with `and -4L` and separately require a live HEAD;
explicit-state DELETE compares the full predecessor, including action.

Encoding/adapters must distinguish a source UUID from an atomic expectation:
`atomic = false` selects null predecessor metadata; `atomic = true` requires a
usable same-target predecessor for atomic UPDATE; `atomic = null` retains automatic
selection from a usable source predecessor. Fork handling still clears the foreign
source predecessor and records origin. Request-level explicit expected versions
remain authoritative in adapters. The encoder currently forwards source metadata
even with `atomic = false`; enforce this translation when wiring the new persistence
boundary in db-07-c, before replacing the current `requireVersion` path.

`NakshaError.expectedVersion` and `actualVersion` are nullable payload properties.
Both retain action bits; zero is a value, not absence. The reconstruction helpers
return null if their version or any of database/catalog/collection/id is absent,
otherwise use the existing `TupleNumber(Id, Id, Id, Id, version)` constructor.
They reconstruct on demand from the error's current fields, with no stale cache.
SQL mapping must preserve the original cause in `NakshaException` and populate
target identity plus available expected/actual versions using existing error codes.

Every mutation requires a caller-owned savepoint, transaction-wide uniqueness of
the full database/catalog/collection/feature identity, and a common deterministic
feature locking order independent of operation/action. `PgDatabase` must neither
duplicate these checks nor sort or manage transactions. On failure, `PgWriter`
rolls back database changes and restores modification tracking to the savepoint,
retaining earlier successful requests. Current tracking only records counts and
current preparation precedes the savepoint; implementing these guarantees remains
db-05, not a claim about the existing writer.

Mutation methods own and close all internally prepared plans and result cursors,
including exception paths. Closing a cursor from a prepared plan does not close
the plan. They never close the caller's connection. `rows_query` instead returns
a caller-owned plan; its caller closes the plan and all cursors before releasing
the connection. Keep existing `PgSession.logQueries`/`logExplain` controls and
`logAtInfo` reporting as writers migrate. Diagnostic EXPLAIN uses planning only
(`explain(..., false, ...)`); do not use ANALYZE for mutation diagnostics because
it executes the mutation. Close the diagnostic plan as well as its cursor.

Done: added nullable version payload fields, both reconstruction
helpers, and version fields in error diagnostics. Added three common tests covering
numeric/hashed/internal IDs, action-bit preservation, missing identity/version,
zero versions, payload storage and diagnostics. Verified:
`:here-naksha-lib-base:jvmTest` (full suite),
`:here-naksha-lib-base:compileTestKotlinJs`, and
`:here-naksha-lib-psql:compileKotlinJvm` / `compileKotlinJs` all pass.
JS test sources were compiled, not executed; no database tests were run.
Runtime SQL identity validation/error mapping and caller rollback/tracking remain
in the existing db-05..db-08 implementation tickets.

### db-01-e — Write the behavior matrix

Write a compact behavior matrix for missing/live/deleted HEAD rows across
CREATE, UPDATE, UPSERT, DELETE, PURGE and both history modes.

#### HEAD outcomes

This matrix specifies the target behavior, not the current SQL implementation.
`Live` means CREATE or UPDATE action; `deleted` means DELETE action. `Expected`
means a supplied same-feature, same-target predecessor (or the expected state in
a GUID). Masked equality is `(expected.version and -4L) == (actual.version and -4L)`.
`Actual predecessor` always refers to the complete HEAD tuple number replaced,
including its original action bits, never the caller's possibly masked expectation.

| Operation / condition | Missing HEAD | Live HEAD | Deleted HEAD |
| --- | --- | --- | --- |
| CREATE / INSERT, null predecessor | Insert supplied CREATE state | Fail: feature exists | Purge tombstone, insert supplied CREATE state |
| UPDATE, null predecessor | Fail: feature not found | Replace with supplied UPDATE state; return actual predecessor | Fail: no live HEAD |
| UPDATE, expected predecessor | Fail: feature not found | Replace only on masked equality; otherwise conflict; return actual predecessor | Fail: no live HEAD, even on masked equality |
| UPSERT, null predecessor | INSERT branch; predecessor null | UPDATE branch; return actual predecessor | Purge tombstone, INSERT branch; predecessor null |
| UPSERT, expected predecessor | INSERT branch regardless of expected version/action; predecessor null | UPDATE branch only on masked equality; otherwise conflict; return actual predecessor | Purge tombstone, INSERT branch regardless of expected version/action; predecessor null |
| DELETE by ID, without full result | Omit result, no error | Create tombstone; return its GUID | Return existing GUID unchanged |
| DELETE by ID, with full result | Omit result, no error | Create tombstone; return `NewTuple` with actual predecessor | Return existing tombstone with predecessor null |
| DELETE by GUID, without full result | Fail: expected state missing | Create tombstone only on masked equality; otherwise conflict; return its GUID | Return existing GUID unchanged, even if expected version differs |
| DELETE by GUID, with full result | Fail: expected state missing | Create tombstone only on masked equality; otherwise conflict; return `NewTuple` with actual predecessor | Return existing tombstone with predecessor null, even if expected version differs |
| Explicit-state DELETE, null predecessor | Fail: feature not found | Write supplied DELETE state; return actual predecessor | Fail: live HEAD required |
| Explicit-state DELETE, expected predecessor | Fail: expected state missing | Write supplied DELETE state only on full tuple-number equality; otherwise conflict | Write supplied DELETE state only on full tuple-number equality; otherwise conflict; no already-deleted shortcut |
| PURGE by ID, no expected version | Return null at this position | Leave unchanged; return current GUID at this position | Purge; return removed tombstone's GUID at this position |

All INSERT predecessor metadata must be null; reject non-null metadata as
`ILLEGAL_ARGUMENT` before any purge or insert. Supplied UPDATE/UPSERT/explicit
DELETE predecessors must identify the same database/catalog/collection/feature;
foreign metadata is `ILLEGAL_ARGUMENT`, even on UPSERT's INSERT branch. GUID
expectations likewise address the selected feature/target, not another feature.
Explicit-state DELETE inputs must encode DELETE action. These argument failures
are not version conflicts and must never select an unconditional fallback.

An expected action difference alone does not reject a live UPDATE/UPSERT or
GUID-based DELETE when masked versions match. The full-equality explicit-state
DELETE row is the action-sensitive exception. PURGE has no atomic variant.
UPSERT falls back to UPDATE only for a live HEAD, not for unrelated SQL or
constraint failures; concurrent races must preserve these same outcomes.

#### History And Payload

Apply the following physical effects to every successful outcome above. `v` is
the successor state's operation version, or the caller's purge transaction version
when no successor is created. New HEAD rows always use `nv = Version.HEAD.number`.

| Successful transition | History ON | History OFF | New-state payload |
| --- | --- | --- | --- |
| Absent to CREATE | Insert HEAD; no predecessor to archive | Same | Complete supplied state |
| DELETE to CREATE (INSERT / UPSERT) | Set old tombstone `nv = v` to archive it, then insert CREATE HEAD | Remove old tombstone, then insert CREATE HEAD | Complete supplied state; no old payload merged |
| Live to UPDATE (UPDATE / UPSERT) | Set old live HEAD `nv = v` to archive it, then insert UPDATE HEAD | Replace current state without retaining its old state | Complete supplied payload; UPSERT may adjust branch-specific control values |
| Live to generated DELETE | Archive full old live state with `nv = v`, then create DELETE HEAD | Replace current state with DELETE HEAD, without archiving it | Retain old payload by default; optional key-only tombstone below |
| Matching HEAD to explicit DELETE | Archive full old HEAD, including an explicitly matched DELETE, with `nv = v`; insert supplied DELETE HEAD | Replace matching HEAD without archiving it | Exact supplied tombstone; no payload merge |
| DELETE to absent (PURGE) | Set removed tombstone `nv = v` to archive it; leave no HEAD | Remove tombstone without an archive; leave no HEAD | No new state |
| Existing deleted outcome returned unchanged / live PURGE / missing no-op | No archive or rewrite | No archive or rewrite | No new state |

`emptyTombstone = true` is available only on the two without-full-result DELETE
variants, defaults to false, and affects newly generated tombstones only. Every
non-key column is SQL NULL; only `fn`, `version`, and `nv` are mandatory. Existing
tombstones returned unchanged are not emptied. History ON still retains the full
old state. The full-result ID/GUID variants retain payload; explicit-state DELETE
uses the complete supplied tombstone. No operation preserves `undefined` payload
members or merges byte-array sentinel values with the old HEAD.

#### Results And Failures

INSERT returns `List<Tuple>` in input order. UPDATE/UPSERT and explicit-state
DELETE return ordered `List<NewTuple>` with actual predecessor metadata; UPSERT
INSERT branches have null predecessors. Generated DELETE returns either GUIDs
or `NewTuple` states as selected by the method, with null predecessor when an
existing tombstone is returned without replacement.

Only ID-based DELETE omits missing inputs: `[A, missing-B, C]` returns
`[result-A, result-C]`. GUID-based DELETE fails for missing HEAD, so it never
silently omits an expected state. PURGE always returns exactly one nullable item
per input, preserving `[result-A, null, result-C]`. All correlation is explicit;
SQL result ordering is not a contract.

Failures throw `NakshaException` carrying `NakshaError`, preserving target identity,
SQL cause, and available full expected/actual versions. Missing HEAD gives null
`actualVersion`; an unconditional request gives null `expectedVersion`, not zero.
Use existing applicable error codes (`ILLEGAL_ARGUMENT`, `FEATURE_EXISTS`,
`FEATURE_NOT_FOUND`, `CONFLICT`), not a new taxonomy. No partial success list is
returned. The caller rolls back to its savepoint and restores modification tracking;
earlier successful requests in the transaction remain intact. Caller-side uniqueness
and deterministic locking-order preconditions apply in both history modes.

Done: completed the HEAD outcome and history/payload matrices,
including result ordering, predecessor metadata and failure/rollback semantics.
Cross-checked against db-01-c/d and the db-01-g declarations/KDoc; a separate
consistency review found no contradictions in the new matrix. Documentation-only
change; no compilation or runtime tests were needed or run for db-01-e.

### db-01-f — Migrate the model to immutable NewTuple

Implement immutable `NewTuple(tuple, previousTupleNumber)` in the model and
remove `previousTupleNumber` from `Tuple`. Change `Tuple.encodeFeature`,
`Tuple.encodeObject`, and `Tuple.encodeRaw` to return `NewTuple` and populate its
predecessor metadata. Migrate encoding/bootstrap/writer/copy call sites without
mutating cached tuples; verify JVM/JS compatibility and cache isolation.

Done: added `NewTuple` to the model; the three encoders return `NewTuple`
and `Tuple.previousTupleNumber` is removed. Migrated call sites: `PgDatabase.init`
admin bootstrap uses `.tuple`; `PgWriter` CREATE/UPDATE/UPSERT use `.tuple`;
`PgWriterUpdate`/`PgWriterUpsert` no longer attach the predecessor via `Tuple.copy`
(the actual predecessor is re-derived from the SQL result in db-06/07). Verified green:
`compileKotlinJvm`, `compileKotlinJs`, and model/psql `jvmTestClasses`/`jsTestClasses`
(compilation only; behavioral tests need a DB). `:here-naksha-lib-model:jvmTest` passes
except 3 pre-existing `MemberTest` failures in `NakshaCollection.addMember` validation,
unrelated to this change.

### db-01-g — Replace the row-buffer signatures

Replace the reviewed-but-superseded row-buffer mutation signatures/KDoc in
`PgDatabase` with `Id` schema/catalog and table/collection arguments, `NewTuple`
inputs/results, and thrown `NakshaException`. INSERT returns supplied tuple
states; UPDATE/UPSERT return ordered `List<NewTuple>` carrying actual predecessor
information. Keep `PgRows` private to persistence implementation and wire agreed
DELETE/PURGE signatures.

Done: the `rows_*` declarations in `PgDatabase` now take a uniform
`(schema: Id, table: Id)` target plus caller `conn`, `storeHistory`, and the caller's
transaction `version` where a state is created/archived; inputs are `List<NewTuple>`,
`List<Id>` (feature), or `List<Guid>`. Signatures: `rows_insert: List<Tuple>`;
`rows_update`/`rows_upsert`: `List<NewTuple>`; `rows_purge: List<Guid?>` (fixed-size
positional); the four `rows_delete_by_{id,guid}_{with,without}_result` variants; and
`rows_delete_with_new_state: List<NewTuple>`. Bodies remain `TODO` (SQL lands in
db-06/07/08). No callers exist yet, so this is a declaration-only change. Verified green:
`compileKotlinJvm`.

### db-02 — Verify pg_partman topology against the deployed version

Parent ticket; decomposed into `db-02-a` … `db-02-d`. **Done when:** a
reproducible topology works with `partitions = 1` and `> 1`; the supported DDL
sequence is recorded before library DDL is implemented.

**Completion:** `DONE`; all children db-02-a through db-02-d are complete.
The approved custom maintainer supersedes pg_partman provisioning; production
implementation remains on the [active workboard](NAKSHA-33.md#db-04--implement-schema-collection-and-index-operations).
**Commit:** Archive completed database contract and partition-maintenance verification tickets.

### db-02-a — Run the pg_partman integration spike

Run the small pg_partman v5.0.1 integration spike described in `partman.md`.

Prepared [NAKSHA-33-partman-spike.sql](NAKSHA-33-partman-spike.sql), invoked with
`psql -X -f ai/NAKSHA-33-partman-spike.sql` against a disposable database with
pg_partman available. If absent, the extension is installed transactionally in a
dedicated schema and rolled back too. The script prints version/API signatures,
creates a dedicated schema (refusing to overwrite an existing one), probes plain
and pre-created fn-partitioned HEAD defaults, checks row archival and invalid nv
rejection, and runs maintenance with HEAD populated. All spike DDL/configuration
is inside a transaction ending in ROLLBACK. Finite fn subpartition provisioning,
retention, index propagation and DROP cleanup still need db-02-c/d coverage.

Verified on the running PostgreSQL 16.2 / pg_partman 5.0.1 instance. Both plain
and fn-partitioned HEAD cases passed archival routing, rejection of uncovered
finite nv / nv above HEAD, and finite partition premaking while HEAD was populated.
Assertions verify the exact HEAD/history rows survive maintenance. ROLLBACK
completed; a follow-up query confirmed zero spike schemas and no installed
pg_partman extension, matching the original state. No application schemas were
cleared or modified.

Version-specific finding: 5.0.1 `create_parent(p_default_table := true)` rejects
a pre-created default relation (duplicate relation error). The alternate probe
passes with `p_default_table := false` followed by explicit ATTACH DEFAULT of
the partitioned HEAD child. This is an experimental topology, not a production
fallback decision. The spike retains both paths to expose version differences.
The checked-in image targets PostgreSQL 18.6 / pg_partman 5.5.0; upstream 5.5.0
source uses CREATE TABLE IF NOT EXISTS for its default, but that version has not
been runtime-tested. Re-run the spike after upgrading. Full finite fn
subpartitioning, index propagation, retention and DROP cleanup remain unverified
and are required before db-02 as a whole can finish.

### db-02-b — Verify nv ranges and default-child partitioning

Verify native `nv` ranges, restricted default/HEAD, and a default child that is
itself range-partitioned by physical `fn`.

Verified on PostgreSQL 16.2 / pg_partman 5.0.1 by extending the retained SQL
spike. Both plain and two-leaf HEAD configurations route physical fn values
`Long.MIN_VALUE`, -1, 0 and `Long.MAX_VALUE` correctly. `tableoid` checks verify
the selected HEAD leaf and that changing nv moves the row out of HEAD.
The mandatory unique index rejects a second HEAD state for the same fn even
when its version differs. NULL nv, uncovered finite nv and nv above HEAD fail;
the HEAD-only CHECK is catalog-verified as validated. Maintenance preserves
the exact test rows. All DDL/data/configuration is rolled back.

This verifies the two-way physical range boundary, not the logical fn encoding
or every partition count (db-03). Finite nv children are still plain tables in
this probe; full finite-child fn partitioning belongs to db-02-c.

### db-02-c — Verify finite-children fn leaves and indexes

Verify how finite children acquire `fn` leaves and indexes: distinguish
template-table behavior from actual pg_partman subpartition configuration. Record
the supported function signatures and creation order.

Source-inspection lead for the next runtime probe: v5.0.1
`create_sub_parent` records the fn key in `part_config_sub`; templates do not
copy partition keys/layouts. `create_partition_id` computes each upper bound
as bigint `lower + interval`, so it cannot express the exclusive upper bound
needed to include `Long.MAX_VALUE`, nor arbitrary unequal widths for counts
such as 3. `create_sub_parent` also omits the top-level default from its child
enumeration. These are source findings, not a verified production provisioning
solution; do not silently substitute manual leaf creation for the agreed
pg_partman ownership. Runtime testing and an explicit resolution remain pending.

Runtime probe added to the retained SQL spike and executed on PostgreSQL 16.2 /
pg_partman 5.0.1. Verified creation order: create empty nv root and root indexes;
`create_parent(nv, interval=100, start=0, premake=1)`;
`create_sub_parent(fn, interval=10, start=0, premake=1,
p_declarative_check='yes', p_default_table=true)`; populate finite history;
increase root premake and run root-scoped maintenance. The supported arguments
are printed by the script. `create_partition_id` has no `p_jobmon` argument in
this version.

Existing and newly premade finite children become `PARTITION BY RANGE (fn)`.
All leaves, including defaults, receive valid mandatory
`UNIQUE (fn, nv) INCLUDE (version)` and custom `(version) INCLUDE (fn, nv)`
indexes inherited from the root. The top-level default remains an ordinary
table; `create_sub_parent` does not convert it. Existing finite tables are
dropped/recreated during setup, so this call must precede application writes.

**Blocker, reproduced:** equal fn/nv intervals are rejected, even though they
control different columns. More importantly, subpartition bounds are restricted
to the enclosing nv numeric interval: under nv `[100,200)`, the generated fn
leaf is `[100,110)`, not the requested full signed-bigint range. Both fn=2 and
`Long.MAX_VALUE` route to that subparent's DEFAULT leaf. An explicit bigint-end
`create_partition_id` request returns false (skipped), not a full-range leaf.
Thus successful API calls/index inheritance do not establish the required
performance topology. Arbitrary N full-range fn leaves remain unresolved; db-02-c
stays IN_PROGRESS pending a provisioning decision or verified newer-version API.
No manual production fallback was added. The full script completed ROLLBACK;
follow-up queries confirmed no spike schemas or installed extension remained.

Upgrade retest: the complete retained spike also ran successfully on PostgreSQL
18.6 / pg_partman 5.5.0. The independent-key subpartition blocker is unchanged:
equal fn/nv intervals are rejected, nv `[100,200)` produces fn `[100,110)`,
fn=2 and bigint maximum route to the sub-default, and the bigint-end explicit
partition request is skipped. Existing/future leaf index checks still pass.
Unlike 5.0.1, 5.5.0 successfully reuses the pre-created partitioned HEAD default
with `p_default_table=true`; the explicit attachment alternate path is not used.
ROLLBACK completed and separate queries confirmed zero leftover spike schemas
and no installed extension. Upgrading alone therefore does not resolve db-02-c.

Resolution approved: replace pg_partman provisioning with Naksha-owned SQL
maintenance. The retained [custom prototype](NAKSHA-33-custom-partman.md) implements
the four-argument per-root function and registry. Tested on PostgreSQL 18.6:
HEAD/current/three future nv ranges, full-domain 16-way fn leaves, 34 retained
sample rows, idempotent calls, repair of missing empty ranges/leaves, rejection
of changed settings, and a quoted-schema 3-way layout. This completes the
topology investigation, not production implementation. Keep the SQL test files
for review. Production custom columns, index propagation/reconciliation, storage
settings, robust version coverage, registry scanning and cron remain TODO under
db-04-b/d/g/h. No production Kotlin/SQL path or cron schedule was changed.

### db-02-d — Verify maintenance and cleanup

Verify maintenance with HEAD populated, rejection of uncovered finite `nv`, and
registry cleanup when dropping a parent or schema. Verify this against the
approved custom maintainer; pg_partman/template cleanup is superseded. Preserve
the committed inspection examples; use isolated rollback-only lifecycle checks.

Verified on PostgreSQL 18.6 using
[NAKSHA-33-custom-partman-lifecycle-test.sql](NAKSHA-33-custom-partman-lifecycle-test.sql).
Repeated maintenance preserves populated HEAD; uncovered finite nv is rejected.
Dropping a root removes all its partitions/indexes but leaves the registry row;
calling maintenance with that row recreates the complete root and 80 leaves.
Dropping its schema leaves the registry row too; maintenance then fails clearly
instead of recreating the schema. Explicit unregister succeeds. Therefore
production collection/schema DROP must remove registry rows in the same
transaction (db-04-e), or maintenance can recreate an intentionally dropped root.
No implicit retention/deletion policy was added. Lifecycle test ROLLBACK passed;
zero test schemas remain and the 34-row inspection example is unchanged.

**Completion:** `DONE`; the user approved the lifecycle verification above.
**Commit:** Archive completed database contract and partition-maintenance verification tickets.
