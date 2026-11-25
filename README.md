# Naksha

![Naksha wordmark beside a Mercator world map with a blue pin over India](docs/images/naksha.svg)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Naksha** (नक्शा) means “map.” It is a geospatial data service and library for storing, querying, and processing features. It exposes a GeoJSON REST API and a JVM API, supports transactional and versioned PostgreSQL storage, and provides extension points for custom storage, request handling, and data-change processing.

## Build and run

- Use **JDK 25** to build and test. Use the checked-in Gradle Wrapper (`./gradlew` or `gradlew.bat`); a separate Gradle installation is not needed. The wrapper currently pins **Gradle 9.6.1** and Gradle provisions Node for Kotlin/JS.
- To run the service or database tests, use PostgreSQL with the required Naksha extensions, including PostGIS and PLV8. The maintained test image is `ghcr.io/naksha-oss/naksha-postgres:v18.6-rc1`. Docker is optional if you supply an appropriately configured PostgreSQL instance.

```bash
git clone --branch v3 https://github.com/heremaps/naksha.git
cd naksha
./gradlew assemble                  # Build without running tests
./gradlew :here-naksha-app-service:shadowJar
```

The runnable JAR is `here-naksha-app-service/build/libs/naksha-app-service-<version>.jar`. To try it locally, start the disposable PostgreSQL container shown in [Testing](#testing), set `NAKSHA_TEST_DB_URL` and `NAKSHA_TEST_MAINTENANCE_JOBS` as shown there, and run:

```bash
java -jar here-naksha-app-service/build/libs/naksha-app-service-*.jar test-config "$NAKSHA_TEST_DB_URL"
```

Open the [local Swagger UI](http://localhost:8080/hub/swagger/index.html) for the API, or see the [OpenAPI document](here-naksha-app-service/src/jvmMain/resources/swagger/openapi.yaml). For a containerized service or a persistent PostgreSQL setup, see [Docker deployment](deployment/docker/README.md).

### Service configuration

The command takes a configuration ID and an optional admin JDBC URL: `java -jar <jar> <config-id> [<jdbc-url>]`. For a custom configuration, place `<config-id>.json` in `NAKSHA_CONFIG_PATH` (the exact directory you specify) or `~/.config/naksha/`. The service looks in those locations before bundled resources. The [bundled test configuration](here-naksha-app-service/src/jvmMain/resources/test-config.json), [service defaults](here-naksha-lib-hub/src/jvmMain/resources/config/default-config.json), and [`NakshaHubConfig`](here-naksha-lib-hub/src/jvmMain/java/com/here/naksha/lib/hub/NakshaHubConfig.java) show the configuration format. Bundled JWT keys and the test configuration are for development; configure your own signing and verification keys for deployment.

Naksha normally registers PostgreSQL maintenance jobs when installing or upgrading storage. Configure preloaded `pg_cron`, access to its control database, and the installer’s connection policy for such deployments, or explicitly configure `maintenanceJobs=false` when using external scheduling. The test-specific opt-out below does not disable synchronous partition upkeep.

## Testing

**Use a disposable database.** `cleanAndTest` cleans Gradle test outputs, not the database. Some test fixtures drop and recreate their own schemas or catalogs during setup; that is not a full database reset. Do not point tests at a database containing data you want to keep. Make sure HTTP port **8080** is free; the tests start and stop the app-service themselves.

### Run the common JVM and Node checks

The root `cleanAndTest` task compiles common metadata, builds JVM production and test code, builds the JS production artifacts and test code, and runs the JVM and Node tests. It cleans the selected test outputs so the tests run again; it does not purge the whole build or Gradle cache. It requires `NAKSHA_TEST_DB_URL` and does not start a database for you.

Start a fresh local test database (the password shown is for this local disposable container only):

```bash
docker run --rm -d --name naksha_test --shm-size=1g \
  -p 127.0.0.1:15433:5432 \
  -e PGDATABASE=naksha_test -e PGPASSWORD=naksha_test \
  ghcr.io/naksha-oss/naksha-postgres:v18.6-rc1
```

Wait for initialization to finish. On first startup the image briefly starts and stops PostgreSQL to initialize the database. After the final start, `docker exec naksha_test pg_isready -h 127.0.0.1 -U postgres -d naksha_test` should report that connections are accepted. Then run:

```bash
export NAKSHA_TEST_DB_URL='jdbc:postgresql://127.0.0.1:15433/naksha_test?user=postgres&password=naksha_test&ssl=false'
export NAKSHA_TEST_MAINTENANCE_JOBS=false
./gradlew cleanAndTest --continue --max-workers=2
```

If you already created a disposable instance, set the same variables to use it instead. For `cleanAndTest`, keep the database URL overrides in the table below unset or exactly equal to `NAKSHA_TEST_DB_URL`, and leave the app-service test context unset or set it to `LOCAL_STANDALONE`. The task ignores app-service URL files so your personal fixture configuration cannot redirect the run. It rejects isolated-suite settings. When finished, `docker stop naksha_test` removes **this test container** because it was created with `--rm`.

### Test environment variables

| Setting | Meaning |
| --- | --- |
| `JAVA_HOME` | Path to JDK 25, if it is not already your default. On macOS: `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`. |
| `NAKSHA_TEST_DB_URL` | Shared test JDBC URL with a database name and `user`/`password` query parameters. Required for `cleanAndTest`; used as a fallback by standalone app-service fixtures and PostgreSQL test storage with no configured master. |
| `NAKSHA_TEST_PSQL_DB_URL` | Global JVM `PgStorage` master override; it also clears configured replicas. `cleanAndTest` sets it in its test JVMs to the validated shared URL so explicitly configured fixture masters stay on the same disposable database. For focused runs without this variable, the test JVM system property `naksha.test.psql.db.url` can be used as an override. |
| `NAKSHA_TEST_ADMIN_DB_URL`, `NAKSHA_TEST_DATA_DB_URL` | Override the app-service admin and data fixture URLs for direct test runs. In those runs a valid `test_admin_db.url` or `test_data_db.url` in the test working directory, `~/.config/naksha/`, or test resources takes precedence; the shared URL follows. |
| `NAKSHA_APP_SERVICE_TEST_CONTEXT` | `LOCAL_STANDALONE` (default) uses configured PostgreSQL. `TEST_CONTAINERS` starts a fixture PostgreSQL container for direct app-service `jvmTest` runs; it is not a `cleanAndTest` mode. |
| `TEST_NAKSHA_PSQL_PASS` | Password for the app-service fallback URL at `localhost:5432/postgres` only. It does not configure a Testcontainers password. |
| `NAKSHA_TEST_STORAGE_ID`, `HUB_ADMIN_STORAGE_ID`, `HUB_ADMIN_MAP_ID` | Test storage defaults to `local_psql_test_storage`. Gradle defaults `HUB_ADMIN_STORAGE_ID` to the same value unless explicitly set. Tests sharing a database must agree on its installed storage identity. The Hub admin map defaults to `naksha-hub-admin`. |
| `NAKSHA_TEST_MAINTENANCE_JOBS` | Exact `true` or `false`. Overrides cron registration independently of the JDBC override; unset preserves the storage configuration. Use `false` for ordinary disposable tests, not for the scheduler integration suite. |
| `NAKSHA_SUPPRESS_PERF_TEST=true` | Skip performance tests marked with this switch, including the annotated CLI and PLV8 benchmarks. It does not disable ordinary functional or integration cases. |
| `DISABLE_BIG_PAYLOAD_TESTS=true` | Skip the two large HTTP feature fixtures. |
| `CICD=true` | Skip one annotated CLI copy test. Not set by the public GitHub workflow. |
| `runConnectorIntegrationTests=true` | Opt into the HTTP connector integration tests; these need external fixtures configured with `dataHubSpace`, `dataHubToken`, and `nakshaSpace`. They are not part of the ordinary run. |

The [advanced test guide](docs/testing.md#isolated-postgresql-suites) lists the separate `NAKSHA_TEST_<SUITE>_DB_URL` variables and exact setup commands. In particular, `NAKSHA_TEST_CRON_CONTROL_URL` enables real scheduler verification, `NAKSHA_TEST_CODEC_CPU=true` enables the database-free codec benchmark, `NAKSHA_TEST_THROUGHPUT_DB_URL` enables the write benchmark, and `NAKSHA_TEST_CPU_PROFILE_DIR` enables JFR for the benchmark runs. Run isolated suites individually against their own disposable database with a focused `jvmTest` command; do not enable them together in the shared run. Do not write real JDBC passwords, tokens, or profiles to Git or shared logs.

For focused tests only, PostgreSQL library fixtures can start their own Testcontainers database when no master/URL is configured. Both managed fixtures use `v18.6-rc1`. The library fallback binds host port **15432**; app-service `TEST_CONTAINERS` binds **5432**. The aggregate command and GitHub workflow use neither fallback.

### Node exclusions and reports

Node runs in every module with a configured Node target. [`gradle/node-test-quarantine.txt`](gradle/node-test-quarantine.txt) lists the **specific currently failing Node cases**, not entire modules. They include Node/PLV8 fixture limitations and existing JavaScript number, hashing, and patching differences. JVM tests are unaffected. To reproduce the excluded failures explicitly:

```bash
./gradlew jsNodeTest -PincludeKnownFailingNodeTests=true --rerun --continue
```

- HTML: `<module>/build/reports/tests/jvmTest/` and `<module>/build/reports/tests/jsNodeTest/`.
- JUnit XML: `<module>/build/test-results/jvmTest/` and `<module>/build/test-results/jsNodeTest/`.
- JVM coverage: add `jacocoAggregatedTestReport` to the `cleanAndTest` command. The report is under `build/reports/jacoco/jacocoAggregatedTestReport/`.

### GitHub pull requests

The [public workflow](.github/workflows/main-ci-only.yml) runs for PRs targeting a `v3*` branch when opened, reopened, or updated; it can also be started manually. GitHub starts PostgreSQL as a **job service container** with a dynamically mapped host port, 1 GiB of shared memory and a readiness check. It passes the resulting JDBC URL to `./gradlew cleanAndTest jacocoAggregatedTestReport`. The runner skips the annotated performance tests and the two large payload fixtures; export `NAKSHA_SUPPRESS_PERF_TEST=true` and `DISABLE_BIG_PAYLOAD_TESTS=true` to reproduce that profile locally. The workflow uploads JVM and Node reports and reports JVM coverage on eligible PRs. It does **not** publish release artifacts.

## Contributing and license

See the [contribution guidelines](CONTRIBUTING.md) and [dependency catalog](gradle/libs.versions.toml). The logo uses public-domain land outlines from [Natural Earth](https://www.naturalearthdata.com/).

Copyright (C) 2017-2022 HERE Europe B.V. Naksha is licensed under the [Apache License, Version 2.0](LICENSE).
