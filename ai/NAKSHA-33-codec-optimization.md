# Tuple codec CPU optimization

Date: 2026-11-23. State: **`DONE`** for the user-approved implementation checkpoint. Commit message: **`Optimize tuple member validation and path routing`**. The user approved committing and pushing the tested optimization, regression tests, profiling helpers and reports before discussing refinements to the encoder-native reference-plan proposal. This checkpoint follows pushed cron commit `a84967e21`; the encoder-native rewrite has not started and is not included. The separate local user skill-file edit remains untouched and excluded from the commit. No version bump, migration/upgrade work, buffer-format change, SQL mutation change or deployment is included.

## Implemented changes

- `MemberList.validateAndSort()` keeps live validation on every access but uses a single-pass name set for valid layouts. Nulls/duplicates invoke the unchanged legacy scanner, so malformed input retains its established first-error code/message/order. The exceptional slow path remains quadratic; normal feature writes do not. The public `validatedMembers` getter is not replaced by an unsafe cached value.
- `TupleMemberRouting` snapshots member name/path/virtual status and actual member-book positions **once per encoding**, after member processing. Its private constructor-built path trie is read-only after construction and does not retain mutable Member/JsonPath proxies for valid string/integer paths. Every encoded value follows only its path rather than rescanning all member definitions through ListProxy/MapProxy boxing.
- Identical paths keep the first nonvirtual declaration; numeric versus textual path segments remain distinct; prefix and exact routes remain separate. Book positions come from `IBook.indexOfName`, not declaration ordinals or stored client indices. A missing book entry is an error only when that path is encountered, preserving prior callback failure timing. Global dictionary routing remains separate.
- The scope deliberately avoids cross-feature/global routing caches: public raw encoding accepts declarations that can change between calls, and proxy identity alone does not detect in-place changes. A per-call snapshot observes those changes while reducing the measured per-value hot path. This allocates a small routing graph per feature; CPU/allocation tradeoffs must be measured, including long paths.
- Member coercion, processor return handling, virtual members, shallow-copy behavior, positional storage book ordering, tuple identity/version semantics, JBON byte format and normal gzip policy are unchanged. Member processors retain their read-only input contract; undefined mutation by a processor is not a new supported feature.

## Correctness verification

Forced metadata/JVM production/JVM test-source/JS production-test-source compilation passed with Java 25 (`naksha-codec-opt-v5-compile-summary.json`, separate per-gate logs), each using `--rerun-tasks --no-build-cache --continue --console=plain`.

Focused tests passed **8/8 on JVM and 8/8 on Node** (`naksha-codec-opt-v5-{jvm,node}-results`): live in-place name changes; exact legacy duplicate/null diagnostic ordering; first nonvirtual matching path; numeric/textual segments; actual book position versus declaration order; private snapshot isolation and fresh-build path changes; deferred missing-book errors (including a later valid duplicate that must not replace the first missing declaration); byte-identical old/new JBON callbacks; full Tuple encoding with processor values/global dictionary references and unchanged feature input; repeated full encodings after path changes. Earlier test-setup failures (withMembers copies its list, Node lacks an MD5 provider) were corrected without disabling assertions. The portable full-codec test uses numeric IDs to isolate routing from that unsupported Node dependency; hashed identity remains covered by JVM/real PostgreSQL tests.

Independent read-only correctness/security reviews found no confirmed blockers. Remaining coverage/performance limits include arbitrary invalid raw path objects outside JsonPath's string/integer contract, per-feature trie allocation, very long paths and the wider compiled-JS request boundary. Full regression, real PLV8/cron execution and the identical codec/write CPU/JFR comparison completed serially on fresh disposable databases, with results below and no existing server reset/reconfiguration. Raw JFR remains private; inclusive CPU shares must not be summed, and sampled allocation weights do not replace exact worker counters.

## Initial implementation comparison

The first implementation comparison (`local-tests-20261123_143046`) passed both codec and write tests. Small Tuple encode CPU fell from 47.76µs to 29.13µs; large from 3,597.96µs to 985.94µs. End-to-end medians improved to about 6,057 INSERT / 4,844 UPDATE / 4,570 UPSERT / 5,245 DELETE features/s with 100-row batches, and 5,869 / 5,338 / 5,563 / 10,121 with 1,000-row batches. These are illustrative sequential comparisons, not interleaved capacity estimates. Full regression (`local-tests-20261123_143348`) passed 1,839 ordinary JVM cases with zero failures/errors and 93 skips; all 85 isolated PostgreSQL cases passed; actual cron integration passed in `cron-tests-20261123_144628`. Existing JS diff/patch remains at 14 failures.

The first snapshot increased small raw Tuple allocation from 7,616 to 11,288 bytes per feature. To address that measured overhead, child maps are now allocated lazily only on continuing paths, with a small initial capacity; leaf maps and per-terminal Route wrapper objects are eliminated. Private node indices distinguish no route (-1) from the first declaration's missing book entry (-2), preserving failure timing and duplicate-path precedence. All forced gates passed again (`naksha-codec-opt-v5-compile-summary.json`) and the strengthened focused tests pass on JVM/Node. Independent correctness/security follow-up reviews found no confirmed blockers. Long paths still require a node per segment and their allocation behavior remains a coverage limit, not claimed bounded by the 64-member limit.

## Completed lean-version comparison

Final CPU/JFR campaign **`local-tests-20261123_145145`** passed both profiling tests. Same workload, Java 25, four workers/four partitions, history ON, 8,000 operations per round, one warmup plus three measured write rounds; the last write round is profiled. The codec runs three warmups plus five profiled measured rounds. Encoding/session/commit are included in request throughput; fixture construction/seeding and recording startup/dump are excluded. Before refers to the matching instrumented baseline `local-tests-20261123_135202`, not the older short capacity illustration.

| Median throughput, features/s | Before / 100 rows | Final / 100 rows | Before / 1,000 rows | Final / 1,000 rows |
| --- | ---: | ---: | ---: | ---: |
| INSERT | 2,775 | **5,938 (2.14×)** | 2,648 | **5,654 (2.13×)** |
| UPDATE | 2,435 | **4,501 (1.85×)** | 2,371 | **4,480 (1.89×)** |
| Existing-key UPSERT | 2,032 | **4,397 (2.16×)** | 2,053 | **4,864 (2.37×)** |
| Tombstone DELETE | 4,840 | **4,695 (−3%)** | 6,426 | **8,744 (1.36×)** |

Not all latency/throughput cases improved. DELETE/100 was 3% below its baseline median despite less worker CPU, and one unprofiled UPSERT/100 round was an I/O-heavy outlier near the old throughput. These are sequential exploratory runs rather than interleaved A/B capacity estimates. Do not attribute all variance to the implementation or unrelated noise without another controlled comparison.

Median exact worker CPU across the three 1,000-row measured rounds:

| Operation | Before CPU/feature | Final CPU/feature | Reduction |
| --- | ---: | ---: | ---: |
| INSERT | 1,273µs | **356µs** | **72%** |
| UPDATE | 1,235µs | **341µs** | **72%** |
| UPSERT | 1,465µs | **416µs** | **72%** |
| DELETE | 203µs | **147µs** | **28%** |

Raw Tuple encoding CPU fell **47.76 → 27.13µs** for the small fixture (43% less CPU) and **3,597.96 → 994.19µs** for the large fixture (72% less). Bare JBON and decoder CPU remained broadly similar; they were not optimized in this increment. Stored Tuple bytes remained 174/3,514 and bare JBON 287/8,992 for the fixtures, consistent with unchanged byte-format/data semantics and the explicit byte-equivalence tests.

### Allocation tradeoff

Lazy child maps and integer terminals reduced the first snapshot's small Tuple allocation **11,288 → 9,976 bytes**, but the final snapshot still allocates **31% more** than the 7,616-byte baseline. Large Tuple allocation was about **237.7KB**, roughly 1% above the 235.4KB baseline. Full INSERT/UPDATE/UPSERT worker allocation at 1,000 rows rose about **22–24%**, and DELETE about 12%. The implementation is a CPU/throughput improvement, **not** an allocation reduction versus baseline. Very long declaration paths remain unprofiled and add constructor work/nodes even if those paths are absent from the feature; further compact-layout reuse would require a real immutable snapshot/invalidation contract rather than unsafe global caching.

### Final profiles

Private exports use `jfr print --json --stack-depth 256` and filter write-worker threads; initial allocation sample carry-over is excluded from weighted class summaries. In representative 1,000-row recordings, the old member-path callback occupied about 49/51/36% of INSERT/UPDATE/UPSERT execution samples; the final indexed route lookup was below 0.5% (zero observed INSERT samples). Route construction now occupies about 11/13/12% respectively. `MemberList.validateAndSort` fell from about 23/24/41% to **7/3/8%** of samples. These are overlapping inclusive execution shares, not wall-time percentages or exact attribution of the measured CPU reduction. Remaining samples include book extraction/proxy handling and `Binary.prepareWrite`; socket waits remain material. Raw JFR, exported JSON, exact comparison metrics and XML remain private in the campaign directory.

## Final verification and handoff

- All forced metadata/JVM production/JVM test-source/JS production-test-source gates pass (`naksha-codec-opt-v5-compile-summary.json` and individual logs), with Java 25 and `--rerun-tasks --no-build-cache --continue --console=plain`.
- Final serial campaign **`local-tests-20261123_145510`**: ordinary JVM **1,932 discovered, 1,839 passed, zero failures/errors, 93 skips**; all **85 opt-in PostgreSQL cases across 18 isolated suites passed**. Non-cron test installations explicitly use the documented maintenance-job opt-out; no existing server settings are changed.
- Focused common-code regressions passed **8/8 JVM and 8/8 Node**, including full Tuple integration with processor-returned values/global-book decoding and raw-path changes between calls. Real PLV8/binary/history/mutation coverage passes in the isolated suites.
- Separate real cron-worker integration passed **1/1** in **`cron-tests-20261123_150825`**, on the dedicated disposable cluster.
- The default JS diff/patch suite remains at the existing **14 failures out of 23 cases**, not fully green JS. The fuller compiled-JS request/subtransaction boundary remains separately unverified.
- Independent read-only correctness/security reviews, including the lean sentinel/allocation follow-up, found no confirmed blockers. Invalid mutable path-segment objects outside JsonPath's string/integer contract and adversarial long-path allocation remain explicit coverage limits.

The user approved committing/pushing this checkpoint with the required sign-off after reviewing the results and allocation tradeoff. The user-owned skill-file edit is preserved and excluded. No version bump, upgrade work, server restart/reconfiguration or deployment is included. The next encoder-native reference-plan design is **discussion only** until the user's proposed refinements are agreed; it is not implemented by this checkpoint.
