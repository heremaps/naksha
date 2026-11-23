# JBON/Tuple CPU measurement

Date: 2026-11-23. State: **baseline measurement complete**, recorded before the subsequent codec optimization. The approved cron work was committed/pushed as `a84967e21` (`Schedule eight-hour partition maintenance with orphan cleanup`). This baseline report and profiling helpers are included in the separately user-approved `Optimize tuple member validation and path routing` checkpoint; see [the optimization report](NAKSHA-33-codec-optimization.md) for final comparisons and commit authorization. The unrelated user change to `.claude/skills/naksha-codestyle/SKILL.md` is preserved and excluded. Project/admin versions remain beta.55.

## Questions and measurements

1. What fraction of write-worker CPU executes inside tuple encoding/decoding, JBON serialization, geometry conversion, compression, proxy/member handling and JDBC?
2. Is available CPU saturated, or are workers mostly waiting for PostgreSQL, synchronization or other I/O?
3. What does allocation/GC contribute, and how does payload size change the picture?

The existing four-worker throughput fixture uses history ON, four configured partitions, 100/1,000-row batches, and 8,000 operations per round. CPU and allocated-byte counters wrap each synchronous write request, including session setup, encoding, SQL/result processing, commit, logging and session close. JVM-wide JFR sampling records **only the final timed round**, excluding fixture/request construction and committed seeding; recording startup/shutdown/dump is outside the round timer. Earlier rounds are unrecorded warmup/reference measurements. Worker CPU excludes PostgreSQL, compiler and GC threads. Worker CPU divided by round wall time reports used CPU cores, not machine utilization or SQL CPU.

`TupleCodecCpuTest` separately measures bare JBON encode/decode and raw Tuple encode/decode with no PostgreSQL, member processors or global dictionary. Collection layout defaults are resolved outside timing. Small and large topology fixtures are approximately 329 and 15,733 compact JSON bytes before per-feature IDs and encoding. Sixty-four deep-copied inputs are prepared outside timing; three warmup and five measured rounds execute fresh encoders/decoders and consume results. Tuple keeps the normal member extraction/coercion and gzip policy. Codec JFR recordings intentionally span all five measured rounds for sample density, so those timings include sampling overhead and are not unprofiled capacity estimates.

Warm Tuple decode reuses the wrapper and warmed identity caches. Fresh Tuple decode constructs a new wrapper per operation over the same immutable bytes/member book to include wrapper/cache initialization; neither substitutes for complete JDBC row decoding or an HTTP read/response benchmark. The write fixture checks returned tuple identity/action without forcing all lazy feature/HTTP serialization, so the absence of decode stacks in that write path cannot establish the cost of fully materialized REST responses.

Bare JBON and Tuple process different representations; their timing difference **cannot** be presented as book/proxy cost by subtraction. JFR inclusive stack percentages overlap and describe sampled JVM execution, not wall-time percentages. Allocation counter totals are exact for measured workers; JFR allocation weights are sampled estimates. GC during a timed round may also reclaim fixture objects allocated beforehand. Fixed case order, warmed reused inputs, small sample counts, JIT/GC, logging and recording overhead limit capacity/statistical conclusions.

## Safety and verification

Profiling is test-only. JFR explicitly excludes environment/system-property events and does not intentionally capture SQL parameters or feature contents. Socket events expose endpoints and stacks expose internal implementation; raw recordings and JSON exports stay in private output directories and are not committed or published. Database-backed measurements use a fresh disposable database and explicit non-cron test opt-out; existing databases/servers are not reset or reconfigured.

Forced common metadata, JVM production, JVM test-source and JS production/test-source compilation passed on Java 25 against `a84967e21` plus uncommitted test changes, with `--rerun-tasks --no-build-cache --continue --console=plain` (`naksha-codec-cpu-v4-compile-summary.json` and separate gate logs in the private OpenCode directory). Early codec attempts exposed fixture prerequisites: resolve layout outside timing, load resources directly instead of triggering PgTestBase's database-backed static initialization, and validate geometry presence against the actual fixture (the large fixture has no top-level geometry). Those benchmark setup issues were corrected, retaining codec identity/fixture checks, before the successful run. Independent read-only methodology and results reviews informed the scope/cache/CPU/privacy caveats and verified the source/percentage interpretation. Runtime selection is focused profiling tests, not another full regression campaign; production code is unchanged from the committed cron checkpoint.

## Completed measurements

Both `TupleCodecCpuTest` and `PgMutationThroughputTest` passed in **`local-tests-20261123_135202`**, with raw metrics in `cpu-measurements.json` and archived XML. Raw JFR recordings and JSON/summary exports remain private under that directory. Five measured single-thread codec samples after three warmups, median **CPU microseconds per feature**:

| Operation | Small (~329 JSON bytes) | Large (~15,733 JSON bytes) |
| --- | ---: | ---: |
| Bare JBON encode | 11.27µs | 801.38µs |
| Bare JBON decode | 4.50µs | 165.36µs |
| Tuple encode (normal member/gzip policy) | **47.76µs** | **3,597.96µs** |
| Tuple decode, warmed wrapper | 3.49µs | 182.85µs |
| Tuple decode, fresh wrapper | **3.59µs** | **183.00µs** |

Approximate allocation per raw Tuple operation: small encode **7.6KB**, fresh-wrapper decode **3.3KB**; large encode **235KB**, fresh-wrapper decode **201KB**. Stored Tuple blobs were 174 and 3,514 bytes; bare JBON outputs were 287 and 8,992 bytes. These sizes represent different payload/member representations; do not infer member overhead or compression benefit by simple subtraction. The large fixture differs in shape, not merely size, and lacks the small fixture's top-level geometry.

Full small-payload write-worker CPU was roughly **0.72–1.56ms per feature** for INSERT/UPDATE/UPSERT, substantially above raw `encodeRaw` cost. Full-session work includes repeated layout validation, member processors, preparation, JDBC/commit and logging. For representative final 1,000-row rounds:

| Operation | Worker CPU per feature | CPU cores used by four workers | Exact worker allocation per feature |
| --- | ---: | ---: | ---: |
| INSERT | 1,276µs | 3.38 | 17.0KB |
| UPDATE | 1,269µs | 2.85 | 17.5KB |
| Existing-key UPSERT | 1,556µs | 3.19 | 22.0KB |
| Tombstone DELETE | 203µs | 1.26 | 12.5KB |

These are instrumented request measurements, not new capacity guarantees. Four workers do not establish full-machine saturation on the 12-logical-CPU host. Socket waits remained material; UPDATE/100's final profiled round had unusually high recorded socket-wait time. Timed-window GC pauses ranged from zero to about 144ms and affect latency, but they do not explain the dominant Java execution sites. GC CPU is not included in worker counters.

## Confirmed CPU hot paths

JFR was re-exported using `--stack-depth 256`: the CLI defaults to showing only five frames, which would underattribute callers. Actual write stacks were complete (roughly 9–47 frames, untruncated); deeper codec stacks were recorded with a 64-frame cap, but the relevant encoder/decoder frames remain present. Native samples are kept separate because they include blocked socket/event-loop activity and cannot be treated as Java CPU time. First allocation sample weights per thread can carry pre-recording allocation history; they are excluded from weighted allocation summaries, while exact counter totals above remain authoritative. Inclusive execution-sample percentages overlap and must not be summed.

Representative final 1,000-row write rounds:

| Inclusive sampled execution path | INSERT | UPDATE | UPSERT | DELETE |
| --- | ---: | ---: | ---: | ---: |
| Tuple encoding | **93.5%** | **93.5%** | **72.8%** | not a material path |
| `NakshaCollection.validatedMembers` | **24.6%** | **24.3%** | **42.5%** | **49.6%** |
| Tuple member-path encoder callback | **49.4%** | **50.6%** | **36.1%** | not a material path |
| Generic proxy boxing | **72.6%** | **73.1%** | **74.3%** | **59.1%** |

Source confirmation:

- `NakshaCollection.validatedMembers` invokes `MemberList.validateAndSort()` on every access and checks mandatory members. Despite its name, `validateAndSort()` performs an **O(member-count²) duplicate-name scan**, not sorting. Tuple feature encoding and JDBC row-to-Tuple reconstruction access the getter; UPSERT also accesses it when stripping the expected-state member.
- `Tuple.encode` installs a callback invoked by `JbEncoder2.encodeValue` for each encountered value. The callback scans the entire member list and repeatedly reads proxy-backed member name/path/virtual flags before resolving positional member-book references. This is a confirmed hot path, not only low-level binary copying.
- Bare large-JBON encoding had about **68% leaf samples in `Binary.prepareWrite`**. Large decoding had roughly **35–38% leaf samples in `AbstractStringBuilder.needsNewBuffer`**, underneath decoder string reconstruction. These identify hot execution sites, not measured buffer-growth counts or proof of excessive allocation.
- DELETE's Java CPU largely lies under row-to-Tuple transport (about 90% inclusive samples), with layout validation nested within it. This is not 90% network CPU. The write fixture does not force complete payload/HTTP response decoding; isolated decoder measurements do not replace a full read/serialization profile.

## Recommended next increment — not implemented

1. Replace quadratic duplicate-member checking with an efficient single-pass strategy while preserving validation of in-place mutations, required error semantics and mandatory-member checks. Do **not** simply return the cached public `validatedMembers` property: its contract intentionally revalidates mutable declarations on every access.
2. Prepare immutable codec/member descriptors and indexed path-to-member routing at a validated physical-collection/request boundary, avoiding repeated ListProxy/MapProxy boxing and scanning on every encoded value. Preserve virtual members, member-book order, first-match path semantics and proper invalidation/snapshot ownership; do not introduce an unsafe global mutable-proxy cache.
3. Re-measure end-to-end CPU/latency before optimizing Binary buffer writes or string decoding. Encoding is the clear first target in the measured write path; decoder work is relevant for larger payloads and fully materialized reads but is not the dominant sampled write CPU here.

No optimization had been applied at this baseline measurement; the subsequent user-approved implementation and comparisons are recorded in [the optimization checkpoint](NAKSHA-33-codec-optimization.md). The baseline results support a CPU bottleneck in the encoding/member-handling path, not a claim that byte serialization alone is responsible or that PostgreSQL/GC/waits are irrelevant.
