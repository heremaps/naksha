# PgDatabase migration workboard

[JIRA: NAKSHA-33](https://here-technologies.atlassian.net/browse/NAKSHA-33)

## Goal and working rules

Make `PgDatabase` the entry point for physical PostgreSQL operations used by
`PgWriter` and its operation writers. Implement the single-root/pg_partman design
in [partman.md](partman.md), replacing the old table-object hierarchy with
dedicated database functions.

- Work in ticket order, one reviewable change at a time. Update the ticket `State`
  in the table and record verification/results below the ticket after each change.
- Ticket states, in order: `TODO` → `PLANNING` → `IN_PROGRESS` → `IN_REVIEW` →
  `DONE`.
  - `TODO`: not started.
  - `PLANNING`: being discussed/planned; several tickets may be here at once.
  - `IN_PROGRESS`: the AI is implementing it.
  - `IN_REVIEW`: handed to the human, before committing.
  - `DONE`: the human approved it; set just before committing.
  Transitions: start discussing → `PLANNING`; the AI starts implementing →
  `IN_PROGRESS`; handed to the human before commit → `IN_REVIEW`; the review asks
  for changes → back to `IN_PROGRESS`; the human approves → `DONE`, then commit.
  Dependencies must be `DONE` before a ticket is marked `DONE`.
- Record the commit message in the table's `Commit` column together with `DONE`.
  Keep changes uncommitted until `DONE`. When the human approves a ticket, mark it
  `DONE`, fill in its `Commit` message, and commit the intended changes using that
  message. Inspect status, diff, and recent history first, and stage only the
  approved ticket's files. Every such commit must include this exact sign-off
  trailer:

  ```text
  Signed-off-by: Alexander Lowey-Weber <alexander.weber@here.com>
  ```
- Ticket identifiers are lowercase (`db-01`). Each ticket has an addressable letter
  suffix (`db-01-a`, `db-01-b`, etc.) describing its work breakdown. A ticket is
  `DONE` only when all of its sub-steps and acceptance checks are complete.
- `PgWriter*` owns request validation, feature-to-tuple encoding, operation
  dispatch, response construction, and transaction bookkeeping.
- `PgDatabase` owns schema/table/index DDL, partition management, row mutation SQL,
  preparation/execution, physical metadata queries, and tuple persistence/result
  reconstruction. Mutation callers supply immutable `NewTuple` wrappers and
  receive final `Tuple` states. `PgRows` is an internal transport detail.
  Request processing, member processors, feature encoding, and admin-feature
  business logic remain outside `PgDatabase`.
- Keep `PgRows` and its columnar binding/reading helpers, `PgRowsColumn`,
  `PgColumn` (the current singular class), and `PgIndex`. Keep logical
  `PgCatalog`/`PgCollection` wrappers. Small SQL-fragment helpers in these types
  are appropriate; executing DDL/DML belongs in `PgDatabase`.
- Keep `PgQueryWhereBuilder` as a focused predicate builder. Consolidate physical
  read SQL through the database boundary after writers work; avoid making this a
  prerequisite for every writer change.
- Support clean/new databases, as agreed in `partman.md`.
- Reviewed partition ownership: pg_partman manages history **and performance**
  partitions. Naksha supplies configuration, not an initial transaction version
  or manually provisioned partition set. This supersedes the caller-calculated
  initial-version/start-boundary approach in the original `partman.md` plan.

## Current-code findings

Paths below are relative to `here-naksha-lib-psql/src/` unless stated otherwise.

| Area | Current state / consequence |
| --- | --- |
| `commonMain/.../PgWriter.kt` | CREATE/UPSERT already call `catalog_get_or_create_schema` and `collection_create_table_if_not_exits`. DELETE/PURGE do not yet perform corresponding physical catalog/collection deletion. |
| `commonMain/.../PgDatabase.kt` | Schema methods are TODOs; collection creation only constructs an unused SQL sketch; drop and `rows_*` functions are empty. Existing signatures omit history/retention/start-range options and useful mutation results. |
| `PgWriterInsert/Update/Upsert/Delete.kt` | Still prepare and execute their own multi-CTE SQL against separate HEAD/history tables. Result aliases and tuple-rebuilding behavior differ per operation. |
| `PgWriterBase.kt` | Refers to removed `PgCollection` members (`headTable`, `historyTable`, `catalog`, `storage`, etc.). `PgCollection` already exposes `catalogId`, `id`, `name`, `columns`, `indices`, and `naksha` instead. This is an incomplete transition, not a working old baseline. |
| `PgRows.kt`, `PgColumn.kt` | Still translate HEAD `nv` to NULL and declare nullable `nv`. Feature-number binding/reading is logical/raw, without the planned physical range-sort conversion. |
| `PgTable`, `PgHeadTable`, `PgHistoryTable`, `PgHistoryPartition`, `PgDistributionPartition` | Contain old DDL, modulo routing, partition maps, storage settings, constraints, and recursive index operations. Inventory their responsibilities before removing them. |
| `PgIndex.kt` | Data fields use `Pair<PgColumn, PgOrder>`, but execution code still treats key elements as columns and requires `PgTable`. Must preserve descriptor functionality while moving execution. |
| `PgDatabaseImpl.jvm.kt` | Admin bootstrap already calls collection creation; it must use the same complete creation API as user collections. SQL error wrappers still have TODO context mapping. |
| `PgDatabaseImpl.js.kt` | Common SQL can be shared, but SQL error wrappers currently just invoke the callback. Bootstrap installation is intentionally unsupported here. |
| `PgWriter.kt`, `PgSession.kt`, `PgCache.kt` | Preparation/cache mutation happens before the writer savepoint. Session commit does not call the existing cache `flush`; rollback does not explicitly clear local metadata. Database rollback and in-memory rollback need one coherent boundary. |
| `PgQueryBuilder`, `PgReader`, `PgSession.loadTuplesFromCollection`, `PgCache` | Read SQL is still distributed. Query builder already attempts a root scan, but contains unfinished SQL assembly. Readers and cache loads must share the same naming/physical-fn contract as writers. |

Some prerequisites are already partly implemented: collection retain/backward/
forward properties, low-byte `Id` routing, the 256-partition limit, and extension
installation entries. Verify and finish these rather than duplicating them.

## Ordered Tickets

| Ticket # | Summary | Depends On | State | Commit |
| --- | --- | --- | --- | --- |
| [db-00](#db-00--restore-a-compilable-baseline) | Restore a compilable baseline | — | `DONE` | Restore compilable psql baseline and rebase import fixes |
| [db-01](#db-01--agree-the-database-boundary-and-operation-contracts) | Agree the database boundary and operation contracts | — | `IN_REVIEW` | — |
| [db-02](#db-02--verify-pgpartman-topology-against-the-deployed-version) | Verify pg_partman topology against the deployed version | [db-01](#db-01--agree-the-database-boundary-and-operation-contracts) | `TODO` | — |
| [db-03](#db-03--finish-the-physical-row-and-naming-contract) | Finish the physical row and naming contract | [db-01](#db-01--agree-the-database-boundary-and-operation-contracts), [db-02](#db-02--verify-pgpartman-topology-against-the-deployed-version) | `TODO` | — |
| [db-04](#db-04--implement-schema-collection-and-index-operations) | Implement schema, collection, and index operations | [db-02](#db-02--verify-pgpartman-topology-against-the-deployed-version), [db-03](#db-03--finish-the-physical-row-and-naming-contract) | `TODO` | — |
| [db-05](#db-05--make-write-failures-restore-database-and-session-state) | Make write failures restore database and session state | [db-01](#db-01--agree-the-database-boundary-and-operation-contracts), [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-06](#db-06--move-create-into-pgdatabase) | Move CREATE into PgDatabase | [db-04](#db-04--implement-schema-collection-and-index-operations), [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | Move UPDATE and UPSERT into PgDatabase | [db-06](#db-06--move-create-into-pgdatabase) | `TODO` | — |
| [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | Move DELETE/PURGE and metadata deletion into PgDatabase | [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | `TODO` | — |
| [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | Consolidate supporting reads and remove the table hierarchy | [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | `TODO` | — |
| [db-10](#db-10--complete-integrated-acceptance-checks) | Complete integrated acceptance checks | [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |

### db-00 — Restore a compilable baseline

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
unfinished, and bootstrap/write functionality requires the tickets below.

### db-01 — Agree the database boundary and operation contracts

- **db-01-a:** Finalize `catalog_*`, `collection_*`, index, and `rows_*` signatures before switching callers. Correct `collection_create_table_if_not_exits` spelling.
- **db-01-b:** Pass explicit columns/indices and physical options: history mode, partition count, shift, retain, backward, forward, and supported storage/tablespace settings. Use a small options value if preferable to many parameters; it must not become a replacement `PgTable` hierarchy.
- **db-01-c:** Revise the plan for tuple-based persistence, immutable `NewTuple` input, thrown `NakshaException` errors, and UPDATE/UPSERT atomicity. Identify remaining decisions before implementation.
- **db-01-d:** Implement/document the settled identity validation, expected/actual error versions and reconstruction helpers, and caller-owned savepoint, mutation-uniqueness, and sorting preconditions. Specify cursor/plan ownership and SQL logging/EXPLAIN hooks.
- **db-01-e:** Write a compact behavior matrix for missing/live/deleted HEAD rows across
  CREATE, UPDATE, UPSERT, DELETE, PURGE and both history modes.
- **db-01-f:** Implement immutable `NewTuple(tuple, previousTupleNumber)` in the model and remove `previousTupleNumber` from `Tuple`. Change `Tuple.encodeFeature`, `Tuple.encodeObject`, and `Tuple.encodeRaw` to return `NewTuple` and populate its predecessor metadata. Migrate encoding/bootstrap/writer/copy call sites without mutating cached tuples; verify JVM/JS compatibility and cache isolation.
- **db-01-g:** Replace the reviewed-but-superseded row-buffer mutation signatures/KDoc in `PgDatabase` with `Id` schema/catalog and table/collection arguments, `NewTuple` inputs/results, and thrown `NakshaException`. INSERT returns supplied tuple states; UPDATE/UPSERT return ordered `List<NewTuple>` carrying actual predecessor information. Keep `PgRows` private to persistence implementation and wire agreed DELETE/PURGE signatures.

**Done when:** the revised tuple contract, error details, rollback ownership, and
behavior matrix are approved; immutable wrapper/model and API changes compile.
Implementation starts only after review of this revised plan and resolution of
the relevant decisions below. db-01-a/b remain historical completed steps; their
mutation contract is superseded by db-01-c..db-01-g.

#### db-01-a — API shape for review

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

Scope of this sub-step:

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

#### db-01-b — Physical collection creation inputs

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

#### db-01-c — Revised tuple persistence contract (plan in IN_REVIEW)

This section records the user's new direction. It changes the planned API, not
production code in this documentation change.

##### Confirmed direction

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

##### Operation sequence

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

##### Proposed interface shape for review

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

##### Settled decisions

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

##### Ordered implementation after plan approval

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
production `previousTupleNumber` usages. Documentation-only change; no compilation
or tests run. This task remains IN_REVIEW until the user approves the revised plan.

### db-02 — Verify pg_partman topology against the deployed version

- **db-02-a:** Run the small pg_partman v5.0.1 integration spike described in `partman.md`.
- **db-02-b:** Verify native `nv` ranges, restricted default/HEAD, and a default child that
  is itself range-partitioned by physical `fn`.
- **db-02-c:** Verify how finite children acquire `fn` leaves and indexes: distinguish
  template-table behavior from actual pg_partman subpartition configuration.
  Record the supported function signatures and creation order.
- **db-02-d:** Verify maintenance with HEAD populated, rejection of uncovered finite `nv`,
  and configuration/template cleanup when dropping a parent or schema.

**Done when:** a reproducible topology works with `partitions = 1` and `> 1`;
the supported DDL sequence is recorded here before library DDL is implemented.

### db-03 — Finish the physical row and naming contract

- **db-03-a:** Complete validation of create-only partition options and checked interval,
  retention, and feature-number encoding/routing configuration for pg_partman.
- **db-03-b:** Add equivalent Kotlin/JVM/JS fn conversion helpers and the initialization SQL
  functions specified in `partman.md`. Define conversion ownership once so
  `PgRows`, raw-id deletes, query arguments, metadata loads, and returned helper
  aliases cannot double-convert or omit conversion.
- **db-03-c:** Keep API/tuple feature numbers logical. Follow `partman.md` for when physical
  encoding applies; preserve the id/fn invariant using logical fn semantics.
- **db-03-d:** Make HEAD `nv = Version.HEAD.number`, remove NULL translation in `PgRows`, and
  make the physical column non-null.
- **db-03-e:** Establish one schema-qualified, correctly quoted root naming rule across
  creation, mutations, and reads; resolve text names versus `Id.*Name()` mapping.

**Done when:** conversion round-trips and range coverage are verified, and all
new low-level operations agree on names, physical fn, and non-null nv.

### db-04 — Implement schema, collection, and index operations

- **db-04-a:** Implement schema lookup/create/drop with explicit existence results and
  unsigned OID handling. Keep logical identifier-collision checks in admin writes.
- **db-04-b:** Implement collection creation from every supplied `PgColumn`, root constraints,
  mandatory indexes, restricted HEAD/default, partman setup/retention, initial
  maintenance, and optional fn range subpartitions. History-OFF gets HEAD only
  and no partman history registration.
- **db-04-c:** Move reusable storage settings, TOAST, constraints, and tablespace behavior
  out of the old table classes; make intentional changes explicit.
- **db-04-d:** Keep `PgIndex` as a descriptor. Move create/drop execution into `PgDatabase`,
  honoring key order, method/opclass, predicates, includes, and future partitions.
  Never infer HEAD status from a relation name to choose storage parameters.
- **db-04-e:** Implement collection/schema drop with verified partman/template cleanup.
- **db-04-f:** Route JVM admin bootstrap and writer CREATE/UPSERT preparation through this
  same API, including creation of each catalog's internal collections table.

**Done when:** clean bootstrap and user collection creation/drop work through
`PgDatabase`; required constraints/indexes reach current and future leaves.

### db-05 — Make write failures restore database and session state

- **db-05-a:** Put DDL preparation and all operation groups within the request's rollback
  boundary. Handle failure before savepoint creation and the first write request.
  Verify the existing `PgWriter` savepoint mechanism covers preparation and all
  mutations, and rolls back on exception; `PgDatabase` assumes this caller guarantee.
- **db-05-b:** Complete schema/table context in error mapping on JVM and PLV8, preserving
  missing-schema/table information needed by session cache invalidation.
- **db-05-c:** Publish local catalog/collection changes only after successful commit; discard
  or restore local metadata and bookkeeping after request/full rollback.
- **db-05-d:** Ensure post-mutation expected-version validation can fail an entire batch
  without leaving successful rows or DDL behind, while preserving earlier
  successful requests in the same session where appropriate.
- **db-05-e:** Enforce one mutation per fully qualified feature identity per
  transaction/version in the session and `PgWriter`, using transaction-object
  modification tracking. Reject duplicates within/across requests before database
  invocation; restore tracking on request rollback. Keep this caller-side invariant
  out of `PgDatabase`.
- **db-05-f:** Verify caller-side deterministic input sorting for every
  mutation path, including ID/GUID DELETE and PURGE; document trusted ordering
  preconditions and preserve input/result correlation without database-side sorting.

**Done when:** a mixed batch failure restores both SQL and session-local state;
successful DDL becomes visible to other sessions only after commit.

### db-06 — Move CREATE into PgDatabase

- **db-06-a:** Implement tuple-based `rows_insert`; make `PgWriterInsert` supply new states
  through the agreed wrapper and use returned tuples. Move row conversion, SQL,
  and result reconstruction inside `PgDatabase`.
- **db-06-b:** Insert through the root with nv=HEAD. Re-create over a tombstone archives by
  changing nv when history is ON; history-OFF replaces current state in place.
- **db-06-c:** Preserve duplicate-live conflicts, lifecycle reset,
  batch correlation, and original-state return
  guarantees. Fail with `NakshaException` carrying structured `NakshaError`.
  Remove `undefined` sentinel handling: inputs are fully encoded replacement states.

**Done when:** insert/re-create cases work in both history modes and CREATE has
no dependency on old table descriptors or direct history INSERTs.

### db-07 — Move UPDATE and UPSERT into PgDatabase

- **db-07-a:** Implement tuple-based `rows_update` / `rows_upsert` and switch their writers
  to `NewTuple` inputs and returned final tuples. Keep internal `PgRows` and
  reconstruction in `PgDatabase`; never update cached tuple metadata.
- **db-07-b:** Archive by UPDATE of nv through the root, then write the successor; with
  history OFF update HEAD in place. Prove SQL statement/CTE dependencies and
  concurrent-writer locking rather than assuming textual CTE order.
- **db-07-c:** Preserve missing/deleted state checks, atomic expected-version semantics,
  CREATE/UPDATE action selection and change counts according to db-01 decisions.
  Remove `undefined` sentinel/old-payload merging and its reconstruction paths;
  callers supply complete encoded states. Read atomic expectations from
  immutable `NewTuple.previousTupleNumber`, not from a mutable `Tuple` field;
  throw structured conflict exceptions. UPSERT purges DELETE HEAD, attempts
  INSERT, then applies conditional/unconditional UPDATE to the live branch.
- **db-07-d:** Close all plans/cursors and keep query logging/EXPLAIN available.

**Done when:** update/upsert behavior and concurrent conflicts are verified in
both history modes; writers handle orchestration/responses and `PgDatabase`
returns final immutable tuples.

### db-08 — Move DELETE/PURGE and metadata deletion into PgDatabase

- **db-08-a:** Implement the four ID/GUID DELETE methods with and without tuple
  results, and the explicit `NewTuple` tombstone variant according to db-01-c.
  Support key-only tombstones for `emptyTombstone = true`, defaulting to full
  old-state tombstones; distinguish permissive GUID DELETE from strict explicit
  tombstone creation. Implement
  PURGE accepting db/catalog/collection `Id` values and ordered feature IDs.
  PURGE removes DELETE HEAD only and returns one `Guid?` per input position,
  leaving live HEAD unchanged without error and returning null for missing HEAD;
  no atomic PURGE variant. Document the three positional outcomes explicitly.
  Throw `NakshaException` for operation failures;
  keep row transport and reconstruction internal.
- **db-08-b:** Keep tombstones non-null in nv and preserve temporal lifetime semantics.
- **db-08-c:** Connect catalog/collection physical drop to successful logical metadata writes
  in the same transaction; do not drop physical data before state validation.
- **db-08-d:** Replace obsolete generic delete stubs and update callers/tests
  to the agreed ID/GUID and explicit-state APIs; verify empty-tombstone decoding,
  column nullability, hashed identities, expected/actual errors, and old-state
  archival. New-state and empty-state deletion are included in this migration.

**Done when:** delete/purge, re-create, and catalog/collection deletion preserve
agreed API outcomes, history, and rollback behavior.

### db-09 — Consolidate supporting reads and remove the table hierarchy

- **db-09-a:** Add database functions for tuple loading, physical metadata inspection, and
  prepared query execution; route SQL currently in `PgCache`, `PgSession`, and
  `PgReader` through them. Retain `PgRows` and focused predicate builders.
- **db-09-b:** Finish root query SQL, current/temporal/latest-N/deleted filtering, history-OFF
  behavior, and physical fn conversion. Preserve deterministic ordering semantics
  explicitly when encoded fn order differs from logical fn order.
- **db-09-c:** Use PostgreSQL inheritance/partition catalogs for default-child and topology
  discovery. Replace or remove `PgRelation` name parsing and its SQL as needed.
- **db-09-d:** Remove `PgTable`, `PgHeadTable`, `PgHistoryTable`, `PgHistoryPartition`, and
  `PgDistributionPartition` after checking all callers, tests, and documentation.
  Review `PgCollectionIndex` and `PgWriterPlan` for remaining value independently.
- **db-09-e:** Remove manual partition creation/maps, recursive index fan-out, obsolete
  `$hst` expectations, modulo routing, and stale writer properties/logging.

**Done when:** retained descriptors have no dependency on the old hierarchy;
writers never select/create physical partitions and normal reads need no cached
list of partman-created children.

### db-10 — Complete integrated acceptance checks

- **db-10-a:** Compile common/JVM/JS sources, recording any pre-existing failures separately.
- **db-10-b:** Run focused existing suites: bootstrap, collection/partitioning, PgRows,
  insert/update/upsert/delete, re-create, duplicate successor, history, ordered
  reads, tuple persistence/materialization, transactions, and error mapping.
- **db-10-c:** Add missing integration coverage for batch rollback, concurrent writes,
  history-OFF, non-divisor partition counts, maintenance/premake, retention,
  cleanup, and historical imports requiring explicit older-range creation.
- **db-10-d:** Verify root/child mandatory/custom indexes and relevant index-only query plans
  after VACUUM; verify HEAD and bounded-history partition pruning.
- **db-10-e:** Follow repository test-running guidance when executing checks; record commands
  and results with each ticket rather than deferring all verification to the end.

**Done when:** the applicable `partman.md` acceptance checks pass and no active
writer SQL depends on separate HEAD/history tables.

## Decisions needed before the affected implementation

1. **GIN/GiST index contract:** `partman.md` asks every custom index to expose fn,
   version, nv while preserving its method. GIN does not support INCLUDE, and
   spatial indexes need separate consideration. Agree an explicit per-method
   rule; do not silently remove the existing tag/spatial index support.
2. **Physical names:** `PgCollection.name` and creation use literal id text, while
   `PgQueryBuilder` uses `Id.catalogName()/collectionName()`. Should all operations
   use the mapped names, or should the literal-id rule in `partman.md` prevail?
3. **Tuple persistence contract — ready for approval:** DELETE/PURGE behavior,
   caller-owned savepoints, transaction-wide mutation uniqueness, and deterministic
   sorting are settled in db-01-c. The new `NewTuple`/exception contract
   supersedes the earlier exposed row-buffer/nullable-error API and the original
   strict physical-rows-only boundary described in `partman.md`.

## Progress log

- Planning: read `ai/partman.md` and reviewed current writers, database stubs,
  platform bootstrap, row/column/index helpers, legacy table classes, readers,
  session/cache lifecycle, and relevant test locations. Implementation tickets
  had not started at planning time.
- db-00: restored the compilation baseline and ran the focused test/compilation
  command above successfully. The database migration tickets remain TODO.
- db-01-a: defined the physical operation API shape, added index signatures,
  replaced unused row no-ops with explicit stubs, and corrected the collection
  creation method name. JVM/JS main and JVM test-source compilation pass; ready
   for review before db-01-b.
- db-01-b: added immutable physical creation options and wired writer
  CREATE/UPSERT and JVM admin bootstrap through the complete input contract.
  JVM/JS main and JVM test-source compilation pass; explicitly approved by the user.
- db-01-c (IN_REVIEW): revised the plan for immutable `NewTuple` expectations,
  tuple-based persistence/results, internal row buffers, and thrown structured
  errors. Added model/API migration tasks db-01-f/g and remaining behavioral
  questions; no production code changed.
- Rebase onto `33dc1827d` completed. Conflict resolutions preserve upstream's
  streaming API, identifier helpers, HTTP logging, collection-index normalization,
  and PostgreSQL 18.6 test image. Tuple copying retains both the existing
  `copyOnWrite` API and the new multi-path `shallowCopy`, with cloned containers
  attached to the copied root.
- Post-rebase verification: `./gradlew compileKotlinJvm compileTestKotlinJvm
  compileKotlinJs --console=plain` passes. The 21 focused psql tests listed in
  db-00, `IdTest`, and `StreamRequestBuilderTest` also pass. No database-backed
  integration tests were run.
