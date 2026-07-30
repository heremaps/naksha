# PostgreSQL Collection Index Performance Test

## Purpose

`NextVersionIndexPerformanceIT` verifies the `lib_data` Hub collection index policy against a real PostgreSQL database. It proves both physical placement and the performance effect of the history-only `next_version` index used by ActivityLog predecessor lookup.

The Docker image does not contain the benchmark collections or features. Responsibilities are split as follows:

| Component | Responsibility |
| --- | --- |
| `ghcr.io/naksha-oss/naksha-postgres:v16.2-r5` | Naksha PostgreSQL image hosted in the `naksha-oss` GitHub Container Registry organization |
| `PsqlTestStorage` | Connects the JVM test to the dedicated PostgreSQL instance and initializes Naksha storage |
| `NextVersionIndexPerformanceIT` | Recreates `idx_perf_map`, creates all three collections, writes all features and updates, executes the queries, and writes the report |
| DBeaver | Inspects the database left behind after the test |

Therefore, yes: `NextVersionIndexPerformanceIT` is what fills the database with the benchmark features. The Docker image only provides the PostgreSQL server expected by the Naksha PSQL implementation.

The benchmark creates three otherwise identical collections:

| Collection | Requested optional indices | Additional test-only index |
| --- | --- | --- |
| `idx_perf_no_nxv` | `tags`, `geo` | none |
| `idx_perf_separate_nxv` | `tags`, `geo` | `btree (nv)` with fill factor 100 on every history leaf |
| `idx_perf_with_nxv` | `tags`, `geo`, `next_version` | none |

All three retain storage-managed mandatory indices, including the `fn`-leading history indices. The comparison therefore evaluates three valid lookup strategies: the mandatory indices alone, a standalone `nv` index plus the mandatory `fn` access path, and the production composite `(nv, fn)` index.

The standalone `nv` index is created directly by the benchmark after data loading. It is deliberately not a Naksha model index and does not change the production collection policy. It uses the same fill factor as the composite index. Creating it after loading keeps this test focused on read plans; this benchmark does not compare write amplification or index-build cost between the standalone and composite alternatives.

The structured index under test is:

```text
next_version -> on(nv, fn), include(version)
```

PostgreSQL creates it on each history leaf as:

```sql
CREATE INDEX ...$ci_next_version
ON ... USING btree (nv, fn)
INCLUDE (version)
WITH (fillfactor=100);
```

It is not created on HEAD. History rows already enforce `nv IS NOT NULL`, so no partial predicate is required.

## What The Test Does

By default, each collection receives 6,000 deterministic line features and nine full update rounds. The rounds use storage upserts against known stable IDs; every row already exists, so each upsert updates HEAD and archives the preceding state. This avoids making the index benchmark depend on optimistic-lock metadata or the separate bulk `UPDATE` writer path. The resulting expected shape is:

| Table family | Rows per collection |
| --- | ---: |
| HEAD | 6,000 |
| HISTORY | 54,000 |

After loading, the benchmark:

1. Creates the test-only standalone `nv` indices on `idx_perf_separate_nxv` history leaves.
2. Runs `ANALYZE` on HEAD and all history leaves.
3. Verifies catalog placement for `tags`, `geo`, the standalone `nv` index, and `next_version`.
4. Verifies unrelated XYZ optional indices are absent.
5. Runs lightweight ID, tags, and geometry plan checks.
6. Samples successor keys as `(featureNumber, successorVersion)` pairs.
7. Executes the Naksha `ReadFeatures` equivalent of the ActivityLog predicate:

```text
OR(
  AND(nv = successorVersion, fn = featureNumber),
  ...
)
```

8. Executes equivalent parameterized SQL against each collection's partitioned history root:

```sql
SELECT fn, version
FROM "idx_perf_map"."idx_perf_with_nxv$hst"
WHERE (nv = $1 AND fn = $2)
   OR (nv = $3 AND fn = $4)
   OR ...;
```

This projection matches `PgQueryBuilder`'s first-stage tuple-number lookup. The feature `id` is not needed to identify or load the predecessor, so it is neither selected here nor stored in the `next_version` index.

9. Captures both text and JSON `EXPLAIN (ANALYZE, BUFFERS)` plans for all three collections.
10. Rotates the execution order of warmed timing repetitions to reduce order and cache bias.
11. Writes the actual report to `here-naksha-lib-psql/build/reports/naksha-index-perf/next-version-index.md`.

The JSON execution is the source for automated plan assertions and shared-buffer totals. The separately executed text plan presents the same PostgreSQL plan information in the conventional indented format for human inspection. Because each uses a separate `EXPLAIN ANALYZE` execution, their timings and buffer values can differ slightly.

The baseline may use a mandatory `fn`-leading index and filter on `nv`; a sequential scan is not required. PostgreSQL may use or ignore the standalone `$ci_nv_only` index depending on its cost estimate, and the report records that decision without treating either choice as a failure. When it combines the separate `fn` and `nv` indices, the plan contains two `Bitmap Index Scan` nodes under a `BitmapAnd`. The composite JSON plan must use an index named with `$ci_next_version`, return the expected rows, and consume fewer shared buffers than the baseline.

## Recreate On Another Machine

The commands below assume a bash-compatible terminal and a fresh dedicated Docker container. Do not use a shared development or production database. During initialization, `PsqlTestStorage` drops user-created schemas in the configured database, and the benchmark recreates `idx_perf_map` on every run.

### Step 1: Check Out The Benchmark Code

Use the branch or commit containing both of these files:

```text
here-naksha-lib-psql/src/jvmTest/kotlin/naksha/psql/NextVersionIndexPerformanceIT.kt
docs/drafts/POSTGRES_COLLECTION_INDEX_PERF_TEST.md
```

For the current implementation:

```bash
git fetch origin
git switch v3_CASL_1890_lib_data_default_indices
git branch --show-current
```

Run all Gradle commands below from the Naksha repository root, where `gradlew` is located.

### Step 2: Verify Prerequisites

Install or make available:

1. Docker Desktop or Docker Engine.
2. JDK 25.
3. DBeaver with the PostgreSQL driver.

Verify Docker and the Gradle JVM:

```bash
docker version
./gradlew --version
```

The Gradle output should show JVM 25. If another JVM is selected, configure `JAVA_HOME` to point to JDK 25 before running the benchmark.

### Step 3: Pull The Naksha PostgreSQL Image

The image is stored in GitHub Container Registry at:

```text
ghcr.io/naksha-oss/naksha-postgres:v16.2-r5
```

Pull it explicitly:

```bash
docker pull ghcr.io/naksha-oss/naksha-postgres:v16.2-r5
```

The same image URI is defined by `PsqlTestStorage.POSTGRES_IMAGE_URI`. Do not substitute a plain PostgreSQL image because the Naksha image contains the database environment expected by the storage implementation.

### Step 4: Start A Dedicated Database Container

```bash
docker run -d --name NakshaIndexPerf \
  -p 15433:5432 \
  -e PGPASSWORD=password \
  ghcr.io/naksha-oss/naksha-postgres:v16.2-r5
```

Confirm that it is running:

```bash
docker ps --filter name=NakshaIndexPerf
docker logs NakshaIndexPerf
```

Wait until PostgreSQL reports that it is ready to accept connections. Port `15433` is deliberately separate from the usual local PostgreSQL port `5432`.

If the container already exists but is stopped, reuse it with:

```bash
docker start NakshaIndexPerf
```

### Step 5: Run The Full Benchmark And Populate The Database

Run from the repository root:

```bash
NAKSHA_RUN_INDEX_PERF_TEST=true \
NAKSHA_TEST_PSQL_DB_URL='jdbc:postgresql://localhost:15433/postgres?user=postgres&password=password' \
./gradlew :here-naksha-lib-psql:jvmTest \
  --tests naksha.psql.NextVersionIndexPerformanceIT
```

`NAKSHA_RUN_INDEX_PERF_TEST=true` enables the opt-in test. Without it, JUnit skips the benchmark.

`NAKSHA_TEST_PSQL_DB_URL` is also important. It makes the test use the separately started `NakshaIndexPerf` container. If it is omitted, `PsqlTestStorage` may start a temporary Testcontainers database that is automatically stopped when the JVM exits, leaving nothing available for DBeaver.

At its defaults, `NextVersionIndexPerformanceIT` performs the following database work:

1. Drops and recreates the `idx_perf_map` catalog.
2. Creates `idx_perf_no_nxv` with requested `tags` and `geo` indices.
3. Creates `idx_perf_separate_nxv` with requested `tags` and `geo` indices.
4. Creates `idx_perf_with_nxv` with requested `tags`, `geo`, and `next_version` indices.
5. Creates 6,000 deterministic features in each collection.
6. Performs nine upsert rounds for every feature in each collection.
7. Adds a standalone `btree (nv)` index with fill factor 100 to each `idx_perf_separate_nxv` history leaf.
8. Produces 6,000 HEAD rows and 54,000 HISTORY rows per collection.
9. Runs `ANALYZE`, catalog assertions, real Naksha history queries, warmed timings, and JSON `EXPLAIN ANALYZE` queries.
10. Leaves the database inside the separately managed Docker container for DBeaver inspection.

The benchmark uses direct PSQL storage APIs, not `here-naksha-cli`. Stable feature IDs and controlled upsert rounds ensure that all three collections receive equivalent logical data.

Available controls:

| Environment variable | Default |
| --- | ---: |
| `NAKSHA_INDEX_PERF_FEATURES` | `6000` |
| `NAKSHA_INDEX_PERF_UPDATES` | `9` |
| `NAKSHA_INDEX_PERF_SAMPLE_SIZE` | `100` |
| `NAKSHA_INDEX_PERF_REPEATS` | `20` |
| `NAKSHA_INDEX_PERF_BATCH_SIZE` | `500` |

For a fast functional smoke run:

```bash
NAKSHA_RUN_INDEX_PERF_TEST=true \
NAKSHA_INDEX_PERF_FEATURES=200 \
NAKSHA_INDEX_PERF_UPDATES=2 \
NAKSHA_INDEX_PERF_SAMPLE_SIZE=10 \
NAKSHA_INDEX_PERF_REPEATS=2 \
NAKSHA_TEST_PSQL_DB_URL='jdbc:postgresql://localhost:15433/postgres?user=postgres&password=password' \
./gradlew :here-naksha-lib-psql:jvmTest \
  --tests naksha.psql.NextVersionIndexPerformanceIT
```

The smoke run replaces the contents of `idx_perf_map` with the smaller dataset. Run the full benchmark afterward if the database should finish with the expected 6,000 HEAD and 54,000 HISTORY rows per collection. Small tables may also produce different PostgreSQL plans, so the smoke run is not performance evidence.

### Step 6: Check The Generated Report

A successful Gradle run prints `BUILD SUCCESSFUL` and writes:

```text
here-naksha-lib-psql/build/reports/naksha-index-perf/next-version-index.md
```

The file is under `build/`, so it is intentionally not tracked by Git. It contains row counts, timing samples, catalog snapshots, JSON-derived buffer counts, conventional text execution plans, and complete JSON execution plans.

### Step 7: Connect With DBeaver

Create a new PostgreSQL connection with:

| DBeaver field | Value |
| --- | --- |
| Host | `localhost` |
| Port | `15433` |
| Database | `postgres` |
| Username | `postgres` |
| Password | `password` |
| SSL | disabled |

Select **Test Connection**, allow DBeaver to download its PostgreSQL driver when prompted, and then select **Finish**.

In the Database Navigator, expand:

```text
postgres
  Schemas
    idx_perf_map
      Tables
```

If `idx_perf_map` is not visible immediately after the Gradle test, refresh the connection or the **Schemas** node.

The three scenarios are collection table families in the same `idx_perf_map` schema, not separate schemas:

| Scenario | HEAD | HISTORY root | History leaves |
| --- | --- | --- | --- |
| Mandatory indices only | `idx_perf_no_nxv` | `idx_perf_no_nxv$hst` | `idx_perf_no_nxv$hst$YYYY` |
| Separate `nv` and mandatory `fn` indices | `idx_perf_separate_nxv` | `idx_perf_separate_nxv$hst` | `idx_perf_separate_nxv$hst$YYYY` |
| Composite `(nv, fn)` | `idx_perf_with_nxv` | `idx_perf_with_nxv$hst` | `idx_perf_with_nxv$hst$YYYY` |

The `$hst` root is partitioned. The physical test-only `$ci_nv_only` and production `$ci_next_version` indices are visible on yearly history leaves such as `idx_perf_separate_nxv$hst$2026` and `idx_perf_with_nxv$hst$2026`.

### Step 8: Validate The Database In DBeaver

Open a SQL Editor for the `postgres` database and run the catalog and row-count queries in the following sections. The expected final counts after the full benchmark are:

| Collection | HEAD | HISTORY |
| --- | ---: | ---: |
| `idx_perf_no_nxv` | 6,000 | 54,000 |
| `idx_perf_separate_nxv` | 6,000 | 54,000 |
| `idx_perf_with_nxv` | 6,000 | 54,000 |

To preserve this database for later inspection, stop rather than remove the container:

```bash
docker stop NakshaIndexPerf
```

Restart it later with `docker start NakshaIndexPerf`. Removing the container deletes its database unless a separate Docker volume was configured.

## Row Count Inspection

Run this in DBeaver's SQL Editor:

```sql
SELECT 'idx_perf_no_nxv HEAD' AS table_name, count(*) AS row_count
FROM "idx_perf_map"."idx_perf_no_nxv"
UNION ALL
SELECT 'idx_perf_no_nxv HISTORY', count(*)
FROM "idx_perf_map"."idx_perf_no_nxv$hst"
UNION ALL
SELECT 'idx_perf_separate_nxv HEAD', count(*)
FROM "idx_perf_map"."idx_perf_separate_nxv"
UNION ALL
SELECT 'idx_perf_separate_nxv HISTORY', count(*)
FROM "idx_perf_map"."idx_perf_separate_nxv$hst"
UNION ALL
SELECT 'idx_perf_with_nxv HEAD', count(*)
FROM "idx_perf_map"."idx_perf_with_nxv"
UNION ALL
SELECT 'idx_perf_with_nxv HISTORY', count(*)
FROM "idx_perf_map"."idx_perf_with_nxv$hst"
ORDER BY table_name;
```

After the full default run, the result must contain 6,000 HEAD rows and 54,000 HISTORY rows for each collection.

## Catalog Inspection

List the benchmark indices:

```sql
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'idx_perf_map'
  AND tablename LIKE 'idx_perf_%'
ORDER BY schemaname, tablename, indexname;
```

List only the standalone and composite next-version indices:

```sql
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'idx_perf_map'
  AND (
    indexname LIKE '%$ci_nv_only%'
    OR indexname LIKE '%$ci_next_version%'
  )
ORDER BY schemaname, tablename, indexname;
```

Expected placement:

| Location | `tags` | `geo` | standalone `nv` | composite `next_version` |
| --- | --- | --- | --- | --- |
| `idx_perf_no_nxv` HEAD | yes | yes | no | no |
| `idx_perf_no_nxv` history leaves | yes | yes | no | no |
| `idx_perf_separate_nxv` HEAD | yes | yes | no | no |
| `idx_perf_separate_nxv` history leaves | yes | yes | yes | no |
| `idx_perf_with_nxv` HEAD | yes | yes | no | no |
| `idx_perf_with_nxv` history leaves | yes | yes | no | yes |

New history leaves inherit the production indices registered on the history root. The manually created `$ci_nv_only` index is benchmark-only and is not registered for future partitions. A collection configured with `storeHistory=OFF` creates neither history tables nor `next_version` indices; focused catalog tests cover both cases.

## Statistics

Index usage:

```sql
SELECT relname, indexrelname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'idx_perf_map'
  AND (
    indexrelname LIKE '%$ci_nv_only%'
    OR indexrelname LIKE '%$ci_next_version%'
  )
ORDER BY relname, indexrelname;
```

Statement statistics, when the extension is preloaded:

```sql
SELECT calls, mean_exec_time, rows, shared_blks_hit, shared_blks_read, query
FROM pg_stat_statements
WHERE query ILIKE '%naksha-index-perf-next-version%'
ORDER BY mean_exec_time DESC
LIMIT 20;
```

`pg_stat_statements` is optional. Without `shared_preload_libraries = 'pg_stat_statements'`, the report records PostgreSQL's preload error as `Unavailable` and continues. This does not compromise the test because explicit JSON `EXPLAIN ANALYZE` plans and their shared-buffer counts are the source of truth. Text plans are additional diagnostic output.

## Pass Criteria

At the default scale the report should show:

| Evidence | Expected result |
| --- | --- |
| HEAD count | `6000` for all three collections |
| HISTORY count | `54000` for all three collections |
| Baseline plan | uses neither `$ci_nv_only` nor `$ci_next_version` |
| Separate-index plan | never uses `$ci_next_version`; `$ci_nv_only` usage is recorded but not required |
| Composite plan | uses `$ci_next_version` through an index or bitmap index scan |
| Result count | equals sampled key count for all three collections |
| Shared buffers | composite lookup is lower than baseline |
| Timings | recorded as evidence; no absolute millisecond threshold |

This covers creation-time behavior only. Existing `lib_data` collections are not reindexed because physical collection evolution is not implemented by `verifyNewHeadState()`. The benchmark does not prove a v3-to-`lib_data` migration, index backfill, or rollback path.
