---
name: naksha-test
description: Use ONLY when asked to run tests for the Naksha project. Do NOT use for other projects or general testing questions.
---

# General
Database-backed tests need either a local PostgreSQL instance or their supported container setup. The psql library can start a container when no master is configured, but app-service defaults to an existing local database. When unclear, ask which database to use. Prefer a fresh disposable local test database; do not clear existing schemas or restart the database server. See `ai/test.md` for the full JVM procedure and the isolated integration suites.

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

## All non-service JVM tests:
Includes base/auth and the remaining modules as well as model/psql/jbon/geo. Set the local database override to avoid the psql library's container fallback:

```bash
./gradlew jvmTest -x :here-naksha-app-service:jvmTest --rerun --continue --console=plain
```

## All ordinary JVM tests (includes the self-starting service tests):
Set the local database variables above and use Java 25. `--rerun` forces test execution; `--continue` collects independent task failures. A failing test is not necessarily a setup error: inspect the XML's first cause before changing configuration.

```bash
./gradlew jvmTest --rerun --continue --console=plain
```

This does not enable the 75 opt-in psql integration tests. Their 16 suites require fresh, separate databases and matching `NAKSHA_TEST_PSQL_DB_URL` plus suite-specific URL variables. Never export every suite variable to the same database and run all of them together. Follow the isolated-suite recipe in `ai/test.md`. One explicitly ignored partitioning test also remains ignored. Performance tests run unless `NAKSHA_SUPPRESS_PERF_TEST=true`; disclose this exclusion if used.

## All library tests (JS):
```bash
./gradlew :here-naksha-lib-model:jsTest :here-naksha-lib-jbon:jsTest :here-naksha-lib-geo:jsTest
```

## Server tests
Run when requested explicitly or as part of the full JVM suite. JUnit starts the service; only the PostgreSQL instance must already be running:

```bash
./gradlew :here-naksha-app-service:jvmTest
```

# Common Issues
- Kotlintest discovery errors: If `here-naksha-lib-psql:jvmTest` fails with test discovery errors, try `./gradlew clean` first
- Docker not available: Configure `NAKSHA_TEST_PSQL_DB_URL` for the local PostgreSQL instance instead of the library's container fallback.
- Port conflicts: The Docker container uses host port 15432. If this port is in use, the container will fail to start
- Service tests fail with ConnectException: Inspect the earlier test-context/bootstrap failure or a port-8080 conflict. These tests normally start their own server; absence of a separately started server is not itself the cause.
