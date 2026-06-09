---
name: naksha-test
description: Use ONLY when asked to run tests for the Naksha project. Do NOT use for other projects or general testing questions.
---

# General
Most tests require a database. If the tests are executed without environment variables, they will start docker containers. When unclear, ask the user if he wants to run the tests using automatically created docker containers, or if he prefers to run the tests against his own, possible local, PostgresQL test database.

# Environment Variables
All environment variables contain some placeholders that need to be replaced:

- `{host}`: The host of the PostgresQL cluster. If not given any other instructions, assume `localhost`.
- `{port}`: Needs to be replaced by you with the port at which the database is listening. If not given any other instructions, assume `5432`.
- `{user}`: Needs to be replaced by you with the user. If not given any other instructions, assume `postgres`.
- `{password}`: Needs to be replaced by you with the password. If not given any other instructions, assume `password`.

You can test the connection to the database. If you detect that the connection to the database fails due to wrong credentials or hostname, ask the user for host, port, user, and password _(whatever needed)_. What he does not provide, use defaults. Tell the user the defaults.

## Library tests (here-naksha-lib-psql)
Only needs one variable. If not set, Docker auto-starts:

```bash
export NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
```

## Server tests (here-naksha-app-service)
Needs all variables. These tests require a running Naksha server and will fail without one. Skip unless explicitly asked:

```bash
export NAKSHA_APP_SERVICE_TEST_CONTEXT=LOCAL_STANDALONE
export NAKSHA_TEST_STORAGE_ID=local_psql_test_storage
export HUB_ADMIN_STORAGE_ID=local_psql_test_storage
export NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
export NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
export NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://{host}:{port}/postgres?user={user}&password={password}&ssl=false"
```

# Commands

## All library tests (JVM):
Docker auto-starts if no env vars are set:

```bash
./gradlew :here-naksha-lib-model:jvmTest :here-naksha-lib-psql:jvmTest :here-naksha-lib-jbon:jvmTest :here-naksha-lib-geo:jvmTest
```

## All JVM tests:
Docker auto-starts if no env vars are set:

```bash
./gradlew jvmTest
```

## All library tests (JS):
```bash
./gradlew :here-naksha-lib-model:jsTest :here-naksha-lib-jbon:jsTest :here-naksha-lib-geo:jsTest
```

## Server tests
Only run if user explicitly asks. Requires a running Naksha server:

```bash
./gradlew :here-naksha-app-service:jvmTest
```

# Common Issues
- Kotlintest discovery errors: If `here-naksha-lib-psql:jvmTest` fails with test discovery errors, try `./gradlew clean` first
- Docker not available: The psql tests require Docker. If Docker isn't running, set `NAKSHA_TEST_PSQL_DB_URL` to an external Postgres instance
- Port conflicts: The Docker container uses host port 15432. If this port is in use, the container will fail to start
- Server tests fail with ConnectException: This is expected when no Naksha server is running. Skip these tests unless the server is available.
