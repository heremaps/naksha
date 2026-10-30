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
- Current authoritative tuple contract: approved
  [db-01-c](NAKSHA-33-DONE.md#db-01-c--revise-the-tuple-persistence-contract),
  [db-01-d](NAKSHA-33-DONE.md#db-01-d--document-identity-validation-and-preconditions),
  and [db-01-e](NAKSHA-33-DONE.md#db-01-e--write-the-behavior-matrix)
  define immutable `NewTuple` inputs/results, identity/version checks, mutation
  outcomes, and caller-owned rollback/uniqueness/locking-order preconditions.
  They supersede the historical row-buffer API and conflicting `partman.md` rules.
- Current partition ownership: Naksha's SQL maintainer manages history **and
  performance** partitions using `"naksha~admin".naksha_partman`. The user approved
  this change after the pg_partman 5.5.0 blocker and successful custom prototype.
  Maintain the current finite range plus three future ranges and HEAD. Earlier
  pg_partman/template references below are historical unless explicitly retained;
  production implementation, custom columns/indexes and hourly cron are tracked
  in db-04-b/d/g/h. Retention remains separate and is not implemented by the prototype.

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
| [db-04](#db-04--implement-schema-collection-and-index-operations) | Implement schema, collection, and index operations | [db-02](NAKSHA-33-DONE.md#db-02--verify-pg_partman-topology-against-the-deployed-version), [db-03](#db-03--finish-the-physical-row-and-naming-contract) | `TODO` | — |
| [db-04-a](#db-04-a--implement-schema-operations) | Implement schema operations | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-b](#db-04-b--implement-collection-creation) | Implement collection creation | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-c](#db-04-c--move-storage-settings-out-of-table-classes) | Move storage settings out of table classes | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-d](#db-04-d--move-index-execution-into-pgdatabase) | Move index execution into PgDatabase | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-e](#db-04-e--implement-collection-and-schema-drop) | Implement collection and schema drop | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-f](#db-04-f--route-bootstrap-and-create-upsert-preparation) | Route bootstrap and create-upsert preparation | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
| [db-04-g](#db-04-g--productionize-the-custom-partition-maintainer) | Productionize the custom partition maintainer | [db-04](#db-04--implement-schema-collection-and-index-operations) | `TODO` | — |
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

## Details

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

### db-04-a — Implement schema operations

Implement schema lookup/create/drop with explicit existence results and
unsigned OID handling. Keep logical identifier-collision checks in admin writes.
Carry the original logical catalog name through creation and store it with COMMENT ON SCHEMA; do not reconstruct it from a physical alias.

### db-04-b — Implement collection creation

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

Keep `PgIndex` as a descriptor. Move create/drop execution into `PgDatabase`,
honoring key order, method/opclass, predicates, includes, and future partitions.
Never infer HEAD status from a relation name to choose storage parameters.
Integrate with the custom maintainer: existing, future and repaired leaves must
inherit the same mandatory/custom index contract; test add/drop after registration.

### db-04-e — Implement collection and schema drop

Implement collection/schema drop with verified custom-maintainer registry cleanup.

### db-04-f — Route bootstrap and create-upsert preparation

Route JVM admin bootstrap and writer CREATE/UPSERT preparation through this same
API, including creation of each catalog's internal collections table.

### db-04-g — Productionize the custom partition maintainer

Move the reviewed prototype into one-time admin SQL installation and invoke it
through PgDatabase creation/maintenance. Keep schema, table, shift and partitions
in `"naksha~admin".naksha_partman`. Create/check the exact HEAD/history/fn layout,
with current plus three future history ranges. Integrate custom columns and index
management via db-04-b/d rather than hard-coding the prototype's three columns.
Harden version-source/manual-version/import coverage, identifier naming, locks,
configuration validation, failure atomicity and dropped-table handling. Verify
all supported partition counts and production shift policies; prototype limits
are not automatically the production contract. No pg_partman dependency.

### db-04-h — Schedule registry-wide maintenance

Add the first SQL function that scans the registry and invokes the per-root
maintainer. Install an idempotent hourly pg_cron job with explicit database/role,
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

Publish local catalog/collection changes only after successful commit; discard
or restore local metadata and bookkeeping after request/full rollback.

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

Add database functions for tuple loading, physical metadata inspection, and
prepared query execution; route SQL currently in `PgCache`, `PgSession`, and
`PgReader` through them. Retain `PgRows` and focused predicate builders.

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
