---
name: naksha-test
description: Use ONLY when asked to run tests for the Naksha project. Do NOT use for other projects or general testing questions.
---

# General
Most tests require Postgres. `here-naksha-lib-psql` starts a Docker container if no database URL is configured. The app-service tests default to a standalone Postgres at localhost; use `TEST_CONTAINERS` to have them start a managed container. When unclear, ask the user which mode they want.

# Environment Variables
All environment variables contain some placeholders that need to be replaced:

- `{host}`: The host of the PostgresQL cluster. If not given any other instructions, assume `localhost`.
- `{port}`: Needs to be replaced by you with the port at which the database is listening. If not given any other instructions, assume `5432`.
- `{user}`: Needs to be replaced by you with the user. If not given any other instructions, assume `postgres`.
- `{password}`: Needs to be replaced by you with the password. If not given any other instructions, assume `password`.

You can test the connection to the database. If you detect that the connection to the database fails due to wrong credentials or hostname, ask the user for host, port, user, and password _(whatever is needed)_. Use defaults for any value not provided. Tell the user the defaults.

## Shared Postgres URL
Set one shared URL when the tests should all use the same standalone Postgres instance:

```bash
export NAKSHA_TEST_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
```

`NAKSHA_TEST_DB_URL` is used by `here-naksha-lib-psql` and as the fallback for admin and data
storage in `here-naksha-app-service`. The specific variables `NAKSHA_TEST_PSQL_DB_URL`,
`NAKSHA_TEST_ADMIN_DB_URL`, and `NAKSHA_TEST_DATA_DB_URL` override it for their respective test
storage when separate databases are needed. `NAKSHA_TEST_STORAGE_ID` defaults to
`local_psql_test_storage`, and the Gradle test tasks default `HUB_ADMIN_STORAGE_ID` to the same value,
so both suites agree on the admin catalog ID without setting them.

Use a dedicated test database: test setup may drop Naksha schemas.
For app-service tests, per-storage URLs in `~/.config/naksha/test_admin_db.url` and
`~/.config/naksha/test_data_db.url` take precedence over environment variables.

## App-service tests (`here-naksha-app-service`)
These tests start a Naksha app instance as part of the test suite; no separately running server is
required. By default, they expect standalone Postgres on localhost, so the context variable is
optional. Set `NAKSHA_APP_SERVICE_TEST_CONTEXT=TEST_CONTAINERS` to use the app-service managed
container instead.

# Commands

## All library tests (JVM):
Docker auto-starts if no env vars are set:

```bash
./gradlew :here-naksha-lib-model:jvmTest :here-naksha-lib-psql:jvmTest :here-naksha-lib-jbon:jvmTest :here-naksha-lib-geo:jvmTest
```

## All JVM tests:
App-service tests start Naksha as part of the suite. They default to standalone Postgres at localhost; set `NAKSHA_TEST_DB_URL` if it is running elsewhere, or explicitly select `TEST_CONTAINERS` for the app-service managed container. `here-naksha-lib-psql` starts its own container when neither URL variable is set.

```bash
./gradlew jvmTest
```

## All library tests (JS):
```bash
./gradlew :here-naksha-lib-model:jsTest :here-naksha-lib-jbon:jsTest :here-naksha-lib-geo:jsTest
```

## App-service tests
The test suite starts its own Naksha app instance and requires standalone Postgres or the `TEST_CONTAINERS` context:

```bash
./gradlew :here-naksha-app-service:jvmTest
```

# Common Issues
- Kotlintest discovery errors: If `here-naksha-lib-psql:jvmTest` fails with test discovery errors, try `./gradlew clean` first
- Connection refused: verify that the standalone Postgres host and port in `NAKSHA_TEST_DB_URL` are reachable from the Gradle process
- Port conflicts: The `here-naksha-lib-psql` Docker container uses host port 15432. If this port is in use, it will fail to start
- App-service tests fail with ConnectException: verify that Postgres is available and that the app-service test context is configured as intended
