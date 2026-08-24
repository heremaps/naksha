# Tests
Always first clear the local storage _(please replave `PGPASSWORD` with you own local password)_:

## Clear Storage
```bash
# Clear local storage storage
PGPASSWORD='XprclPDUTMsWpHvvtusuVCNDWsJZPVTP' \
NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
psql -h localhost -p 5432 -U postgres -c 'DROP SCHEMA IF EXISTS "naksha-hub-admin" CASCADE; DROP SCHEMA IF EXISTS naksha_data_schema CASCADE; DROP SCHEMA IF EXISTS "naksha~admin" CASCADE;'
```

## Run all tests
```bash
# Run app-service tests
PGPASSWORD='XprclPDUTMsWpHvvtusuVCNDWsJZPVTP' \
NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_SUPPRESS_PERF_TEST=true \
./gradlew jvmTest
```

The environment variable `NAKSHA_SUPPRESS_PERF_TEST=true` will disable performance tests, remove it if full testing wanted.

## Run a specific test
```bash
# Run app-service tests
PGPASSWORD='XprclPDUTMsWpHvvtusuVCNDWsJZPVTP' \
NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_SUPPRESS_PERF_TEST=true \
./gradlew :here-naksha-lib-psql:jvmTest
```

If a specific individual test is wished, just append `--tests "{full qualified classname}"` to the above `gradlew` call.
