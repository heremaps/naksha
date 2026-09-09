# pg_partman Collection Partitioning Plan

## Goal

Replace the current two-root-table design (`<collection>` for HEAD and
`<collection>$hst` for history) with one PostgreSQL partitioned root table named
exactly after the collection id. PostgreSQL will route a row when its `nv` value
changes. `pg_partman` will create, retain, and remove the finite history
partitions.

No implementation is included in this change. The work below is deliberately
split into independently reviewable, executable parts.

## Confirmed Target Shape

For a collection `roads` with no performance partitioning:

```sql
CREATE TABLE roads (
  fn bigint NOT NULL,
  version bigint NOT NULL,
  nv bigint NOT NULL,
  ...,
  CHECK (nv <= <Version.HEAD>),
  UNIQUE (fn, nv) INCLUDE (version)
) PARTITION BY RANGE (nv);

-- Created by pg_partman as its default partition, then restricted by Naksha.
ALTER TABLE roads_default
  ADD CONSTRAINT roads_default_head CHECK (nv = <Version.HEAD>);
```

The root `CHECK` rejects values above `Version.HEAD`. pg_partman creates the
physical default partition, and Naksha immediately adds `CHECK (nv =
Version.HEAD)` to turn it into the logical HEAD partition. An uncovered finite
value routes to that default partition but fails the additional check, so it
still raises an error. This intentionally replaces the prior requirement of no
physical default partition; there is no unrestricted catch-all/default table.

`pg_partman` owns the finite `nv` range partitions below `Version.HEAD`. The
root table is registered as a native range parent with:

- an interval derived from `NakshaCollection.shift`;
- `forward = 3`, mapped to pg_partman's `p_premake`, creating three history
  partitions ahead;
- retention derived from `NakshaCollection.retain`, retaining `range * retain`;
- its default partition restricted to HEAD rows only.

For `partitions > 1`, each HEAD/history `nv` partition is itself partitioned
by `RANGE (fn)`. The physical `fn` value is a reversible big-endian/range-sort
encoding of Naksha's logical feature number, so its high-order byte represents
the logical low-order partition number. Range bounds therefore replace the
former modulo expression while preserving deterministic feature routing.

## Settled Decisions

1. Add `backward: Int` and `forward: Int` to `NakshaCollection`.
   `backward` is a relative negative number with default `-1`; `forward` is a
   non-negative number with default `3`; and both must be strictly less than
   `retain` in magnitude. At collection
   creation, calculate the current finite transaction-version range, move back
   `abs(backward)` ranges, and use that aligned value as pg_partman's
   `p_start_partition`. Thus the default creates the current history range and
   one preceding range before premaking `forward` future ranges. This controls only
   initial coverage. Historical imports explicitly create any older ranges with
   `partman.create_partition_id` before loading.
2. Configure pg_partman retention to detach **and drop** expired history
   partitions. Retention is `range * retain`; the exact `part_config` flags are
   `retention_keep_table = false` and `retention_keep_index = false`.
3. For `storeHistory = OFF`, writers update the existing HEAD row in place;
   they never change its `nv` from `Version.HEAD`. No history partition set is
   registered for that collection.
4. Only clean/new databases are supported. There is no old-table migration,
   upgrade compatibility, or rollback path in this work.
5. The production image and online PostgreSQL configuration will use
   `pg_partman_bgw`. Creation still invokes `partman.run_maintenance` for the
   specific new collection before returning so its initial partition set is
   immediately usable. Background-worker configuration and retention timing are
   deployment work and are not blockers for the library change.
6. Add pg_partman to the existing one-time extension-installation script. Once
   `naksha~admin` exists, normal access assumes this installation succeeded;
   initialization does not revalidate or reinstall it. Use the extension's
   installed schema and schema-qualified function calls.
7. Client-defined unique indexes are not supported by the public `Member` and
   `Index` API. Preserve that API restriction and reject any internally supplied
   custom unique index that would be invalid for the partition hierarchy.

## Feature-Number Storage and Range Subpartitioning

When `partitions > 1`, store every physical `fn` in a reversible range-sort
encoding instead of partitioning by an expression. Define the encoding as:

```text
storedFn = bswap8(logicalFn) xor Long.MIN_VALUE
logicalFn = bswap8(storedFn xor Long.MIN_VALUE)
```

The byte swap moves logical bits `0..7` to the high byte of the physical value.
Xoring the physical sign bit makes PostgreSQL's signed `int8` range ordering
match the unsigned byte-swapped order. Consequently contiguous physical ranges
divide the 256 logical partition-number values into `partitions` routing groups.

Redefine Naksha's logical partition-number contract as the unsigned lowest byte
of the logical feature number:

```text
partitionNumber(fn) = (fn & 255).toInt()  // 0 .. 255
```

`partitions` is therefore limited to `1..256`. For `N > 1`, generate `N + 1`
encoded `fn` bounds by splitting the 256 ordered partition-number values into
`N` contiguous, non-empty ranges. `N` must not exceed 256. When `N` does not
divide 256, use integer-scaled boundaries (`floor(i * 256 / N)`) so every byte
value belongs to exactly one range, with groups differing in size by at most
one. The logical partition number no longer uses `% partitions`; it identifies
the byte-value range assigned to the feature.

This must be *physical column storage*, not a `PARTITION BY RANGE (bswap8(fn))`
expression. PostgreSQL may not create a unique constraint/index on a
partitioned table whose partition key is an expression. With physical `fn`, the
mandatory root `UNIQUE (fn, nv) INCLUDE (version)` includes the root `nv` key
and every child `(fn, nv)` index includes its child `fn` range key; PostgreSQL
can therefore enforce the requested collection-wide uniqueness normally.

Install a schema-qualified immutable SQL function, for use by direct SQL users
and internally at every SQL boundary:

```sql
CREATE OR REPLACE FUNCTION "naksha~admin".bswap8(v int8) RETURNS int8
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
  SELECT (get_byte(b,7)::int8
        | get_byte(b,6)::int8 << 8
        | get_byte(b,5)::int8 << 16
        | get_byte(b,4)::int8 << 24
        | get_byte(b,3)::int8 << 32
        | get_byte(b,2)::int8 << 40
        | get_byte(b,1)::int8 << 48
        | get_byte(b,0)::int8 << 56)
  FROM (SELECT int8send(v) AS b) s
$$;
```

The Kotlin/JVM/JS implementations should use the equivalent byte-swap plus
sign-bit flip instead of calling this SQL function per row. All query bind
values that compare `fn`, all `fn` values read from storage, and all direct-SQL
examples must convert at the boundary. `fn` remains logical everywhere in the
Naksha API and tuple encoding; only the PostgreSQL column is physical.

## Partition-Number Contract Change

Files expected to change:

- `here-naksha-lib-model/.../Naksha.kt`
- feature-id/feature-number partition helper tests
- `here-naksha-lib-psql/.../PgPlatform.kt`
- writer routing, relation tests, and documentation that refer to the former
  16-bit/modulo calculation

Work:

1. Change `Naksha.partitionNumber(featureNumber)` and
   `Naksha.partitionNumber(featureId)` to return the unsigned low eight bits,
   in the inclusive range `0..255`.
2. Update their documentation and all callers to remove the former low-16-bit
   and `% partitions` behavior. `partitionNumber` represents a stable logical
   byte bucket, while the collection's encoded `fn` range bounds determine its
   physical PostgreSQL leaf.
3. Limit `NakshaCollection.partitions` and the `PgWriter` create-collection
   validation to `1..256`. Remove the inconsistent current psql-only `1000`
   cap and update error messages/tests accordingly.
4. Update `Id`-related partition helpers and tests to use the same low-byte
   contract for identifier-derived feature numbers. Ensure negative feature
   numbers are masked, not modulo-divided, so the result remains `0..255`.
5. Add conversion tests covering every low-byte value, negative and positive
   feature numbers, the encoded `fn` order, each valid partition count, and
   exact coverage/no-overlap of the generated range bounds.

Acceptance checks:

- `partitionNumber` always returns `0..255`.
- Collection creation rejects `partitions > 256` before issuing DDL.
- Every logical feature number maps to exactly one encoded-`fn` leaf for every
  valid `partitions` value.
- SQL reads/writes round-trip logical `fn` values despite physical encoding.

## HEAD Default-Partition Design

pg_partman ID maintenance ignores rows in its default partition when deriving
the current finite range. Therefore use its default partition as the physical
HEAD partition:

1. Create the root table with `CHECK (nv <= Version.HEAD)`.
2. Call `partman.create_parent` with `p_default_table := true` and the computed
   finite-history start, interval, and `forward` value.
3. Locate the default child created by pg_partman from PostgreSQL catalogs, not
   from its generated name.
4. Before any application row is written, add a validated constraint
   `CHECK (nv = Version.HEAD)` to that default child.
5. When `partitions > 1`, ensure the default child is created as
   `PARTITION BY RANGE (fn)` and receives the encoded-`fn` leaf ranges. A
   pg_partman template may apply only to finite range children, not its default
   child, so the implementation spike must determine whether pg_partman offers
   a supported default-partition template or whether Naksha must pre-create the
   partitioned default child before `create_parent`.

The default partition remains compatible with later history-partition creation:
its HEAD-only check proves it contains no values that can belong to any finite
history range. pg_partman maintenance can continue to premake and retain
finite ranges without treating the HEAD sentinel as the current range.

This behavior must be covered by a small integration spike against deployed
pg_partman `v5.0.1`: create a collection, insert HEAD rows, run maintenance,
assert future finite partitions are created, and assert both an uncovered
finite `nv` and `nv > Version.HEAD` fail. The spike must additionally verify a
performance-partitioned default/HEAD child can be created without violating
pg_partman's default-partition ownership assumptions.

## Part 1: Model and API Contract

Files expected to change:

- `here-naksha-lib-model/.../NakshaCollection.kt`
- model serialization/proxy tests that cover `NakshaCollection`

Work:

1. Add `retain: Int` to `NakshaCollection`, backed by a non-null property with
   default `24`.
2. Add `backward: Int`, backed by a non-null property with default `-1`, plus
   `withBackward(value)`. Validate `-retain < backward < 0`.
3. Add `withRetain(value)` and require `retain > 1`, because a valid negative
   `backward` must remain available.
4. Add `forward: Int`, backed by a non-null property with default `3`, plus
   `withForward(value)`. Validate `0 <= forward < retain`.
5. Change the default `shift` from `41` to `37` and update its documentation:
   `2^37` version units group normal transaction versions approximately by
   month, rather than the current yearly `2^41` grouping.
6. Mark `backward`, `forward`, `retain`, and `shift` create-only in
   validation/documentation.
   Changing any of them affects physical partition management.
7. Update callers/tests that assert the former default and verify the properties
   round-trips through JBON/proxy representations.

Acceptance checks:

- A collection without configuration resolves `shift == 37`, `retain == 24`,
  `backward == -1`, and `forward == 3`.
- Explicit valid values round-trip unchanged.
- Invalid retain/backward/forward combinations fail before collection DDL is
  issued.

## Part 2: Extension Installation and Capability Check

Files expected to change:

- `here-naksha-lib-psql/.../PgAdminCatalog.kt`
- `here-naksha-lib-psql/.../PsqlAdminCatalog.kt`
- test container/image configuration if the existing image does not expose the
  extension to the test database

Work:

1. Add `CREATE EXTENSION IF NOT EXISTS pg_partman` to the existing one-time
   extension installation SQL (`common.sql`), before internal collections are
   created. Install it in its default schema and use schema-qualified partman
   calls from library DDL.
2. Let initialization fail directly if the extension package/privilege is
   unavailable. Do not add a per-access availability check or a manual
   partitioning fallback.
3. Verify the installed extension version supports PostgreSQL 16 native range
   parents, retention, premake, and subpartition template support. The checked
   image currently compiles pg_partman `v5.0.1` in
   `deployment/docker/postgres/naksha-pg-3-misc/Dockerfile`.
4. Record extension presence/version in initialization diagnostics during the
   one-time creation path. Qualify partman function names rather than relying
   on mutable `search_path`.
5. Add integration coverage proving a clean test storage installs and can call
   the selected `pg_partman` APIs.

Acceptance checks:

- Storage creation fails when pg_partman cannot be installed.
- A fresh storage has exactly one known pg_partman extension installation.

## Part 3: Replace Physical Collection Schema

Files expected to change:

- `here-naksha-lib-psql/.../PgCollection.kt`
- `here-naksha-lib-psql/.../PgColumn.kt`
- `here-naksha-lib-psql/.../PgHeadTable.kt`
- `here-naksha-lib-psql/.../PgHistoryTable.kt`
- `here-naksha-lib-psql/.../PgHistoryPartition.kt`
- `here-naksha-lib-psql/.../PgDistributionPartition.kt`
- `here-naksha-lib-psql/.../PgTable.kt`
- `here-naksha-lib-psql/.../PgCatalog.kt`
- relation/table-name helpers and their tests

Work:

1. Make `PgColumn.NextVersionColumn` `NOT NULL` and remove the former
   table-specific null checks (`HEAD: nv IS NULL`, `HISTORY: nv IS NOT NULL`).
2. Replace the separate `PgHeadTable`/`PgHistoryTable` roots with a collection
   root descriptor whose name is `collection.id` and whose DDL is `PARTITION BY
   RANGE (nv)`.
3. Add a root constraint limiting `nv` to valid values, including
   `nv <= Version.HEAD.number`. Preserve the existing `fn`/`id` invariant.
4. Do not create an explicit HEAD range partition. Allow `create_parent` to
   create its default child, locate that default child through catalogs, and
   immediately constrain it to `nv = Version.HEAD.number`. Treat it as HEAD in
   Naksha metadata without relying on pg_partman's generated table name.
5. Calculate the partman interval as `1L shl shift` in a checked Kotlin helper.
   Calculate the creation start boundary by floor-aligning the current
   transaction version to that interval and adding `backward * interval`.
6. Validate the shift/range and calculated start boundary before issuing DDL.
   The pg_partman ID interval must be a signed PostgreSQL bigint and at least
   two; arithmetic must not overflow.
7. Call `pg_partman.create_parent` after the root and template exist,
   passing native range control `nv`, the calculated interval,
   `p_start_partition`, `p_premake := forward`, `p_default_table := true`, and the
   selected template. Immediately invoke `partman.run_maintenance` for this
   schema-qualified collection root, before returning from creation.
8. Set `partman.part_config.retention = interval * retain`,
   `retention_keep_table = false`, and `retention_keep_index = false`. Do not
   depend on the background worker for initial tables.
9. Assert that the only default partition is restricted with
   `CHECK (nv = Version.HEAD)` and cannot accept any finite value. It is the
   logical HEAD partition, not a catch-all table.
10. Store `fn` using the reversible range-sort encoding described in
    [Feature-Number Storage and Range Subpartitioning](#feature-number-storage-and-range-subpartitioning).
    Install `"naksha~admin".bswap8(int8)` in the initialization SQL and add
    equivalent Kotlin/JS conversion helpers.
11. For performance partitioning, create and supply a partman template that
    makes each finite-`nv` child `PARTITION BY RANGE (fn)`, then creates the
    `N` leaf ranges from the encoded low-byte bounds. The physical `fn` column,
    rather than an expression, is the second-level key. Determine from the
    verified pg_partman API whether the template must be created before
    `create_parent` and how it is attached to future children.
12. Verify the selected default/HEAD creation path also creates a partitioned
    default child. Create its encoded-`fn` leaves and attach matching indexes
    before accepting writes; do not assume a finite-history template is applied
    to the default child.
13. Remove manual history partition maps, creation, naming assumptions, and
   recursive index fan-out once no callers need them. Retain only descriptors
   needed to address the root and HEAD partition.
14. Simplify collection deletion to `DROP TABLE <collection> CASCADE`; this
    removes the HEAD partition, all partman partitions, templates where
    applicable, and catalog dependencies. Also remove the partman parent-config
    row if `DROP TABLE` does not do so for the verified partman version.

Acceptance checks:

- Creation initially emits Naksha's root table; pg_partman emits the restricted
  default/HEAD partition and all finite history partitions.
- `nv = Version.HEAD` reaches HEAD; a valid finite `nv` reaches its matching
  history leaf; `NULL`, values above HEAD, and uncovered finite values fail.
- `partitions = N` routes both HEAD and history rows by the calculated range
  over encoded physical `fn` values.
- `pg_partman` reports `premake = forward`, the requested aligned start
  boundary, and retention of `2^shift * retain` with detached partitions
  dropped.

## Part 4: Index Contract and Index-Only Reads

Files expected to change:

- `here-naksha-lib-psql/.../PgCollection.kt`
- `here-naksha-lib-psql/.../PgIndex.kt`
- root/HEAD schema descriptors and index tests

Work:

1. Create the mandatory root unique index on `(fn, nv) INCLUDE (version)`.
   Because both partition levels use physical columns, PostgreSQL can attach the
   matching child indexes and enforce collection-wide uniqueness for both
   unpartitioned and range-subpartitioned collections.
2. For every custom index, append missing `fn`, `version`, and `nv` to its
   `INCLUDE` list. Do not duplicate a member already present in either the key
   columns or caller-supplied includes. Keep a client's key-column order and
   index method unchanged.
3. Ensure included columns are valid for the index method. The existing code
   correctly rejects non-btree included values, so the plan must either preserve
   that validation or define how client GIN/GiST indexes satisfy the new
   requirement.
4. Create custom indexes on the root and verify PostgreSQL attaches them to
   existing children. Ensure the partman template preserves the required index
   contract for future history range children and their `fn` subpartitions.
5. Rework runtime add/drop-index behavior if it is exposed: creating/dropping a
   root partitioned index must affect existing child indexes and the template
   used by future partman children.
6. Add `EXPLAIN (ANALYZE, BUFFERS)` integration checks after `VACUUM` to show
   relevant tuple-number/history queries can use index-only scans. Treat this
   as plan-shape/performance coverage, not an unconditional guarantee while
   visibility-map bits are unset after writes.

Acceptance checks:

- The mandatory `(fn, nv) INCLUDE (version)` index exists on the root/children.
- Every custom index exposes `fn`, `version`, and `nv` once, as key or include
  columns, on HEAD and all history leaves.
- New partman-created children receive the same index contract.

## Part 5: Writer Simplification

Files expected to change:

- `here-naksha-lib-psql/.../PgWriterBase.kt`
- `here-naksha-lib-psql/.../PgWriterInsert.kt`
- `here-naksha-lib-psql/.../PgWriterUpdate.kt`
- `here-naksha-lib-psql/.../PgWriterUpsert.kt`
- `here-naksha-lib-psql/.../PgWriterDelete.kt`
- `here-naksha-lib-psql/.../PgWriter.kt`
- `here-naksha-lib-psql/.../PgAdminCatalog.kt`

Work:

1. Delete both manual three-partition seeding paths:
   `PgWriter.seedHistoryPartitions` and
   `PgAdminCatalog.seedAdminHistoryPartitions`. Collection creation instead
   invokes the selected partman initialization operation after registration.
2. Insert all new/current tuples through the collection root with
   `nv = Version.HEAD.number`, never `NULL`.
3. When `storeHistory = ON`, for an update/upsert that archives a prior HEAD tuple, update that row's
   `nv` to the successor version rather than copying to history, deleting from
   HEAD, and reinserting a replacement. PostgreSQL routes the changed row into
   the matching history partition.
4. When `storeHistory = OFF`, update the existing HEAD row in place; retain
   `nv = Version.HEAD` and do not register, create, or query history partitions.
5. Keep the operation's locking, atomic expected-version checks, action-bit
   semantics, byte-array `undefined` behavior, and returned tuple reconstruction
   unchanged while reducing CTEs to the required update/insert operations.
6. For `storeHistory = ON` DELETE/PURGE, set the prior live row's `nv` to the deletion version to
   archive it. Tombstone rows must never use a null `nv`. For
   `storeHistory = OFF`, preserve current-only delete/purge behavior without
   changing `nv` away from HEAD.
7. Remove direct references to `$hst`, `historyTable`, and manual partition
   targeting from writers. All writes target the collection root.

Acceptance checks:

- UPDATE, UPSERT, DELETE, and PURGE have the same observable API behavior and
  conflicts as before.
- A changed `nv` physically moves exactly one old HEAD row into the appropriate
  history partition without an application-issued history INSERT.
- No writer attempts to create a partition.

## Part 6: Query Planning and Metadata

Files expected to change:

- `here-naksha-lib-psql/.../PgQueryBuilder.kt`
- `here-naksha-lib-psql/.../PgQueryWhereBuilder.kt` where partition-aware
  predicates are assumed
- `here-naksha-lib-psql/.../PgRelation.kt`
- `here-naksha-lib-psql/.../PgTable.kt`
- collection/relation tests

Work:

1. Replace the HEAD/HISTORY `UNION ALL` in `PgQueryBuilder` with a single scan
   of the collection root. For current-only reads add `nv = Version.HEAD`; for
   history reads use the root and retain the existing version/lifetime filters.
   `storeHistory = OFF` queries always constrain to HEAD.
2. Re-evaluate the current `next_version IS NULL` latest-version predicate,
   replacing it with `nv = Version.HEAD` where it describes the current state.
3. Keep deleted-state filtering correct: it applies only to current rows unless
   the request explicitly asks for deleted rows, while history scans must retain
   archived tombstones needed for temporal reads.
4. Remove `$hst` naming parsing from `PgRelation`/`PgTable` and replace it with
   catalog/inheritance-based classification suitable for partman's generated
   names. Do not make correctness depend on pg_partman's naming convention.
5. Update catalog refresh/introspection code so future partitions created by
   maintenance do not have to be cached by Naksha to remain queryable.

Acceptance checks:

- Current-only queries prune to the HEAD partition.
- History queries scan the root and PostgreSQL prunes `nv` range partitions for
  bounded temporal requests.
- No production SQL relies on a `$hst` relation.

## Part 7: Retention, Maintenance, and Operational Coverage

Files expected to change:

- storage initialization/configuration
- deployment Docker/config documentation
- integration tests and possibly operator documentation

Work:

1. Update the PostgreSQL Docker configuration to set `pg_partman_bgw.dbname`
   and `pg_partman_bgw.role` for the Naksha database/owner, retaining the
   preloaded `pg_partman_bgw` library. Document matching online-database
   settings. This deployment work may follow the library change.
2. Test an initial maintenance run creates the configured three future history
   partitions and provisions indexes/subpartitions through the template.
3. Test retention after enough elapsed version ranges: exactly the agreed number
   of old history ranges are dropped/detached and HEAD is never touched.
4. Define monitoring for failed partman maintenance, missing future partitions,
   and partitions not receiving expected indexes.
5. Update image/runtime requirements: pg_partman extension package, worker
   database list/role, and operator configuration.

Acceptance checks:

- Maintenance is observable and repeatable in integration tests.
- A failed maintenance job is discoverable before writers reach an uncovered
  finite `nv` range.
- Retention cannot remove the HEAD partition or current data.

## Test Matrix

Add or revise integration tests for:

- default `shift = 37`, `retain = 24`, `backward = -1`, `forward = 3`, and
  explicit overrides;
- extension installation/unavailability;
- root, HEAD, and pg_partman-created history topology with and without
  performance partitions;
- restricted default/HEAD partition, invalid `nv` rejection, and absence of an
  unrestricted catch-all partition;
- all writer operations, expected-version conflicts, and `storeHistory` modes;
- current, temporal, latest-N-version, deleted, and ordered queries;
- mandatory/custom index definitions and future-partition propagation;
- maintenance premake and retention;
- collection/schema deletion cleanup;
- explicit historical range creation with `partman.create_partition_id` followed
  by import through the collection root.

The existing tests that explicitly expect `<collection>$hst`, calendar-year
partition names, and writer-created future partitions need replacement rather
than incremental adjustment. Relevant current locations include
`CollectionTests.kt` and `PartitioningTest.kt` in `here-naksha-lib-psql`.
