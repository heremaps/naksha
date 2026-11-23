# Collection-owned member layout and array-backed books

Date: 2026-11-23. State: **`DONE`** for the user-approved checkpoint. Commit message: **`Compile collection member layouts and use array-backed books`**. Implemented, independently reviewed and tested against local checkpoint `d4ffcd41b`. The user approved committing the collection-owned immutable member layout and array-backed storage member books while preserving `IBook` and required Tuple behavior; no push was requested. Version remains beta.55; fresh installs only, no migration/upgrade work or existing-database recreation.

## Implementation

- `PgCollection.memberLayout` owns a new `CompiledMemberLayout` for each descriptor instance, not a global cache keyed by collection ID. Declaration content is deep-copied at construction; names, types, filtered slots and private paths are compiled once. Processor-visible copied declaration paths are distinct from private encoding paths. Processors retain their existing read-only argument contract; concurrent declaration/processor mutation is not supported.
- `MemberBookLayout` privately copies canonical names and owns the shared name-to-slot lookup. `MemberValuesBook : IBook` allocates only a fresh values array per instance, preserves null slots, supports indexed/name/string/name-section/metadata/hash access, and copies values without rebuilding layout names/lookups. Member-book string references address the parallel names section, matching the previous named-book behavior; hash lookup finds value hashes and returns all collisions.
- `Tuple.encodeWithLayout` uses fixed slot descriptors and the existing native reference plan, while keeping feature preparation, processors, coercion, action/identity/origin handling, compression and undeclared feature content. No new feature-specific `IntArray` is allocated. The existing public/raw mutable-declaration APIs intentionally retain their content-checked `HeapBook` path; local/global dictionaries are unchanged.
- `PgWriter` invokes the descriptor's compiled encoding helper. Feature coordinates use the descriptor database number, **catalog ID and collection ID**, not the metadata tuple's catalog/collection coordinates.
- Physical-column/book-slot maps are computed once per descriptor. `PgRows` fills logical slots directly, reconstructs tuple numbers from the split columns, and preserves ordinal conversion through `PgRowsColumn.get`. Row-set collection assignment establishes physical column order; additional aliases can still be appended. Indexed row writing requires exact layout identity; other books use the generic named path.
- Batch result and history overlays copy books before metadata adjustment. `Tuple.nextVersion` supports the new book and invalidates its successor-number cache on update. No values-array reuse or slot compaction is introduced.

## Validation and review

Focused JVM and Node tests cover owned name/value arrays, copies and metadata, null/out-of-range access, name/string/hash collisions, raw/compiled byte equality, source declaration mutation, processor-generated absent fields, global references, row reconstruction, hashed tombstones and descriptor replacement. Additional parity tests cover mandatory types, optional/virtual Origin matching, relocated metadata and action classification. All final focused runs pass. The first broader campaign (`local-tests-20261123_185725`) passed all 85 isolated PostgreSQL cases but recorded the new virtual-Origin test failure while fixes were still being validated. A subsequent forced JVM rebuild confirmed byte equality; the remaining literal assertion exposed a test-fixture error: plain standard members use root `tn`, whereas `NakshaFeature.tupleNumber` writes the XYZ path. Both foreign-Origin fixtures now write to the declared `Tn` path, and the final JVM/Node tests pass. Final compilation/regression and serial CPU/allocation/end-to-end results follow.

Independent correctness review identified missing one-time mandatory type checks in the public compiler API; compilation now performs the same required `useMember` checks as raw encoding. Independent security review found no exploitable defect but identified optional-Origin type matching parity. Follow-up correctness review also identified correctly typed virtual Origin preparation: its path must be copied independently of stored book slots so it can still be emitted literally. The compiler now privately snapshots the type-matched optional Origin path, including when virtual. Follow-up review confirmed the fixes with no remaining confirmed findings. Raw malformed-path diagnostic timing/error-code differences and broader arbitrary nested input-copy coverage are not certified by this increment; current feature preparation is retained as planned.

Measurements use private scripts/artifacts and only previously authorized fresh local disposable databases. Credentials, JDBC URLs and raw profiles stay private. No server settings or scheduling defaults are changed. End-to-end results must disclose concurrent laptop/background-I/O sensitivity rather than imply production capacity.

## Serial measurements

Campaign order: candidate-1 **`local-tests-20261123_191419`**, baseline-1 **`local-tests-20261123_191822`**, candidate-2 **`local-tests-20261123_192114`**, baseline-2 **`local-tests-20261123_192340`**. The `d4ffcd41b` source was exported read-only into a private directory and independently compiled for metadata/JVM/JS. The candidate was stable throughout measurement. No agent compiler, regression or other benchmark ran concurrently with measurement. No new quiet-laptop exclusivity was established for this campaign; machine/background-I/O variation remains relevant.

All these timings are **unprofiled** (`jfr=false`). The codec fixture measures five samples after three warmups, with 20,000 small/2,000 large operations per sample. Layout construction and fixture setup are outside the timed loop; Tuple feature preparation, coercion, JBON emission and normal compression are inside. The candidate fixture verifies raw/compiled bytes for all 64 small and 64 large features before measuring. It includes both content-checked raw and compiled operations in the same JVM, isolating the new fast path rather than relying only on separate-source throughput comparisons.

| Tuple encoding | Candidate raw CPU | Compiled CPU | Within-JVM reduction |
| --- | ---: | ---: | ---: |
| Small, run 1 | 18.340µs | **10.217µs** | **44.3%** |
| Small, run 2 | 18.566µs | **10.090µs** | **45.7%** |
| Large, run 1 | 355.302µs | **346.458µs** | **2.5%** |
| Large, run 2 | 359.604µs | **356.156µs** | **1.0%** |

The array-backed path saves exactly **2,352 allocated bytes per encoded feature** relative to the raw path in both JVMs and both payload sizes: small 7,480 → 5,128 and 7,240 → 4,888; large 240,968 → 238,616 and 221,848 → 219,496. This is approximately **31–32% less small-feature codec allocation** and about **1% less large-feature allocation**. Differences between forks in the large totals are not attributed to this source change; JVM warmup/escape-analysis behavior varies. Large payload traversal/compression dominates the remaining CPU, so this increment is primarily a small-feature metadata/allocation optimization. Decoder timings remain broadly unchanged; the new book does not optimize the general JBON decoder.

The saved-source raw encoding CPU remained around 18.56µs for small fixtures and 358–370µs for large fixtures. Do not interpret large cross-fork raw-versus-compiled variation as a stronger CPU gain than the within-JVM comparison.

### Whole-request measurements

The unchanged workload uses four workers, 8,000 features per round, four partitions, history and tombstones ON, one warmup and three measured rounds per case/source. Each fork uses a fresh local disposable database. Timed work includes feature encoding, session/transaction work and commit; fixture copying, committed seed preparation and counters/recorder setup are excluded. The tables pool **six measured round rates per source/case**, not fastest samples or a median-of-medians. Exact worker CPU/allocation counters cover the timed request execution. These are not production capacity estimates.

| Operation / batch | Checkpoint features/s | Candidate features/s | Pooled change |
| --- | ---: | ---: | ---: |
| INSERT / 100 | 7,000 | **7,824** | **+11.8%** |
| UPDATE / 100 | 4,460 | **6,426** | **+44.1%** |
| Existing-key UPSERT / 100 | 5,234 | **6,446** | **+23.1%** |
| Tombstone DELETE / 100 | 4,407 | **8,751** | **+98.6%** |
| INSERT / 1,000 | 6,435 | **9,114** | **+41.6%** |
| UPDATE / 1,000 | 5,149 | **6,940** | **+34.8%** |
| Existing-key UPSERT / 1,000 | 5,648 | **7,001** | **+23.9%** |
| Tombstone DELETE / 1,000 | 10,023 | **11,127** | **+11.0%** |

| Operation / batch | Worker CPU µs/feature, checkpoint → candidate | Allocated bytes/feature, checkpoint → candidate |
| --- | ---: | ---: |
| INSERT / 100 | 251.3 → **165.6** (−34.1%) | 19,809 → **15,387** (−22.3%) |
| UPDATE / 100 | 264.3 → **163.6** (−38.1%) | 21,697 → **17,234** (−20.6%) |
| UPSERT / 100 | 245.7 → **187.0** (−23.9%) | 27,949 → **21,383** (−23.5%) |
| DELETE / 100 | 109.6 → **65.5** (−40.2%) | 16,702 → **12,663** (−24.2%) |
| INSERT / 1,000 | 351.0 → **177.5** (−49.4%) | 18,365 → **13,919** (−24.2%) |
| UPDATE / 1,000 | 328.8 → **201.0** (−38.9%) | 18,841 → **14,395** (−23.6%) |
| UPSERT / 1,000 | 355.7 → **242.7** (−31.8%) | 24,784 → **18,234** (−26.4%) |
| DELETE / 1,000 | 118.0 → **67.6** (−42.7%) | 13,892 → **9,872** (−28.9%) |

These worker counters measure the complete request path, not just the encoder, and therefore include gains from indexed row handling and cheaper result/history book copies. Every pooled case has lower CPU/allocation, but attribution to individual changes requires further profiling. Throughput retains substantial variation: candidate INSERT/1,000 medians were **2,854/s in run 1** versus **11,998/s in run 2**, against checkpoint **6,545/s** and **5,947/s**. Its first pair regressed despite the improved pooled rate. The nearly doubled pooled DELETE/100 rate also includes much slower baseline rounds. No precise I/O cause was established, and these outliers were not removed. CPU/allocation and repeated small-codec gains are stronger evidence than universal throughput claims.

## Final gates and limits

- Final forced `compileCommonMainKotlinMetadata`, `jvmMainClasses`, `jvmTestClasses`, and `jsNodeProductionLibraryDistribution compileTestKotlinJs` all passed with Java 25, `--rerun-tasks --no-build-cache --continue --console=plain`. Private summary: `member-layout-final-compile-summary.json`.
- Final full campaign **`local-tests-20261123_192845`**: **1,956 discovered JVM cases, 1,863 passed, zero failures/errors, 93 skips**. All **85 isolated PostgreSQL cases across 18 suites passed**, including real writer/batch/history/session/error/rollback/empty-tombstone behavior. No ordinary test/performance exclusions were added.
- Final focused runs: **25 JVM cases** (17 model routing/layout, 6 storage/mapping/stream, 2 book contract), and **13 Node cases** (7 layout/parity, 4 raw routing integration, 2 book contract) passed. Existing common/JVM reference and ordinary codec cases are also covered by the full JVM campaign.
- Actual cron-worker test **1/1 passed**, artifacts **`cron-tests-20261123_194404`**, using the existing separate disposable fixture endpoint. No container restart or server configuration change was made in this increment.
- Default JS diff/patch still has **14 failures out of 23** in the full campaign, unchanged from the saved checkpoint. Previously reproduced baseline Node JBON floating-point failures were not fixed by this change; the expanded numeric suite was not rerun for this increment. Full compiled Kotlin/JS write-request/subtransaction execution inside PLV8 remains unverified; Node model tests and runtime maintenance tests must not be described as that missing coverage.
- Independent correctness/security reviews were followed up after the mandatory/Origin fixes; no confirmed findings remain. Arbitrary malformed-path diagnostic timing/code equivalence, concurrent mutation of declarations/processor metadata and stronger arbitrary nested input-copy guarantees remain outside the verified contract. Current feature preparation and read-only processor arguments are retained, not redesigned.

Private `member-layout-ab-summary.json`, `member-layout-aggregate.json`, per-run XML and compiler logs preserve results and failed/rerun outcomes. The user approved committing the completed source/tests and this note with the required sign-off. No push was requested; prior checkpoint `d4ffcd41b` also remains local/unpushed. No version bump, migration, deployment, existing-database recreation or server-setting change was performed.
