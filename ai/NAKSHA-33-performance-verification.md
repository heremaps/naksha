# NAKSHA-33 Performance Verification

## Hook-Final Tree Verification

Latest verification on 2026-11-05 is **green for the requested hook-final scope**, superseding the earlier green tree's counts below. The owner added FeatureTuple.onTupleLoaded while preserving the public JVM tuple field, invoked it from TupleCache/JVM heapcache and integrated response-local successor metadata through PgSession/PgReader. Verification made no source edits in this rerun and no commit. The three FeatureTupleLoadHookTest regressions passed: heap loads preserve the cached tuple, cache-boundary hooks respect the requested slice, and tuple remains a public JVM field. PgSessionMutationTest passed all 15 cases; mutation correctness passed all 26 in the shared full run.

| Hook-Final Run | Tests | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Model | 154 | 154 | 0 | 0 | 0 |
| Jbon | 128 | 128 | 0 | 0 | 0 |
| Geo | 22 | 22 | 0 | 0 | 0 |
| Psql, excluding only Partman | 315 | 300 | 0 | 0 | 15 |
| Full JVM total | 619 | 604 | 0 | 0 | 15 |
| Fresh isolated Partman | 8 | 8 | 0 | 0 | 0 |
| Hook-final combined executions | 627 | 612 | 0 | 0 | 15 |

Used the exact full JVM command documented below with --rerun-tasks, the same temporary init excluding only Partman, and all six new writer/coverage/query/session/mutation/empty-tombstone URL gates equal to the fresh PSQL URL. Coverage progress assertions were true, query baseline mode unset and old performance cases suppressed. Full BUILD SUCCESSFUL in 4m 30s. Full XML was copied/counts captured before the isolated Partman command overwrote psql results. Fresh isolated Partman used its documented command without the init exclusion and passed in 22s. Forced production JS compilation and production-library distributions for model/jbon/geo/psql passed in 29s using the exact task list below with --rerun-tasks. All Gradle invocations were sequential.

Hook-final session microbenchmarks, same fixture/sampling definitions below: lazy/eager reference medians 1.372875/123.089666 ms (512 rows, 4,096 title characters, 20 measured samples); fn/version versus added-nv projection medians 0.446333/0.527666 ms (40 measured samples). Coverage medians/p95 at 1/7/16 partitions were 9.757000/12.267208, 11.737000/17.958542 and 10.786584/18.599167 ms, respectively; reader=true and disjoint_writer=true with the transaction held for all three counts. These are local observations, not controlled causal performance comparisons or production-capacity claims.

Per user scope, the four performance baselines and dedicated Insert/UpdateUpsert/ReadFlow gates were not rerun after the hook change. Their earlier green results below remain valid evidence for that earlier tree, not fresh measurements of the hook-final tree. The 15 full-run skips remain explicitly recorded; JS runtime tests were not run.

Databases naksha_perf_verify_20261105_hook_full and naksha_perf_verify_20261105_hook_partman were created fresh and dropped. Final pg_database query for the entire naksha_perf_verify_20261105_% prefix returned zero rows. The full database drop succeeded but PostgreSQL warned it could not remove old directory base/1924206 because it was not empty and useless files may remain. Database catalog cleanup is verified; physical residual-file cleanup is not verified, and verification did not manually alter PostgreSQL's data directory. No infrastructure restart was needed in this hook-final rerun.

Local sensitive artifacts use performance-verification-hook-final-{full,partman,js}.log/.json and performance-verification-hook-final-{full,partman}-xml/{module}/TEST-*.xml under the same temporary opencode directory documented below. Credentials were supplied silently from the local vault. git diff --check passed. Earlier failures remain resolved history; no production edits or commits by this rerun.

## Previous Green Tree

Final verification on 2026-11-05 of the uncommitted workspace: **the requested final verification is green**, with skips and scope limitations recorded below. No production source was edited and no commit was made. During the final retry, two reviewed stale duplicate-CREATE assertions were changed from CONFLICT to FEATURE_EXISTS in InsertFeatureTest and PsqlErrorMappingTest, matching the restored documented contract. All other source fixes were supplied by their owners. Prior failed runs remain below as resolved historical evidence, not active blockers.

## Final Green Results

All Gradle invocations ran sequentially. Every database-backed run used a newly created disposable database; XML was copied and counted before the next run. The full command and gates are the same as those documented below, excluding only Partman. The six new writer/coverage/query/session/mutation/empty-tombstone URLs all matched NAKSHA_TEST_PSQL_DB_URL; coverage progress was required and query pruning assertions were active. The mutation fixture now uses Platform.getTestStorageId(), and all 26 mutation cases passed in the full shared-database run. No mutation exclusion or gate suppression was needed.

| Final Run | Tests | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Full model | 151 | 151 | 0 | 0 | 0 |
| Full jbon | 128 | 128 | 0 | 0 | 0 |
| Full geo | 22 | 22 | 0 | 0 | 0 |
| Full psql, Partman excluded | 315 | 300 | 0 | 0 | 15 |
| Full total | 616 | 601 | 0 | 0 | 15 |
| Isolated Partman | 8 | 8 | 0 | 0 | 0 |
| Isolated Insert | 1 | 1 | 0 | 0 | 0 |
| Isolated UpdateUpsert | 1 | 1 | 0 | 0 | 0 |
| Isolated ReadFlow | 1 | 1 | 0 | 0 | 0 |
| Performance Insert | 1 | 1 | 0 | 0 | 0 |
| Performance Upsert | 1 | 1 | 0 | 0 | 0 |
| Performance grouped Insert | 1 | 1 | 0 | 0 | 0 |
| Performance grouped Upsert | 1 | 1 | 0 | 0 | 0 |
| Final combined executions | 631 | 616 | 0 | 0 | 15 |

Full BUILD SUCCESSFUL in 4m 21s. Full-suite evidence includes PgDatabaseMutationCorrectnessTest 26/26, PgWriterCoverageTest 10/10, PgSessionMutationTest 15/15, PgWriterOrderingRegressionTest 8/8, PgQueryHistoryPartitionTest 4/4, PgEmptyTombstoneReadTest 2/2, PgMutationAuditTest 4/4, AllowTombstoneTest 4/4 and PgDatabaseIndexTest 3/3. This exercises the supplied dropped-root, duplicate error-priority, shared-identity, index-mock, cold pending-history and no-op-identity fixes without production changes by verification.

The 15 full skips are the same cases listed in the historical section. Insert/UpdateUpsert/ReadFlow and the four suppressed performance cases were subsequently run with their gates enabled, all passing. The other seven gated integration cases and PartitioningTest remain unexecuted in this campaign. The final combined row counts the requested executions, including seven cases skipped in full and then exercised separately; it is not a unique-case count and does not erase the skip evidence.

Forced compileKotlinJs and jsNodeProductionLibraryDistribution for all four libraries, using the exact task list below with --rerun-tasks, passed in 23s. JS runtime tests and PLV8 writer-entrypoint throughput were not measured.

### Final Performance Measurements

Same exact workload and timing definitions as the Four Performance Cases section: one fresh database/JVM per case, small topology template, 1,200 features, 12 batches, four workers, history/deleted storage ON; UPSERT updates committed seeded rows non-atomically. Suppression was false. These replace the earlier local numbers as the final-code baseline, not a controlled speedup comparison or production-throughput claim.

| Final Case | Features | Batches | Seconds | Features/s | Median Batch ms | P95 Batch ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Insert | 1200 | 12 | 1.032467875 | 1162.263765 | 300.407542 | 469.610542 |
| Upsert, seeded existing | 1200 | 12 | 1.044066166 | 1149.352444 | 323.347375 | 424.348333 |
| Grouped Insert | 1200 | 12 | 1.085482083 | 1105.499592 | 320.487458 | 506.475167 |
| Grouped Upsert, seeded existing | 1200 | 12 | 0.937086708 | 1280.564530 | 332.667105 | 377.109667 |

Final full-run covered-CREATE latency, n=30 measured after 10 warmups, same sampling method as below:

| Partitions | Median ms | P95 ms | Held-Transaction Reader | Disjoint Writer |
| --- | ---: | ---: | --- | --- |
| 1 | 10.101708 | 12.765792 | true | true |
| 7 | 10.791458 | 14.400792 | true | true |
| 16 | 13.443750 | 20.205666 | true | true |

Final query benchmark, same 768-row fixtures, one warmup/five measured EXPLAINs and predicate definitions below:

| Partitions | Predicate | Leaves | Planning ms | Execution ms | Shared Hits | Shared Reads |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 7 | 0 | 1 | 0.093 | 0.008 | 2 | 0 |
| 7 | 1 | 2 | 0.142 | 0.013 | 4 | 0 |
| 7 | 2 | 3 | 0.181 | 0.031 | 8 | 0 |
| 16 | 0 | 1 | 0.109 | 0.009 | 2 | 0 |
| 16 | 1 | 2 | 0.130 | 0.012 | 4 | 0 |
| 16 | 2 | 3 | 0.163 | 0.026 | 8 | 0 |
| 256 | 0 | 1 | 0.120 | 0.007 | 2 | 0 |
| 256 | 1 | 2 | 0.215 | 0.022 | 4 | 0 |
| 256 | 2 | 3 | 0.262 | 0.031 | 6 | 0 |

Final session fixture: lazy/eager reference medians 1.459084/120.552791 ms (512 rows, 4,096 title characters, 20 measured samples); fn/version versus added-nv projection medians 0.425125/0.490333 ms (40 measured samples). These remain local current-code path comparisons, not historical deployment comparisons.

### Resolved History And Cleanup

The historical tombstone/coverage and Insert contract failures are resolved: final AllowTombstone 4/4 and isolated Insert 1/1 pass. Mutation identity mismatch is resolved with Platform.getTestStorageId(), proven by 26 full-run passes. Updated index mocks pass 3/3. The first final attempt had only two stale duplicate-CREATE expectations (616 tests, 599 passed, 2 failed, 15 skipped); after reviewing both call paths against the restored FEATURE_EXISTS contract, verification changed only those assertion constants and reran all four full suites. That failed attempt is retained separately, not counted in the final green totals.

During cleanup of that first final attempt PostgreSQL disconnected and the container stopped. Starting naksha_pg18 and waiting for recovery restored access; its database naksha_perf_verify_20261105_final_full was then removed before the green run. No infrastructure restart was needed during the subsequent green run or its isolated gates. Do not treat either campaign as a controlled performance comparison across recovery.

Final databases used prefix naksha_perf_verify_20261105_green_ and suffixes full, partman, insert, update, read and the four perf_method-name stages. PostgreSQL truncated the two grouped-case database identifiers to 63 bytes (ending shouldInsertGroupedByPar / shouldUpsertGroupedByPar); they remained distinct and both were successfully dropped. Final pg_database query for the entire naksha_perf_verify_20261105_% prefix returned zero rows, including earlier campaigns. No unrelated databases were deleted.

Final artifacts in the temporary opencode directory use performance-verification-final-green-{stage}.log/.json and performance-verification-final-green-{stage}-xml/{module}/TEST-*.xml. The first final attempt uses performance-verification-final-full.* and its XML directory; original historical campaign artifacts retain their original names. Logs/XML remain sensitive local artifacts. git diff --check passed. No commits and no ticket-state promotion.

## Historical Results (Resolved)

Everything from here through the original limitations records the earlier campaign before owner fixes. It does not describe current active failures; the final green results and measurements above supersede it. Original verification made no source edits; the later two assertion changes are explicitly recorded above.

JUnit XML totals, captured and copied before each following Gradle run could overwrite them:

| Run | Tests | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Full model | 151 | 151 | 0 | 0 | 0 |
| Full jbon | 128 | 128 | 0 | 0 | 0 |
| Full geo | 22 | 22 | 0 | 0 | 0 |
| Full psql, Partman excluded | 308 | 264 | 29 | 0 | 15 |
| Full total | 609 | 565 | 29 | 0 | 15 |
| Isolated Partman | 8 | 8 | 0 | 0 | 0 |
| Isolated Insert | 1 | 0 | 1 | 0 | 0 |
| Isolated UpdateUpsert | 1 | 1 | 0 | 0 | 0 |
| Isolated ReadFlow | 1 | 1 | 0 | 0 | 0 |
| Isolated mutation correctness | 25 | 25 | 0 | 0 | 0 |
| Performance Insert | 1 | 1 | 0 | 0 | 0 |
| Performance Upsert | 1 | 1 | 0 | 0 | 0 |
| Performance grouped Insert | 1 | 1 | 0 | 0 | 0 |
| Performance grouped Upsert | 1 | 1 | 0 | 0 | 0 |
| Isolated AllowTombstone reproduction | 4 | 2 | 2 | 0 | 0 |
| All executions, including repeated cases | 653 | 606 | 32 | 0 | 15 |

The final row counts executions, not unique tests. Isolated passes do not erase full-run failures. Forced production JS compilation and production-library distributions passed for model, jbon, geo and psql; JS tests were not run.

New full-run coverage actually enabled: PgWriterOrderingRegressionTest 8/8, PgWriterCoverageTest 9/9 with progress assertions, PgQueryHistoryPartitionTest 4/4, PgSessionMutationTest 10/10, PgEmptyTombstoneReadTest 2/2, and PgMutationAuditTest 4/4. Mutation correctness required the additional isolated run described below.

The 15 full-run skips were four suppressed Plv8PerfTest cases, PartitioningTest.createCollectionWithPartitions, and one gated case each in PgBootstrapTest, PgCatalogCreationTest, PgCreateFlowTest, PgDatabaseDropTest, PgDatabaseInsertTest, PgDatabaseUpdateUpsertTest, PgMaintenanceConcurrencyTest, PgMaintenancePermissionsTest, PgPlv8MaintenanceTest and PgReadFlowTest. Insert, UpdateUpsert, ReadFlow and all four performance cases were subsequently executed, not skipped. The other gated suites remain unverified in this campaign. The ignored non-test helper in Plv8PerfTest is not an additional XML case.

## Historical Failures (Resolved)

Failures were reported promptly during execution; production was left untouched.

| Classification | Evidence | Outcome |
| --- | --- | --- |
| Reproducible production-path regression | AllowTombstoneTest.deletingAlreadyDeletedCollectionShouldSucceed and recreatedCollectionShouldBeVisible fail with IllegalState, `Unregistered partition root naksha_psql_test.t$_2047361539` and `naksha_psql_test.tombstone_recreate_col_test` | Both fail in the full run and again alone on a fresh database. Repeated DELETE and CREATE after physical collection deletion are not accepted as working. Fixtures/expectations were not changed. |
| Error-contract discrepancy, needs owner decision/fix | PgDatabaseInsertTest.insertsValidatesRecreatesAndLeavesRollbackToCaller reports two nested assertion failures: expected FeatureExists, actual Conflict | One failing XML test. Workboard's root CREATE contract explicitly promises FEATURE_EXISTS for live HEAD. Therefore this is not assumed to be a stale expectation. Other assertions in its two failing subchecks after that assertion were not reached. |
| Full-suite fixture incompatibility | All 25 PgDatabaseMutationCorrectnessTest cases expect storage ID mutation_correctness, but the full-suite database already has local_psql_test_storage | Fixture constructs its own PgStorage against the URL and checks installed identity; merely setting its gate is insufficient for suite coexistence. All 25 pass on a fresh isolated database with both mutation and PSQL URLs equal. No isolation fix was made. |
| Stale mock setup, not evidence of live index failure | Two PgDatabaseIndexTest cases throw NPE at cursor.get during PgPartitions.lockCollection's upgrade probe | The mock returns cursors from execute but does not stub fetch/get for the new `AS upgrading` query; it also expects the former six-statement create/drop sequence. Requires a deliberate mock update, not a production change or weaker live assertions. Left unchanged under report-only ownership. |

Relevant sources: `src/commonTest/kotlin/naksha/psql/AllowTombstoneTest.kt` lines 94-162; `src/jvmTest/kotlin/naksha/psql/PgDatabaseInsertTest.kt` lines 134-157 and 218-236; `src/jvmTest/kotlin/naksha/psql/PgDatabaseMutationCorrectnessTest.kt` lines 484-495; `src/jvmTest/kotlin/naksha/psql/PgDatabaseIndexTest.kt` lines 16-36 and 55-79; `src/commonMain/kotlin/naksha/psql/PgPartitions.kt` lines 272-296. Paths are relative to here-naksha-lib-psql.

## Environment And Isolation

- Client: Mac14,10, 12 logical CPUs, 16 GiB RAM; macOS 26.7.1 (25G241). Java Homebrew OpenJDK 25.0.5.0.2. Gradle wrapper 9.6.1 / Kotlin 2.4.10. No extra benchmark JVM flags.
- Local PostgreSQL 18.6, aarch64 Linux / GCC 11.5.0, Docker container naksha_pg18. Observed after recovery: shared_buffers=4GB, synchronous_commit=on, fsync=on, max_connections=1024. Docker VM resource allocation and competing machine load were not controlled.
- Credentials were parsed silently from the first/local section of vault/naksha.credentials.md, never written into this report. All test URLs used the local endpoint with ssl=false; existing storage normalization/logging may differ, so transport encryption was not independently verified.
- Every Gradle invocation was sequential. Each database-backed isolated run created its own fresh database, captured XML, then dropped that database. No application/service suites were run.
- Full run used database naksha_perf_verify_20261105_full. Isolated suffixes were partman, insert, update, read, mutation, perf_shouldInsertManyFeatures, perf_shouldUpsertManyFeatures, perf_shouldInsertGroupedByPartition, perf_shouldUpsertGroupedByPartition and tombstone under the same prefix.
- After the full JVM build finished, PostgreSQL became unreachable and cleanup blocked. Docker reported Desktop stopped/unable to start. A start attempt reported already running; `docker desktop restart`, `docker start naksha_pg18`, and waiting for PostgreSQL recovery restored access. The full database was then removed before continuing. Full-run XML had already been captured. This infrastructure interruption is not attributed to a particular test.
- Final pg_database query for `naksha_perf_verify_20261105_%` returned zero rows. Only this campaign's databases were removed. All local logs/XML should be treated as sensitive because existing test logging can emit connection details.

## Commands And Gates

Temporary init `/var/folders/6k/vbn28hc915n4pqjrbd2xt0hr0000gn/T/opencode/naksha-final-tests.init.gradle.kts` excludes only naksha.psql.PgDatabasePartmanTest from psql test execution, not other suites or test source compilation.

Full command:

```bash
./gradlew -I /var/folders/6k/vbn28hc915n4pqjrbd2xt0hr0000gn/T/opencode/naksha-final-tests.init.gradle.kts :here-naksha-lib-model:jvmTest :here-naksha-lib-jbon:jvmTest :here-naksha-lib-geo:jvmTest :here-naksha-lib-psql:jvmTest --rerun-tasks --console=plain
```

Full-run environment set NAKSHA_TEST_PSQL_DB_URL, NAKSHA_TEST_WRITER_DB_URL, NAKSHA_TEST_COVERAGE_DB_URL, NAKSHA_TEST_QUERY_DB_URL, NAKSHA_TEST_SESSION_DB_URL, NAKSHA_TEST_MUTATION_DB_URL and NAKSHA_TEST_EMPTY_TOMBSTONE_DB_URL to the same fresh database URL; NAKSHA_TEST_COVERAGE_ASSERT_PROGRESS=true, NAKSHA_SUPPRESS_PERF_TEST=true. NAKSHA_QUERY_BASELINE was unset, so pruning assertions were active. Other NAKSHA_TEST_*DB_URL overrides were unset. BUILD FAILED in 5m 7s.

Each isolated command used this exact form without the exclusion init:

```bash
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.PgDatabasePartmanTest --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.PgDatabaseInsertTest --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.PgDatabaseUpdateUpsertTest --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.PgReadFlowTest --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.PgDatabaseMutationCorrectnessTest --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.Plv8PerfTest.shouldInsertManyFeatures --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.Plv8PerfTest.shouldUpsertManyFeatures --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.Plv8PerfTest.shouldInsertGroupedByPartition --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.Plv8PerfTest.shouldUpsertGroupedByPartition --console=plain
./gradlew :here-naksha-lib-psql:jvmTest --tests naksha.psql.AllowTombstoneTest --console=plain
```

PSQL URL was set for every isolated run. Insert also set NAKSHA_TEST_INSERT_DB_URL, UpdateUpsert set NAKSHA_TEST_UPDATE_DB_URL, ReadFlow set NAKSHA_TEST_READ_DB_URL, and mutation set NAKSHA_TEST_MUTATION_DB_URL, each equal to its own fresh PSQL URL. Partman used the PSQL gate only and a database without a persisted registry. Performance cases set NAKSHA_SUPPRESS_PERF_TEST=false; other isolated runs set it true. Unrelated URL gates were cleared.

Final forced JS command (BUILD SUCCESSFUL in 28s):

```bash
./gradlew :here-naksha-lib-model:compileKotlinJs :here-naksha-lib-model:jsNodeProductionLibraryDistribution :here-naksha-lib-jbon:compileKotlinJs :here-naksha-lib-jbon:jsNodeProductionLibraryDistribution :here-naksha-lib-geo:compileKotlinJs :here-naksha-lib-geo:jsNodeProductionLibraryDistribution :here-naksha-lib-psql:compileKotlinJs :here-naksha-lib-psql:jsNodeProductionLibraryDistribution --rerun-tasks --console=plain
```

An earlier identical JS task list without --rerun-tasks also passed; only the forced final run is relied on for recompilation. git diff --check passed.

## Four Performance Cases

These are new local baselines, one JVM run per case, not before/after speedup measurements. Despite the Plv8PerfTest name, the timed code calls PgTestBase.executeWrite through JVM PgStorage/JDBC; this is not a measurement of a PLV8 SQL entrypoint. Production Kotlin/JS compilation is verified separately, not runtime writer parity.

Workload from Plv8PerfTest: JSON_TOPOLOGY_SMALL deep copies of small_topology.json, four performance partitions, history ON and deleted storage ON, four worker threads, overload factor 1, three batches per worker, 12 timed batches and 1,200 features per case. Ordinary batches contain 100 features; grouped batches route IDs using Id.partitionIndex(4), then spread each partition across three batches, so grouped sizes can differ from 100. IDs are distinct within each case. UPSERT is explicitly non-atomic and updates existing features seeded and committed before timing; it is not an insert-only UPSERT benchmark. Request/template construction, collection creation and UPSERT seeding are outside the timed interval. Timed work includes worker submission/completion and executeWrite session/write/commit. Each worker future is awaited, responses must be SuccessResponse, and failed workers cannot silently produce a successful measurement.

No dedicated measured-workload warmup or repeated independent trials. Throughput is total completed features divided by the common wall-clock interval, not summed worker durations. Batch median averages the two middle sorted durations; p95 is nearest rank ceil(12*0.95), the maximum of these 12 samples. Batch latency begins when the worker starts execution, not when it enters the executor queue. This is a short test-harness observation, not JMH, sustained load, a confidence interval, or a production-capacity claim.

| Case | Features | Batches | Seconds | Features/s | Median Batch ms | P95 Batch ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Insert | 1200 | 12 | 0.950933250 | 1261.918226 | 275.201979 | 405.158000 |
| Upsert, seeded existing | 1200 | 12 | 0.967229500 | 1240.656949 | 315.578729 | 392.632833 |
| Grouped Insert | 1200 | 12 | 0.962331292 | 1246.971817 | 314.123042 | 401.469875 |
| Grouped Upsert, seeded existing | 1200 | 12 | 0.953054292 | 1259.109801 | 348.779312 | 387.214125 |

## Writer Coverage Baselines

Prior agent baseline/follow-up comes from [writer report](NAKSHA-33-writer-coverage-performance.md), not a newly rerun historical checkout. Current numbers were collected by the full JVM run. Each history-ON collection at shift 41 gets 40 sequential single-feature CREATE requests; first 10 warm up, last 30 measured. Timer includes construction, session/version allocation, write and commit/transaction-log work; excludes collection creation. Median is sorted sample 15, p95 sample 28, both zero-based. Progress probes are separate from latency sampling.

| Partitions | Agent Before Median ms | Agent Before P95 ms | Agent Final Median ms | Agent Final P95 ms | This Run Median ms | This Run P95 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 208.614834 | 470.797333 | 9.588500 | 15.266792 | 11.344667 | 18.546459 |
| 7 | 265.458334 | 390.610792 | 9.255250 | 13.307583 | 12.931792 | 15.733042 |
| 16 | 351.982541 | 407.482708 | 14.411125 | 20.855792 | 12.425292 | 22.604917 |

This run required and observed reader=true and disjoint_writer=true with held_transaction=true at all three counts. Agent baseline had both false; baseline/follow-up deadlines differed and other agents changed code between measurements. These establish local progress and latency observations, not concurrent commit throughput or a controlled causal speedup.

## Query And Session Baselines

Query historical numbers are recorded in PgQueryHistoryPartitionTest KDoc, not reconstructed from an old checkout. It reports equality fanout before/after for N=7/16/256: leaves 7/16/256 to 1/1/1; planning 0.386/0.599/913.813 ms to 0.183/0.150/0.174 ms; execution 0.041/0.046/2.773 ms to 0.018/0.012/0.011 ms; shared hits 8/17/257 to 2 each. These are source-reported local historical observations, with zero shared reads, not independent historical remeasurement.

Current query fixture: 768 features per collection (512 named, 256 numeric IDs), N=7/16/256, ANALYZE, EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON), one warmup plus five measured plans. Planning/execution are separate medians; leaves/hits/reads come from the first measured plan. Predicate 0 is named-42 equality, 1 is named-42/named-43 membership, 2 is named-42/256/128 membership; 256 is absent but still contributes a candidate leaf. Physical routing and expected pruning assertions passed.

| Partitions | Predicate | Leaves | Planning ms | Execution ms | Shared Hits | Shared Reads |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 7 | 0 | 1 | 0.112 | 0.008 | 2 | 0 |
| 7 | 1 | 2 | 0.126 | 0.011 | 4 | 0 |
| 7 | 2 | 3 | 0.231 | 0.035 | 8 | 0 |
| 16 | 0 | 1 | 0.096 | 0.008 | 2 | 0 |
| 16 | 1 | 2 | 0.174 | 0.015 | 4 | 0 |
| 16 | 2 | 3 | 0.213 | 0.036 | 8 | 0 |
| 256 | 0 | 1 | 0.135 | 0.010 | 2 | 0 |
| 256 | 1 | 2 | 0.238 | 0.026 | 4 | 0 |
| 256 | 2 | 3 | 0.213 | 0.031 | 6 | 0 |

Session source benchmark uses 512 rows with 4,096 title characters each and a held connection/warmed metadata. Alternates lazy/eager order, five warmups then 20 samples: lazy upper-middle median 1.436083 ms, eager 122.388625 ms. Eager explicitly clears tuple references and loads payloads; the output records zero versus one payload SELECT, and one extra reference bigint per row. Separate alternating projection experiment, five warmups then 40 samples: fn/version baseline median 0.421750 ms, adding nv 0.498333 ms. These are different controlled code paths in the current fixture, not a historical deployment comparison or production query latency estimate.

## Artifacts And Limits

Local artifacts live under `/var/folders/6k/vbn28hc915n4pqjrbd2xt0hr0000gn/T/opencode/`: performance-verification-{stage}.log, performance-verification-{stage}.json, and performance-verification-{stage}-xml/{module}/TEST-*.xml. Stages are full, partman, insert, update, read, mutation, the four perf_method-name stages, tombstone and js (JS has no test XML). The temporary Ruby runner creates databases, supplies gates without printing URLs, runs Gradle synchronously, copies XML, calculates totals and drops its databases. Raw artifacts are local and uncommitted.

At that historical point there had been no full green rerun or fixture/mock repairs, and tombstone lifecycle, Insert error-contract and suite compatibility were blockers. All are resolved in the final green campaign above. Continuing limitations: no JS runtime tests, production dataset, sustained or externally concurrent load, server/service tests, or claims that deferred cron/cache-isolation/legacy-removal work is complete. Database recovery adds another reason not to compare these short timings as a controlled performance experiment.
