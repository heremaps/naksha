# Tests
Always first clear the local storage _(please replave `PGPASSWORD` with you own local password)_:

```bash
# Clear local storage storage
PGPASSWORD='XprclPDUTMsWpHvvtusuVCNDWsJZPVTP' \
NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
psql -h localhost -p 5432 -U postgres -c 'DROP SCHEMA IF EXISTS "naksha-hub-admin" CASCADE; DROP SCHEMA IF EXISTS naksha_data_schema CASCADE; DROP SCHEMA IF EXISTS "naksha~admin" CASCADE;'
```

The run the test.

```bash
# Run app-service tests
PGPASSWORD='XprclPDUTMsWpHvvtusuVCNDWsJZPVTP' \
NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
./gradlew :here-naksha-app-service:jvmTest
```

If a specific test is wished, just append `--tests "com.here.naksha.app.service.PatchOnViewWithHttpStorageTest"` to the `gradlew` call.
