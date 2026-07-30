# Default Collection Indices Before And After CASL-1890

## Scope

This document compares Hub-created XYZ collections on the `lib_data` baseline with the behavior introduced by `v3_CASL_1890_lib_data_default_indices`.

The change applies when Naksha Hub creates its admin collections or `DefaultStorageHandler` automatically creates a missing remote collection. It does not change an explicit empty index list, an explicit custom index list, or a custom-member collection.

The PostgreSQL fallback for a collection sent directly to PSQL with `indices == null` and `members == null` also still exists. The Hub change prevents normal Hub-created collections from reaching that fallback by assigning an explicit slim index list first.

## Index Selection Semantics

| Collection metadata | Meaning | PSQL optional-index result |
| --- | --- | --- |
| `indices == null`, `members == null` | No explicit index or member configuration for an XYZ collection | `XyzIndices.ALL` fallback |
| `indices` is an empty `IndexList` | Explicitly request no optional indices | No optional indices |
| `indices` contains definitions | Explicit custom selection | Only the requested optional indices |
| `indices == null`, `members != null` | Custom-member collection without an index selection | No incompatible XYZ fallback |

Mandatory storage indices and intrinsic table constraints are created in every case. An explicit empty list disables optional indices, not mandatory database structures.

## What Mandatory Means

There are two mandatory physical layers in the current `lib_data` PostgreSQL implementation.

### Model-Declared Mandatory Indices

`StandardIndices.MANDATORY` contains five internal index definitions. `PgCollection.indicesFor()` starts with this list before considering any optional index configuration.

| Model definition | Physical name suffix | Indexed columns | Purpose |
| --- | --- | --- | --- |
| `FeatureNumberUnique` | `$ci_fn_unique` | `(fn)` | Feature-number lookup |
| `IdUnique` | `$ci_id_unique` | `(id)` | ID lookup |
| `Id` | `$ci_id` | `(id, fn, version)` | Covering ID/version lookup |
| `Version` | `$ci_version` | `(version)` | Version lookup |
| `GlobalBookNumber` | `$ci_gbn` | `(gbfn)` | Global-book reference lookup |

The generic `PgIndex` DDL currently emits these as normal `CREATE INDEX` statements. Despite the `*_unique` model names, physical uniqueness is enforced by the table constraints described below.

### Intrinsic PostgreSQL Structures

These are emitted directly by the HEAD and HISTORY table DDL. They are not entries in `StandardIndices.MANDATORY`, but they are also mandatory and cannot be removed through `NakshaCollection.indices`.

| Physical index | HEAD definition | HISTORY-leaf definition |
| --- | --- | --- |
| `$c_pkey` | unique `(fn) INCLUDE (version, id)` | unique `(fn, version) INCLUDE (nv, id)` |
| `$c_id` | unique `(id) INCLUDE (version, fn)` | unique `(id, version) INCLUDE (nv, fn)` |
| `$i_version` | `(version) INCLUDE (fn, id)` | `(version, nv) INCLUDE (fn, id)` |

Together, the five model-declared indices and these three intrinsic structures produce a fixed base of eight physical indices on an ordinary HEAD table and on each physical HISTORY leaf.

## Before The Default-Index Change

Before CASL-1890, the Hub creation paths constructed XYZ collections without an explicit index list. They reached PSQL with:

```text
indices = null
members = null
```

`PgCollection.indicesFor()` interpreted that as a default XYZ collection and selected all 15 definitions from `XyzIndices.ALL`:

| Optional index | PostgreSQL shape |
| --- | --- |
| `here_tile` | B-tree `(here_tile, fn, version)` |
| `app_id` | B-tree `(app_id, updated_at, fn, version)` |
| `author` | B-tree `(author, author_ts, fn, version)` |
| `tags` | GIN `(tags)` |
| `feature_type` | B-tree `(ft, fn, version)` |
| `cv0`, `cv1`, `cv2`, `cv3` | B-tree `(cvN, fn, version)` |
| `cs0`, `cs1`, `cs2`, `cs3` | B-tree `(csN, fn, version)` |
| `ref_point` | GiST `naksha_2d(ref_point)` |
| `geo` | GiST `naksha_2d(geo)` |

None of these definitions target `nv`, so all 15 were allowed on both HEAD and every HISTORY leaf.

| Table role | Mandatory/intrinsic | Optional XYZ | Total physical indices |
| --- | ---: | ---: | ---: |
| HEAD | 8 | 15 | 23 |
| Each HISTORY leaf | 8 | 15 | 23 |

There was no `next_version` index.

## After The Default-Index Change

`CollectionIndexPolicy.normalizeForHubCreation()` now deep-copies a Hub-created XYZ collection and, when both `indices` and `members` are null, assigns this explicit list:

```text
tags
geo
next_version
```

Because the resulting `indices` value is no longer null, `PgCollection.indicesFor()` uses this declared list instead of `XyzIndices.ALL`.

The optional physical placement is:

| Optional index | HEAD | Each HISTORY leaf | PostgreSQL shape |
| --- | ---: | ---: | --- |
| `$ci_tags` | yes | yes | GIN `(tags)` |
| `$ci_geo` | yes | yes | GiST `naksha_2d(geo)` |
| `$ci_next_version` | no | yes | B-tree `(nv, fn) INCLUDE (version)` |

`PgCollection.isRequestedIndexAllowed()` rejects `next_version` for HEAD and allows it for HISTORY. Filtering the complete definition before member-to-column conversion also prevents `(nv, fn)` from being incorrectly reduced to a HEAD index on only `fn`.

The other 13 optional XYZ indices are no longer created automatically by the Hub policy.

| Table role | Mandatory/intrinsic | Optional slim policy | Total physical indices |
| --- | ---: | ---: | ---: |
| HEAD | 8 | 2 | 10 |
| Each HISTORY leaf | 8 | 3 | 11 |

ID indexing has not been removed. ID remains covered by `$c_id`, `$ci_id_unique`, and `$ci_id`; therefore the slim optional policy does not need to request another `id` index.

Version indexing also remains covered by `$i_version` and `$ci_version`. The new HISTORY-only index addresses a different query: locating an exact predecessor from the successor pair `(nv, fn)`.

## HEAD And HISTORY Details

For an unpartitioned collection HEAD, the current physical index family is:

```text
$c_pkey
$c_id
$i_version
$ci_fn_unique
$ci_id_unique
$ci_id
$ci_version
$ci_gbn
$ci_tags
$ci_geo
```

For each yearly HISTORY leaf, the current physical index family is:

```text
$c_pkey
$c_id
$i_version
$ci_fn_unique
$ci_id_unique
$ci_id
$ci_version
$ci_gbn
$ci_tags
$ci_geo
$ci_next_version
```

The partitioned `collection$hst` table is the logical HISTORY root. The physical indices are created on leaves such as `collection$hst$2026`. `PgHistoryTable` registers the selected history indices so newly created yearly leaves receive the same set.

For distribution-partitioned collections, the same logical index set is created on the applicable physical distribution partitions, so raw catalog counts multiply with the number of physical leaves.

## Code Path Reference

This table identifies the class and method or property responsible for each decision. It intentionally avoids reproducing implementation snippets.

| Responsibility | Class | Method or property |
| --- | --- | --- |
| Nullable collection index metadata | `naksha.model.objects.NakshaCollection` | `indices` property |
| Explicitly request no optional indices | `naksha.model.objects.NakshaCollection` | `withMinimalIndices()` |
| Explicitly request all XYZ indices | `naksha.model.objects.NakshaCollection` | `withXyzIndices()` |
| Define all legacy XYZ defaults | `naksha.model.objects.XyzIndices` | `ALL` property |
| Define the five mandatory model indices | `naksha.model.objects.StandardIndices` | `MANDATORY` property |
| Define the new structured history index | `naksha.model.objects.StandardIndices` | `NextVersion` property |
| Return the Hub slim index list | `com.here.naksha.lib.core.util.CollectionIndexPolicy` | `hubSlimIndices()` |
| Apply defaults only when indices and members are null | `com.here.naksha.lib.core.util.CollectionIndexPolicy` | `normalizeForHubCreation()` |
| Create the six Hub admin collection definitions | `com.here.naksha.lib.hub.NakshaHub` | `upsertAdminCollectionsRequest()` |
| Select Handler, Space, or target-ID collection configuration | `com.here.naksha.lib.handlers.DefaultStorageHandler` | `retrieveCollectionFromRequest()` |
| Normalize collection write features | `com.here.naksha.lib.handlers.DefaultStorageHandler` | `normalizeWriteRequest()` |
| Auto-create a missing remote collection with the normalized definition | `com.here.naksha.lib.handlers.DefaultStorageHandler` | `createXyzCollection()` |
| Add mandatory definitions and apply `indices == null` / `XyzIndices.ALL` fallback | `naksha.psql.PgCollection` | `indicesFor()` |
| Restrict `next_version` to HISTORY | `naksha.psql.PgCollection` | `isRequestedIndexAllowed()` |
| Create HEAD and register HISTORY indices | `naksha.psql.PgCatalog` | `createPgCollection()` |
| Apply registered indices to a newly created history leaf | `naksha.psql.PgHistoryTable` | `createPartition()` |
| Generate generic optional/mandatory `$ci_*` DDL | `naksha.psql.PgIndex` | `create()` |
| Generate intrinsic HEAD constraints and `$i_version` | `naksha.psql.PgHeadTable` | `CONSTRAINT()` and `CREATE_SQL()` |
| Generate intrinsic HISTORY constraints | `naksha.psql.PgHistoryTable` | `CONSTRAINT()` |
| Generate intrinsic history-leaf `$i_version` | `naksha.psql.PgHistoryPartition` | `CREATE_SQL()` |

## Important Boundaries

- Explicit collection configuration wins over the Hub default policy.
- Mandatory indices remain even when `indices` is explicitly empty.
- `StandardIndices.NextVersion` is deliberately absent from `XyzIndices.ALL`; only the Hub slim policy adds it automatically.
- Direct PSQL callers can still trigger `XyzIndices.ALL` by sending an XYZ collection with both `indices` and `members` null.
- This is creation-time behavior. Updating collection metadata does not currently reconcile or backfill physical indices on an existing collection.
- This change does not provide a v3-to-`lib_data` migration or index-backfill mechanism.
