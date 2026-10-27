# PgDatabase migration workboard

[JIRA: NAKSHA-33](https://here-technologies.atlassian.net/browse/NAKSHA-33)

## Goal and working rules

Make `PgDatabase` the entry point for physical PostgreSQL operations used by
`PgWriter` and its operation writers. Implement the single-root/pg_partman design
in [partman.md](partman.md), replacing the old table-object hierarchy with
dedicated database functions.

- Work in ticket order, one reviewable change at a time. Update status and record
  verification/results here after each ticket.
- Task lifecycle: `TODO` → `REVIEW` → `DONE`. Start with `TODO`; after implementing
  and verifying a change, switch it to `REVIEW`. Only explicit user approval
  permits switching it to `DONE`. Dependencies must be complete before a task
  is marked done. A parent with work under review is `REVIEW` until all sub-steps
  and acceptance checks are approved.
- Maintain one short **Commit summary** as a nested sub-point directly below each
  task, and include it in the response. Revise that same summary throughout review
  to describe the current changes; do not append separate review/follow-up commit
  summaries for an unapproved task.
- Keep changes uncommitted during `REVIEW`. When the user explicitly approves a
  task, mark it `DONE` and commit its intended changes using the agreed summary.
  Inspect status, diff, and recent history first, and stage only the approved task's
  files. Every such commit must include this exact sign-off trailer:

  ```text
  Signed-off-by: Alexander Lowey-Weber <alexander.weber@here.com>
  ```
- Ticket identifiers are lowercase (`db-01`). Each task has an addressable letter
  suffix (`db-01-a`, `db-01-b`, etc.) and its own status. Complete all sub-steps
  and the acceptance checks before marking the parent ticket done.
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
**Status:** REVIEW · **Depends on:** none

- **db-01-a · DONE:** Finalize `catalog_*`, `collection_*`, index, and `rows_*` signatures before switching callers. Correct `collection_create_table_if_not_exits` spelling.
  - **Commit summary:** Define PgDatabase operation signatures, add index APIs, and fix collection creation naming.
  - **Review follow-up commit summary:** Document physical database operations and return nullable mutation errors while updating PgRows in place.
- **db-01-b · DONE:** Pass explicit columns/indices and physical options: history mode, partition count, shift, retain, backward, forward, and supported storage/tablespace settings. Use a small options value if preferable to many parameters; it must not become a replacement `PgTable` hierarchy.
  - **Commit summary:** Pass physical collection creation settings to PgDatabase while leaving partition management to pg_partman.
- **db-01-c · REVIEW:** Revise the plan for tuple-based persistence, immutable `NewTuple` input, thrown `NakshaException` errors, and UPDATE/UPSERT atomicity. Identify remaining decisions before implementation.
  - **Commit summary:** Plan immutable NewTuple mutation inputs, tuple results, and exception-based database errors.
- **db-01-d · TODO:** Agree previous-tuple validity/version matching, structured error details, DELETE/PURGE inputs/results, and batch behavior. Specify cursor/plan ownership and SQL logging/EXPLAIN hooks.
- **db-01-e · TODO:** Write a compact behavior matrix for missing/live/deleted HEAD rows across
  CREATE, UPDATE, UPSERT, DELETE, PURGE and both history modes. Resolve the open
  decisions below before implementing the affected operations.
- **db-01-f · TODO:** Implement immutable `NewTuple(tuple, previousTupleNumber)` in the model and remove `previousTupleNumber` from `Tuple`. Change `Tuple.encodeFeature`, `Tuple.encodeObject`, and `Tuple.encodeRaw` to return `NewTuple` and populate its predecessor metadata. Migrate encoding/bootstrap/writer/copy call sites without mutating cached tuples; verify JVM/JS compatibility and cache isolation.
- **db-01-g · TODO:** Replace the reviewed-but-superseded row-buffer mutation signatures/KDoc in `PgDatabase` with `NewTuple` inputs/results and thrown `NakshaException`. INSERT returns supplied tuple states; UPDATE/UPSERT return ordered `List<NewTuple>` carrying actual predecessor information. Keep `PgRows` private to persistence implementation and wire agreed DELETE/PURGE signatures.

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

#### db-01-c — Revised tuple persistence contract (plan in REVIEW)

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
- A present, valid `NewTuple.previousTupleNumber` requests an atomic UPDATE;
  otherwise UPDATE is unconditional. No separate atomic flag is needed at this
  persistence boundary. Request adapters may still accept their existing flags
  and translate them into wrapper metadata.
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
  is intended to be the supplied new state; payload resolution remains an open
  question below. Input predecessor metadata is an expectation; output metadata
  describes the predecessor actually replaced.
- UPSERT returns `NewTuple` with `previousTupleNumber` set when UPDATE was
  performed, and null when INSERT was performed. Re-creation after purging a
  DELETE HEAD counts as INSERT, so the result predecessor is null in that case.
  The final tuple may differ from input due to branch-specific state changes.
  Returned tuples/wrappers may reuse input references only when all values match;
  inputs and cached tuples are never modified to populate result metadata.
- Schema/index APIs continue receiving physical names/settings/descriptors.
  The tuple persistence boundary still needs target columns/configuration;
  tuples alone do not describe the collection's full physical schema.

##### Operation sequence

| Operation             | Planned behavior                                                                                                                                                                                                                                                             |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| INSERT | Require null input `previousTupleNumber`, otherwise throw illegal argument before mutation. Purge matching HEAD states only when DELETE, then insert. A live HEAD causes an error. Return the supplied tuple states. |
| UPDATE, unconditional | Require an existing live HEAD and replace it. Return `NewTuple` with the new state and the actual replaced HEAD's tuple number as `previousTupleNumber`. |
| UPDATE, atomic | Require a matching live HEAD according to the expected predecessor. On mismatch throw a structured error. Return `NewTuple` with the new state and the actual replaced HEAD's tuple number. |
| UPSERT | Purge DELETE HEAD states, attempt INSERT, then UPDATE the existing-live branch atomically or unconditionally according to input metadata. Return `NewTuple` with predecessor set only for UPDATE, null for INSERT (including re-creation). |
| DELETE / PURGE        | Inputs, atomicity, missing/deleted behavior, and returned-state meaning still require decisions below. Do not silently force these operations into a new-state wrapper when no new state exists.                                                                             |

Archival remains the agreed root-table behavior: change predecessor `nv` when
history is enabled; replace/remove current state without retaining history when
disabled. pg_partman owns partition provisioning. The UPSERT sequence is a
behavioral contract, not yet a choice of one SQL statement versus several;
locking and concurrent INSERT/UPDATE races must preserve it.

##### Proposed interface shape for review

```kotlin
// Schematic: target/schema options are omitted here, not removed from the API.
fun rows_insert(/* target/config */, tuples: List<NewTuple>): List<Tuple>
fun rows_update(/* target/config */, tuples: List<NewTuple>): List<NewTuple>
fun rows_upsert(/* target/config */, tuples: List<NewTuple>): List<NewTuple>
// Failure: throw NakshaException(error: NakshaError).
```

Use the common wrapper for new-state operations. INSERT rejects non-null
predecessor metadata; UPDATE/UPSERT results expose the actual predecessor only
when a live HEAD was updated.
Proposed result ordering is input order, including mixed CREATE/UPDATE UPSERT
batches; SQL result order must never be assumed.

##### Remaining decisions before mutation implementation

1. **Valid expected predecessor:** `TupleNumber` currently has no universal
   `isValid` predicate. Define permitted version/action values and whether its
   database/catalog/collection/feature must match the new tuple's address.
   Proposed: null/invalid means unconditional as requested; a structurally valid
   predecessor for a different feature/target is an illegal argument rather than
   silently downgrading atomicity. Confirm this distinction.
2. **Version comparison:** compare the complete tuple number/version including
   action bits, or mask the low two action bits as current writer SQL does?
   Keep the live-HEAD condition regardless of the choice.
3. **Atomic UPSERT insert branch:** when an expected predecessor is present but
   HEAD is missing or DELETE, does insertion still succeed? The stated sequence
   applies atomicity to UPDATE, suggesting yes. Confirm whether an expectation
   only constrains the live-HEAD UPDATE branch or the entire UPSERT.
4. **Exact UPDATE/INSERT return state:** existing SQL may preserve byte arrays
   marked `undefined`, increment change count, and normalize action bits. Returning
   the original immutable tuple is only correct if its values already describe
   the stored state. Decide whether callers must provide fully resolved new
   states, or whether persistence may return a replacement tuple for these cases.
   Do not mutate the input to reconcile the difference.
5. **Error payload:** proposed conflict fields are operation, batch input index,
   target/feature identity, expected tuple number, actual tuple number (nullable
   when missing), and a reason distinguishing missing/deleted/version mismatch.
   `NakshaError` already has database/catalog/collection/id and a code/message;
   avoid a base-to-model dependency when adding tuple-number details (the base
   `TupleNumber` type or serialized form is available). Decide code compatibility
   (`CONFLICT` versus existing not-found/exists codes) and first-error versus
   aggregated batch errors.
6. **DELETE/PURGE:** choose selection arguments when no new tuple exists; decide
   whether DELETE uses `NewTuple` for supplied tombstone states, how old-state or
   empty-state DELETE works, and whether PURGE returns removed states or no
   tuples. Confirm already-deleted HEAD and atomic no-op behavior.
7. **Batch/transaction guarantee:** proposal is all-or-error with no partial result
   list. A thrown exception can follow SQL changes; request-level savepoint/full
   rollback must restore them before the caller continues. Decide handling of
   duplicate feature numbers in one batch (reject or process in a defined order),
   error correlation, and results for no-ops before defining internal row columns.

##### Ordered implementation after plan approval

1. Resolve the relevant decisions in db-01-d/e, including the return-state issue.
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
or tests run. This task remains REVIEW until the user approves the revised plan.

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
  retention, and feature-number encoding/routing configuration for pg_partman.
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

- **db-06-a · TODO:** Implement tuple-based `rows_insert`; make `PgWriterInsert` supply new states
  through the agreed wrapper and use returned tuples. Move row conversion, SQL,
  and result reconstruction inside `PgDatabase`.
- **db-06-b · TODO:** Insert through the root with nv=HEAD. Re-create over a tombstone archives by
  changing nv when history is ON; history-OFF replaces current state in place.
- **db-06-c · TODO:** Preserve duplicate-live conflicts, lifecycle reset, undefined byte-array
  handling as agreed in db-01-d/e, batch correlation, and original-state return
  guarantees. Fail with `NakshaException` carrying structured `NakshaError`.

**Done when:** insert/re-create cases work in both history modes and CREATE has
no dependency on old table descriptors or direct history INSERTs.

### db-07 — Move UPDATE and UPSERT into PgDatabase
**Status:** TODO · **Depends on:** db-06

- **db-07-a · TODO:** Implement tuple-based `rows_update` / `rows_upsert` and switch their writers
  to `NewTuple` inputs and returned final tuples. Keep internal `PgRows` and
  reconstruction in `PgDatabase`; never update cached tuple metadata.
- **db-07-b · TODO:** Archive by UPDATE of nv through the root, then write the successor; with
  history OFF update HEAD in place. Prove SQL statement/CTE dependencies and
  concurrent-writer locking rather than assuming textual CTE order.
- **db-07-c · TODO:** Preserve missing/deleted state checks, atomic expected-version semantics,
  CREATE/UPDATE action selection, change counts, and undefined-versus-null
  byte-array behavior according to db-01 decisions. Read atomic expectations from
  immutable `NewTuple.previousTupleNumber`, not from a mutable `Tuple` field;
  throw structured conflict exceptions. UPSERT purges DELETE HEAD, attempts
  INSERT, then applies conditional/unconditional UPDATE to the live branch.
- **db-07-d · TODO:** Close all plans/cursors and keep query logging/EXPLAIN available.

**Done when:** update/upsert behavior and concurrent conflicts are verified in
both history modes; writers handle orchestration/responses and `PgDatabase`
returns final immutable tuples.

### db-08 — Move DELETE/PURGE and metadata deletion into PgDatabase
**Status:** TODO · **Depends on:** db-07

- **db-08-a · TODO:** Implement existing delete-with-old-state and purge behavior using root row
  operations and the agreed behavior matrix; use the selection/result signatures
  agreed in db-01-d/e and throw `NakshaException` on errors. Keep row transport
  and tuple reconstruction internal; preserve atomic/no-op outcomes.
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
4. **Tuple persistence contract:** resolve the seven decisions under db-01-c
   before implementing affected mutations. The new `NewTuple`/exception contract
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
- db-01-c (REVIEW): revised the plan for immutable `NewTuple` expectations,
  tuple-based persistence/results, internal row buffers, and thrown structured
  errors. Added model/API migration tasks db-01-f/g and remaining behavioral
  questions; no production code changed.
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
