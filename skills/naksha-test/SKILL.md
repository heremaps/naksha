---
name: naksha-test
description: Use ONLY when asked to run tests for the Naksha project. Do NOT use for other projects or general testing questions.
---

# General
Database-backed tests need a separate disposable PostgreSQL instance or database. `./gradlew cleanAndTest` runs the shared JVM and Node checks and requires `NAKSHA_TEST_DB_URL`; see `README.md#testing` for the setup and `docs/testing.md` for isolated suites. Focused library or app-service tasks still support their own Testcontainers setup. When unclear, ask which database to use; do not clear existing schemas or restart the database server.

For ticket-specific test notes and non-sensitive summaries, follow `naksha-common`: use `ai/<ticket-id>/` relative to the code checkout. Create the ticket subfolder as needed. Keep sensitive logs and profiles private.

# Mandatory verification order

Run the following gates from the repository root, in order, before claiming a successful test campaign. Use Java 25 (`export JAVA_HOME=$(/usr/libexec/java_home -v 25)` on macOS), record the tested commit and any uncommitted changes, and preserve each gate's command, exit status and log separately. Database setup is not needed for the compilation gates.

`jvmTest` and `jvmMainClasses` do not cover standalone common-metadata compilation. Platform compilation can succeed because it sees the `actual` implementations while the common `expect` declarations still fail metadata compilation and IntelliJ analysis. Neither passing JVM tests nor a warm Gradle build proves the multiplatform build is valid.

## Gate 1: compile common metadata

Compile common metadata across all projects, not just the changed module:

```bash
./gradlew compileCommonMainKotlinMetadata --rerun-tasks --no-build-cache --continue --console=plain
```

This must detect invalid common declarations such as an `expect` class that does not declare inherited abstract interface members. If this gate fails, report the actual compiler error before proceeding. Do not dismiss it as an IDE/cache issue merely because a platform build passes.

## Gate 2: compile production and test code

After metadata compilation succeeds, force fresh JVM production compilation:

```bash
./gradlew jvmMainClasses --rerun-tasks --no-build-cache --continue --console=plain
```

Then force fresh JVM test-source compilation without executing tests:

```bash
./gradlew jvmTestClasses --rerun-tasks --no-build-cache --continue --console=plain
```

Also compile/link JS production artifacts and compile JS test sources across the configured JS projects, even when JS runtime tests are disabled:

```bash
./gradlew jsNodeProductionLibraryDistribution compileTestKotlinJs --rerun-tasks --no-build-cache --continue --console=plain
```

This catches JS-incompatible references in shared sources, including JVM-only `java`, `System` or `Thread` usage in `commonTest`. `jvmCompile` is not a task in this repository; use `jvmMainClasses` to compile both JVM Kotlin and Java production sources.

`--rerun-tasks --no-build-cache` is required for these compilation gates: `--rerun` on a test invocation only forces the selected test tasks to execute and does not establish that their compilation dependencies were rebuilt. Do not substitute `clean`, cached outputs or an earlier compilation result for these gates.

## Gate 3: execute tests

Only after the compilation gates pass, configure the requested local database and execute the test commands below. A focused test request may narrow runtime test selection, but must not silently omit the metadata/compilation checks. Any explicitly agreed narrower verification must be disclosed in the results.

Stop between gates on failure. `--continue` collects independent failures within a gate; it does not authorize treating a failed gate as successful. If further test execution is explicitly requested for diagnosis despite a compilation failure, label it supplemental verification and retain the original blocker in the final report. Do not fix code, suppress tests, weaken assertions or disable a failing task just to obtain a successful result without user authorization.

# Environment Variables
All environment variables contain some placeholders that need to be replaced:

- `{host}`: The host of the PostgresQL cluster. If not given any other instructions, assume `localhost`.
- `{port}`: Needs to be replaced by you with the port at which the database is listening. If not given any other instructions, assume `5432`.
- `{user}`: Needs to be replaced by you with the user. If not given any other instructions, assume `postgres`.
- `{password}`: Needs to be replaced by you with the password. If not given any other instructions, assume `password`.

You can test the connection to the database. If you detect that the connection to the database fails due to wrong credentials or hostname, ask the user for host, port, user, and password _(whatever is needed)_. Use defaults for any value not provided. Tell the user the defaults.

## Library tests (here-naksha-lib-psql)
Only needs one variable. If not set, Docker auto-starts:

```bash
export NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
```

Alternatively, set `NAKSHA_TEST_DB_URL` as a shared URL for library, admin and data storage. The specific `NAKSHA_TEST_PSQL_DB_URL`, `NAKSHA_TEST_ADMIN_DB_URL`, and `NAKSHA_TEST_DATA_DB_URL` variables override it for their respective storage. `NAKSHA_TEST_STORAGE_ID` defaults to `local_psql_test_storage`, and Gradle defaults `HUB_ADMIN_STORAGE_ID` to the same value. Use a dedicated test database: test setup may drop Naksha schemas.

## Server tests (here-naksha-app-service)
The app-service JUnit extension starts and stops Naksha itself in LOCAL_STANDALONE mode; do not start a second server. Port 8080 must be free. Run when service tests or the full project JVM suite are requested. For the shared-database recipe, keep the library/admin storage IDs identical and all three URLs identical:

`LOCAL_STANDALONE` is the default context. Set `NAKSHA_APP_SERVICE_TEST_CONTEXT=TEST_CONTAINERS` to use the app-service managed container instead.

```bash
export NAKSHA_APP_SERVICE_TEST_CONTEXT=LOCAL_STANDALONE
export NAKSHA_TEST_STORAGE_ID=local_psql_test_storage
export HUB_ADMIN_STORAGE_ID=local_psql_test_storage
export NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
export NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
export NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
```

Important: `TestStorageConfig` checks URL files in the working directory / `~/.config/naksha/` before environment variables. Existing `test_admin_db.url` or `test_data_db.url` can therefore override exports. Verify their target without printing credentials. `NAKSHA_TEST_PSQL_DB_URL` also globally overrides PgStorage connections, so do not combine it with service fixture URLs pointing elsewhere. Database storage identity persists in `naksha~admin`; an old database with a different storage ID is not fixed by exporting a new ID. Use a fresh database instead of deleting someone else's schemas.

# Commands

These are Gate 3 execution commands, not substitutes for Gates 1 and 2.

## All non-service JVM tests:
Includes base/auth and the remaining modules as well as model/psql/jbon/geo. Set the local database override to avoid the psql library's container fallback:

```bash
./gradlew jvmTest -x :here-naksha-app-service:jvmTest --rerun --no-build-cache --continue --console=plain
```

## All ordinary JVM tests (includes the self-starting service tests):
Set the local database variables above and use Java 25. `--rerun` forces test execution; `--continue` collects independent task failures. A failing test is not necessarily a setup error: inspect the XML's first cause before changing configuration.

```bash
./gradlew jvmTest --rerun --no-build-cache --continue --console=plain
```

This does not enable the opt-in psql integration tests. Each suite requires a fresh, separate database and matching `NAKSHA_TEST_PSQL_DB_URL` plus its suite-specific URL variable. The independent-client bootstrap regression uses `NAKSHA_TEST_BOOTSTRAP_LOCK_DB_URL` and must run separately from `PgBootstrapTest`. Never export every suite variable to the same database and run all of them together. Follow the isolated-suite recipe in `docs/testing.md`. One explicitly ignored partitioning test also remains ignored. Performance tests run unless `NAKSHA_SUPPRESS_PERF_TEST=true`; disclose this exclusion if used.

## All library tests (JS):
```bash
./gradlew jsNodeTest --rerun --no-build-cache --continue --console=plain
```

Check runtime test results and distinguish them from JS compilation. The default Node run excludes the exact cases listed in `gradle/node-test-quarantine.txt`; use `-PincludeKnownFailingNodeTests=true` to reproduce them. Report quarantined or skipped cases separately from passing cases.

## Server tests
Run when requested explicitly or as part of the full JVM suite. JUnit starts the service; only the PostgreSQL instance must already be running:

```bash
./gradlew :here-naksha-app-service:jvmTest --rerun --no-build-cache --continue --console=plain
```

# Reporting verification

- Report metadata compilation, JVM production compilation, JVM test-source compilation, JS production/test-source compilation and runtime tests as separate results. Never summarize them all as successful if any requested gate failed or did not run.
- State the exact commands and JDK/toolchain used. Distinguish compiler failures, failed assertions, infrastructure/resource failures and intentionally skipped/disabled tests. A known or pre-existing failure is still a failure, not a pass.
- Include performance-test exclusions, service-test exclusions and opt-in integration coverage explicitly. Preserve per-run XML reports before later runs overwrite them. When opt-in cases pass in separate runs, replace their earlier skipped status rather than counting them twice; preserve unsuccessful attempts and rerun outcomes.
- Do not infer standalone metadata, JS or clean compilation success from passing JVM tests. If the task name is invalid, report a task-selection failure rather than a source-compilation error.

# Common Issues
- Kotlintest discovery errors: Inspect the first discovery/initialization cause and the compilation-gate logs. Do not use `clean` as routine setup or assume a cache problem without evidence.
- Docker not available: Configure `NAKSHA_TEST_PSQL_DB_URL` for the local PostgreSQL instance instead of the library's container fallback.
- Port conflicts: The Docker container uses host port 15432. If this port is in use, the container will fail to start
- Service tests fail with ConnectException: Inspect the earlier test-context/bootstrap failure or a port-8080 conflict. These tests normally start their own server; absence of a separately started server is not itself the cause.
