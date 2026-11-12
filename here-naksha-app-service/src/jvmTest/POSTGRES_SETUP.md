## Testing Naksha service from Postgres perspective

Tests of Naksha service rely on Postgres database. \
Currently, there are two options to provision Postgres in test environment
- connecting tests to standalone Postgres instance ([described in detail below](#local-standalone-postgres-instance-default))
- utilizing TestContainers library that initializes container with Postgres image ([described in detail below](#test-containers))


The app-service tests use `NAKSHA_APP_SERVICE_TEST_CONTEXT` to choose how Postgres is provided. If the variable is unset, tests use a standalone Postgres instance.

| `NAKSHA_APP_SERVICE_TEST_CONTEXT` | Chosen option |
| - | - |
| undefined / empty | Local standalone Postgres instance |
| `LOCAL_STANDALONE` | Local standalone Postgres instance |
| `TEST_CONTAINERS` | Container spawned by Testcontainers |
| Unknown value | Local standalone Postgres instance |

For tests that use a standalone database, the shared URL variable `NAKSHA_TEST_DB_URL` can configure
the connection for `here-naksha-lib-psql` as well as both admin and data storage tests in
`here-naksha-app-service`. The specific variables `NAKSHA_TEST_PSQL_DB_URL`,
`NAKSHA_TEST_ADMIN_DB_URL`, and `NAKSHA_TEST_DATA_DB_URL` can override the shared URL when a test
needs a separate database. When running both suites against the same database, their admin catalog
IDs must match: `NAKSHA_TEST_STORAGE_ID` defaults to `local_psql_test_storage`, and the Gradle test
tasks default `HUB_ADMIN_STORAGE_ID` to the same value, so neither has to be set. Ensure the URL points to a dedicated test database: the tests may drop Naksha schemas during
setup.

For app-service tests, per-storage URLs in `~/.config/naksha/test_admin_db.url` and
`~/.config/naksha/test_data_db.url` take precedence over environment variables.

Running tests (or any other `gradle` task) that triggers JUnit pipeline "happens as usual":

| Command | Behavior |
| - | - |
| `gradle test` | will run gradle tests expecting local standalone Postgres instance |
| `NAKSHA_APP_SERVICE_TEST_CONTEXT=TEST_CONTAINERS gradle test` | will run gradle test in Testcontainers mode |
| `NAKSHA_APP_SERVICE_TEST_CONTEXT=LOCAL_STANDALONE gradle test` | will run gradle tests expecting local standalone Postgres instance |

The same applies to other gradle tasks one might want to use that depend on `test` task (like `shadowJar`). See [the main project README](../../../README.md) to get familiar with all options.

### Local standalone Postgres instance \[default\]

This is the default and most basic approach. It assumes that there's a Postgres DB running in our local environment.\
The CI pipeline may use either this option or Testcontainers; see the workflow configuration for the selected mode.

#### Prerequisites:
- Postgres with PostGIS extension running on localhost
- By default, Postgres is expected on localhost port **5432**; use `NAKSHA_TEST_DB_URL` to specify another host or port
- Environment variable `NAKSHA_APP_SERVICE_TEST_CONTEXT` can be unset or set to `LOCAL_STANDALONE`

#### Notes:
- The schema used by tests: `naksha_data_schema`
- This schema will be dropped before the test suite starts
- When running tests with local standalone instance, tests don't expect any extensions enabled (see [test-config.json](../jvmMain/resources/test-config.json)
- Related class: `com.here.naksha.app.init.context.LocalTestContext`

### Test Containers

[TestContainers](https://java.testcontainers.org/) is a library that can manage containers based on supplied image as part of test suite.\
In tests of Naksha service, it relies on custom container image (which is simply `postgis/postgis` with some additonal extensions installed).

#### Prerequisites:
- `Docker` or some equivalent (we suggest `Podman` for those without `Docker` license) available on host machine
- Environment variable `NAKSHA_APP_SERVICE_TEST_CONTEXT` must be set to `TEST_CONTAINERS`
- Port `5432` must be available (it is possible for TestContainers to utilize any other port but the majority of tests that were written before supporting this approach rely on strict port mapping - that is likely to change in the future)
- To build image locally (ie when you don't have access to the registry to pull it), run:
  ```
  docker build --no-cache -t <IMAGE_ID> .
  ```
  if you want to use the expected image id, you can source image configuration entries and combine them
  ```
  source psql_container/naksha_psql_image.conf # this will affect your env vars
  IMAGE_ID=$CONTAINER_REPOSITORY/$IMAGE_NAMESPACE/$IMAGE_NAME:$IMAGE_VERSION
  docker build --no-cache -t "$IMAGE_ID" .
  ```

#### Notes:
- You shouldn't run container manually when using this option, the `TestContainers` library will do that for you. You only need docker/podman/other container engine on your host.
- Related classes: 
  - `com.here.naksha.app.init.context.ContainerTestContext`
  - `com.here.naksha.app.init.PostgresContainer`
- To see how the image is built, see [Dockerfile](psql_container/Dockerfile)
- When running tests with test containers, tests expect `pg_hint_plan` and `pg_stat_statements` extensions enabled (see [test-config-with-extensions.json](../jvmMain/resources/test-config-with-extensions.json). These are taken care of due to image definition.

#### Known issues (observed locally on Mac with M1/2 chips): 
- When running all tests of Naksha Service and utilizing `postgis/postgis` image (or images based on it - like our custom one), one may notice segmentation fault related errors. This will be addressed and fixed.
  - The issue above does not occur when running smaller portions of tests, hence should not be problematic on "regular development activities"
