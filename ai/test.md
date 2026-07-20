# Tests
Dear AI, this is the code you should execute to run the tests.

```bash
# Clear local storage storage
PGPASSWORD='XprclPDUTMsWpHvvtusuVCNDWsJZPVTP' \
NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
psql -h localhost -p 5432 -U postgres -c 'DROP SCHEMA IF EXISTS "naksha-hub-admin" CASCADE; DROP SCHEMA IF EXISTS naksha_data_schema CASCADE; DROP SCHEMA IF EXISTS "naksha~admin" CASCADE;'

# Run app-service tests
PGPASSWORD='XprclPDUTMsWpHvvtusuVCNDWsJZPVTP' \
NAKSHA_TEST_ADMIN_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_PSQL_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
NAKSHA_TEST_DATA_DB_URL="jdbc:postgresql://localhost:5432/postgres?user=postgres&password=$PGPASSWORD&ssl=false" \
./gradlew :here-naksha-app-service:jvmTest --tests "com.here.naksha.app.service.ReadFeaturesByTileTest"
```

Please fix the `ReadFeaturesByTileTest` in the `here-naksha-app-service` module. Before you change the code, tell the user what the error is and how you plan to fix it. The user will provide you with approval or instructions what to consider.
