# Tests
Use a dedicated test Postgres database. Test setup can drop Naksha schemas, so do not point these commands at a database with data you need to keep. Replace the example password with your test database password.

## Clear Storage
```bash
# Clear schemas from the dedicated local test database only.
PGPASSWORD='password' \
psql -h localhost -p 5432 -U postgres -c 'DROP SCHEMA IF EXISTS "naksha-hub-admin" CASCADE; DROP SCHEMA IF EXISTS naksha_data_schema CASCADE; DROP SCHEMA IF EXISTS "naksha~admin" CASCADE;'
```

## Run all tests
```bash
# Run all JVM tests against the dedicated local test database.
NAKSHA_TEST_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&ssl=false" \
NAKSHA_SUPPRESS_PERF_TEST=true \
NAKSHA_DEBUG=true \
./gradlew jvmTest
```

The environment variable `NAKSHA_SUPPRESS_PERF_TEST=true` will disable performance tests, remove it if full testing wanted.

## Run a specific test
```bash
# Run the PostgreSQL library tests.
NAKSHA_TEST_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&ssl=false" \
NAKSHA_SUPPRESS_PERF_TEST=true \
NAKSHA_DEBUG=true \
./gradlew :here-naksha-lib-psql:jvmTest
```

For only app-service tests:

```bash
NAKSHA_TEST_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=password&ssl=false" \
NAKSHA_SUPPRESS_PERF_TEST=true \
./gradlew :here-naksha-app-service:jvmTest
```

If a specific individual test is wished, just append `--tests "{full qualified classname}"` to the above `gradlew` call.
