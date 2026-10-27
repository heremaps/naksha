# PgDatabase migration workboard

[JIRA: NAKSHA-33](https://here-technologies.atlassian.net/browse/NAKSHA-33)

## Goal and working rules

Make `PgDatabase` the entry point for physical PostgreSQL operations used by
`PgWriter` and its operation writers. Implement the single-root/pg_partman design
in [partman.md](partman.md), replacing the old table-object hierarchy with
dedicated database functions.

- Work in ticket order, one reviewable change at a time. Update status and record
  verification/results here after each ticket.
- After completing each step, add a short commit summary as a nested sub-point
  directly below that task, and include it in the response for use after review
  and approval.
- Status: `TODO`, `ACTIVE`, `BLOCKED`, `DONE`. Dependencies must be complete before
  a ticket is marked done.
- Ticket identifiers are lowercase (`db-01`). Each task has an addressable letter
  suffix (`db-01-a`, `db-01-b`, etc.) and its own status. Complete all sub-steps
  and the acceptance checks before marking the parent ticket done.
- `PgWriter*` owns request validation, tuple encoding/reconstruction, operation
  dispatch, result interpretation, and transaction bookkeeping.
- `PgDatabase` owns schema/table/index DDL, partition management, row mutation SQL,
  preparation/execution, and physical metadata queries. Its row operations use
  explicit physical names/options and row data, rather than `Write`, `Tuple`,
  JBON, or admin-feature business logic.
- Keep `PgRows` and its columnar binding/reading helpers, `PgRowsColumn`,
  `PgColumn` (the current singular class), and `PgIndex`. Keep logical
  `PgCatalog`/`PgCollection` wrappers. Small SQL-fragment helpers in these types
  are appropriate; executing DDL/DML belongs in `PgDatabase`.
- Keep `PgQueryWhereBuilder` as a focused predicate builder. Consolidate physical
  read SQL through the database boundary after writers work; avoid making this a
  prerequisite for every writer change.
- Support clean/new databases, as agreed in `partman.md`.

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

## Ordered tickets

### db-00 — Restore a compilable baseline
**Status:** DONE · **Depends on:** none

- **db-00-a · DONE:** Adapted stale identifier/member/transaction APIs in psql writers, row helpers,
  tests, and downstream Java callers. Added a test-only `adminConnection` helper
  using `newConnection`; existing tests remain included.
  - **Rebase follow-up commit summary:** Fix identifier-constant imports and tuple-copy helper imports after rebasing onto upstream.
- **db-00-b · DONE:** Temporarily excluded superseded implementations through the psql Gradle source
  sets, preserving the source files for reference:
  - JVM: `PgAdminCatalogGc.jvm.kt`, `PsqlAdminCatalog.kt`, `PsqlCatalog.kt`,
    `PsqlCollection.kt`, `PsqlStorageListener.kt`.
  - JS: `PgAdminCatalogManager.js.kt`, `Plv8Cluster.kt`, `Plv8Cursor.kt`,
    `Plv8Plan.kt`, `Plv8Storage.kt`.
- **db-00-c · DONE:** Legacy writers temporarily construct HEAD/history descriptors locally rather
  than restoring removed fields on `PgCollection`. Remove these adapters in
  db-06..db-09 together with the legacy hierarchy and Gradle exclusions.
- **db-00-d · DONE:** Legacy table DDL temporarily uses the existing 2048-byte TOAST default and no
  explicit tablespace. Restore supported physical settings in db-04.
- **db-00-e · DONE:** Added required database accessors to storage implementations/test doubles.
  `NHAdminStorage` delegates to its physical storage; virtual/remote/generated
  storages explicitly throw unsupported-operation until their database semantics
  are implemented.
- **db-00-f · DONE:** Corrected `MemberType` initialization validation: byte alignments are positive
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
**Status:** ACTIVE · **Depends on:** none

- **db-01-a · DONE:** Finalize `catalog_*`, `collection_*`, index, and `rows_*` signatures before
  switching callers. Correct `collection_create_table_if_not_exits` spelling.
  - **Commit summary:** Define PgDatabase operation signatures, add index APIs, and fix collection creation naming.
  - **Review follow-up commit summary:** Document physical database operations and return nullable mutation errors while updating PgRows in place.
- **db-01-b · TODO:** Pass explicit columns/indices and physical options: history mode, partition
  count, shift, retain, backward, forward, initial finite version, and supported
  storage/tablespace settings. Use a small options value if preferable to many
  parameters; it must not become a replacement `PgTable` hierarchy.
- **db-01-c · TODO:** Define mutation input/result columns using `PgRows`: correlation key, previous
  version/state, final version, operation outcome, and final byte-array/change-
  count values needed by writers. Do not rely on PostgreSQL result row order.
- **db-01-d · TODO:** Include expected-version data for atomic operations; the current purge API
  accepting only `Array<Id>` is insufficient. Specify cursor/plan ownership and
  SQL logging/EXPLAIN hooks so diagnostics survive the move.
- **db-01-e · TODO:** Write a compact behavior matrix for missing/live/deleted HEAD rows across
  CREATE, UPDATE, UPSERT, DELETE, PURGE and both history modes. Resolve the open
  decisions below before implementing the affected operations.

**Done when:** signatures, result schema, rollback ownership, and behavior matrix
are documented and agreed; no row-level database function needs tuple knowledge.

#### db-01-a — API shape for review

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
- Creation options remain the existing `shift`/`partitions` parameters in code;
  db-01-b expands them to the full option set. Exact row columns, expected-version
  representation, diagnostics, and behavior rules remain db-01-c..db-01-e.

Verification: **BUILD SUCCESSFUL** for the following command; `git diff --check`
also passes. No runtime tests were needed for these declarations and unused stubs.

```bash
./gradlew :here-naksha-lib-psql:compileKotlinJvm :here-naksha-lib-psql:compileKotlinJs :here-naksha-lib-psql:compileTestKotlinJvm --console=plain
```

### db-02 — Verify pg_partman topology against the deployed version
**Status:** TODO · **Depends on:** db-01

- **db-02-a · TODO:** Run the small pg_partman v5.0.1 integration spike described in `partman.md`.
- **db-02-b · TODO:** Verify native `nv` ranges, restricted default/HEAD, and a default child that
  is itself range-partitioned by physical `fn`.
- **db-02-c · TODO:** Verify how finite children acquire `fn` leaves and indexes: distinguish
  template-table behavior from actual pg_partman subpartition configuration.
  Record the supported function signatures and creation order.
- **db-02-d · TODO:** Verify maintenance with HEAD populated, rejection of uncovered finite `nv`,
  and configuration/template cleanup when dropping a parent or schema.

**Done when:** a reproducible topology works with `partitions = 1` and `> 1`;
the supported DDL sequence is recorded here before library DDL is implemented.

### db-03 — Finish the physical row and naming contract
**Status:** TODO · **Depends on:** db-01, db-02

- **db-03-a · TODO:** Complete validation of create-only partition options and checked interval,
  retention, aligned start, and encoded-fn bounds calculations.
- **db-03-b · TODO:** Add equivalent Kotlin/JVM/JS fn conversion helpers and the initialization SQL
  functions specified in `partman.md`. Define conversion ownership once so
  `PgRows`, raw-id deletes, query arguments, metadata loads, and returned helper
  aliases cannot double-convert or omit conversion.
- **db-03-c · TODO:** Keep API/tuple feature numbers logical. Follow `partman.md` for when physical
  encoding applies; preserve the id/fn invariant using logical fn semantics.
- **db-03-d · TODO:** Make HEAD `nv = Version.HEAD.number`, remove NULL translation in `PgRows`, and
  make the physical column non-null.
- **db-03-e · TODO:** Establish one schema-qualified, correctly quoted root naming rule across
  creation, mutations, and reads; resolve text names versus `Id.*Name()` mapping.

**Done when:** conversion round-trips and range coverage are verified, and all
new low-level operations agree on names, physical fn, and non-null nv.

### db-04 — Implement schema, collection, and index operations
**Status:** TODO · **Depends on:** db-02, db-03

- **db-04-a · TODO:** Implement schema lookup/create/drop with explicit existence results and
  unsigned OID handling. Keep logical identifier-collision checks in admin writes.
- **db-04-b · TODO:** Implement collection creation from every supplied `PgColumn`, root constraints,
  mandatory indexes, restricted HEAD/default, partman setup/retention, initial
  maintenance, and optional fn range subpartitions. History-OFF gets HEAD only
  and no partman history registration.
- **db-04-c · TODO:** Move reusable storage settings, TOAST, constraints, and tablespace behavior
  out of the old table classes; make intentional changes explicit.
- **db-04-d · TODO:** Keep `PgIndex` as a descriptor. Move create/drop execution into `PgDatabase`,
  honoring key order, method/opclass, predicates, includes, and future partitions.
  Never infer HEAD status from a relation name to choose storage parameters.
- **db-04-e · TODO:** Implement collection/schema drop with verified partman/template cleanup.
- **db-04-f · TODO:** Route JVM admin bootstrap and writer CREATE/UPSERT preparation through this
  same API, including creation of each catalog's internal collections table.

**Done when:** clean bootstrap and user collection creation/drop work through
`PgDatabase`; required constraints/indexes reach current and future leaves.

### db-05 — Make write failures restore database and session state
**Status:** TODO · **Depends on:** db-01, db-04

- **db-05-a · TODO:** Put DDL preparation and all operation groups within the request's rollback
  boundary. Handle failure before savepoint creation and the first write request.
- **db-05-b · TODO:** Complete schema/table context in error mapping on JVM and PLV8, preserving
  missing-schema/table information needed by session cache invalidation.
- **db-05-c · TODO:** Publish local catalog/collection changes only after successful commit; discard
  or restore local metadata and bookkeeping after request/full rollback.
- **db-05-d · TODO:** Ensure post-mutation expected-version validation can fail an entire batch
  without leaving successful rows or DDL behind, while preserving earlier
  successful requests in the same session where appropriate.

**Done when:** a mixed batch failure restores both SQL and session-local state;
successful DDL becomes visible to other sessions only after commit.

### db-06 — Move CREATE into PgDatabase
**Status:** TODO · **Depends on:** db-04, db-05

- **db-06-a · TODO:** Implement `rows_insert`; make `PgWriterInsert` assemble rows, call the database,
  and interpret results. Move its SQL/plan preparation out of the writer.
- **db-06-b · TODO:** Insert through the root with nv=HEAD. Re-create over a tombstone archives by
  changing nv when history is ON; history-OFF replaces current state in place.
- **db-06-c · TODO:** Preserve duplicate-live conflicts, lifecycle reset, undefined byte-array
  handling, batch correlation, and returned tuples.

**Done when:** insert/re-create cases work in both history modes and CREATE has
no dependency on old table descriptors or direct history INSERTs.

### db-07 — Move UPDATE and UPSERT into PgDatabase
**Status:** TODO · **Depends on:** db-06

- **db-07-a · TODO:** Implement `rows_update` / `rows_upsert` and switch their writers to delegation.
- **db-07-b · TODO:** Archive by UPDATE of nv through the root, then write the successor; with
  history OFF update HEAD in place. Prove SQL statement/CTE dependencies and
  concurrent-writer locking rather than assuming textual CTE order.
- **db-07-c · TODO:** Preserve missing/deleted state checks, atomic expected-version semantics,
  CREATE/UPDATE action selection, change counts, previous tuple numbers, and
  undefined-versus-null byte-array behavior.
- **db-07-d · TODO:** Close all plans/cursors and keep query logging/EXPLAIN available.

**Done when:** update/upsert behavior and concurrent conflicts are verified in
both history modes; writers contain orchestration and tuple reconstruction only.

### db-08 — Move DELETE/PURGE and metadata deletion into PgDatabase
**Status:** TODO · **Depends on:** db-07

- **db-08-a · TODO:** Implement existing delete-with-old-state and purge behavior using root row
  operations and the agreed behavior matrix; preserve atomic/no-op outcomes.
- **db-08-b · TODO:** Keep tombstones non-null in nv and preserve temporal lifetime semantics.
- **db-08-c · TODO:** Connect catalog/collection physical drop to successful logical metadata writes
  in the same transaction; do not drop physical data before state validation.
- **db-08-d · TODO:** Keep new-state/empty-state deletion APIs as explicit follow-up work unless
  selected in db-01; replace silent empty stubs with implemented or clearly
  unsupported behavior.

**Done when:** delete/purge, re-create, and catalog/collection deletion preserve
agreed API outcomes, history, and rollback behavior.

### db-09 — Consolidate supporting reads and remove the table hierarchy
**Status:** TODO · **Depends on:** db-08

- **db-09-a · TODO:** Add database functions for tuple loading, physical metadata inspection, and
  prepared query execution; route SQL currently in `PgCache`, `PgSession`, and
  `PgReader` through them. Retain `PgRows` and focused predicate builders.
- **db-09-b · TODO:** Finish root query SQL, current/temporal/latest-N/deleted filtering, history-OFF
  behavior, and physical fn conversion. Preserve deterministic ordering semantics
  explicitly when encoded fn order differs from logical fn order.
- **db-09-c · TODO:** Use PostgreSQL inheritance/partition catalogs for default-child and topology
  discovery. Replace or remove `PgRelation` name parsing and its SQL as needed.
- **db-09-d · TODO:** Remove `PgTable`, `PgHeadTable`, `PgHistoryTable`, `PgHistoryPartition`, and
  `PgDistributionPartition` after checking all callers, tests, and documentation.
  Review `PgCollectionIndex` and `PgWriterPlan` for remaining value independently.
- **db-09-e · TODO:** Remove manual partition creation/maps, recursive index fan-out, obsolete
  `$hst` expectations, modulo routing, and stale writer properties/logging.

**Done when:** retained descriptors have no dependency on the old hierarchy;
writers never select/create physical partitions and normal reads need no cached
list of partman-created children.

### db-10 — Complete integrated acceptance checks
**Status:** TODO · **Depends on:** db-09

- **db-10-a · TODO:** Compile common/JVM/JS sources, recording any pre-existing failures separately.
- **db-10-b · TODO:** Run focused existing suites: bootstrap, collection/partitioning, PgRows,
  insert/update/upsert/delete, re-create, duplicate successor, history, ordered
  reads, tuple persistence/materialization, transactions, and error mapping.
- **db-10-c · TODO:** Add missing integration coverage for batch rollback, concurrent writes,
  history-OFF, non-divisor partition counts, maintenance/premake, retention,
  cleanup, and historical imports requiring explicit older-range creation.
- **db-10-d · TODO:** Verify root/child mandatory/custom indexes and relevant index-only query plans
  after VACUUM; verify HEAD and bounded-history partition pruning.
- **db-10-e · TODO:** Follow repository test-running guidance when executing checks; record commands
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
3. **Delete scope:** default proposal is to move the existing old-state API first.
   Confirm PURGE behavior for an already-deleted HEAD (current SQL filters it out
   despite comments describing tombstone removal), and whether new-state/empty-
   state deletes belong in this migration.

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
- Rebase onto `33dc1827d` completed. Conflict resolutions preserve upstream's
  streaming API, identifier helpers, HTTP logging, collection-index normalization,
  and PostgreSQL 18.6 test image. Tuple copying retains both the existing
  `copyOnWrite` API and the new multi-path `shallowCopy`, with cloned containers
  attached to the copied root. Post-rebase constant/helper import fixes are left
  uncommitted for review.
- Post-rebase verification: `./gradlew compileKotlinJvm compileTestKotlinJvm
  compileKotlinJs --console=plain` passes. The 21 focused psql tests listed in
  db-00, `IdTest`, and `StreamRequestBuilderTest` also pass. No database-backed
  integration tests were run.
