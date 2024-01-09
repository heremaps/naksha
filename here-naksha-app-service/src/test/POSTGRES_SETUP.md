## Testing Naksha service from Postgres perspective

Tests of Naksha service rely on Postgres database. \
Currently, there are three options to provide Postgres to test environment
- connecting tests to standalone Postgres instance
- utilizing TestContainers library that initializes container with Postgres image
- using mock implementations responsible for actions on storage

Below, one can find instructions on how to use each of these approaches.

### Local standalone Postgres instance \[default\]

This is the default and most basic approach. It assumes that there's a Postgres DB running in our local environment.\
That is also the way our tests are executed on GH pipeline.

#### Prerequisites:
- Postgres with PostGIS extension running on localhost
- Postgres instance must be publishing **5432** port
- Environment variable `NAKSHA_LOCAL_TEST_CONTEXT` must be null, empty or set to `LOCAL_STANDALONE`

#### Notes:
- The schema used by tests: `naksha_data_schema`
- This schema will dropped before test suite starts
- Related class: `com.here.naksha.app.init.context.LocalTestContext`

### Test Containers

[TestContainers]() is a library that can manage containers based on supplied image as part of test suite.\
In tests of Naksha service, it relies on custom container image (which is simply `postgis/postgis` with some additonal extensions installed).

#### Prerequisites:
- `Docker` or some equivalent (we suggest `Podman` for those without `Docker` license) available on host machine
- Environment variable `NAKSHA_LOCAL_TEST_CONTEXT` must be set to `TEST_CONTAINERS`
- Port `5432` must be available (it is possible for TestContainers to utilize any other port but the majority of tests that were written before supporting this approach rely on strict port mapping - that is likely to change in the future)

#### Notes:
- Related classes: 
  - `com.here.naksha.app.init.context.ContainerTestContext`
  - `com.here.naksha.app.init.PostgresContainer`

#### Known issues (observed locally on Mac with M1/2 chips): 
- When running all tests of Naksha Service and utilizing `postgis/postgis` image (or images based on it - like our custom one), one may notice segmentation fault related errors. This will be addressed and fixed.
  - The issue above does not occur when running smaller portions of tests, hence should not be problematic on "regular development activities"

### Mock

This is deprecated approach that is discouraged to be utilized. Nevertheless, it might come handy in situations when setting up database is not feasible. \
Mocked suite relies on set of classes that try to mimic behavior of actual storage (it was developed with Postgres in mind). Because our functionalities set grows each day and Mock was mainly on initial stage of the project, some advanced functionalities might be mssing - use this one with caution.

#### Prerequisites:
- Environment variable `NAKSHA_LOCAL_TEST_CONTEXT` must be set to `MOCK`

#### Notes:
- Related classes:
  - `com.here.naksha.lib.hub.mock.NakshaHubMock`
  - `com.here.naksha.app.init.context.MockTestContext`
- Config file: `mock-config.json`
