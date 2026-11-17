# Running JVM tests against local PostgreSQL

## What “all JVM tests” means

There are two stages:

1. `./gradlew jvmTest` selects every project's ordinary JVM test task, including app-service API tests. It does not automatically enable opt-in integration suites.
2. The 16 opt-in PostgreSQL suites (75 tests in the 2026-11-17 snapshot) each require their own fresh database and their specific environment variable. They cannot all share the database from stage 1. One separately ignored partitioning test remains ignored.

No separately started Naksha service is needed: the app-service JUnit extension starts and stops it. PostgreSQL must already be running, and port 8080 must be free. The earlier guidance saying a running external Naksha server was required was incorrect.

## Prerequisites and safety

- JDK 25; on macOS, `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`.
- PostgreSQL with the extensions required by Naksha bootstrap, including PostGIS, PLV8 and pgcrypto. Use the supported local Naksha database image/installation, not an arbitrary plain PostgreSQL server. The permission suite also needs role administration, and allocator-boundary tests create/drop their own databases; the designated local test superuser supports these scenarios.
- PostgreSQL client tools (`psql`, `createdb`) on PATH for one-time disposable database creation.
- Obtain the local connection credentials from the user-designated vault. Do not copy passwords into documentation, Git, logs, or command-line Gradle properties. Supply them through your local environment. A JDBC password must be URL-encoded if it contains URI-reserved characters.
- Use a **fresh disposable test database**. Fixtures intentionally change test catalogs, and some integration tests require the admin schema not to exist. Do not drop arbitrary schemas from an existing database or perform bulk cleanup just to get tests green.
- Run only one full campaign at a time; the service uses port 8080 and the psql fixtures are stateful.

## Stage 1: ordinary full-project JVM run

`NAKSHA_TEST_DB_URL` can supply one shared PostgreSQL URL for library, admin and data storage. The per-storage variables used below override this fallback. The storage IDs default to `local_psql_test_storage`, and app-service defaults to `LOCAL_STANDALONE`; `NAKSHA_APP_SERVICE_TEST_CONTEXT=TEST_CONTAINERS` selects its managed container. Always use a dedicated test database because test setup may drop Naksha schemas.

Set PostgreSQL client variables in your shell. The password below is a placeholder, not a default credential:

```bash
export PGHOST=localhost
export PGPORT=5432
export PGUSER=postgres
export PGPASSWORD='<local test password from vault>'
```

Choose a new database name for this campaign, create it, and construct the JDBC URL. This example assumes the password is already URI-safe; otherwise substitute its URL-encoded form in the JDBC query:

```bash
export NAKSHA_LOCAL_TEST_DB="naksha_jvm_$(date +%Y%m%d%H%M%S)"
createdb --template=template0 "$NAKSHA_LOCAL_TEST_DB"

export NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${NAKSHA_LOCAL_TEST_DB}?user=${PGUSER}&password=${PGPASSWORD}&ssl=false"
export NAKSHA_TEST_ADMIN_DB_URL="$NAKSHA_TEST_PSQL_DB_URL"
export NAKSHA_TEST_DATA_DB_URL="$NAKSHA_TEST_PSQL_DB_URL"
export NAKSHA_TEST_STORAGE_ID=local_psql_test_storage
export HUB_ADMIN_STORAGE_ID="$NAKSHA_TEST_STORAGE_ID"
export NAKSHA_APP_SERVICE_TEST_CONTEXT=LOCAL_STANDALONE
```

**Check configuration precedence before running:** app-service's `TestStorageConfig` checks `test_admin_db.url` and `test_data_db.url` in its working-directory/resource/home search before the environment. In particular, existing `~/.config/naksha/test_admin_db.url` or `test_data_db.url` may silently defeat the exports. Ensure any such files target the same test URL, or use the isolated test-home option below. Do not print their full contents into shared logs. `NAKSHA_LOCAL_TEST_CONTEXT` is an obsolete name; the actual selector is `NAKSHA_APP_SERVICE_TEST_CONTEXT`.

`NAKSHA_TEST_PSQL_DB_URL` globally overrides PgStorage connections. For this simple recipe, all three URLs must identify the same disposable database, and the library/Hub storage IDs must agree with its installed storage identity. Do not point the service fixture URLs at another database while retaining the global override. Changing exported IDs does not rewrite an existing database's identity.

Run the tests from the repository root:

```bash
./gradlew jvmTest --rerun --continue --console=plain
```

`--rerun` forces the selected test tasks to execute; it does not force a clean rebuild of every compilation task. `--continue` collects independent task failures. Do not use `clean` as routine setup or suppress failing tests to make the command green.

For a faster functional campaign, optionally export `NAKSHA_SUPPRESS_PERF_TEST=true`; this excludes performance cases and must be disclosed in the results. Leave it unset for the ordinary full campaign, including performance cases. Do not set any of the stage-2 suite-specific database variables for this run.

### Optional: isolate test JVMs from home-directory URL overrides

When you want to preserve your existing home config unchanged, create an empty temporary home and a temporary Gradle init script. This affects test JVMs only, not the Gradle user home or its caches:

```bash
export NAKSHA_TEST_USER_HOME="$(mktemp -d "${TMPDIR:-/tmp}/naksha-jvm-home.XXXXXX")"
cat > "$NAKSHA_TEST_USER_HOME/local-test-home.gradle" <<'GRADLE'
allprojects {
  tasks.withType(Test).configureEach {
    systemProperty('user.home', System.getenv('NAKSHA_TEST_USER_HOME'))
  }
}
GRADLE

./gradlew jvmTest --rerun --continue --console=plain \
  --init-script "$NAKSHA_TEST_USER_HOME/local-test-home.gradle"
```

Working-directory/resource URL overrides still take priority if present. The checked-in test configuration and bundled auth keys remain available as resources. Passing `-Duser.home=...` to Gradle alone is not the same as explicitly configuring its forked test JVMs. No configuration file or database needs to be deleted by this procedure.

### Useful narrower runs

```bash
# Everything except app-service; still includes psql database tests.
./gradlew jvmTest -x :here-naksha-app-service:jvmTest --rerun --continue --console=plain

# Non-database JVM campaign used for the handler/hub repair checkpoint.
./gradlew jvmTest -x :here-naksha-app-service:jvmTest \
  -x :here-naksha-lib-psql:jvmTest --rerun --continue --console=plain

# Only service/API tests; JUnit starts the service itself.
./gradlew :here-naksha-app-service:jvmTest --rerun --console=plain

# A focused API bootstrap check (uses the same environment).
./gradlew :here-naksha-app-service:jvmTest \
  --tests 'com.here.naksha.app.service.StorageApiTest' --rerun --console=plain
```

If using the isolated-home option, append its `--init-script` argument to narrower service/API runs too.

## Stage 2: opt-in fresh-database PostgreSQL suites

Run each row separately with a newly created database. Set both `NAKSHA_TEST_PSQL_DB_URL` and the row's suite variable to that database's exact JDBC URL. Never export every suite variable to one database and run all classes together: several suites explicitly assert that `naksha~admin` does not yet exist.

| Suite variable suffix | Test class (`naksha.psql.` prefix) |
| --- | --- |
| `BOOTSTRAP` | `PgBootstrapTest` |
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

For example, to run the PLV8 suite (same local client variables as above):

```bash
suite_db="naksha_plv8_$(date +%Y%m%d%H%M%S)"
createdb --template=template0 "$suite_db"
suite_url="jdbc:postgresql://${PGHOST}:${PGPORT}/${suite_db}?user=${PGUSER}&password=${PGPASSWORD}&ssl=false"

NAKSHA_TEST_PSQL_DB_URL="$suite_url" \
NAKSHA_TEST_PLV8_DB_URL="$suite_url" \
./gradlew :here-naksha-lib-psql:jvmTest \
  --tests 'naksha.psql.PgPlv8MaintenanceTest' --rerun --console=plain
```

Repeat with each row's class and `NAKSHA_TEST_<SUFFIX>_DB_URL` variable. These tests are executed by Gradle/JUnit; `createdb` is just the disposable setup. Provisioning all 16 fresh databases and creating separate Test tasks per suite is not currently automated by the ordinary `jvmTest` task. A single native Gradle lifecycle task for both stages would require additional build wiring, not more exports. Record these results separately from stage 1, replacing skipped cases rather than double-counting them.

## Reports and diagnosing failure

- HTML: `<module>/build/reports/tests/jvmTest/index.html`.
- JUnit XML: `<module>/build/test-results/jvmTest/TEST-*.xml`.
- Later focused runs overwrite that module's ordinary report files. Preserve reports before stage 2 if you need a combined campaign summary.
- Start with the first initialization exception, not the final cascade of HTTP connection failures. Look for wrong credentials/URL, storage-ID mismatch, unavailable extensions, home-file overrides or an occupied port.
- An HTTP assertion after successful startup is usually a real application/test failure, not a missing export. During the 2026-11-17 investigation, an isolated local `StorageApiTest` started Naksha automatically and ran all 15 cases: 14 passed; duplicate-storage creation returned HTTP 500 because `FeatureExists` was not mapped to HTTP 409. This is a genuine open HTTP error-mapping issue.
- Existing database contents can matter; prefer another fresh disposable database over schema-clearing recipes. Keep created databases for inspection until cleanup is explicitly agreed.

No real credentials belong in this document. The previous inline-password/schema-dropping recipe has been replaced with environment placeholders and isolated setup.

## Verified full JVM campaign — 2026-11-17

This is the failure baseline before the subsequently approved service-recovery increment. The six underlying issues have since been fixed with code changes and the specifically agreed test/fixture updates; the final complete ordinary JVM campaign is now green, as recorded below. Keep these baseline counts separate from its results.

Executed the root `jvmTest --rerun --continue --console=plain` against fresh local database `naksha_local_jvm_59fea8f072e7`, with all three URLs aligned, both storage IDs set to `local_psql_test_storage`, LOCAL_STANDALONE service context and an empty test-only user home. Credentials were loaded privately from the local vault; no external Naksha server or schema-clearing step was needed. No performance-suppression flag was added. The database was left in place for inspection.

**Results: 1,756 discovered tests; 1,665 passed, 12 failed, 79 skipped; zero test errors.** All failures are app-service assertions/fixtures after successful server startup, not Gradle compilation or connection/setup failures. All other modules passed. The service had 444 passed / 12 failed / three skipped; psql had 263 passed / zero failed / 76 skipped. Of the 79 total skips, 75 are the opt-in PostgreSQL tests not enabled by this ordinary run, one is the ignored partitioning test, and three are service skips. The isolated 75-test integration campaign from the earlier checkpoint was not rerun or combined into these fresh totals.

| Failure group | Tests | Observed result |
| --- | ---: | --- |
| `StorageApiTest.tc0002_testCreateDuplicateStorage`, `SpaceApiTest.tc0201_testCreateDuplicateSpace`, `EventHandlerApiTest.tc0101_testDuplicateEventHandler` | 3 | Expected HTTP 409, received 500: `FeatureExists` is unmapped in the HTTP error adapter. |
| `DeleteFeatureTest.tc0900_testDeleteFeatures`, `tc0901_testDeleteNonExistingFeatures`; `WriteFeaturesAtomicityTest.tc_1103_partialDeleteShouldSucceed`; `ValDryRunTest.tc3002_testValDryRunUnsupportedOperation` | 4 | Received 500 with unmapped `IllegalState`; expected 200 for DELETE cases and 501 for validation dry-run. Trace the underlying IllegalState as well as fixing the adapter; mapping alone may not restore the expected operation. |
| `ReadFeaturesByRadiusPostTest`, property-selection cases TC11 and TC12 | 2 | Returned the same named features in another array order, producing positional ID/tag/property mismatches. Verify ordering requirements before changing production sorting or weakening assertions. |
| `IterateFeaturesTest.tc1104_testIterateInTwoPagesWithPropSelection` | 1 | Missing fixture `ReadFeatures/Iterate/TC1104_testIterateInTwoPagesWithPropSelection/all_features.json`. |
| `CollectionModificationTest.tc1400_testActivityLogPerformanceAfterCollectionMod` | 1 | Expected 200, received 400: collection partition/storage settings are immutable after creation. Reconcile this test with the agreed immutable-creation contract. |
| `PatchOnViewWithHttpStorageTest.tc01_testPatchForFeatureOnlyInBase` | 1 | Expected 200, received 400: atomic UPSERT requires a version/UUID. Check the view-overlay PATCH operation's atomic expectation and predecessor handling. |

Local log (temporary, not committed): `/private/var/folders/6k/vbn28hc915n4pqjrbd2xt0hr0000gn/T/opencode/naksha-local-jvm-naksha_local_jvm_59fea8f072e7.log`. App-service HTML/XML reports are at the normal module paths above. These failures explain why the correctly configured root command still exits nonzero; further exports will not fix them.

## Service-recovery verification — 2026-11-17

After the user-approved fixes, the same root Gradle procedure succeeded against fresh local database `naksha_local_jvm_60d03b783d4e`: **1,776 total / 1,697 passed / zero failures or errors / 79 skipped**. App-service has **471 passed / three skipped**, including the previous failures, new HTTP-conflict mapping tests, strict unordered-feature projection assertions, and actual API/PostgreSQL PATCH collision/rollback/reread and explicit-base-UUID checks. The ordinary psql task has **264 passed / 76 skipped**, including execution-time rejection of an unresolved late-bound catalog. All other modules pass. No performance-suppression flag was added.

The 79 ordinary-run skips consist of 75 separately enabled integration cases, one ignored partitioning case and three service skips. The subsequent fresh-database campaign passed **all 75 integration tests across all 16 suites**, with no failures or skips. Replacing those 75 ordinary-run skips gives final unique JVM totals of **1,772 passed / zero failures or errors / four skipped** (1,776 total). The four remaining skips are the three service cases and the ignored partitioning test. JS was not rerun. The user approved committing the fixes and documentation as `Fix service JVM regressions and guard view PATCH copy-on-write`; their scope and remaining follow-ups are recorded in [the service-recovery increment](NAKSHA-33.md#service-jvm-recovery-increment--2026-11-17).

Local evidence under `/private/var/folders/6k/vbn28hc915n4pqjrbd2xt0hr0000gn/T/opencode/`: final log `naksha-local-jvm-naksha_local_jvm_60d03b783d4e.log`, archived per-module XML plus `summary.json` in `naksha-service-fixes-results-ab37ac3c/`, and all isolated integration reports/logs plus their `summary.json` in `naksha-review-results-bacfe0cf/`. Keep the archived ordinary results when inspecting later focused-run reports. Created databases were retained, not bulk-dropped.
