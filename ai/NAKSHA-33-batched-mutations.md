# Batched mutation plan

Date: 2026-11-20. Scope: replace per-feature SQL execution in `PgWriter`/`PgDatabase` with genuine multirow CREATE, UPSERT, UPDATE, DELETE and PURGE. This records the implementation plan; batched mutations are not implemented yet.

## Memory policy

Connection initialization sets `work_mem` to **512MB for writes** and **128MB for reads**, resetting it on every pool checkout. The JVM initializer and generated JS initializer use the requested `readOnly` mode. `SKIP_INIT` and borrowed PLV8 connections continue to preserve the caller's settings. This is a per-operation allowance, not a reservation or whole-query cap; spill and concurrent OOM risk are explicitly accepted in favor of performance. Batch sizing and spill measurements remain necessary.

## Query pipeline

- Generate SQL from the collection's complete physical layout; convert feature numbers to exact ordinals in JVM/JS, not SQL helpers. Validate duplicate inputs and retain their original ordinals for result correlation.
- Use typed arrays/`UNNEST` for all supported platforms. Use a normal `input` CTE, allowing PostgreSQL's default inlining/materialization decision. Capture existing predecessors once in the locking CTE. COPY and temporary staging tables are outside this increment.
- Optimistic bulk CREATE/UPSERT inserts new HEADs in deterministic physical-key order. Unique `(fn, nv)` arbitration replaces absent-key advisory locking. Classify non-returned keys in a subsequent statement with a fresh snapshot: live CREATE conflicts, tombstones may be recreated, and live UPSERTs transition to UPDATE.
- Existing-HEAD transitions read and lock the schema-qualified HEAD relation directly, using `PgTableName.headName` derived from the verified physical root and separately quoted schema/table names. HEAD may itself be partitioned by `fn`. Lock rows with `FOR UPDATE`, ordered by collection and `fn`; apply expected-version/successor checks, replace complete state, and archive the exact captured predecessor only for successful changes. Keep mutation/archival DML targeting the root initially to preserve the existing entry point and history routing. DELETE/PURGE preserve existing tombstone, retention and no-op rules.

SQL shape below is a generator blueprint, not executable SQL; each placeholder expands all declared columns and existing operation/error predicates:

```sql
-- Fast absent-key path:
WITH input AS (<multirow UNNEST input>)
INSERT INTO <root> (<columns>)
SELECT <HEAD states> FROM input ORDER BY fn
ON CONFLICT (fn, nv) DO NOTHING
RETURNING fn, version;

-- Existing-state transition path, on a fresh statement snapshot:
WITH input AS (<multirow UNNEST input>),
locked AS MATERIALIZED (
  SELECT h.* FROM <schema-qualified_head> h JOIN input i USING (fn)
  ORDER BY h.fn FOR UPDATE OF h
),
changed AS (<validated multirow UPDATE/DELETE, RETURNING identity>),
archived AS (
  INSERT INTO <root> (<columns>)
  SELECT <old columns, successor nv>
  FROM locked JOIN changed USING (fn) JOIN input USING (fn)
  WHERE <history enabled> RETURNING fn
)
SELECT <identity, original ordinal, status, actual/new versions>;
```

`RETURNING` dependencies, not textual CTE order, connect mutation and archive phases. Return only needed status/identity/version fields, not old blobs; never rely on returned row order. Preserve error priority, change-count handling, public request atomicity and required low-level failure/rollback behavior. No mutation may touch the same physical row twice in one statement.

Remove **per-feature advisory locks**, not bootstrap/catalog/collection lifecycle guards. Retain coverage checks until the separately agreed background provisioning guarantees exist. Ordered row locks do not lock missing keys or eliminate all mixed-operation deadlocks. Use bounded contention handling: roll back the batch before replay, obtain a fresh READ COMMITTED snapshot, and require whole-transaction retry when retained locks or previous writes make local replay unsafe. Do not silently allocate another logical version or release earlier caller work.

## Implementation sequence

1. Apply read/write `work_mem` defaults and verify alternating pool checkouts. **Implemented:** real JVM write/read/write/read `SHOW work_mem` assertions pass; all forced metadata, JVM production/test and JS production/test-source compilation gates pass. No batch-performance gain is claimed from this setting alone.
2. Build a reusable collection execution context and multirow input/result mapping; cache projections/column lookups and reuse prepared statements. Implement optimistic bulk CREATE and its conflict/recreation fallback.
3. Implement bulk UPDATE/UPSERT and DELETE/PURGE transitions, predecessor archival, version checks and structured outcomes. Preserve deterministic key ordering without regrouping operations unsafely.
4. Add race and rollback regressions: absent contenders, invisible conflict winners, delete/recreate, overlapping mixed operations, duplicate/invalid input, shuffled results, retained-transaction locks and retry exhaustion. Review failure-prefix contracts before changing implementation or assertions.
5. Benchmark UNNEST batch rows/bytes, partition counts, payload sizes, client concurrency and network latency; capture executions, EXPLAIN planning/execution, temporary I/O, WAL and end-to-end commit latency. Optimize the correlated coverage check separately without dropping its guarantee. Run all metadata/forced compilation gates before tests, then independent correctness/security review; disclose unrelated failures.
