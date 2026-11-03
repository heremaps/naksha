# PgDatabase migration workboard

[JIRA: NAKSHA-33](https://here-technologies.atlassian.net/browse/NAKSHA-33)

## Goal and working rules

Make `PgDatabase` the entry point for physical PostgreSQL operations used by
`PgWriter` and its operation writers. Implement the single-root/pg_partman design
in [partman.md](partman.md), with the custom-maintainer decision below superseding
pg_partman provisioning, replacing the old table-object hierarchy with
dedicated database functions.

- Work in ticket order, one reviewable change at a time. Update the ticket `State`
  in the table and record verification/results in the ticket's `Details` section.
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
- Tickets are flat leaves with no nested children. A `db-NN` parent groups
  `db-NN-a`, `db-NN-b`, … children; each child links back to its parent through the
  table's `Depends On` column. A child is `DONE` when its own acceptance checks
  pass; a parent is `DONE` once all of its children are.
- Completed tickets move to [NAKSHA-33-DONE.md](NAKSHA-33-DONE.md) and are struck
  out (`~~db-00~~`) in the table so they stay linked but out of the way.
- `PgWriter*` owns request validation, feature-to-tuple encoding, operation
  dispatch, response construction, and transaction bookkeeping.
- `PgDatabase` owns generation and execution of all write operations: schema/table/index
  DDL, partition management, and INSERT/UPDATE/UPSERT/DELETE/PURGE SQL, including
  preparation, binding and tuple persistence/result reconstruction. Mutation callers
  supply immutable `NewTuple` wrappers and receive final `Tuple` states.
  Request processing, member processors, feature encoding, and admin-feature
  business logic remain outside `PgDatabase`.
- Keep `PgRows` and its columnar binding/reading helpers, `PgRowsColumn`,
  `PgColumn` (the current singular class), and `PgIndex`. Keep logical
  `PgCatalog`/`PgCollection` wrappers. Small SQL-fragment helpers in these types
  are appropriate; descriptors must not execute writes. `PgIndex` may retain
  capability validation and index-definition fragments; `PgColumn` may retain
  identifier/column-definition fragments.
- `PgRows`/`PgRowsColumn` remain internal transport helpers: column arrays,
  ordinal conversion, aliases, placeholders, type names, binding values, UNNEST
  projections and cursor ingestion. They do not open connections or execute DDL/DML.
- Read planning, predicate/order generation and read execution may remain in
  query builders, readers, caches and sessions where appropriate. Keep
  `PgQueryWhereBuilder` focused. Fix naming/ordinal consistency as needed, but do
  not require consolidation of all read SQL or move every SQL fragment into
  `PgDatabase` as part of this migration.
- Write methods use the caller's connection and transaction, close their own
  plans/cursors, and never commit internally. Initial root/child index builds
  remain synchronous; members and index definitions are immutable after collection
  creation until a dedicated schema/index modification operation is introduced.
- Support clean/new databases, as agreed in `partman.md`.
- Current authoritative tuple contract: approved
  [db-01-c](NAKSHA-33-DONE.md#db-01-c--revise-the-tuple-persistence-contract),
  [db-01-d](NAKSHA-33-DONE.md#db-01-d--document-identity-validation-and-preconditions),
  and [db-01-e](NAKSHA-33-DONE.md#db-01-e--write-the-behavior-matrix)
  define immutable `NewTuple` inputs/results, identity/version checks, mutation
  outcomes, and caller-owned rollback/uniqueness/locking-order preconditions.
  They supersede the historical row-buffer API and conflicting `partman.md` rules.
- Current partition ownership: Naksha's shared Kotlin maintainer will manage history **and performance** partitions using `"naksha~admin".naksha_partman`. The user approved Naksha ownership after the pg_partman 5.5.0 blocker and successful custom prototype. Maintain the current finite range plus three future ranges and HEAD. Earlier pg_partman/template references below are historical unless explicitly retained. The JVM-first Kotlin direction below supersedes PL/pgSQL algorithm ownership; SQL prototypes remain verification references. Production implementation, custom columns/indexes and hourly cron are tracked in db-04-b/d/g/h. Retention remains separate and is not implemented by the prototype.

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

## Tickets

| Ticket # | Summary | Depends On | State | Commit |
| --- | --- | --- | --- | --- |
| ~~[db-00](NAKSHA-33-DONE.md#db-00--restore-a-compilable-baseline)~~ | ~~Restore a compilable baseline~~ | — | `DONE` | Restore compilable psql baseline and rebase import fixes |
| ~~[db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts)~~ | ~~Agree the database boundary and operation contracts~~ | — | `DONE` | Archive completed database contract and partition-maintenance verification tickets. |
| ~~[db-01-a](NAKSHA-33-DONE.md#db-01-a--finalize-operation-signatures)~~ | ~~Finalize operation signatures~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | — |
| ~~[db-01-b](NAKSHA-33-DONE.md#db-01-b--pass-physical-creation-options)~~ | ~~Pass physical creation options~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | — |
| ~~[db-01-c](NAKSHA-33-DONE.md#db-01-c--revise-the-tuple-persistence-contract)~~ | ~~Revise the tuple persistence contract~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | — |
| ~~[db-01-d](NAKSHA-33-DONE.md#db-01-d--document-identity-validation-and-preconditions)~~ | ~~Document identity validation and preconditions~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | Add expected and actual error versions with tuple-number reconstruction helpers. |
| ~~[db-01-e](NAKSHA-33-DONE.md#db-01-e--write-the-behavior-matrix)~~ | ~~Write the behavior matrix~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | Document mutation outcomes and history behavior for tuple persistence. |
| ~~[db-01-f](NAKSHA-33-DONE.md#db-01-f--migrate-the-model-to-immutable-newtuple)~~ | ~~Migrate the model to immutable NewTuple~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | Introduce an immutable NewTuple wrapper and remove the mutable previousTupleNumber field from Tuple. |
| ~~[db-01-g](NAKSHA-33-DONE.md#db-01-g--replace-the-row-buffer-signatures)~~ | ~~Replace the row-buffer signatures~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | Replace row-buffer mutation signatures with immutable tuple inputs and typed results. |
| ~~[db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version)~~ | ~~Verify pg_partman topology against the deployed version~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts) | `DONE` | Archive completed database contract and partition-maintenance verification tickets. |
| ~~[db-02-a](NAKSHA-33-DONE.md#db-02-a--run-the-pg_partman-integration-spike)~~ | ~~Run the pg_partman integration spike~~ | [db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version) | `DONE` | Verify pg_partman HEAD topology with a rollback-only SQL spike. |
| ~~[db-02-b](NAKSHA-33-DONE.md#db-02-b--verify-nv-ranges-and-default-child-partitioning)~~ | ~~Verify nv ranges and default-child partitioning~~ | [db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version) | `DONE` | Verify HEAD range boundaries, uniqueness and invalid next-version rejection. |
| ~~[db-02-c](NAKSHA-33-DONE.md#db-02-c--verify-finite-children-fn-leaves-and-indexes)~~ | ~~Verify finite-children fn leaves and indexes~~ | [db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version) | `DONE` | Verify partition provisioning limits and prototype a Naksha-owned maintainer. |
| ~~[db-02-d](NAKSHA-33-DONE.md#db-02-d--verify-maintenance-and-cleanup)~~ | ~~Verify maintenance and cleanup~~ | [db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version) | `DONE` | Archive completed database contract and partition-maintenance verification tickets. |
| ~~[db-03](NAKSHA-33-DONE.md#db-03--finish-the-physical-row-and-naming-contract)~~ | ~~Finish the physical row and naming contract~~ | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts), [db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version) | `DONE` | Unify identifier aliases and physical naming across storage boundaries. |
| ~~[db-03-a](NAKSHA-33-DONE.md#db-03-a--validate-create-only-partition-options)~~ | ~~Validate create-only partition options~~ | [db-03](#db-03--finish-the-physical-row-and-naming-contract) | `DONE` | Validate collection creation options and add physical feature-number conversion helpers. |
| ~~[db-03-b](NAKSHA-33-DONE.md#db-03-b--add-fn-conversion-helpers-and-init-sql)~~ | ~~Add fn conversion helpers and init SQL~~ | [db-03](#db-03--finish-the-physical-row-and-naming-contract) | `DONE` | Validate collection creation options and add physical feature-number conversion helpers. |
| ~~[db-03-c](NAKSHA-33-DONE.md#db-03-c--keep-feature-numbers-logical)~~ | ~~Keep feature numbers logical~~ | [db-03](#db-03--finish-the-physical-row-and-naming-contract) | `DONE` | Keep feature identities logical and store feature-ordinals with aligned partition ranges. |
| ~~[db-03-d](NAKSHA-33-DONE.md#db-03-d--make-head-nv-non-null)~~ | ~~Make HEAD nv non-null~~ | [db-03](#db-03--finish-the-physical-row-and-naming-contract) | `DONE` | Preserve non-null HEAD next-version values at the row boundary. |
| ~~[db-03-e](NAKSHA-33-DONE.md#db-03-e--establish-the-root-naming-rule)~~ | ~~Establish the root naming rule~~ | [db-03](NAKSHA-33-DONE.md#db-03--finish-the-physical-row-and-naming-contract) | `DONE` | Unify identifier aliases and physical naming across storage boundaries. |
| [db-04](#db-04--implement-schema-collection-and-index-operations) | Implement schema, collection, and index operations | [db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version), [db-03](#db-03--finish-the-physical-row-and-naming-contract) | `IN_PROGRESS` | — |
| ~~[db-04-a](NAKSHA-33-DONE.md#db-04-a--implement-schema-operations)~~ | ~~Implement schema operations~~ | [db-04](#db-04--implement-schema-collection-and-index-operations) | `DONE` | Implement transactional schema lookup and creation with logical-name comments. |
| [db-04-b](#db-04-b--implement-collection-creation) | Implement collection creation | [db-04](#db-04--implement-schema-collection-and-index-operations) | `IN_REVIEW` | — |
| [db-04-c](#db-04-c--move-storage-settings-out-of-table-classes) | Move storage settings out of table classes | [db-04](#db-04--implement-schema-collection-and-index-operations) | `IN_PROGRESS` | — |
| [db-04-d](#db-04-d--move-index-execution-into-pgdatabase) | Move index execution into PgDatabase | [db-04](#db-04--implement-schema-collection-and-index-operations) | `IN_PROGRESS` | — |
| [db-04-e](#db-04-e--implement-collection-and-schema-drop) | Implement collection and schema drop | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-f](#db-04-f--route-bootstrap-and-create-upsert-preparation) | Route bootstrap and create-upsert preparation | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-g](#db-04-g--productionize-the-custom-partition-maintainer) | Productionize the custom partition maintainer | [db-04](#db-04--implement-schema-collection-and-index-operations) | `IN_PROGRESS` | — |
| [db-04-h](#db-04-h--schedule-registry-wide-maintenance) | Schedule registry-wide maintenance | [db-04-g](#db-04-g--productionize-the-custom-partition-maintainer) | `TODO` | — |
| [db-05](#db-05--make-write-failures-restore-database-and-session-state) | Make write failures restore database and session state | [db-01](NAKSHA-33-DONE.md#db-01--agree-the-database-boundary-and-operation-contracts), [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-05-a](#db-05-a--cover-ddl-and-writes-in-the-rollback-boundary) | Cover DDL and writes in the rollback boundary | [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-05-b](#db-05-b--complete-error-mapping-context) | Complete error-mapping context | [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-05-c](#db-05-c--publish-local-changes-after-commit) | Publish local changes after commit | [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-05-d](#db-05-d--fail-the-whole-batch-on-validation-error) | Fail the whole batch on validation error | [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-05-e](#db-05-e--enforce-one-mutation-per-feature-per-version) | Enforce one mutation per feature per version | [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-05-f](#db-05-f--verify-deterministic-input-sorting) | Verify deterministic input sorting | [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-06](#db-06--move-create-into-pgdatabase) | Move CREATE into PgDatabase | [db-04](#db-04--implement-schema-collection-and-index-operations), [db-05](#db-05--make-write-failures-restore-database-and-session-state) | `TODO` | — |
| [db-06-a](#db-06-a--implement-tuple-based-insert) | Implement tuple-based insert | [db-06](#db-06--move-create-into-pgdatabase) | `TODO` | — |
| [db-06-b](#db-06-b--insert-through-the-root-at-head) | Insert through the root at HEAD | [db-06](#db-06--move-create-into-pgdatabase) | `TODO` | — |
| [db-06-c](#db-06-c--preserve-conflicts-and-drop-the-undefined-sentinel) | Preserve conflicts and drop the undefined sentinel | [db-06](#db-06--move-create-into-pgdatabase) | `TODO` | — |
| [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | Move UPDATE and UPSERT into PgDatabase | [db-06](#db-06--move-create-into-pgdatabase) | `TODO` | — |
| [db-07-a](#db-07-a--implement-tuple-based-update-and-upsert) | Implement tuple-based update and upsert | [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | `TODO` | — |
| [db-07-b](#db-07-b--archive-by-nv-update-through-the-root) | Archive by nv update through the root | [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | `TODO` | — |
| [db-07-c](#db-07-c--preserve-atomic-semantics-and-remove-merging) | Preserve atomic semantics and remove merging | [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | `TODO` | — |
| [db-07-d](#db-07-d--close-plans-and-cursors-keep-logging) | Close plans and cursors, keep logging | [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | `TODO` | — |
| [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | Move DELETE/PURGE and metadata deletion into PgDatabase | [db-07](#db-07--move-update-and-upsert-into-pgdatabase) | `TODO` | — |
| [db-08-a](#db-08-a--implement-id-and-guid-delete-and-purge) | Implement ID and GUID delete, and purge | [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | `TODO` | — |
| [db-08-b](#db-08-b--keep-tombstones-non-null-in-nv) | Keep tombstones non-null in nv | [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | `TODO` | — |
| [db-08-c](#db-08-c--connect-physical-drop-to-metadata-writes) | Connect physical drop to metadata writes | [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | `TODO` | — |
| [db-08-d](#db-08-d--replace-the-generic-delete-stubs) | Replace the generic delete stubs | [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | `TODO` | — |
| [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | Consolidate supporting reads and remove the table hierarchy | [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase) | `TODO` | — |
| [db-09-a](#db-09-a--add-tuple-loading-and-query-functions) | Add tuple loading and query functions | [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |
| [db-09-b](#db-09-b--finish-root-query-sql-and-filtering) | Finish root query SQL and filtering | [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |
| [db-09-c](#db-09-c--use-partition-catalogs-for-topology) | Use partition catalogs for topology | [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |
| [db-09-d](#db-09-d--remove-the-old-table-hierarchy) | Remove the old table hierarchy | [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |
| [db-09-e](#db-09-e--remove-manual-partition-creation) | Remove manual partition creation | [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |
| [db-10](#db-10--complete-integrated-acceptance-checks) | Complete integrated acceptance checks | [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |
| [db-10-a](#db-10-a--compile-common-jvm-and-js-sources) | Compile common, JVM, and JS sources | [db-10](#db-10--complete-integrated-acceptance-checks) | `TODO` | — |
| [db-10-b](#db-10-b--run-the-focused-existing-suites) | Run the focused existing suites | [db-10](#db-10--complete-integrated-acceptance-checks) | `TODO` | — |
| [db-10-c](#db-10-c--add-missing-integration-coverage) | Add missing integration coverage | [db-10](#db-10--complete-integrated-acceptance-checks) | `TODO` | — |
| [db-10-d](#db-10-d--verify-indexes-and-query-plans) | Verify indexes and query plans | [db-10](#db-10--complete-integrated-acceptance-checks) | `TODO` | — |
| [db-10-e](#db-10-e--follow-the-test-running-guidance) | Follow the test-running guidance | [db-10](#db-10--complete-integrated-acceptance-checks) | `TODO` | — |
| [db-11](#db-11--restore-transactional-metadata-cache-isolation) | Restore transactional metadata cache isolation after functional recovery | [db-06](#db-06--move-create-into-pgdatabase), [db-07](#db-07--move-update-and-upsert-into-pgdatabase), [db-08](#db-08--move-deletepurge-and-metadata-deletion-into-pgdatabase), [db-09](#db-09--consolidate-supporting-reads-and-remove-the-table-hierarchy) | `TODO` | — |

## Details

### Agreed JVM-First Implementation

Approved combined increment `DONE`: `Resolve storage layouts and scope catalog and collection locking.` The user approved the pending storage-owned paired-layout rules, transactional physical drops and replacement of the coarse global lock with catalog/collection locks. All reviewed implementation and regression files are included; detailed acceptance is archived in [NAKSHA-33-DONE.md](NAKSHA-33-DONE.md#storage-layouts-physical-drops-and-scoped-locking-increment). Earlier IN_REVIEW/global-lock notes below are historical and superseded by this approval and the current scoped-lock contract. Remaining db-04 work includes catalog-internal collection orchestration; cron remains deferred.

Current locking contract (supersedes all earlier global-sweep/coarse-lock notes): exclusive transaction advisory locks are catalog-scoped only for actual catalog creation/drop. Collection creation/drop, index DDL and explicit per-root provisioning take a compatible shared catalog guard and an exclusive collection advisory lock. Reusing an existing catalog takes a shared guard, not an exclusive catalog lock. There is no global provisioning or sweep mutex. Catalog-exclusive upgrades after earlier collection work use a nonblocking attempt when the transaction already holds a shared guard; if another transaction prevents the upgrade, fail promptly with an explicit retry error rather than waiting in a shared-to-exclusive deadlock. Callers touching multiple catalogs/collections should still order their own blocking operations consistently; arbitrary external DDL is outside this cooperative protocol.

Maintenance tries the shared catalog guard, collection advisory lock and recursive physical ACCESS EXCLUSIVE lock NOWAIT per collection. Busy collections/catalogs are skipped while independent collections, including others in the same catalog, progress. Each attempt is isolated in a JDBC savepoint or PLV8 subtransaction; skipped attempts roll back to release newly acquired locks and do not poison the outer transaction. Successful locks remain until caller commit/rollback. The return value counts completed collections, not registry entries; callers must allow fewer completions and retry later. Stale snapshot entries removed by a committed cooperative drop are skipped; invalid retained registrations/topology and non-lock errors still fail the sweep. SQLSTATE 55P03 is classified inside PLV8's callback before that runtime strips native error fields; other original errors are captured and rethrown after rollback.

Scoped-lock increment `IN_REVIEW`, uncommitted. Verification on fresh PostgreSQL 18.6 / PLV8 3.2.5 databases passes: collection A remains transaction-locked while PLV8 completes same-catalog B and other-catalog C; both holder commit/rollback and later retry; ordinary INSERT/SELECT conflicts skipped via NOWAIT with no leaked A advisory lock; exclusive catalog guard skips only its catalog; independent collection creation progresses during an uncommitted 20,000-row insert; same-root try-lock conflicts correctly; independent catalog creation progresses; contested shared-to-exclusive catalog upgrades fail promptly while preserving prior writes and upgrade successfully after competing guards release; no retired global advisory lock is held/waited on. Updated drop lifecycle tests pass with exact collection/schema lock waits, rollback and missing-registry cleanup. Full installed PLV8 maintenance and non-superuser permission tests pass. Twenty-two focused schema/index/layout tests and production JS compilation pass. Temporary databases/roles were removed. General high-contention stress and deterministic ordering for arbitrary multi-target application transactions remain outside this verification; cron remains deferred. Existing staged layout changes and uncommitted drop work were preserved, and no commit was made.

Approved JVM creation/bootstrap/registry-sweep increment: `Implement shared Kotlin collection creation and partition maintenance.` The user approved committing this increment and continuing with PLV8 attachment; the parent db-04 tickets remain open for their remaining acceptance requirements. Approval covers the tested permanent-storage scope and bootstrap prerequisite fixes, not deferred storage classes, logical mutation migration, PLV8 parity or scheduling. The completed increment is recorded in [NAKSHA-33-DONE.md](NAKSHA-33-DONE.md#jvm-creation-bootstrap-and-registry-sweep-increment).

Cron decision: defer all pg_cron implementation until scheduling is reached. Each Naksha database remains an independent system with one storage and one future maintenance job. The cron extension metadata may live in the cluster-designated cron database (often `postgres`); that does not move the local `naksha~admin` runtime, registry, sequence or admin collections out of the managed database. No cross-database connection or administrative scheduling permission is required for the current manual/JVM maintenance path.

Implement physical creation and maintenance once in `commonMain` Kotlin behind `PgDatabase`, issuing PostgreSQL catalog queries and DDL through the caller's `PgConnection`. JVM creation and maintenance must execute that implementation directly through JDBC, without invoking PLV8 or PL/pgSQL maintenance functions. Compile the same Kotlin to JavaScript using the existing production-library/resource/module installation process; add PLV8 attachment and invocation only after JVM behavior is verified. Do not retain two independently maintained algorithms.

The PLV8 bootstrap will read a versioned JSON configuration from the `naksha~admin` schema comment, reconstruct the database-local `PgConfig`, attach a `PgStorage` to the already-installed database, open a write session, and invoke the same database operations. Store only explicitly allowlisted, credential-free identity/runtime settings: never serialize the complete JVM `PgConfig`, master/replica URIs, passwords or credentials into the comment. Derive ambient database/role connection context inside PostgreSQL; configuration does not authenticate or switch the PLV8 backend. Treat missing/malformed configuration and identity/version mismatches as explicit attachment failures. Admin bootstrap alone owns this comment format; ordinary catalog/collection logical-name comments retain their existing contract.

Physical maintenance obtains a mutable connection from the session without calling `useTransaction()` or allocating a logical feature version. JVM callers own commit/rollback; PLV8 uses the invoking SQL transaction. Attachment must not install/upgrade objects, recursively bootstrap the runtime, or claim to commit the outer transaction. Initially create a storage/session per entrypoint invocation; cross-invocation cache reuse requires a separate invalidation/rollback contract.

Ordered reviewable increments:

1. **JVM Kotlin creation and per-root maintenance (`db-04-b/c/g`):** fix `_jdbcConn` initialization to `null` and prove a real JVM connection works. Preserve completed schema creation, implement collection root/HEAD creation from all supplied columns and mandatory/custom indexes, then registration, finite coverage and repair in shared Kotlin. Move reusable storage behavior out of the legacy hierarchy; resolve non-permanent persistence and HEAD/history fillfactor policy before enabling those modes. Use the existing naming/ordinal/options contracts, transactional logical-name ownership, member collision checks, and table/column comments. History-OFF creates HEAD only without history registration. Replace the new SQL-call-only register/maintain methods and PL/pgSQL algorithm installation as the Kotlin equivalent becomes verified; retain investigation scripts as historical references rather than adding production compatibility code. No JavaScript facade or cron job in this increment.
2. **JVM verification and registry-wide maintenance (`db-04-d/f/g`):** exercise the real JDBC path, clean creation/bootstrap, both history modes, payload/check/index propagation, repeat calls, existing/future/repaired leaves, imported/manual versions, boundaries, conflicts and rollback. Run the registry scan in shared Kotlin with deterministic root ordering. Settle automatic initial/sweep version selection without session-local `currval()` or allocating/committing through `new_version()`. Provision finite ranges needed by writes/imports explicitly: hourly premaking cannot guarantee coverage at small shifts. Decide sweep failure isolation, lock duration, timeouts and reporting before scheduling. Do not claim full writer semantics until the later mutation/transaction tickets are complete.
3. **PLV8 storage attachment (`db-04-f/g`):** write/read the allowlisted admin-schema configuration and add a small exported Kotlin/JS entrypoint that constructs `PgStorage`, opens a write session and invokes shared Kotlin. Reuse the current `naksha_psql` module loader/installer; make attach-existing initialization work without installation or unwanted session/transaction side effects. Verify fresh-backend module initialization, exact Long/BigInt SQL transport, SQL exception propagation, session cleanup and outer-transaction rollback. Fix only adapter defects required by this path; prepared-plan/batching rewrites are not automatic prerequisites.
4. **Thin SQL wrapper and one cron job (`db-04-h`):** expose registry-wide Kotlin maintenance through a minimal `VOLATILE`, `PARALLEL UNSAFE` PLV8 wrapper; temporary wrappers may be used for parity tests first. Install one idempotently managed hourly job per database, not per collection, with explicit database/role, permissions, search path, timeouts, overlap behavior and observable failures. Re-run JVM and PLV8 parity scenarios against the same implementation. No provisioning loop or maintenance algorithm belongs in the wrapper. Runtime module refresh/versioning must prevent stale backend code from silently diverging from JVM code.

Retention remains separately undecided and must not delete data implicitly. Collection/schema DROP must unregister in the same transaction. The earlier uncommitted PL/pgSQL core below is a tested reference, not the approved final implementation language or a completed production ticket. Each increment stops for review; no commit is authorized by this plan.

<a id="db-03--finish-the-physical-row-and-naming-contract"></a>
Completed db-03 parent and children are archived in [NAKSHA-33-DONE.md](NAKSHA-33-DONE.md#db-03--finish-the-physical-row-and-naming-contract).

### Agreed Routing Contract

Use case: a collection `foo` has seven performance partitions. Its finite history range begins at `nv`, and `shifted = nv >> shift`. Performance leaves are named `foo_h{shifted}_p0` through `foo_h{shifted}_p6`; the `_p{suffix}` is the partition-index.

All of the following live in `Id` (companion functions keyed by value/index, plus instance members keyed by this id). `featureOrdinal` is the physical storage encoding and is stored for every feature regardless of partition count:

- `featureOrdinal = byteSwap(featureNumber) XOR Long.MIN_VALUE` — the byte swap moves the logical low byte to the high byte; the sign flip makes PostgreSQL signed bigint ordering follow logical bytes 0..255.
- `partitionNumber = featureNumber & 0xff` — 0..255, the routing key (the low byte of the feature-number; equivalently the signed block-index of the ordinal).
- `partitionMinOrd = (partitionNumber XOR 0x80) * 2^56`, `partitionMaxOrd = partitionMinOrd + (2^56 - 1)` — the inclusive ordinal block owned by one partition-number.
- `partitionIndex = ((partitionNumber + 1) * partitions - 1) / 256` — the contiguous (not modulo) group; the exact inverse of the byte boundary `floor(i * 256 / partitions)`.
- `partitionNumberMin(index, partitions) = index * 256 / partitions`; `partitionNumberMax(index, partitions) = 255` for the last index, else `(index + 1) * 256 / partitions - 1` — the inclusive partition-number range of an index.
- `partitionIndexMin(index, partitions) = partitionMinOrd(partitionNumberMin(index, partitions))`; `partitionIndexMax(index, partitions) = partitionMaxOrd(partitionNumberMax(index, partitions))` — the inclusive ordinal range of an index, i.e. the PostgreSQL range bounds.

All counts validate 1..256; one partition returns index 0 and spans the whole ordinal space. API, tuple and cache identities remain logical.

N=7 worked example (inclusive ordinal bounds):

| Index | partNumMin | partNumMax | partitionIndexMin | partitionIndexMax | PostgreSQL Range |
| --- | --- | --- | --- | --- | --- |
| 0 | 0 | 35 | -9223372036854775808 | -6629298651489370113 | `[MINVALUE, -6629298651489370112)` |
| 1 | 36 | 72 | -6629298651489370112 | -3963167672086036481 | `[-6629298651489370112, -3963167672086036480)` |
| 2 | 73 | 108 | -3963167672086036480 | -1369094286720630785 | `[-3963167672086036480, -1369094286720630784)` |
| 3 | 109 | 145 | -1369094286720630784 | 1297036692682702847 | `[-1369094286720630784, 1297036692682702848)` |
| 4 | 146 | 181 | 1297036692682702848 | 3891110078048108543 | `[1297036692682702848, 3891110078048108544)` |
| 5 | 182 | 218 | 3891110078048108544 | 6557241057451442175 | `[3891110078048108544, 6557241057451442176)` |
| 6 | 219 | 255 | 6557241057451442176 | 9223372036854775807 | `[6557241057451442176, MAXVALUE)` |

The PostgreSQL upper bound is the next index's `partitionIndexMin` (or `MAXVALUE` for the last), because ranges are half-open. `Id` and `TupleNumber` share `partitionIndex`. This contract supersedes conditional fn encoding and modulo routing from earlier notes. Unconditional ordinal transport is implemented; production leaf provisioning remains db-04-g.

Verification: `IdTest` (11 tests) passes — ordinal blocks tile the space monotonically (2^56 wide, spanning `Long.MIN_VALUE`..`Long.MAX_VALUE`), partition-number ranges tile 0..255, `partitionIndex` matches `partitionNumberMin/Max` for every value and count, `partitionIndexMin/Max` are contiguous across indexes, the N=7 example matches the table, and the instance methods equal the companion methods.

Completed transport details and verification are archived in [db-03-c](NAKSHA-33-DONE.md#db-03-c--keep-feature-numbers-logical). `Long.toOrdinalFeatureNumber()` and `Long.fromOrdinalFeatureNumber()` in `LibBase` are the shared conversions; the redundant `PgUtil` helpers have been removed. Query-builder and standalone cache fn search arguments must also use ordinals during db-09; legacy sign/modulo DDL and writer SQL remain scheduled for replacement.

### Agreed Naming Contract

Establish one schema-qualified, correctly quoted root naming rule across
creation, mutations, and reads; resolve text names versus `Id.*Name()` mapping.
Agreed naming groundwork: `Id.textType` classifies text as ALPHABET_ONLY (0), SAFE (1), URL_SAFE (2), UNSAFE (3), INTERNAL (4), RESERVED (5), or ILLEGAL (127). Acceptance is cumulative across legal types. INTERNAL requires at least one dollar sign and `[a-z][a-z0-9_$]*`. RESERVED matches `naksha~[a-z0-9_]{1,25}`; all other names starting with `naksha~` are illegal. Quotes, controls, empty names and malformed Unicode are illegal. Tilde otherwise remains URL_SAFE. Classification and alias KDoc describe an abstract agreement, without storage-specific terminology.

ALPHABET_ONLY and SAFE accept lowercase `a..z` only; uppercase ASCII letters raise classification to URL_SAFE. Mixed-case text is preserved only when the expected type accepts URL_SAFE (or a more permissive type), otherwise it is aliased without case folding. Updated tests pass on JVM and JS test compilation passes.

Implemented `IdTextType` as an exported JsEnum, `Id.textType`, and `Id.textAlias(expectedTextType, maxBytes, prefix)`. The alias preserves text only if its type is accepted and its UTF-8 size fits. Otherwise legal text becomes `{prefix}${intValue}` with a negative sign replaced by underscore; prefix and fallback size are deliberately not validated. INTERNAL/RESERVED follow the same checks, without bypasses. Illegal alias requests throw ILLEGAL_ID. Authorization and collision checking remain separate responsibilities.

Verification: all five `IdTextTypeTest` and 11 `IdTest` JVM tests pass; JS main/production and test compilation pass. Direct Node production-export checks pass classification, UTF-8 boundary handling and positive/negative alias generation. Node text-only Id construction requires an MD5 provider unavailable in this environment, so direct checks use verified numeric inputs. Tests include all C0/C1 controls, invalid internal/reserved patterns, supplementary Unicode, malformed surrogate sequences, cumulative acceptance and intentionally unchecked fallback prefixes/budgets.

Storage integration implemented: catalog/root naming uses URL_SAFE text within the existing 25-byte budget, with `s$`/`t$` fallback aliases; RESERVED built-ins are preserved within their defined 32-byte maximum. Member columns use SAFE/42 bytes with `m$` fallback. Logical member names remain book/index/lookup keys; `PgColumn.id` reuses `member.id`, `PgColumn.columnName = id.columnName()` provides its physical alias, and `PgRowsColumn.sqlAlias` resolves physical SQL labels. Cursor descriptor access and row ingestion resolve physical names, preserving explicit result aliases. Identifier quoting always preserves case and keywords, doubles only double quotes, and does not change backslashes. Multipart quoting still concatenates one identifier, not schema qualification.

Member-name integration: `Member.id` is an on-demand hidden virtual property, cached in private `_id_org` / `_id_val` fields outside the serialized map, following the documented virtual-property skill pattern. The cached Id is reused only while the name has the same string identity (`===`); setter and raw-map name changes refresh it. Member construction/setters and collection insertion now accept ordinary legal names through UNSAFE, retaining the raw name and implicit path while rejecting INTERNAL, RESERVED and ILLEGAL names. Directly loaded raw descriptors are validated on id access. Three new `MemberIdTest` tests pass, covering wide names, cache reuse/invalidation, serialization exclusion and invalid setter/raw-map inputs; model JS test compilation and the nine focused psql naming/cursor/row tests pass.

Naming review adjustment: catalog/collection RESERVED branches return the original text directly, because classification already guarantees validity and the 32-byte limit. `Id.databaseName()` uses `textAlias(UNSAFE, 42, "d")`, preserving ordinary legal names including Unicode/spaces within the UTF-8 budget and otherwise generating `d$` aliases. Removed the obsolete character/name validation helpers. All four `PgNamingTest` tests and JS main/production compilation pass after this adjustment.

Collection/catalog wrappers, writer creation callers and JVM bootstrap now use the same physical names. Collection roots have a separately quoted schema-qualified reference used by query building, tuple loading and metadata reads; legacy mutation table targets are schema-qualified without changing their algorithms. Predicate and ordering/projection SQL resolve physical member identifiers. Fixed DELETE's use of a quoted identifier as a logical member name, and query projection list rendering/unmatched trailing parenthesis exposed by integration. This is naming alignment, not completion of the remaining query/mutation/DDL migrations or fn binding conversion.

Verification: four `PgNamingTest`, one JDBC-backed in-memory `PgNamingCursorTest`, four `PgRowsTest` and three `PsqlQueryParsingTest` tests pass. JVM and JS main/production compilation pass; diff checks pass. Coverage includes URL-safe/root aliases, reserved names, UTF-8/member budget boundaries, logical/physical separation, explicit aliases, quoting and cursor round-trip without a database. Normal Member construction now accepts the wider names used by these tests. End-to-end bootstrap/query verification remains dependent on existing member-list validation and db-04..09 migration work. No database objects changed. Completed ticket details are in [db-03-e](NAKSHA-33-DONE.md#db-03-e--establish-the-root-naming-rule).
Approved child naming: use compact history range numbers (`nv >> shift`) and
performance-slot numbers, not full numeric bounds. Partition directly on physical
nv/fn columns, preserving root primary/unique constraints. Keep logical low-byte
routing and the byte-swap/sign-bit normalization of stored fn; manual partition
creation does not make low-byte groups contiguous in unmodified bigint order.

### db-04 — Implement schema, collection, and index operations

Parent ticket; decomposed into `db-04-a` … `db-04-h`. **Done when:** clean
bootstrap and user collection creation/drop work through `PgDatabase`; required
constraints/indexes reach current and future leaves.

### db-04-b — Implement collection creation

Approved storage-owned layout contract, implementation `IN_PROGRESS`: `members` and `indices` must either both be null or both be explicitly supplied. Either mixed-null combination is ILLEGAL_ARGUMENT; an explicitly empty index array requests no optional indexes. Both-null is an unresolved storage-default request, not a model-level XYZ compatibility mode. Each storage selects/injects its defaults and validates the resulting layout before encoding collection metadata, so persisted and cached definitions agree. For lib-psql the initial defaults remain independent copies of XyzMembers.ALL/XyzIndices.ALL. Mandatory members and their final ordering must be resolved before encoding too. PgCollection translates resolved declarations only and must not choose a fallback. CREATE/UPSERT, existing-collection schema comparison and admin bootstrap use the same PSQL resolution path. No migration/adoption of historical persisted null layouts is introduced. Existing explicit metadata updates remain subject to immutable-layout validation after resolution.

Storage-owned layout increment `IN_REVIEW`, uncommitted: model `validateLayoutDeclarations` enforces paired declarations without selecting defaults; `validatedMembers` rejects unresolved defaults and injects only independent generic mandatory members. `PgCollection.resolveLayout` selects deep-copied XYZ defaults for both-null requests and copies explicit members before assigning collection-local positions, avoiding shared admin/member descriptor mutation. PSQL writer CREATE/UPDATE/UPSERT resolves before encoding, compares explicit existing-layout updates before normalization to retain order-sensitive immutability, then compares resolved layouts. DELETE/PURGE do not resolve unused payload layout fields. Physical descriptor generation has no XYZ fallback and rejects unresolved order. Bootstrap resolves every internal layout before encoding. Hub normalization now selects a complete explicit layout for both-null requests, rejects mixed-null input and copies mutable defaults; related explicit handler fixtures were aligned. Mandatory physical indexes remain intrinsic, independent of an empty optional index list.

Verification: 39 focused JVM tests pass (CollectionLayoutTest 8, BootstrapMemberListTest 3, PgCollectionLayoutTest 12, PgCollectionSchemaTest 4, PgCollectionCreateOptionsTest 3, CollectionIndexPolicyTest 9). Tests cover paired-null rejection, no model default injection, deep-copy isolation, explicit empty/custom layouts, mandatory completion/order, metadata tuple encode/decode consistency and immutable update comparison. Clean PgBootstrapTest passes; live PgPlv8MaintenanceTest passes against fresh PostgreSQL 18.6 databases, removed afterward. A targeted JS default-resolution test passes using a temporary test-only Gradle setup because normal JS tests are disabled and existing common tests contain JVM-only references. Larger explicit bootstrap metadata exposed existing JS recursive-copy and gzip adapter bugs: recursive copy now uses strict-mode-safe named recursion with correct array/object values; gzip uses the already-installed pako module with Uint8Array input and Int8Array output to preserve Kotlin byte semantics. Cold PLV8 bootstrap/maintenance verifies this compressed metadata path. Production JS/JVM compilation and diff checks pass. Full logical CREATE/UPSERT writer integration remains dependent on the ongoing mutation migration; the encode-before-cache path is covered at its shared preparation/tuple boundary.

Handler-suite limitation: the attempted DefaultStorageHandlerTest run was blocked at Java test compilation by pre-existing stale API usages: missing boolean arguments to Write.upsertCollection in DefaultStorageHandlerTest and RequestTypesUtilTest, and removed NakshaCollection.useMember in DefaultStorageHandlerTest. These unrelated compile failures were not modified. No compatibility migration or commit was made; previous uncommitted physical-drop work and the user's existing KDoc edit were preserved.

JVM-first increment `IN_REVIEW`: `PgDatabase` now executes descriptor-driven root creation, mandatory primary/unique/version/logical-ID indexes, supplied custom indexes, logical owner/member comments and native partition inheritance. Creation rejects physical member/index alias collisions and unowned/different-owner existing relations; same-owner repeat calls leave the existing root unchanged. Writer CREATE/UPSERT and JVM bootstrap pass logical identity explicitly. `PgPartitions` in `commonMain` owns registry installation, registration, root/child validation, HEAD/history/fn creation and retained-leaf repair; no installed maintenance functions are used. The JDBC backing connection now initializes to null before its secondary constructor assigns the supplied connection. `PgCollection` resolves the documented null storage class to Consistent. Touched legacy-indented Kotlin files were normalized to two spaces.

User-approved first storage scope: permanent collections only, HEAD fillfactor 50 and history fillfactor 100 regardless of performance partition count. Other storage classes fail explicitly before DDL; their implementation and removal of legacy storage helpers remain db-04-c. History-OFF provisions HEAD without registry access. History-ON registers immutable settings and provisions the range selected by max(server UTC date bits, installed allocator last_value), plus three future ranges; creation never calls nextval/setval/currval or commits. The allocator watermark test confirms an ahead sequence is used without advancing it. This initial coverage is not a write-time guarantee: small-shift/manual/import writes still require explicit finite-range preparation. Retain/backward/forward do not enable deletion or override the agreed current-plus-three coverage.

Verification on PostgreSQL 18.6: six real-JDBC `PgDatabasePartmanTest` cases pass using a dedicated disposable database and rollback-only schemas. They cover history ON/OFF, counts 1/7/256, shifts 12/41, root columns/comments/mandatory indexes, nullable-payload tombstones, bigint endpoint routing, HEAD uniqueness, rejected uncovered nv, explicit imported ranges, missing-leaf repair, native custom index add/drop propagation, immutable registration, ownership/column collisions, unsupported storage rejection, missing-root failure, partial-DDL savepoint rollback and ahead allocator coverage. The JDBC path uses real `PgInstance`/`PgConnection`; only the otherwise-unused storage dependency of `PgDatabaseImpl` is mocked. Combined focused suites pass all 20 tests: `PgDatabasePartmanTest`, `PgDatabaseSchemaTest`, `PgDatabaseIndexTest`, `PgCollectionCreateOptionsTest`, `PgNamingTest`. Command: `NAKSHA_TEST_PSQL_DB_URL=<dedicated local database URL from vault> NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgDatabasePartmanTest' --tests 'naksha.psql.PgDatabaseSchemaTest' --tests 'naksha.psql.PgDatabaseIndexTest' --tests 'naksha.psql.PgCollectionCreateOptionsTest' --tests 'naksha.psql.PgNamingTest' --console=plain`. JVM/test compilation, JS main/production compilation and diff checks pass. No PLV8 execution or cron was added. Full storage bootstrap/admin descriptors, catalog-internal collection orchestration, concurrent sessions and non-btree index propagation remain separate verification/integration work; do not mark db-04 as complete. No commit was made.

Current schema policy: members and indices are immutable after collection creation. Index builds remain synchronous during initial root/child creation; user index modification/concurrent builds are deferred to a dedicated future operation. UPDATE/UPSERT preparation compares existing and incoming declared member/index definitions before encoding or replacing caches and rejects changes. Validation lives in PgWriter, compares definitions in their original order with direct loops and early failure, and neither serializes metadata nor expands/defaults/sorts/copies declarations. Its list comparison is a private companion method. Missing declarations differ from explicitly empty declarations; storage-maintained member positions are ignored. Unrelated metadata updates and UPSERT creation remain allowed. Four focused schema-comparison tests pass, including nested field changes and member/index reordering rejection; JVM and JS main/production compilation pass. End-to-end/concurrent metadata enforcement remains dependent on the write transaction/cache migration. Removed the unused Write force property and fluent setter; no callers or test references remained.

Dependency inspection: production creation requires the db-04-g maintainer core, db-04-c storage policy and db-04-d index generation; the current prototype cannot be installed unchanged. It hard-codes three columns, accepts a different shift domain, always subpartitions fn, and cannot reconstruct payload schema/storage settings from its four-field registry. Prefer bringing the shared maintainer core forward rather than duplicating initial topology creation in Kotlin. No partial production DDL has been introduced.

Approved index policy: reject index definitions that cannot be created; do not silently omit INCLUDE columns or change methods/operator classes. Added `PgIndex.validate()` at descriptor generation, collection creation preflight and legacy CREATE execution. GIN with INCLUDE is rejected, as are mixed string/spatial keys in either order and other unsupported secondary method combinations. String payload INCLUDE in GiST remains distinct from a string key and is supported. Three `PgIndexTest` JVM tests pass for rejection, supported primitive combinations/payloads, unchanged requested includes and mutation revalidation; JS main/production compilation passes. Full collection creation remains in planning; these are preflight foundations, not completed DDL.

Approved preflight increment commit: `Reject unsupported index definitions before issuing DDL.` This does not close db-04-b or db-04-d; production creation, index execution and partition propagation remain outstanding.

Implement collection creation from every supplied `PgColumn`, root constraints,
mandatory indexes, restricted HEAD/default, custom maintainer registration,
initial maintenance, and optional fn range subpartitions. Support custom columns
on the root and inherited existing/future/repaired leaves. History-OFF gets HEAD
only and no history maintenance registration. Retention needs its own explicit
policy; the current prototype never deletes old history.
Creation must retain the fully qualified logical collection name and logical member names in table/column comments. Pass logical identity alongside physical names, check alias ownership transactionally, and reject physical member-name collisions before DDL. Final generated names must fit the byte limit including suffixes; do not rely on truncation or cache-only existence checks.

### db-04-c — Move storage settings out of table classes

Move reusable storage settings, TOAST, constraints, and tablespace behavior out
of the old table classes; make intentional changes explicit.

### db-04-d — Move index execution into PgDatabase

Approved implementation increment commit: `Centralize index writes and enforce immutable collection schemas.` Includes synchronous database-owned index CREATE/DROP, removal of PgIndex execution adapters, cached virtual Index.id and physical aliases, fixed partition/index name budgets, Write.force removal and ordered writer-side schema validation. PgWriter.kt and PgDatabase.kt now use two-space indentation throughout; embedded SQL text was preserved. Project indentation defaults are recorded in .editorconfig. Completion is recorded in [the approved increment archive](NAKSHA-33-DONE.md#db-04-bd--index-execution-and-schema-immutability-increment); parent tickets remain open for collection/maintainer integration and live partition-index propagation checks. Stop after this commit; do not start the next job without a new instruction.

Naming helper organization: removed the separate `PgNames.kt` file. Identifier budgets and naming helpers now live beside the existing Id naming extensions in `PgDatabase.kt`. `Id.indexName()` returns the fixed-budget physical index alias; `Id.indexName(table)` appends it to a physical table name and validates the complete identifier. CREATE/DROP and naming tests use these extensions. The PgNames reference in the budget history below describes the earlier file organization, not an additional class. Eight focused naming/database-index JVM tests and JS production compilation pass after relocation.

Current identifier budget supersedes the earlier `_h..._p...` / `$ci_` examples: finite leaves use `{root}${nv >> shift}${partitionIndex}` (dollar separators), and indexes use `{table}${indexAlias}`. The standard identifier maximum is 63 UTF-8 bytes. Reserve 16 bytes for the history suffix including `$`, four for `$255`, and 14 for `$` plus a 13-byte index alias. This leaves 29 bytes for collection roots: `29 + 16 + 4 + 1 + 13 = 63`. The worst-case signed Int is -2147483648, producing `i$_2147483648` (13 bytes), not -2000000000. Index aliases use a fixed SAFE/13-byte budget and prefix `i` across roots/leaves. Collection aliases use URL_SAFE/29 bytes and prefix `t`; RESERVED collection names exceeding 29 bytes must also alias, while reserved schema names may remain unchanged. PgDatabase.kt defines these budgets and finite-history/index naming helpers; CREATE/DROP use the same index helper and reject oversized final names rather than truncating. Tests cover the exact 63-byte boundary, one-byte overflow, minimum Int, all supported shifts 12..41 and performance index 255. Eight naming/database-index JVM tests pass and JS production compilation passes. The prototype maintainer/legacy hierarchy still has historical suffixes; production maintainer integration must use this current naming contract.

Index-name alignment: `Index.id` is a hidden virtual read-only property cached by source-name identity in `_id_org` / `_id_val`, excluded from serialized metadata. Name setters and collection insertion accept ordinary legal text through UNSAFE, rejecting INTERNAL, RESERVED and ILLEGAL custom names; raw-map descriptors are validated on id access. `PgIndex.id` retains the logical identity for physical naming. CREATE and DROP use the same SAFE alias policy with prefix `i` and a UTF-8 budget accounting for the table name and `$ci_` separator, then quote and validate the full identifier. Long/custom index names now alias rather than causing truncation; the final length guard still rejects roots that leave insufficient space even for a generated alias. Existing SQL quoting remains the injection protection. Two IndexIdTest, four PgIndexTest and three PgDatabaseIndexTest JVM tests pass; model JS test compilation and JS production compilation pass. Tests cover cache invalidation/serialization, illegal names and identical long/unsafe aliases for CREATE/DROP.

Approved prerequisite increment: `PgIndex.definition()` renders validated method/key/order/INCLUDE/predicate fragments. `PgDatabase.collection_create_index` and `collection_drop_index` execute schema-qualified root DDL, validate generated identifier byte limits, close cursors and preserve caller transaction ownership. Parent indexes are intended to propagate through native PARTITION OF inheritance; existing/future/repaired partition behavior still needs database integration verification with db-04-g. CREATE deliberately does not use IF NOT EXISTS to silently accept an unrelated existing index. DROP uses the same physical alias as CREATE and does not use CASCADE.

Removed relation-name-based fillfactor inference and `PgIndex.create/drop` execution methods. The legacy table hierarchy now takes an explicit PgDatabase for index creation/removal and delegates actual DDL to `collection_create_index/drop_index`, preserving its existing traversal/bookkeeping until the hierarchy is removed. `PgIndex` has no connection/table execution dependency; validation and definition fragments remain. Four PgIndexTest and two PgDatabaseIndexTest JVM tests pass, covering rendered SQL, unsupported definitions, qualified DDL, byte-limit rejection and caller/cursor ownership. JVM test-source and JS main/production compilation pass with the updated caller signatures. No live database index propagation tests were run. Collection creation remains blocked on the shared maintainer/storage integration, not duplicated here.

Keep `PgIndex` as a descriptor. Move create/drop execution into `PgDatabase`,
honoring key order, method/opclass, predicates, includes, and future partitions.
Completed execution relocation: removed `PgIndex.create/drop` and redirected
legacy callers to the database boundary. Keep `PgIndex.validate()` /
`definition()` and `PgColumn` SQL fragments; a properties-only descriptor rewrite
is not required. Root/child index creation stays synchronous and transactional;
these internal operations do not enable user index-definition updates.
Never infer HEAD status from a relation name to choose storage parameters.
Integrate with the custom maintainer: existing, future and repaired leaves must
inherit the same mandatory/custom index contract; test add/drop after registration.

### db-04-e — Implement collection and schema drop

Physical drop increment `IN_REVIEW`, uncommitted after verification commit `ad672f1f6`: `PgDatabase.collection_drop_if_exists` and `catalog_drop_schema` now remove exact root/schema registry rows and issue quoted cascading DROP in the same caller-owned transaction. Missing targets are successful no-ops; orphaned registration rows are removed, and an absent registry is not installed. Names and transaction ownership are validated before DDL. Logical feature metadata is deliberately not changed here; connecting physical deletion to validated logical writes remains db-08-c. Dropping the admin schema is a physical administrative operation that leaves the storage unusable.

Lock-order policy: cooperative schema creation, collection creation, registration/per-root provisioning, registry-wide sweeps and physical drops now all take the database-wide sweep transaction advisory lock before schema/root locks. This deliberately serializes these operations until caller commit/rollback. The coarser lock avoids a cross-call inversion where a transaction creates a root, retains its schema lock and later requests a sweep/drop while another sweep holds the global lock waiting for that schema. It does not claim deadlock freedom for arbitrary external DDL or callers holding unrelated row/index locks. Finer-grained concurrency is deferred.

Verification: real PostgreSQL 18.6 PgDatabaseDropTest passes fresh-bootstrap history ON/OFF lifecycle, cascading child/index cleanup, exact registry cleanup, root/schema savepoint rollback restoring OIDs and data, committed visibility, other-schema preservation, installed-sweep no-resurrection, malformed-name/autocommit rejection, missing-registry no-install behavior and rollback restoration. Two-backend regression observes the competing drop waiting on the exact global sweep lock while the holder creates, maintains and drops within its transaction, then verifies progress after commit. The isolated database was removed. Command uses `NAKSHA_TEST_DROP_DB_URL` and `NAKSHA_TEST_PSQL_DB_URL` pointing to the same fresh isolated database and `NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgDatabaseDropTest' --console=plain --quiet`. Eleven focused schema/index/naming tests and production JS compilation also pass; schema mock assertions now include global-before-schema locking. No commit was made. Non-owner drop permissions and schema-drop concurrency are not separately covered; no cron changes.

Implement collection/schema drop with verified custom-maintainer registry cleanup.

### db-04-f — Route bootstrap and create-upsert preparation

Catalog-internal collection increment approved `DONE`; commit message `Prepare internal collections for catalog creation and cold lookup.` The user approved the implementation and requested continuing functional catalog/feature recovery before redesigning metadata caching. Completed preparation scope is archived in [NAKSHA-33-DONE.md](NAKSHA-33-DONE.md#catalog-internal-collection-preparation-increment). The new db-11 TODO records the cache review; cache isolation must not be inferred from preparation-level tests or block this functional recovery sequence.

Catalog-internal collection increment `IN_REVIEW`, uncommitted after approved combined commit `e54fac8e2`: ordinary catalog CREATE/UPSERT now builds a resolved NakshaCatalogCollection descriptor, provisions its physical table through the existing PgDatabase creation API, and publishes catalog/collection caches only after preparation DDL succeeds. CREATE retains existing-schema rejection; UPSERT ensures the table on reuse. Reserved internal descriptors are synthesized from a known catalog during cold-cache lookup without needing a self-registration row. Admin reserved-descriptor behavior remains separate. Logical orchestration stays outside PgDatabase; no new feature mutation algorithm or cache transaction boundary is introduced.

Identity correctness: the internal descriptor uses the owning catalog number in both its legacy metadata tuple-number and encoded Tuple, while reusing only the admin collection's member schema for encoding. Child collection metadata therefore encodes into the ordinary catalog, not the admin catalog. Fixed the existing missing return in NakshaCatalog.databaseNumber when falling back to databaseId. Layout resolution occurs before metadata encoding, including generic mandatory members and the explicit Tags index.

Verification: PgCatalogCollectionsTest and isolated real-JDBC PgCatalogCreationTest pass (2 tests, no failures/skips), including descriptor/child tuple addressing, resolved generic layout, physical root/HEAD/history/indexes, idempotency, logical-owner conflict, rollback cleanup and committed cold-session lookup with zero self-rows. JVM test compilation and JS main/production compilation pass; the temporary database was removed. Command uses NAKSHA_TEST_CATALOG_DB_URL and NAKSHA_TEST_PSQL_DB_URL pointing to the same fresh isolated database and `NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgCatalogCollectionsTest' --tests 'naksha.psql.PgCatalogCreationTest' --console=plain --quiet`. Existing writer preparation still precedes its request savepoint; fixing that remains db-05. End-to-end legacy logical catalog CREATE/UPSERT mutation was not claimed by these preparation-level tests. No commit was made; cron remains deferred.

JVM bootstrap follow-up implemented, uncommitted: a fresh-database integration test now creates storage through `Naksha.useStorage`, verifies committed storage identity/admin version/sequence and all four physical admin collection roots, then invokes registry-wide maintenance using a real mutable session without creating a logical transaction. Reproduced prerequisite fixes make configuration visible during initialization (restoring previous base fields on failure and preserving `afterInit`'s initialized contract), recognize typed master configuration in `PgConfig`, and correct `MemberList.verify`'s zero-index sentinel/missing-member insertion. Corrected escaped schema quoting in the final bootstrap search-path statement. `common.sql` no longer installs superseded pg_jobmon/pg_partman dependencies or pg_cron; cron extension/job installation belongs to db-04-h and must respect the cluster's designated cron database. No existing server configuration or application database was modified.

Verification: both `PgBootstrapTest`/`PgBootstrapConfigTest` passed against a fresh PostgreSQL 18.6 database with both `NAKSHA_TEST_BOOTSTRAP_DB_URL` and `NAKSHA_TEST_PSQL_DB_URL` pointing to that database. Command: `./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgBootstrap*Test' --console=plain --quiet`. The dedicated database was removed afterward. Five new model initialization/member-list regression tests pass. A broader `MemberTest` run still has the same three previously recorded collection-builder failures (`addMemberStoresInOrder`, `addMemberRejectsDuplicates`, `addMemberCapsAt64`); these were not changed or hidden. Catalog-internal collection orchestration and full logical write flows remain outstanding.

Route JVM admin bootstrap and writer CREATE/UPSERT preparation through this same
API, including creation of each catalog's internal collections table.

### db-04-g — Productionize the custom partition maintainer

PLV8 attachment approval: the user approved this increment and requested committing all staged files, including instruction updates. Increment state is `DONE`; commit message: `Attach PLV8 storage from schema comments and share Kotlin maintenance.` This supersedes the earlier IN_REVIEW/uncommitted notes below. Final method names are `PgLocalConfig.readFromSchemaComment` and `writeToSchemaComment`. Completed scope is archived in [NAKSHA-33-DONE.md](NAKSHA-33-DONE.md#plv8-storage-attachment-and-shared-maintenance-increment); parent tickets remain open. Next increment installs a thin maintenance SQL entry point and verifies it instead of the temporary test wrapper; cron remains deferred.

PLV8 attachment increment `IN_REVIEW`, uncommitted after approved JVM commit `4f675f892`: JVM installation writes a version-1 JSON allowlist into the admin-schema comment through `PgLocalConfig`. It includes storage identity/exact decimal-string number/admin version, hard cap and tablespace settings; never full PgConfig/master/replica credentials. Reading validates format, types and installed identity/version, derives database/role/server context from the active SQL backend, and disables creation/upgrade/override. Admin/minimum runtime versions now require beta.52 so older module installations cannot be silently reused; no old-schema compatibility or automatic comment adoption is introduced.

Exported Kotlin/JS `naksha_maintain` reads the comment, constructs a fresh `PgStorage`, opens a mutable session and invokes the same `PgDatabase.maintain_partitions` implementation used on JVM. The protected base initialization helper makes direct configured storage constructors honor the normal lifecycle on both platforms. Attach-existing initialization requires create/upgrade/override disabled, exact installed runtime version, and performs no install, SET SESSION or commit. PLV8 sessions skip JVM-style connection settings while explicit connection initialization callbacks remain supported. No storage is retained in the global storage registry by the entrypoint. The existing per-backend module/instance caches remain; live upgrades of already-loaded backends are not implemented.

Runtime prerequisites fixed from actual failures: initialize the JS tuple-cache singleton (other tuple-cache operations remain unimplemented and are not needed by physical maintenance); bind native PLV8 execute/prepare overloads to their actual API names; make module import aliases local destructuring bindings instead of mutating cached dependency exports; reorder MemberList through a sorted snapshot and base setters rather than transient duplicate-rejecting setters. These are execution-path fixes, not a rewrite of the loader or a complete general PLV8 request/cache implementation.

Verification on fresh PostgreSQL 18.6 databases: 13 tests passed with none skipped (`PgPlv8MaintenanceTest` 1, `PgLocalConfigTest` 6, `StorageInitializationTest` 3, `BootstrapMemberListTest` 3), plus production JS compilation and diff checks. The live test bootstraps modules on JVM, uses only temporary SQL wrappers to invoke the compiled Kotlin export, verifies exact signed bigint boundary transport, new finite ranges and registry count, JVM/PLV8/repeated-call idempotency, unchanged allocator/settings/data, caller rollback, failure after partial multi-root maintenance with rollback/retry, and missing/malformed/mismatched comment errors. Explicit crossed-alias fixture modules verify cached exports are unchanged. The shuffled-member regression exercises sorting and lookup/index reconstruction; the PLV8 bootstrap also exercises this sorting in the JS runtime. Local config tests include IDs beyond JavaScript numeric precision and credential exclusion. Command: `NAKSHA_TEST_PLV8_DB_URL=<fresh local database URL> NAKSHA_TEST_PSQL_DB_URL=<same URL> NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew :here-naksha-lib-model:jvmTest --tests 'naksha.model.StorageInitializationTest' --tests 'naksha.model.BootstrapMemberListTest' :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgPlv8MaintenanceTest' --tests 'naksha.psql.PgLocalConfigTest' :here-naksha-lib-psql:compileProductionExecutableKotlinJs --console=plain --quiet`. Disposable databases were removed; existing application/prototype databases were untouched. JS test-source compilation separately remains blocked by pre-existing JVM references in TupleCacheReadPathTest; actual PLV8 runtime checks passed.

Remaining limits: no production SQL wrapper/cron extension/job, no invocation cache/module-refresh policy, no concurrent-session stress or all-method index parity, no full feature-write/cache parity, and no PLV8-exposed collection creation facade yet. Collection creation itself remains shared Kotlin and is JVM-verified. Keep these changes uncommitted pending review; the user's commit approval applied to the preceding JVM increment only.

The current JVM-first Kotlin core and registry-wide loop are implemented and verified with db-04-b/h. Production bootstrap invokes `PgPartitions.install`, which creates the registry only. The previous PL/pgSQL resource has moved out of packaged resources to `ai/NAKSHA-33-plpgsql-core-reference.sql`; its rollback-only reference test now loads that historical file. PLV8 attachment/configuration comment, runtime parity and scheduling remain outstanding. Registration immutability is enforced by shared Kotlin entrypoints; direct administrative SQL writes are outside that contract and are not protected by a trigger.

Historical core increment, superseded for implementation ownership by the agreed JVM-first plan above: install `naksha-partman.sql` through JVM admin bootstrap and expose bound register/maintain calls in `PgDatabase`. The core requires an existing permanent root with mandatory keys, registers explicit TOAST/head/history fillfactor/resolved tablespace settings and its OID, and provisions HEAD plus the explicit finite version's range and three future ranges (clipped at HEAD). Explicit finite versions support manual/imported coverage without a date-only assumption; no allocator or transaction ownership moves into maintenance. Use current compact dollar naming, bigint range numbers, direct nv leaves for one performance partition, and native `PARTITION OF` inheritance for actual payload columns/checks/indexes. Missing/replaced roots fail rather than being recreated. Registration and maintenance serialize using a per-root transaction advisory lock and lock the root/descendants against conflicting DDL. Root identity is checked again after lock acquisition. The JVM bootstrap file now uses two-space indentation without other logic changes.

This is a reviewable prerequisite, not completion of db-04-g/b/c/d: automatic initial version selection, write-time coverage preparation for small shifts/imports, non-permanent storage policy, collection creation/ownership/comments, drop/unregister integration and concurrency stress remain outstanding. Storage defaults/asymmetries are not decided by this core; callers must supply explicit values. Cron, retention and old prototype compatibility are deliberately excluded. Production installation requires a clean registry; inspection examples must not be overwritten.

Verification: `psql -X -f ai/NAKSHA-33-production-partman-test.sql` passed on PostgreSQL 18.6 in a separate disposable database using local vault credentials; all installation/test objects rolled back. Coverage includes shifts 12/41, counts 1/3/7/256, every routing-byte boundary and bigint extremes, last finite/HEAD boundaries, explicit imported versions, retained-leaf repair, payload/default/check inheritance, mandatory/custom indexes on existing/future/repaired leaves, root index add/drop, immutable configuration, altered topology/storage rejection, missing/replaced roots, failure atomicity and repeated resource installation. PostgreSQL rejects explicit `pg_default` on partitioned intermediates, so tablespace/heap reloptions are applied to storage-bearing leaves only. `NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgDatabasePartmanTest' --tests 'naksha.psql.PgDatabaseIndexTest' --tests 'naksha.psql.PgNamingTest' --console=plain` passed, as did `:here-naksha-lib-psql:compileProductionExecutableKotlinJs` and `git diff --check`. The new JVM test verifies bound arguments, cursor closure and caller-owned connection/transaction. Full bootstrap, concurrent-session stress, all shift/count combinations and non-btree index-method propagation are not claimed. No existing application/prototype objects were changed; no commit was made.

Implement the maintainer in shared Kotlin, verified first through JVM and later through PLV8 using the agreed bootstrap plan. Keep schema, table, shift and partitions
in `"naksha~admin".naksha_partman`. Create/check the exact HEAD/history/fn layout,
with current plus three future history ranges. Integrate custom columns and index
management via db-04-b/d rather than hard-coding the prototype's three columns.
Harden version-source/manual-version/import coverage, identifier naming, locks,
configuration validation, failure atomicity and dropped-table handling. Verify
all supported partition counts and production shift policies; prototype limits
are not automatically the production contract. No pg_partman dependency.

### db-04-h — Schedule registry-wide maintenance

Permissions/concurrency approval: increment `DONE`; user-approved commit message `Verify maintenance permissions and concurrent JVM/PLV8 sweeps.` This supersedes the earlier IN_REVIEW/uncommitted wording below. Parent scheduling ticket remains open and cron remains deferred. Next work is db-04-e transactional collection/schema deletion with registry cleanup and sweep-compatible locking.

Permissions/concurrency verification increment `IN_REVIEW`, uncommitted after installed-wrapper commit `d3717c98f`: added two isolated real-database tests without production changes. PgMaintenancePermissionsTest uses unique NOSUPERUSER/NOINHERIT/NOLOGIN roles and cold backends to prove SQLSTATE 42501 for missing wrapper EXECUTE, module-table SELECT, sequence SELECT, registry SELECT, root lock permission and root ownership. Granting UPDATE can permit locking but cannot authorize partition creation. A positive non-superuser sweep requires schema USAGE/CREATE, metadata/sequence reads and ownership of every managed root/partition tree (including admin roots); it verifies role identity, settings, allocator, rollback and idempotency without installer/superuser membership. This is a tested privilege model, not automatic role provisioning.

PgMaintenanceConcurrencyTest uses independent backend PIDs and bounded pg_locks/pg_stat_activity polling to observe the PLV8 sweep waiting on the same global advisory transaction lock held by JVM. Both first-caller commit and rollback scenarios pass: the waiting sweep completes, committed ranges are reused or rolled-back ranges reprovisioned exactly once, and native keys/data/allocator state remain unchanged. No timing-only sleep assertion or shared JDBC connection across worker threads is used. Reverse PLV8-first ordering, direct per-root creation races and broader stress remain unverified.

Verification: both focused integration tests passed individually on fresh PostgreSQL 18.6 / PLV8 3.2.5 databases with zero failures; the concurrency test includes both completion scenarios. Commands use `NAKSHA_TEST_PERMISSIONS_DB_URL` or `NAKSHA_TEST_CONCURRENCY_DB_URL` and `NAKSHA_TEST_PSQL_DB_URL` pointing to the same fresh isolated database for that suite, then `NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgMaintenancePermissionsTest' --console=plain --quiet` or the matching concurrency class. Role creation/cleanup is explicit and unique; temporary roles and databases were removed and removal verified. Tests skip when their dedicated environment variables are absent; no existing schemas are cleared. Diff checks pass. No cron extension/job or cross-database scheduling was introduced.

Installed maintenance entrypoint approved: increment `DONE`, commit message `Install restricted PLV8 maintenance entry point.` This supersedes the earlier IN_REVIEW/uncommitted wording below; completed scope is archived in [NAKSHA-33-DONE.md](NAKSHA-33-DONE.md#installed-plv8-maintenance-entrypoint-increment). PUBLIC means all database roles, not the public schema; revoking function execution leaves owner/superuser access and explicit grants intact. SECURITY INVOKER does not elevate a granted caller: underlying schema, registry, sequence and DDL privileges remain required. Continue with real non-owner and concurrent-session verification; cron remains deferred.

Installed-entrypoint increment `IN_REVIEW`, uncommitted after approved PLV8 attachment commit `23b8d83cb`: `naksha.sql` now installs `"naksha~admin".naksha_maintain()` as a thin PLV8 wrapper over the compiled Kotlin export. It initializes the existing module loader on a cold backend, returns the maintained-root count and retains caller transaction ownership. It is VOLATILE, PARALLEL UNSAFE, SECURITY INVOKER, has a fixed admin/pg_catalog search path, and revokes PUBLIC execution; the installation owner may explicitly grant execution to the future maintenance role together with the underlying schema/table/DDL privileges. No scheduling or cross-database access is added. A cold-wrapper failure exposed unqualified pgcrypto digest calls in the JS ID hashing adapter; both calls now target public.digest, matching the current extension-installation policy rather than weakening the wrapper search path. Existing installations with pgcrypto in a different schema are not supported by this qualification and require a separately agreed extension-location policy.

Verification: the live PgPlv8MaintenanceTest now invokes the installed function, not a temporary maintenance wrapper. Fresh PostgreSQL 18.6 bootstrap, cold-backend invocation before fixture module loading, metadata/search-path/ACL checks, root counts, idempotency, exact bigint transport, successful and partial-failure rollback/retry, unchanged settings/data/allocator, crossed-alias fixtures and invalid-comment checks all pass (1 integration test, 0 failures, 0 skipped); JS rebuild and JVM test compilation pass. Both database environment variables targeted the same fresh isolated database and it was removed afterward. Command: `NAKSHA_TEST_PLV8_DB_URL=<fresh local database URL> NAKSHA_TEST_PSQL_DB_URL=<same URL> NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgPlv8MaintenanceTest' --console=plain --quiet`. Cron extension/job setup remains explicitly deferred. Permissions have catalog-level coverage; non-owner-role execution and concurrent-session tests remain outstanding.

Shared Kotlin registry scanning implemented and JDBC-verified ahead of scheduling: `PgPartitions.maintainAll` snapshots registered roots in schema/table C-collation order, closes the snapshot cursor before DDL, serializes sweeps with a transaction advisory lock, and invokes the same per-root implementation. `PgDatabase.maintain_partitions` exposes automatic server-date/allocator watermark selection; creation now reuses that version-source function too. The initial sweep policy is fail-fast in one caller-owned transaction: no root-level catch/recovery, all root locks held until transaction end, any failure requires whole-sweep rollback. Callers may test explicit anchors through the internal core; neither form allocates versions or commits. Registrations after the snapshot wait for the next sweep. This does not yet settle production scheduler lock/time limits or failure reporting.

Verification: eight real-JDBC `PgDatabasePartmanTest` cases pass, including new empty/multi-root sweep, C-order fail-fast behavior, repeat calls, ahead allocator without advancement and rollback of earlier roots after a later conflict. The combined run of the five new model regressions and 20 focused psql tests (`PgDatabasePartmanTest`, `PgBootstrapConfigTest`, `PgDatabaseSchemaTest`, `PgDatabaseIndexTest`, `PgNamingTest`) passed; JS production compilation passed. The clean bootstrap test separately proves the storage/session-backed sweep path. No PLV8 facade, schema configuration comment, cron wrapper, cron extension or job has been installed by this increment. Concurrent-session stress remains unverified.

Implement registry scanning and per-root invocation in shared Kotlin, callable directly on JVM and through one thin PLV8 SQL wrapper. Install one idempotent hourly pg_cron job per database with explicit database/role,
permissions, timeout/concurrency policy and observable failures. Define whether
one failing root blocks others, and test that behavior. Do not silently drop old
history: implement retention only once its configuration and policy are settled.

### db-05 — Make write failures restore database and session state

Parent ticket; decomposed into `db-05-a` … `db-05-f`. **Done when:** a mixed
batch failure restores both SQL and session-local state; successful DDL becomes
visible to other sessions only after commit.

### db-05-a — Cover DDL and writes in the rollback boundary

Put DDL preparation and all operation groups within the request's rollback
boundary. Handle failure before savepoint creation and the first write request.
Verify the existing `PgWriter` savepoint mechanism covers preparation and all
mutations, and rolls back on exception; `PgDatabase` assumes this caller guarantee.

### db-05-b — Complete error-mapping context

Complete schema/table context in error mapping on JVM and PLV8, preserving
missing-schema/table information needed by session cache invalidation.

### db-05-c — Publish local changes after commit

Metadata cache lifecycle is explicitly deferred to db-11 while functional catalog/feature recovery continues. Its approved policy supersedes publishing descriptors upward: invalidate affected parent keys and discard local overrides on both commit and rollback; never copy session descriptors into the master. Request/full rollback bookkeeping still needs coverage under db-05-a/d/e. Do not mark this ticket DONE or claim cross-session metadata consistency before db-11 is complete.

### db-05-d — Fail the whole batch on validation error

Ensure post-mutation expected-version validation can fail an entire batch without
leaving successful rows or DDL behind, while preserving earlier successful
requests in the same session where appropriate.

### db-05-e — Enforce one mutation per feature per version

Enforce one mutation per fully qualified feature identity per transaction/version
in the session and `PgWriter`, using transaction-object modification tracking.
Reject duplicates within/across requests before database invocation; restore
tracking on request rollback. Keep this caller-side invariant out of `PgDatabase`.

### db-05-f — Verify deterministic input sorting

Verify caller-side deterministic input sorting for every mutation path, including
ID/GUID DELETE and PURGE; document trusted ordering preconditions and preserve
input/result correlation without database-side sorting.

### db-06 — Move CREATE into PgDatabase

Parent ticket; decomposed into `db-06-a` … `db-06-c`. **Done when:**
insert/re-create cases work in both history modes and CREATE has no dependency
on old table descriptors or direct history INSERTs.

### db-06-a — Implement tuple-based insert

Implement tuple-based `rows_insert`; make `PgWriterInsert` supply new states
through the agreed wrapper and use returned tuples. Move row conversion, SQL,
and result reconstruction inside `PgDatabase`.
Keep transport-fragment generation in `PgRows`; the database uses it to prepare,
bind and execute mutations. Remove direct mutation execution from the writer.

### db-06-b — Insert through the root at HEAD

Insert through the root with nv=HEAD. Re-create over a tombstone archives by
changing nv when history is ON; history-OFF replaces current state in place.

### db-06-c — Preserve conflicts and drop the undefined sentinel

Preserve duplicate-live conflicts, lifecycle reset, batch correlation, and
original-state return guarantees. Fail with `NakshaException` carrying
structured `NakshaError`. Remove `undefined` sentinel handling: inputs are fully
encoded replacement states.

### db-07 — Move UPDATE and UPSERT into PgDatabase

Parent ticket; decomposed into `db-07-a` … `db-07-d`. **Done when:**
update/upsert behavior and concurrent conflicts are verified in both history
modes; writers handle orchestration/responses and `PgDatabase` returns final
immutable tuples.

### db-07-a — Implement tuple-based update and upsert

Implement tuple-based `rows_update` / `rows_upsert` and switch their writers to
`NewTuple` inputs and returned final tuples. Keep internal `PgRows` and
reconstruction in `PgDatabase`; never update cached tuple metadata.
Writers retain request orchestration only. Adapt the old HEAD/history algorithms
to root mutations rather than mechanically relocating their SQL; keep transport
fragments in `PgRows` and execute all mutations through `PgDatabase`.

### db-07-b — Archive by nv update through the root

Archive by UPDATE of nv through the root, then write the successor; with history
OFF update HEAD in place. Prove SQL statement/CTE dependencies and
concurrent-writer locking rather than assuming textual CTE order.

### db-07-c — Preserve atomic semantics and remove merging

Preserve missing/deleted state checks, atomic expected-version semantics,
CREATE/UPDATE action selection and change counts according to db-01 decisions.
Remove `undefined` sentinel/old-payload merging and its reconstruction paths;
callers supply complete encoded states. Read atomic expectations from immutable
`NewTuple.previousTupleNumber`, not from a mutable `Tuple` field; throw
structured conflict exceptions. UPSERT purges DELETE HEAD, attempts INSERT, then
applies conditional/unconditional UPDATE to the live branch.

### db-07-d — Close plans and cursors, keep logging

Close all plans/cursors and keep query logging/EXPLAIN available.

### db-08 — Move DELETE/PURGE and metadata deletion into PgDatabase

Parent ticket; decomposed into `db-08-a` … `db-08-d`. **Done when:**
delete/purge, re-create, and catalog/collection deletion preserve agreed API
outcomes, history, and rollback behavior.

### db-08-a — Implement ID and GUID delete, and purge

Implement the four ID/GUID DELETE methods with and without tuple results, and the
explicit `NewTuple` tombstone variant according to db-01-c. Support key-only
tombstones for `emptyTombstone = true`, defaulting to full old-state tombstones;
distinguish permissive GUID DELETE from strict explicit tombstone creation.
Implement PURGE accepting db/catalog/collection `Id` values and ordered feature
IDs. PURGE removes DELETE HEAD only and returns one `Guid?` per input position,
leaving live HEAD unchanged without error and returning null for missing HEAD;
no atomic PURGE variant. Document the three positional outcomes explicitly. Throw
`NakshaException` for operation failures; keep row transport and reconstruction
internal.

### db-08-b — Keep tombstones non-null in nv

Keep tombstones non-null in nv and preserve temporal lifetime semantics.

### db-08-c — Connect physical drop to metadata writes

Connect catalog/collection physical drop to successful logical metadata writes in
the same transaction; do not drop physical data before state validation.

### db-08-d — Replace the generic delete stubs

Replace obsolete generic delete stubs and update callers/tests to the agreed
ID/GUID and explicit-state APIs; verify empty-tombstone decoding, column
nullability, hashed identities, expected/actual errors, and old-state archival.
New-state and empty-state deletion are included in this migration.

### db-09 — Consolidate supporting reads and remove the table hierarchy

Parent ticket; decomposed into `db-09-a` … `db-09-e`. **Done when:** retained
descriptors have no dependency on the old hierarchy; writers never select/create
physical partitions and normal reads need no cached list of partman-created
children.

### db-09-a — Add tuple loading and query functions

Finish tuple loading, physical metadata inspection and prepared-read support where
needed by the migration. Read SQL generation/execution may remain in `PgCache`,
`PgSession`, `PgReader` and query builders; routing all reads through `PgDatabase`
is not an acceptance requirement. Retain `PgRows` and focused predicate builders,
and verify that every read uses consistent physical naming and ordinal fn values.

### db-09-b — Finish root query SQL and filtering

Finish root query SQL, current/temporal/latest-N/deleted filtering, history-OFF
behavior, and physical fn conversion. Preserve deterministic ordering semantics
explicitly when encoded fn order differs from logical fn order.

### db-09-c — Use partition catalogs for topology

Use PostgreSQL inheritance/partition catalogs for default-child and topology
discovery. Replace or remove `PgRelation` name parsing and its SQL as needed.

### db-09-d — Remove the old table hierarchy

Remove `PgTable`, `PgHeadTable`, `PgHistoryTable`, `PgHistoryPartition`, and
`PgDistributionPartition` after checking all callers, tests, and documentation.
Review `PgCollectionIndex` and `PgWriterPlan` for remaining value independently.
Move their remaining DDL/DML execution to `PgDatabase` before deletion; preserve
useful descriptor and transport fragments without requiring a broad read rewrite.

### db-09-e — Remove manual partition creation

Remove manual partition creation/maps, recursive index fan-out, obsolete `$hst`
expectations, modulo routing, and stale writer properties/logging.

### db-10 — Complete integrated acceptance checks

Parent ticket; decomposed into `db-10-a` … `db-10-e`. **Done when:** the
applicable `partman.md` acceptance checks pass and no active writer SQL depends
on separate HEAD/history tables.

### db-10-a — Compile common, JVM, and JS sources

Compile common/JVM/JS sources, recording any pre-existing failures separately.

### db-10-b — Run the focused existing suites

Run focused existing suites: bootstrap, collection/partitioning, PgRows,
insert/update/upsert/delete, re-create, duplicate successor, history, ordered
reads, tuple persistence/materialization, transactions, and error mapping.

### db-10-c — Add missing integration coverage

Add missing integration coverage for batch rollback, concurrent writes,
history-OFF, non-divisor partition counts, maintenance/premake, retention,
cleanup, and historical imports requiring explicit older-range creation.

### db-10-d — Verify indexes and query plans

Verify root/child mandatory/custom indexes and relevant index-only query plans
after VACUUM; verify HEAD and bounded-history partition pruning.

### db-10-e — Follow the test-running guidance

Follow repository test-running guidance when executing checks; record commands
and results with each ticket rather than deferring all verification to the end.

### db-11 — Restore transactional metadata cache isolation

Explicitly deferred by the user until catalog creation/management and feature reading/writing are restored. Coordinate with db-05-a/b/c/d; this is one flat follow-up ticket, not permission to hide cache failures or mark integrated transaction acceptance complete.

Approved contract: the master cache approximates committed database metadata. A session cache stores only created/modified/removed or transaction-observed overrides, including explicit tombstone/absence states. Local overrides must take precedence; otherwise consult the parent baseline. A parent miss read through an active session transaction must remain local, never populate the master from uncommitted SQL. A nontransactional missing/existing mismatch updates/invalidates the master immediately; a transactional mismatch affects only the session and records affected keys. Both commit and rollback (including close/failed commit) invalidate affected parent catalogs/collections and clear local state rather than copying descriptors upward. Catalog invalidation also invalidates its collections. Request-savepoint rollback restores previous local overrides while preserving earlier successful requests; final transaction cleanup still invalidates all affected parent keys.

Recorded implementation gaps: PgCache.flush copies descriptors upward and is unused; PgSession transaction cleanup does not clear local metadata/invalidate parent; invalidation recurses globally regardless of active transaction; parent misses use the caller's connection and can publish transaction-local rows; UPDATE/DELETE/PURGE lack final overrides and UPSERT may retain provisional CREATE descriptors; no absence sentinel prevents stale-parent fallback; reserved internal descriptors are synthesized into otherwise unmodified child caches; preparation precedes request savepoints and failed requests do not restore metadata state; missing/existing error identity/context is incomplete; public getters return mutable cached descriptors. Address the last item through immutable state or safe returned copies, not rollback of shared mutable objects.

Acceptance/tests: warm-parent/local override precedence for all metadata operations; parent-miss isolation with and without a transaction; current-session absence versus another session's committed entity; commit, rollback, close, failed commit and session reuse; stale missing/existing mismatch scope; request preparation/execution failure and restoration of earlier overrides; final-result UPSERT descriptors; no mutation/publication of shared cached objects; reserved descriptor parent reuse. Use independent sessions and committed database checks, not only direct PgDatabase/preparation tests. During preceding functional work, keep these limitations explicit, use fresh-session/database assertions where needed, and record cache-only failures against this ticket rather than silently weakening tests.

## Decisions needed before the affected implementation

1. **GIN/GiST index contract:** `partman.md` asks every custom index to expose fn,
   version, nv while preserving its method. GIN does not support INCLUDE, and
   spatial indexes need separate consideration. Agree an explicit per-method
   rule; do not silently remove the existing tag/spatial index support.
2. **Physical names:** `PgCollection.name` and creation use literal id text, while
   `PgQueryBuilder` uses `Id.catalogName()/collectionName()`. Should all operations
   use the mapped names, or should the literal-id rule in `partman.md` prevail?
3. **Tuple persistence contract — approved:** DELETE/PURGE behavior,
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
  command successfully; moved to `NAKSHA-33-DONE.md`. The database migration
  tickets remain TODO.
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
