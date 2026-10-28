# PgDatabase migration — completed tickets

Completed tickets for [JIRA: NAKSHA-33](https://here-technologies.atlassian.net/browse/NAKSHA-33).
These are moved out of [NAKSHA-33.md](NAKSHA-33.md) so the active workboard stays
flat; they remain listed (struck out) in its overview table. Every ticket here is
`DONE`.

## db-00 — Restore a compilable baseline

**Commit:** Restore compilable psql baseline and rebase import fixes.

- **db-00-a:** Adapted stale identifier/member/transaction APIs in psql writers, row helpers,
  tests, and downstream Java callers. Added a test-only `adminConnection` helper
  using `newConnection`; existing tests remain included.
- **db-00-b:** Temporarily excluded superseded implementations through the psql Gradle source
  sets, preserving the source files for reference:
  - JVM: `PgAdminCatalogGc.jvm.kt`, `PsqlAdminCatalog.kt`, `PsqlCatalog.kt`,
    `PsqlCollection.kt`, `PsqlStorageListener.kt`.
  - JS: `PgAdminCatalogManager.js.kt`, `Plv8Cluster.kt`, `Plv8Cursor.kt`,
    `Plv8Plan.kt`, `Plv8Storage.kt`.
- **db-00-c:** Legacy writers temporarily construct HEAD/history descriptors locally rather
  than restoring removed fields on `PgCollection`. Remove these adapters in
  db-06..db-09 together with the legacy hierarchy and Gradle exclusions.
- **db-00-d:** Legacy table DDL temporarily uses the existing 2048-byte TOAST default and no
  explicit tablespace. Restore supported physical settings in db-04.
- **db-00-e:** Added required database accessors to storage implementations/test doubles.
  `NHAdminStorage` delegates to its physical storage; virtual/remote/generated
  storages explicitly throw unsupported-operation until their database semantics
  are implemented.
- **db-00-f:** Corrected `MemberType` initialization validation: byte alignments are positive
  powers of two shared by multiple types, not contiguous sort-order identifiers.
  The old check prevented even the database-independent `PgRowsTest` from starting.

**Verification:** all configured JVM main/test Kotlin compilation tasks and JS
main compilation tasks pass (including Java compilation dependencies):

```bash
NAKSHA_SUPPRESS_PERF_TEST=true ./gradlew compileKotlinJvm compileTestKotlinJvm compileKotlinJs :here-naksha-lib-psql:jvmTest --tests 'naksha.psql.PgRowsTest' --tests 'naksha.psql.PsqlInstanceTest' --tests 'naksha.psql.PsqlQueryParsingTest' --tests 'naksha.psql.PgExceptionMapperTest' --console=plain
```

Result: **BUILD SUCCESSFUL**, all **21 selected tests passed**. Database-backed
integration tests were not run: the schema/row methods in `PgDatabase` remain
unfinished, and bootstrap/write functionality requires the tickets in
`NAKSHA-33.md`.
