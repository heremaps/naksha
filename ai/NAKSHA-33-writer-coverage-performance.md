# Writer Coverage Performance

Measured on 2026-11-05. Baseline was captured before the coverage implementation edits. No commit was created. This report is separate from the main workboard.

## Environment

- Client: Mac14,10, Apple M2 Pro, 12 logical CPUs, 16 GiB RAM; macOS 26.7.1 (25G241).
- JVM: Homebrew OpenJDK 25.0.5.0.2, 64-bit Server VM. Gradle wrapper 9.6.1; Kotlin 2.4.10. No additional benchmark JVM flags were supplied.
- Database endpoint: localhost:5432, Docker container `naksha_pg18`, image `ghcr.io/naksha-oss/naksha-postgres:v18.6`.
- Server: PostgreSQL 18.6, aarch64 Linux, GCC 11.5.0. Container has no explicit Docker Memory/NanoCpus/CpuCount limit; Docker VM allocation was not recorded.
- Server settings observed during follow-up: `shared_buffers=4GB`, `synchronous_commit=on`, `fsync=on`, `max_connections=1024`. Settings were not independently snapshotted at baseline.
- Credentials came from the local vault and are deliberately omitted. Each implementation run used a newly created disposable database. Test URLs specified `ssl=false`; storage initialization logged its normalized URL with `ssl=true`, so SSL behavior was not independently controlled.

## Method

`PgWriterCoverageTest.coveredCreateBenchmark` creates separate history-ON collections with partition counts 1, 7 and 16, in that order, using shift 41. Each already-provisioned collection receives 40 sequential single-feature CREATE requests with distinct IDs. The first 10 are warmup; the remaining 30 are measured using `System.nanoTime`. The timer includes feature/request construction, session allocation, version allocation, writer execution and commit, including the transaction-log write. Collection creation is outside the timer. Results include logging and normal Gradle test instrumentation; this is not JMH.

Samples are sorted. Reported median is zero-based sample 15 (upper middle of 30), and P95 is sample 28 (nearest rank 29 of 30). Samples are not pooled across JVM runs.

Progress is measured separately with a successful CREATE transaction held open. A two-thread executor runs a direct JDBC `SELECT count(*)` reader and a same-collection CREATE of a disjoint ID. Both must finish before the held transaction rolls back. The independent writer's transaction/version and reader connection are allocated before holding the transaction, isolating collection progress from allocator/setup behavior. Baseline timeouts were reader 800 ms and writer lock/statement 800/1500 ms. Follow-up timeouts were raised to reader 3 s and writer lock/statement 3/5 s after a heavily contended run; latency sampling was unchanged. These probes establish progress, not concurrent commit throughput or a latency equivalence at identical deadlines.

## Measurements

All latencies are milliseconds. Baseline test timestamp: 01:19:38 UTC. Final follow-up test timestamp: 01:52:16 UTC.

| Partitions | Baseline Median | Baseline P95 | Final Median | Final P95 |
| --- | ---: | ---: | ---: | ---: |
| 1 | 208.614834 | 470.797333 | 9.588500 | 15.266792 |
| 7 | 265.458334 | 390.610792 | 9.255250 | 13.307583 |
| 16 | 351.982541 | 407.482708 | 14.411125 | 20.855792 |

Before: reader=false and disjoint_writer=false for all three partition counts. Final: reader=true and disjoint_writer=true for all three while the held transaction remained open.

An immediately preceding successful full run produced medians 11.527625/13.548000/10.717708 and P95 18.500458/28.302291/19.275125 for 1/7/16. Other intermediate runs varied more under concurrent builds. There is only one completed pre-edit baseline run; these numbers are local observations, not controlled causal estimates, production projections, sustained-throughput results or confidence intervals. Concurrent agents changed other mutation/query code between baseline and follow-up, and shared build outputs occasionally produced transient missing-class failures. Fresh reruns passed. Timing also includes metadata and transaction-log work, not only the feature's partition probe.

## Correctness And Lock Scope

The writer acquires catalog guards and the combined collection guard set in deterministic sorted order before preparation or row locking. Catalog guards remain shared except for catalog-exclusive creation/drop work. Ordinary covered writes use shared collection advisory locks. Catalog UPSERT reuses an existing internal collections table under shared guards and verifies its physical ownership; it no longer unnecessarily reserves that table exclusively. A missing physical table is reserved exclusively before creation.

Coverage locks `ONLY` the registered root in ACCESS SHARE mode. It checks the required HEAD and finite child plus their expected fn leaves, not all retained ranges. Materialized candidate CTEs filter relation OIDs before PostgreSQL catalog deparsers execute: without this optimization fence, the planner evaluated `pg_get_expr` on an unrelated relation being dropped and blocked mixed-catalog progress. Live `pg_stat_activity` sampling confirmed the relation-lock wait before the fence; the unchanged mixed-catalog regression passes afterward.

Shared collection/catalog advisory guards exclude cooperative maintenance/drop until transaction completion. Missing coverage rolls back the probe subtransaction to release its shared/physical locks, reacquires the combined guard set in deterministic scoped modes, rechecks coverage, and invokes full maintenance only if still missing. Locks retained from an earlier request cannot be released by that probe rollback; attempted upgrades use a nonblocking retry error rather than deadlocking. Explicit maintenance still performs full topology/index/storage validation and repair. Noncooperating manual child DDL is not serialized by the shared advisory protocol; this is not a full integrity audit on every write.

## Verification

- Final full `PgWriterOrderingRegressionTest`: 8 passed, 0 failed, unchanged tests, including mixed-catalog progress and opposite-order combined guards.
- Final full `PgWriterCoverageTest`: 9 passed, 0 failed. Includes UPDATE/UPSERT/DELETE/PURGE beyond initial finite coverage, leaf repair, drop exclusion, progress, actual tableoid routing at every fn slot boundary for 1/7/256 including clipped HEAD-1, and explicit maintenance integrity rejection.
- The retained-history regression holds ACCESS EXCLUSIVE on an unneeded retained leaf and confirms required coverage completes without touching it.
- PURGE originally failed in `PgDatabase.rows_purge` because changing HEAD nv in place violated the inherited HEAD check. The mutation agent supplied the archive fix in its owned file; this agent did not edit PgDatabase. Final PURGE routing/archive regression passes against that fix.
- Existing `PgDatabasePartmanTest`: 8 passed in the preceding implementation verification; not rerun as part of the final combined writer command.
- `git diff --check` passes.

## Reproduction

Use a fresh disposable database and set `NAKSHA_TEST_PSQL_DB_URL`, `NAKSHA_TEST_COVERAGE_DB_URL` and `NAKSHA_TEST_WRITER_DB_URL` to the same JDBC URL. Never point these suites at a valuable database; the shared test bootstrap cleans test schemas. Set `JAVA_HOME` to Java 25. Set `NAKSHA_TEST_COVERAGE_ASSERT_PROGRESS=true` for the fixed implementation; omit it when measuring the old implementation's expected blocked progress.

```bash
./gradlew :here-naksha-lib-psql:jvmTest \
  --tests naksha.psql.PgWriterOrderingRegressionTest \
  --tests naksha.psql.PgWriterCoverageTest --console=plain
```

The actual final command also used an external Gradle init script solely to isolate XML, HTML and binary test reports from concurrent agents. Final local artifacts: `$TMPDIR/opencode/coverage-full3.log`, `$TMPDIR/opencode/coverage-results/TEST-naksha.psql.PgWriterCoverageTest.xml`, and `$TMPDIR/opencode/coverage-results/TEST-naksha.psql.PgWriterOrderingRegressionTest.xml`. Reports can contain connection URLs emitted by existing storage logging; treat local artifacts as sensitive. The report itself contains no credentials.
