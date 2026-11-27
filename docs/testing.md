# Advanced Naksha testing

Use the [README testing setup](../README.md#testing) for the ordinary JVM and Node checks. This page covers focused opt-in runs. Use disposable databases: these tests can create, modify, and drop database fixtures. Do not use a database you need to keep.

## Isolated PostgreSQL suites

Create a **fresh separate database for each suite** using the PostgreSQL 18.6 test image or an equivalently configured instance. Export its JDBC URL both as `NAKSHA_TEST_PSQL_DB_URL` and as the table's `NAKSHA_TEST_<SUITE>_DB_URL`. Do not export multiple suite URLs pointing at one database; some tests require an empty database or check initialization and locking directly. Disable maintenance-job registration for these non-cron runs.

For example, after creating the disposable database, put its JDBC URL in `SUITE_DB_URL` and run:

```bash
NAKSHA_TEST_PSQL_DB_URL="$SUITE_DB_URL" \
NAKSHA_TEST_PLV8_DB_URL="$SUITE_DB_URL" \
NAKSHA_TEST_MAINTENANCE_JOBS=false \
./gradlew :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgPlv8MaintenanceTest' --rerun --console=plain
```

| `<SUITE>` | Test class (under `naksha.psql`) |
| --- | --- |
| `BOOTSTRAP` | `PgBootstrapTest` |
| `BOOTSTRAP_LOCK` | `PgBootstrapConcurrencyTest` |
| `BATCH` | `PgBatchMutationTest` |
| `CATALOG` | `PgCatalogCreationTest` |
| `CREATE` | `PgCreateFlowTest` |
| `DROP` | `PgDatabaseDropTest` |
| `INSERT` | `PgDatabaseInsertTest` |
| `UPDATE` | `PgDatabaseUpdateUpsertTest` |
| `MUTATION` | `PgDatabaseMutationCorrectnessTest` |
| `SESSION` | `PgSessionMutationTest` |
| `EMPTY_TOMBSTONE` | `PgEmptyTombstoneReadTest` |
| `READ` | `PgReadFlowTest` |
| `QUERY` | `PgQueryHistoryPartitionTest` |
| `WRITER` | `PgWriterOrderingRegressionTest` |
| `COVERAGE` | `PgWriterCoverageTest` |
| `CONCURRENCY` | `PgMaintenanceConcurrencyTest` |
| `PERMISSIONS` | `PgMaintenancePermissionsTest` |
| `PLV8` | `PgPlv8MaintenanceTest` |

### Cron integration

Run `naksha.psql.PgCronIntegrationTest` separately with `NAKSHA_TEST_CRON_CONTROL_URL` set to the JDBC URL of the designated cron control database on a dedicated test cluster. The cluster must preload `pg_cron`, allow the test role to create databases and schedule jobs, and allow the job workers to connect. Leave `NAKSHA_TEST_PSQL_DB_URL`, `naksha.test.psql.db.url` and the maintenance-job opt-out unset. This suite creates its own databases and runs real workers; do not use a shared service cluster for it.

## Benchmarks

Run benchmarks separately from test and build jobs. Select the corresponding class with `:here-naksha-lib-psql:jvmTest --tests 'naksha.psql.<class>' --rerun`.

| Test | Setup |
| --- | --- |
| `TupleCodecCpuTest` | `NAKSHA_TEST_CODEC_CPU=true`; no database needed. |
| `PgMutationThroughputTest` | Set both `NAKSHA_TEST_THROUGHPUT_DB_URL` and `NAKSHA_TEST_PSQL_DB_URL` to a fresh disposable database. |
| `PgMutationPlanProfileTest` | Use the same two URL variables for a separate fresh database, and set `NAKSHA_TEST_MUTATION_PROFILE_DIR` to a private output directory. |

For CPU profiling, `NAKSHA_TEST_CPU_PROFILE_DIR` enables JFR output in a private directory. Keep credentials, recordings, and captured database plans out of Git and shared logs. `NAKSHA_SUPPRESS_PERF_TEST=true` disables only tests explicitly marked with that switch.
