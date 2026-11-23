# Quiet-laptop performance follow-up

Date: 2026-11-23. State: **`DONE`** for this user-approved implementation checkpoint. Commit message: **`Reduce tuple encoding CPU with fused checks and bulk copies`**. Implemented/tested with repeated quiet comparisons against saved native-reference checkpoint **`00a065c0d`** (`Encode member references natively with compact reusable plans`). The user approved committing these improvements before discussing another idea; no new push or further implementation was requested. Earlier mixed results are preserved, not dismissed; the user reported competing activity during those runs.

## Boundaries

- Preserve the user's delegation-hint commit `911879f18` and all unrelated work. No version bump, migration/upgrade work, deployment or automatic commit of subsequent improvements.
- Run CPU/throughput measurements serially, never alongside Gradle compilation/regression or another benchmark. Pause only the agent-created disposable cron container to eliminate scheduled test-work noise; do not stop/reconfigure/reset the user's existing PostgreSQL server.
- Continue using fresh disposable benchmark databases, unchanged workload shapes, exact worker CPU/allocation counters and private JFR. Fixture/seed construction and recorder start/stop remain excluded. Keep default-JS diff/patch and independently reproduced baseline Node float failures disclosed.

## Work plan

1. Repeat the pushed native-reference checkpoint under quieter conditions before editing production code; preserve that run separately from earlier measurements.
2. Remove redundant metadata reads by comparing cached reference-layout content during the already required member extraction pass. Preserve actual book positions, live declaration checks, current coercion types, readonly processor contracts, first-match/error semantics and atomic immutable publication; do not replace checking with identity-only reuse.
3. Investigate encoder-local bulk movement for already validated in-place compaction/final assembly, replacing per-byte read/write checks with overlap-safe byte-array copies. Preserve mapped-view offsets, boundaries, readonly checks, byte format and malformed-input behavior; do not alter the global Binary accessor contract or unchecked caller-owned byte arrays.
4. Validate focused JVM/Node reference, global dictionary, mutable-layout, numeric guard, buffer boundary/offset/overlap and full regression/actual-PLV8 behavior after forced metadata/JVM/JS compilation gates.
5. Repeat the same quiet CPU/allocation/end-to-end measurements and inspect JFR. Use repeat/interleaved baseline comparisons where feasible, not just the fastest single sample. Report negative or mixed cases rather than claiming capacity or universal gains.

Raw profiles, credentials, temporary baseline exports and detailed logs remain in the private OpenCode temporary directory, never committed. This report records tested commands, commit plus then-uncommitted changes, exclusions, compiler/test failures and rerun outcomes. The user approved committing the completed improvements after review.

## Implemented candidate

- Tuple's normal cached-plan path now compares name, the current post-processing path, actual book index and filtered position while extracting/coercing/writing each member. The immutable candidate is used only when every entry and the final count match. Cold/mismatched cases retain full validation and snapshot rebuilding. Public live layout validation and current coercion types remain unchanged; readonly member-processor/input contracts remain required. The cached plan itself is never mutated.
- Member writes use the already read path, equivalent to the final `Member.write` helper, avoiding a further delegated-property read. No processor values or coercion types are added to caches.
- Encoder-local leftward string/text/structure/dictionary compaction and final fresh-array tuple assembly use overlap-safe bulk copies. Each range retains mapped-view bounds checking, and movement retains readonly checking; backing-array offsets are included. Zero-length ranges remain no-ops. The global Binary API and caller-owned payload append methods are not changed.
- Focused verification passed **149 JVM and 21 Node cases**, including new offset/prefix/suffix sentinels and 0/60/61/316/317/65,535/65,536-length boundaries, Unicode/global-reference reset, and raw name/virtual count changes through the integrated extraction path. All forced metadata/JVM/JS gates passed with Java 25 (`naksha-quiet-opt-compile-summary.json`).

The quiet saved-checkpoint run (`local-tests-20261123_164137`) passed both profiling fixtures, yet still had substantial database-wait outliers. Do not assume all prior variability came from foreground laptop activity. Four serial candidate/baseline/candidate/baseline campaigns include read-only `pg_stat_checkpointer`/`pg_stat_io` snapshots, private JFR and fresh databases; the saved `00a065c0d` source was exported read-only into a separate private directory and all four forced gates passed independently (`naksha-quiet-baseline-compile-summary.json`). No statistics reset or timing/server setting change was performed. Full regression and real cron-worker validation followed the performance runs; only then was the agent-created paused cron container resumed. Independent correctness/security reviews found no confirmed blockers; results follow.

## Repeated quiet A/B results

The four successful campaigns were **candidate-1 `local-tests-20261123_165950`**, **baseline-1 `local-tests-20261123_170245`**, **candidate-2 `local-tests-20261123_170555`**, and **baseline-2 `local-tests-20261123_170841`**. Each source runs in its own JVM with fresh database fixtures and the unchanged four-worker, four-partition/history-ON workload. No other agent compilation, regression, benchmark or scheduled test-cron work runs concurrently with measurements. Raw JFR and XML remain private; there is no production deployment or server configuration change.

Codec median CPU per feature, shown as the range of the two independent JVM medians (each has five measured samples after three warmups):

| Encoding | Saved checkpoint | Candidate | Approximate CPU reduction |
| --- | ---: | ---: | ---: |
| Bare JBON, small fixture | 10.30–10.35µs | **5.29–5.67µs** | **45–49%** |
| Tuple, small fixture | 26.83–26.97µs | **18.47–18.66µs** | **30–32%** |
| Bare JBON, large fixture | 823–841µs | **248–254µs** | **69–70%** |
| Tuple, large fixture | 944–948µs | **357–370µs** | **61–62%** |

Decoder timings are not improved by this encoder-only increment and remain broadly similar, with one candidate bare-decoder sample slower. Tuple allocation is similar/slightly lower: small checkpoint 7,568 bytes versus candidate 7,240–7,480; large checkpoint 241,056 bytes versus candidate 221,848–240,968. Do not attribute the complete large-allocation difference to the source rewrite: JVM escape analysis and case warmup differ across forks. The changes introduce no per-byte temporary objects; the final-assembly DataViewProxy allocation is removed.

End-to-end rates below pool the **six measured write-round rates per source/case** (two JVMs × three measured rounds), excluding warmups. Encoding, session/transaction work and commit are included; fixture construction, committed seeding and JFR startup/dump are excluded. These are medians, not the fastest rates or a median-of-medians capacity claim.

| Operation / batch size | Checkpoint features/s | Candidate features/s | Change |
| --- | ---: | ---: | ---: |
| INSERT / 100 | 5,977 | **5,814** | **−2.7%** |
| UPDATE / 100 | 4,816 | **4,872** | **+1.1%** |
| Existing-key UPSERT / 100 | 3,800 | **4,706** | **+23.8%** |
| Tombstone DELETE / 100 | 6,173 | **6,548** | **+6.1%** |
| INSERT / 1,000 | 5,290 | **6,993** | **+32.2%** |
| UPDATE / 1,000 | 4,085 | **5,633** | **+37.9%** |
| Existing-key UPSERT / 1,000 | 4,878 | **4,744** | **−2.7%** |
| Tombstone DELETE / 1,000 | 8,764 | **8,422** | **−3.9%** |

The encoding CPU gain is repeatable in both forks. End-to-end behavior is **not universally faster**: large INSERT/UPDATE improve consistently in both pairwise comparisons, whereas UPSERT/DELETE and smaller batches retain mixed or modest changes. The original 8,000-feature rounds are short, and the machine/DB background workload is not a laboratory-controlled capacity setup. The checkpoint/I/O snapshots showed 2–3 timed checkpoints and 1–2 completions per write-fixture campaign, with substantial accumulated write/sync activity. Those snapshots include seeding and checkpoints spanning phase boundaries; they do not identify the exact cause of any individual timed-round spike. No timing setting was enabled or statistical reset performed.

JFR comparisons confirm the targeted sites: full cached-layout matching is replaced by in-pass entry checks at about **3.5–4.5%** inclusive worker samples versus **12–13%** for the old full pass; large bare-JBON `Binary.prepareWrite` inclusive samples fall from about **69% to 35%**. These are overlapping sampled execution shares, not exact wall-time attribution. Remaining work includes string emission/coercion/proxy handling and database waits; no further unsafe caching or parser numeric-dispatch change is included.

## Final verification and evidence

- All forced common metadata/JVM production/JVM test-source/JS production-test-source gates passed on Java 25 before tests (`naksha-quiet-opt-compile-summary.json` and individual logs), with `--rerun-tasks --no-build-cache --continue --console=plain`. The exported checkpoint independently passed the same gates.
- Focused tests: **149 JVM cases and 21 Node cases passed**, including mapped offsets, sentinels, Unicode/global references, boundary lengths, raw name/virtual/type/path checks, first-match semantics and immutable plan publication. A separate external golden corpus of **40 combinations** (length/header boundaries, Unicode/control characters, nested arrays, global dictionary and member references) produced **identical combined SHA-256** on the compiled checkpoint and candidate: `6f82d15b072b7916c923fd45b618ffa891b3eb45a21258cba8f7ed2d3acec7e1`. The corpus/harness/classpaths/results are private, not product code.
- Final serial full campaign **`local-tests-20261123_171151`** passed **1,852 ordinary JVM tests, zero failures/errors, 93 skips**; all **85 isolated PostgreSQL cases across 18 suites passed**. No performance exclusions were set, and unrelated non-cron installs explicitly use the documented scheduling opt-out.
- After resuming the dedicated cron test container, Docker reassigned its ephemeral host port. The first follow-up (`cron-tests-20261123_172614`) failed connection setup using the old port, not a product assertion. Updating the private runner's endpoint to the actual mapped port produced a successful **1/1 actual cron-worker rerun** in **`cron-tests-20261123_172712`**. The user's existing server was not restarted or reconfigured.
- Existing default JS diff/patch still has **14 failures out of 23**. The independently reproduced baseline-only 12 Node JBON floating-point failures remain separately disclosed, not fixed or disabled by this increment.
- Independent correctness/security reviews found no confirmed introduced blockers. Residual gaps include stronger arbitrary readonly/insufficient-capacity/invalid-start API tests and unsupported concurrent/processor metadata mutation; valid mapped-copy paths and readonly contracts were inspected, not certified for arbitrary hostile custom view implementations. Bulk prevalidation may change partial buffer mutation on invalid API calls, which is not presented as a supported intermediate-state contract.

The user approved committing the completed candidate source/tests and this report with the required sign-off; no new push was requested. No version bump, upgrade implementation, user-database cleanup, server configuration change or deployment was performed. The saved/pushed rollback checkpoint is `00a065c0d`; the user's hint commit remains preserved. Private `quiet-codec-ab-summary.json`, aggregate results, server snapshots, full-depth JFR exports and archived per-run XML preserve commands, source status, negative cases and rerun outcomes. Further implementation waits for discussion of the user's next idea.
