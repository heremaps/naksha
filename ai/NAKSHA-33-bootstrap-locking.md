# Concurrent admin bootstrap and upgrade — beta.54

Date: 2026-11-19. Scope: the database initialization deadlock reported when independent clients concurrently open a storage requiring admin installation or upgrade. This is a review checkpoint, not completion of NAKSHA-33 or clearance of the historical findings in [REVIEW.md](REVIEW.md).

## Cause

`PgDatabase.init` previously read the installed admin version and executed installation DDL without cross-process coordination. Separate clients could all observe the same old version, then concurrently replace PostgreSQL function definitions, update `es_modules`, and create administrative relations. JVM-local storage locks do not coordinate different processes. The reported deadlock included tuple locks in `pg_proc` (relation OID 1255), consistent with competing function-definition updates; identifying every statement and the other relation in that particular production cycle still requires its server logs.

## Implemented contract

1. Probe the installed state without acquiring an installation advisory lock. Validate storage ID/number, installed version and creation/upgrade/override policy.
2. Current installations and compatible older installations that do not request an upgrade return through the advisory-lock-free path.
3. If installation or upgrade is required and permitted, complete the read-only probe transaction before waiting. Committing this probe releases its read locks while preserving connection `SET SESSION` options; it does not commit installation DDL.
4. Start the lock-owning transaction with `READ COMMITTED`, then acquire the fixed two-int transaction advisory lock defined by `PG_ADMIN_INSTALL_LOCK_CLASS` and `PG_ADMIN_INSTALL_LOCK_KEY`. PostgreSQL scopes it to the target database, not the client storage ID or requested version. Its two-int namespace is distinct from the existing bigint feature/collection guards.
5. Re-read all admin state under the lock. A preceding installer may have completed, changed identity, or installed another version. Reapply the same identity and permission checks and skip redundant installation when the target version is already present.
6. Hold the lock through installation and final commit. On failure, JVM connection cleanup rolls back the transaction and releases the lock. PLV8 `attachExisting` remains validation-only: it does not lock, install, change session settings, or complete its caller's transaction.

The explicit `READ COMMITTED` transaction matters even after ending the probe: under a persistent `REPEATABLE READ` default, the advisory-lock statement itself could otherwise establish a snapshot before waiting, making the subsequent recheck stale.

Normal startup still takes PostgreSQL's ordinary read locks; “lock-free” here means no additional bootstrap advisory lock. This does not add a global feature-write/maintenance mutex. All cooperating installers must use this protocol; older clients that ignore the advisory lock are not serialized by it. Arbitrary external DDL and online migration against active writers remain separate deployment concerns.

## Versioning

Project version, `NakshaVersion.CURRENT`, OpenAPI version and installed `adminVersion` advance to **3.0.0-beta.54**. Minimum compatible admin version stays **3.0.0-beta.53** because this change does not alter the on-disk schema contract. Compatible beta.53 installations may still be opened without upgrade when upgrade is disabled; explicitly requested upgrades to beta.54 use the guarded path.

## Regression coverage and independent review

- `PgBootstrapLockTest`: 11 unit cases cover the current/compatible fast paths, required installation and upgrade, another client's completed work, identity and version changes while waiting, denied operations, PLV8 attachment, transaction ordering and installer failure cleanup.
- `PgBootstrapConcurrencyTest`: four independent `PgStorage` instances and JDBC backends bypass `Naksha.useStorage`'s process-global lock. An external controller holds the exact installer lock until all four clients are observed waiting. The test verifies cold installation, current-version startup while the lock is held, and exactly one real upgrade transaction using an audit trigger on module updates.
- Test connections assert both a persistent session default and initial transaction isolation of `REPEATABLE READ`; this exercises snapshot freshness after the probe commit.
- The failure phase executes the real installer, creates a marker relation, then throws before commit. Independent SQL assertions verify the previous version and function catalog tuple survive, module audit writes and the marker are rolled back, and the installer lock is released. Subsequent independent clients successfully upgrade again.
- Another phase changes storage identity while clients are waiting and verifies that every waiter rejects it without performing installation.
- Independent correctness and security reviews found no introduced blockers. Follow-up correctness review confirmed the strengthened persistent-isolation and real-DDL rollback coverage. These static reviews do not imply that reviewers independently executed the reported tests or approved the whole branch.

## Verification

All compilation gates passed on JDK/toolchain 25, in the order required by [the test skill](../.claude/skills/naksha-test/SKILL.md):

```bash
./gradlew compileCommonMainKotlinMetadata --rerun-tasks --no-build-cache --continue --console=plain
./gradlew jvmMainClasses --rerun-tasks --no-build-cache --continue --console=plain
./gradlew jvmTestClasses --rerun-tasks --no-build-cache --continue --console=plain
./gradlew jsNodeProductionLibraryDistribution compileTestKotlinJs --rerun-tasks --no-build-cache --continue --console=plain
```

The local PostgreSQL 18.6 server was recreated by the user with 1 GiB shared memory. The successful campaign used fresh, separate databases, local vault credentials supplied privately, aligned storage IDs/URLs, isolated test JVM home configuration, and performance tests enabled. Existing application databases were not cleared, and campaign databases were retained for inspection. Earlier resource-limited failures are preserved as unsuccessful attempts, not counted as passes.

| Execution | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: |
| Ordinary full-project JVM run, including service tests | 1,829 | 0 | 82 |
| Opt-in PostgreSQL tests, 17 isolated suites | 76 | 0 | 0 |
| Unique combined JVM result, replacing the 76 opt-in skips | **1,905** | **0** | **6** |
| Configured JS runtime tests (`jsTest`) | 9 | 14 failures | 0 executed-case skips |

The six remaining JVM skips are three service data-ingest cases, the ignored partitioning case, and the two `V3StreamSmokeTest` cases disabled by the preceding checkpoint. Some JS runtime tasks are disabled by project configuration; they are not counted as executed tests. The 14 JS diff/patch failures remain unresolved and are not hidden by the passing JS compilation gates. This is not a completely green multiplatform runtime campaign.

The full JVM/integration campaign ran against the final production implementation. After test-only review improvements, the compilation gates and strengthened concurrency regression were rerun successfully. The controlled concurrency test allows a 60-second lock wait for queued cold installation while retaining PostgreSQL deadlock detection and bounded futures; production timeout defaults were not changed.

Local evidence is under `/private/var/folders/6k/vbn28hc915n4pqjrbd2xt0hr0000gn/T/opencode/`:

- Final compilation logs: `naksha-bootstrap-final-gate1-metadata.log`, `naksha-bootstrap-final-gate2-jvm-main.log`, `naksha-bootstrap-final-gate2-jvm-tests.log`, and `naksha-bootstrap-final-gate2-js.log`.
- Full campaign with archived per-module/per-suite XML and summaries: `local-tests-20261119_142252/`.
- Final strengthened concurrency regression: `local-tests-20261119_144707/`.

Only summaries and commands without credentials belong in Git. Raw local runtime logs may contain sensitive URLs from existing logging and must not be copied wholesale into review material.
