# Batched mutations — plan and implementation checkpoint

Date: 2026-11-20. Scope: replace per-feature SQL execution in `PgWriter`/`PgDatabase` with genuine multirow CREATE, UPSERT, UPDATE, DELETE and PURGE. The approved plan was committed/pushed as `19efc3f94` and summarized on NAKSHA-33 (comment `5221450`). The user-approved baseline rollback checkpoint is `a21289c47` (`Implement UNNEST batched mutations and record performance baseline`). The user has now **approved releasing the separate HEAD-discovery improvement as 3.0.0-beta.55, committing and pushing it**, then assigning NAKSHA-33 to Phuc Mai for review. Manual deployment/testing belongs to the user; JS improvements remain a subsequent task. The plan below is retained as design context; the checkpoint records the implementation and verification limits.

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

## Implemented pipeline

- `PgBatchMutation` owns one collection execution context. `PgWriter` submits mixed operations together rather than splitting contiguous operation runs; all low-level `PgDatabase.rows_*` mutation APIs delegate to the same engine. The obsolete per-feature SQL/advisory-lock helpers are removed. Bootstrap/catalog/collection/provisioning guards and coverage checks remain.
- All-CREATE/UPSERT inputs first attempt one ordered bulk INSERT. Only complete correlated RETURNING results succeed. Partial attempts roll back before fresh-snapshot classification of the **whole batch**, not just non-returned keys. Candidate conversion/constraint errors also roll back and fall back to classification, preserving duplicate-CREATE semantic-error priority.
- The classified CTE uses normal `input`, materialized ordered HEAD `locked`, materialized `classified`, earliest-error `first_error`, and valid-prefix `eligible` relations. Disjoint `changed` UPDATE and `removed` PURGE paths feed `archived` through RETURNING identities; absent CREATE/UPSERT keys use `inserted` with unique-index arbitration. Archive columns come from the exact captured predecessor, changing only `nv` to the successor version.
- Semantic errors retain only the valid prefix at low-level APIs and are thrown after releasing the attempt subtransaction. Missing mutation results retry after attempt rollback, at most four classified attempts. SQLSTATE `40P01`/`40001` signals **complete-transaction retry**, not blind local replay or new version allocation. Native JVM savepoints and PLV8 subtransactions isolate attempts and whole writer requests; PgWriter no longer issues textual SAVEPOINT commands.
- Correlation uses input ordinal and exact physical feature ordinal, never server row order. Changed UPSERT tuple metadata is copied; inputs remain immutable. DELETE/PURGE return the required stored state/no-op information, so these operations may return payload columns; CREATE/UPSERT/UPDATE output payload projections are NULL, although internal UPDATE RETURNING captures the full stored row.
- TAG_LIST/TAG_MAP JSON strings ride `text[]` transport and are parsed after UNNEST. Native `jsonb[]` binding incorrectly encoded JS strings as JSON scalars. Bigint result metadata uses decimal text. PLV8 mutations use prepared-plan SPI `execute`, not read cursors. Native JS causes are retained on exception carriers rather than lost through typed JSON-property access.

## Performance evidence

Single illustrative before/after runs of the existing 1,200-feature, 12-batch, four-worker small-topology tests:

| Case | Before features/s | After features/s | Change |
| --- | ---: | ---: | ---: |
| CREATE | 1,080 | 1,657 | +53% |
| Grouped CREATE | 1,291 | 1,843 | +43% |
| Seeded UPSERT | 1,308 | 1,582 | +21% |

Before evidence: `local-tests-20261120_083103`; after: `local-tests-20261120_123920`, under the approved private OpenCode temporary directory. These are not controlled capacity estimates. CREATE median end-to-end batch latency was about 170ms; the logged 100-row batch-engine median was about 21ms. Encoding, preparation, coverage and commit still contribute substantially. A separate `bulkSqlBenchmarkExcludesEncodingCoverageAndCommit` test measures the bound batch API with pre-encoded inputs (including client binding/planning/result handling), not server execution alone.

`pg_stat_statements` is not preloaded locally; no server restart or configuration change was made. Server-only planning/execution, temporary I/O/WAL attribution, wide-topology/payload/concurrency/network sweeps and maximum capacity remain unmeasured.

The SQL-focused benchmark passed in `local-tests-20261120_124905`: 15 measured samples after eight warm-ups per size, median/p95 **0.86/1.10ms for one row**, **4.48/10.65ms for 100 rows**, and **34.08/46.51ms for 1,000 rows**. These measurements exclude encoding, coverage and commit, but include the bound batch API's client-side costs and run within one caller-owned transaction.

### Requested four-operation local throughput run

`PgMutationThroughputTest` passed in `local-tests-20261120_132500`, after fully serial forced compilation gates (`naksha-throughput-serial-compile-summary.json`). Local host: Apple M2 Pro, 12 logical CPUs, 16GiB RAM; existing PostgreSQL 18 Docker container, 1GiB shared memory, no explicit per-container CPU/RAM cap. No competing regression workload ran during measurement. Four workers, four configured partitions, history/tombstones ON, small LineString topology fixture (~329 compact JSON bytes before per-feature IDs/encoding), 8,000 features per round, one warmup plus three measured rounds per case. JVM/JDBC request execution includes encoding, session/transaction setup and commit; request construction and committed seeding are excluded. This is not an HTTP or complete PLV8 runtime benchmark.

| Operation | 100-row batches: median features/s (measured range) | 1,000-row batches: median features/s (measured range) |
| --- | ---: | ---: |
| INSERT | 2,887 (2,803–3,034) | 2,712 (2,636–3,056) |
| UPDATE existing keys | 468 (343–624) | 458 (332–607) |
| UPSERT existing keys | 440 (322–647) | 454 (349–566) |
| DELETE live keys → tombstones | 588 (447–899) | 616 (465–961) |

**Significant scaling warning:** existing-key operations slowed monotonically as their collections accumulated rows/history across rounds. For 100-row batches, UPDATE went from 1,129/s in the warmup to 624 → 468 → 343/s; UPSERT from 926 to 647 → 440 → 322/s; DELETE from 1,469 to 899 → 588 → 447/s. The 1,000-row cases exhibited similar degradation. Larger batches did not remove it. These results establish measured local behavior, not stable throughput or a maximum-capacity claim. Root cause is not yet established; query plans/statistics, growing HEAD/history work and client costs require further profiling. No optimization or server-setting change was made to conceal the result. The earlier short illustrative before/after runs are a different workload and should not substitute for this repeated-run evidence.

Each case performed 32,000 operations including warmup (256,000 total across eight cases), plus 192,000 committed seed inserts outside timing. Raw round throughput and median/p95 batch latencies are archived in `throughput-summary.json` and test XML; at batch size 1,000 each round has only eight batches, so p95 is the maximum. The test helper's normal logging remains enabled.

### Bottleneck attribution

The archived throughput logs place the growing delay inside the batch engine, not commit. For the last 100-row round, median UPDATE request/engine/commit times were **1,134/1,057/8.8ms**, UPSERT **1,228/1,170/7.0ms**, DELETE **902/889/7.4ms**, and INSERT **145/24/8.7ms**. These independently computed medians are not additive per-request accounting; the engine includes binding/planning/SQL/result processing. INSERT's remaining time is outside the engine and requires finer client-side profiling before attributing it specifically to encoding.

Exact generated SQL/bindings were then profiled with `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON)` in rollback-only attempt subtransactions on fresh disposable fixtures with 8,000 and 32,000 live HEAD rows. `PgMutationPlanProfileTest` passed (`local-tests-20261120_134545`); all forced metadata/JVM/JS gates passed before execution. No production SQL or server configuration was changed. An initial failed attempt used `PgTestBase`, whose catalog-reset behavior removed the earlier disposable benchmark fixtures; raw benchmark XML/results remain preserved. The successful profiler does not extend that resetting base and uses a newly created database.

**Confirmed dominant cost: HEAD predecessor discovery in `CTE locked`.** It reads all HEAD rows through the ordered partition index scans, then nested-loops over the input CTE for every HEAD row instead of restricting/index-probing the requested keys. Only matching rows are subsequently locked; this is not evidence of lock waiting.

| Profile, 32,000 live HEADs | Locked discovery time | SQL execution time | Rejected candidate pairs |
| --- | ---: | ---: | ---: |
| UPDATE 100 inputs | 1,121ms | 1,141ms | 3,199,900 |
| UPDATE 1,000 inputs | 9,662ms | 9,811ms | 31,999,000 |
| DELETE 100 inputs | 830ms | 845ms | 3,199,900 |

Locked discovery accounts for about **98%** of these instrumented executions. The UPDATE/archive nodes took approximately **6/13ms** for 100 inputs and **81/50ms** for 1,000 inputs. At 8,000 live HEADs, 100-row UPDATE discovery took 241ms of 249ms SQL execution. The same pathology occurs without accumulated committed history, so history growth alone does not explain the slowdown. Do not add overlapping inclusive CTE/node times. EXPLAIN timing overhead is significant with millions of comparisons, but the candidate-pair counts establish the algorithmic problem.

The captured settings include the existing initializer's `enable_sort=off`, `enable_seqscan=off`, and custom planner cost/cache settings; these are not default PostgreSQL plans. No shared-read or temporary-spill blocks were observed in the representative warm-cache plans. This does not exclude WAL/write I/O or cold-cache costs. Independent read-only review confirmed the diagnosis and these caveats. Next experiment: explicitly restrict/index-probe the batch's feature ordinals during HEAD capture and compare the existing planner policy against transaction-local planner settings, while preserving deterministic `FOR UPDATE` order, exact materialized predecessors, and retry/history contracts. No optimization has yet been implemented or claimed.

## Separate HEAD-discovery improvement

Rollback checkpoint: **`a21289c47` — `Implement UNNEST batched mutations and record performance baseline`**. NAKSHA-33 comment `5224144` records the checkpoint and next increment. The following improvement is separately user-approved for commit/push as **3.0.0-beta.55**, with commit message `Optimize batched HEAD discovery and release beta.55`.

Release versions: Gradle project version, generated `NakshaVersion.CURRENT`, generated OpenAPI version, and installed `adminVersion` advance to **3.0.0-beta.55**. The admin runtime revision ensures permitted upgrades refresh installed Kotlin/JS modules containing the batch changes; this is not an on-disk schema migration. Minimum compatible admin remains **3.0.0-beta.53**. Existing compatible installations with upgrade disabled retain their prior admin runtime under the existing policy; manual deployment must permit the intended runtime refresh. No deployment, server restart or remote database operation is performed by the agent.

`PgBatchMutation` now captures `locked AS MATERIALIZED (SELECT h.* FROM <verified HEAD> h WHERE h.fn = ANY(<existing bound ordinal array>::bigint[]) ORDER BY h.fn FOR UPDATE OF h)`. The array parameter is derived from the actual transport column index, not assumed to be `$1`; it contains the already-converted physical ordinals. Downstream predecessor joins use unique `fn`, while input ordinals continue to drive semantic-error boundaries and result correlation. Mutation/archival, absent-key arbitration, retry/subtransaction boundaries, guards and production planner settings are unchanged.

Forced metadata/JVM production/JVM test/JS production-test compilation passed (`naksha-keyed-head-compile-summary.json`). Focused BATCH 8/8, mutation 27/27, session 15/15 and writer ordering 8/8 passed in `local-tests-20261120_140825`. The additional BATCH case reverses transport column order and verifies exact parameter selection, numeric/hashed identities, UPDATE outcomes and history counts. Independent correctness/security reviews found no confirmed blockers. Remaining fuller native-JS request coverage and strengthened reversed multi-key contention coverage remain disclosed, not claimed established by static review.

### Same-data SQL-shape/planner matrix

`PgMutationPlanProfileTest` compares the keyed query with a test-only reconstruction of the checkpoint's HEAD/ordinal-join shape, on identical fresh 8,000/32,000-row fixtures. Each runs under current settings and under transaction-local `enable_sort=on; enable_seqscan=on`, twice. Every variant asserts that all requested HEADs are mutated, then rolls back the attempt including local settings. This is a SQL-shape comparison, not execution of a separately compiled checkpoint binary. Full semantic equivalence is established by complementary mutation tests, not that row-count assertion alone.

All variants passed in `local-tests-20261120_141118`. Median **server execution time** across two instrumented samples, at 32,000 live HEADs:

| Operation / batch | Baseline, current settings | Baseline, sorts/scans allowed | Keyed, current settings | Keyed, sorts/scans allowed |
| --- | ---: | ---: | ---: | ---: |
| UPDATE / 100 | 1,216ms | 52ms | **30ms** | 14ms |
| UPSERT / 100 | 1,109ms | 30ms | **17ms** | 13ms |
| DELETE / 100 | 906ms | 25ms | **14ms** | 14ms |
| UPDATE / 1,000 | 10,673ms | 138ms | **141ms** | 147ms |
| UPSERT / 1,000 | 9,553ms | 136ms | **135ms** | 127ms |
| DELETE / 1,000 | 8,959ms | 90ms | **141ms** | 137ms |

Under current settings, keyed HEAD discovery returns only the requested 100/1,000 rows from partition scans, with **zero rejected join pairs**, rather than 32,000 HEADs and 3.2/32 million rejected pairs. Representative 100-row lookup time fell from ~0.9–1.2s to ~0.4–2.1ms; 1,000-row lookup time is ~1.7–2.3ms. Thus the rewrite removes the measured HEAD × batch pathology without changing production planner policy. Allowing sorts/scans also improves the old shape, but still reads almost the entire HEAD population. Two samples, fixed variant order, warm caches and rollback-induced physical/WAL effects limit fine timing comparisons; no global planner-policy change is justified by these measurements alone. Raw JSON and `plan-matrix-summary.json` are retained privately.

### Completed end-to-end comparison and review handoff

The identical four-operation throughput fixture passed after the improvement (`local-tests-20261120_141325`). Same four workers/partitions, history/tombstones ON, 8,000 features per round, one warmup and three measured rounds; encoding and commit included, fixture preparation/seeding excluded. Before refers to the approved checkpoint's earlier repeated run (`local-tests-20261120_132500`). Median features/s:

| Operation | Before / 100 rows | After / 100 rows | Before / 1,000 rows | After / 1,000 rows |
| --- | ---: | ---: | ---: | ---: |
| INSERT | 2,887 | **2,988** | 2,712 | **2,335** |
| UPDATE | 468 | **2,667** (5.7×) | 458 | **2,345** (5.1×) |
| Existing-key UPSERT | 440 | **1,875** (4.3×) | 454 | **1,910** (4.2×) |
| Tombstone DELETE | 588 | **6,190** (10.5×) | 616 | **5,830** (9.5×) |

The severe population-dependent collapse disappeared in this tested range. Measured 100-row UPDATE rounds were 2,671 → 2,667 → 2,549/s rather than 624 → 468 → 343/s; DELETE was 6,190 → 5,884 → 6,291/s rather than 899 → 588 → 447/s. UPSERT 100 still showed a smaller decline (2,037 → 1,875 → 1,832/s); further client/coverage/optimistic-conflict profiling remains appropriate. The 1,000-row INSERT run was **14% lower** than the earlier run despite its unchanged optimistic SQL path. Do not label all performance cases improved or attribute that difference conclusively to unrelated variance: repeated interleaved A/B measurements are needed. No benchmark establishes maximum capacity or behavior beyond the tested population/payload/concurrency range.

Final serial campaign (`local-tests-20261120_141641`) passed **1,831 ordinary JVM tests**, zero failures/errors, 91 skips. All **85 opt-in PostgreSQL cases across 18 isolated suites** passed, including BATCH 8/8, mutation 27/27, session 15/15, writer ordering 8/8, coverage 10/10 and real PLV8 SQL transport. Replacing ordinary opt-in skips with their isolated outcomes yields 1,916 JVM passes and six remaining skips; the separate throughput and plan-matrix fixtures have successful independent runs. The isolated benchmark databases were retained; the server was not restarted or reconfigured. Full JS runtime remains non-green with the existing 14 diff/patch failures out of 23 tests. Focused actual JS mutation-plan/error tests passed 2/2 again, with XML and command/status archived in `naksha-keyed-head-js-results`.

### Beta.55 release verification

The canonical Gradle version generators synchronized `NakshaVersion.CURRENT` and OpenAPI with project beta.55; installed admin runtime also advances to beta.55 and minimum compatible admin stays beta.53. Post-bump forced gates passed in order with Java 25 (`naksha-beta55-final-compile-summary.json`, `naksha-beta55-final-compile-{metadata,jvm-main,jvm-tests,js}.log`), using the same metadata/JVM/JS commands and `--rerun-tasks --no-build-cache --continue --console=plain` flags recorded above.

First post-bump campaign (`local-tests-20261120_144027`): all 85 isolated PostgreSQL cases and focused Node mutation tests passed, while ordinary JVM had one failure because `PgBootstrapLockTest` hardcoded beta.55 as a future installed version. With the release now beta.55, that fixture no longer represented a newer runtime. The test now derives a strictly newer revision from `adminVersion`; failure/recheck/no-install assertions and production validation are unchanged. Independent follow-up review found no blocker in this test-only correction. The subsequent serial ordinary-JVM rerun (`local-tests-20261120_145854`) passed **1,831 tests, zero failures/errors, 91 skips**. Post-bump focused Node XML/command/status are archived in `naksha-beta55-js-results`; the existing 14 diff/patch failures remain unchanged and outside this release task.

Release commit message: **`Optimize batched HEAD discovery and release beta.55`**, with the required sign-off trailer. Both the earlier baseline checkpoint and this improvement are authorized for a normal push to `origin/v3-naksha-33`. NAKSHA-33 is to be updated with the release/results and moved to **In Review**, assigned to **Phuc Mai**, without replacing its original cache-issue description or closing deferred work. No artifact publication, deployment or remote database change is performed by the agent; the user will deploy manually for testing. JS improvements begin only as the subsequent user-directed task.

All final production/test changes passed forced metadata/JVM/JS gates before runtime execution. Independent correctness/security reviews found no confirmed blockers. The user approved the separate improvement for commit/push (`DONE` for this implementation increment), with NAKSHA-33 to remain **In Review** assigned to Phuc Mai after publishing. Version-bump verification is recorded separately below. Remaining limitations include the full compiled Kotlin/JS PLV8 request/subtransaction boundary, stronger reversed multi-key contention coverage, larger-population/network/payload sweeps and the non-interleaved benchmark comparison.

## Verification checkpoint

Testing uses Java 25, commit `19efc3f94` plus the uncommitted implementation. Forced gates pass in order: `compileCommonMainKotlinMetadata`, `jvmMainClasses`, `jvmTestClasses`, then `jsNodeProductionLibraryDistribution compileTestKotlinJs`, each with `--rerun-tasks --no-build-cache --continue --console=plain`. Post-error-contract-fix commands, exit statuses and source status are preserved in `naksha-batch-final-compile-summary.json`; logs are `naksha-batch-final-compile-{metadata,jvm-main,jvm-tests,js}.log` in the private temporary directory. Earlier compilation logs are retained separately.

Targeted prior passes: BATCH 6/6 (`local-tests-20261120_123742`), mutation 27/27 (`local-tests-20261120_123212`), session 15/15 and writer ordering 8/8, plus insert/update/empty-tombstone regressions. Node `PgMutationExecutionJsTest` passed 2/2 again after the error-contract fixes using a private init script to enable the otherwise disabled psql Node task.

The first full batch campaign (`local-tests-20261120_124905`) ran 1,919 ordinary JVM cases: 1,824 passed, seven failed, 88 skipped, zero errors. All **84 opt-in cases across 18 isolated PostgreSQL suites passed**, including BATCH 7/7 with the benchmark. The seven ordinary failures exposed operation-specific error-message and metadata-not-found translation regressions in the shared dispatcher. These were corrected by restoring established messages and translating the failing metadata UPDATE input, without weakening assertions. Independent follow-up review found no blockers in those fixes; post-fix forced gates passed and the final full runtime rerun is in progress. The separate JS runtime run again had 14 failures out of 23 diff/patch tests; focused Node mutation tests passed 2/2 with archived XML in `naksha-batch-js-results-final`.

The post-fix campaign (`local-tests-20261120_130623`) passed all 84 isolated PostgreSQL cases again. Its ordinary JVM run had 1,912 discovered cases, 1,814 passed, ten failed and 88 skipped: all ten failures were HTTP-storage `NoClassDefFoundError` for `HttpStorageProperties$HeaderMap` during concurrent compilation; the previous seven error-contract assertions no longer failed. A throughput compilation attempt also failed on a missing generated JS module under that concurrent Gradle activity. Neither run is treated as green. Fully serial forced metadata/JVM/JS gates subsequently passed (`naksha-throughput-serial-compile-summary.json`); the requested four-operation benchmark now runs without competing tests. A serial ordinary-JVM rerun remains required.

The serial ordinary-JVM rerun (`local-tests-20261120_133308`) subsequently passed: **1,920 discovered, 1,831 passed, zero failures/errors, 89 skipped**, including the disabled opt-in integration/throughput fixtures. It ran after the successful throughput benchmark and before adding the separate plan-profile fixture; all production code matches that successful campaign. The plan-profile fixture has separate compilation/runtime evidence above. JS diff/patch remains non-green; the full compiled Kotlin/JS PLV8 request/subtransaction boundary remains unverified. The implementation is still uncommitted; the measured scaling problem is not considered resolved.

Coverage includes 128 inserts in one execution, shuffled optimistic and classified results, mixed history/no-op behavior, semantic-error prefixes, constraint-error priority, unique-key wait/retry, no-op PURGE row-lock retention, real deadlock retry signalling preserving prior caller SQL, and real PLV8 SPI execution of generated SQL with binary/bigint/structured-state assertions. Test-only `pg_temp.batch_spi_execute_*` functions are session-local proof harnesses, not production mutation wrappers or staging tables.

Independent initial and final follow-up correctness/security reviews found no confirmed blockers; requested no-op locking, shuffled-classified-result and deadlock tests were added and passed. Reviews were static/read-only; the user separately approved the baseline checkpoint and later the beta.55 improvement release. Full **compiled Kotlin/JS request/batch subtransaction behavior inside actual PLV8 remains a coverage gap**: real SPI SQL proof and Node tests of actual JS plan/error handling do not establish that entire path. Existing JS diff/patch runtime failures (14) remain outside this increment and must not be represented as a green JS suite. JS improvements start only after this release/review handoff is complete and the user directs the next task.
