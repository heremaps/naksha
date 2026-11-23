# Encoder-native member reference plans

Date: 2026-11-23. State: **`DONE`** for the user-approved checkpoint. Commit message: **`Encode member references natively with compact reusable plans`**. The user approved committing/pushing the tested implementation before further unattended performance work. This checkpoint follows `0e6f648bd` plus the user's preserved `911879f18` delegation-hint commit. No version bump, upgrade work, SQL change, server reconfiguration or deployment is included. The memory reduction is measured; a consistent end-to-end CPU/throughput benefit is not yet established. The user reported competing laptop activity during the earlier runs, so subsequent tests should repeat comparisons serially under quieter conditions without dismissing prior results.

## Implementation

- `IMemberEncoder`, `withMemberEncoder`, the full `Array<Any?>` traversal path and the model's `TupleMemberRouting` implementation are removed. Existing consumers and callback-observation tests are migrated to explicit paths, reference emission and decoded-value assertions.
- `JbMemberReferenceBuilder` prepares a compact `JbMemberReferencePlan` in lib-jbon, with private arrays for keys, first children, next siblings and terminal book indices. No map/object is allocated per path prefix. Build copies only populated entries; the plan has a private constructor and no exposed arrays. Later builder/input edits cannot modify a built plan. String/integer segments, first duplicate path and actual member-book positions are preserved; -1 registration represents a deferred missing-book error rather than silent omission.
- `JbEncoder2` advances/restores its reference node when entering/leaving keys or integer positions. A small encoder-owned integer parent stack replaces full paths. Unmatched subtree depth avoids further reference lookups or stack growth until traversal returns to a matching parent. Terminal references emit existing `JB2_REF_BOOK_MEMBERS` bytes without descending into the replaced value. Ordinary value encoding, key dictionaries, compression and wire format are unchanged.
- Reset clears local dictionary and traversal state, retaining only immutable plan bindings. Oversized parent stacks are released. `encodeValue` may match an empty/root path, while `buildTupleFromMap` still encodes its root object directly; this pre-existing distinction is explicitly tested. Rebinding is rejected at nonzero path depth; unsupported reentrant clear/global mutation is not turned into a supported sandbox contract.
- Tuple extraction/coercion and member processors remain in lib-model. Its `TupleMemberReferenceLayout` binder does **no per-value routing**: it supplies the generic jbon builder with copied names/paths and actual book positions, and checks those before reuse. A private AtomicRef on each MemberList publishes one immutable layout snapshot, not a global identity-only cache.
- Cache matching checks the complete ordered nonvirtual declaration content and current book positions on every encoding. Raw/in-place names, paths, virtual flags, reorder/replacement and book reorder are observed. Member types and values are not cached: unchanged routing can be reused while fresh extraction/coercion supplies new values/types. No Member/JsonPath proxies or book/feature values are retained in the cache. Public validatedMembers validation remains live.
- The new JS-facing builder enforces finite int32 keys/slots explicitly, not just Kotlin's JS Int runtime check. Fractional/nonfinite/overflow indices and string/null/BigInt book slots are rejected before storage. This hardens the new API without modifying the existing generic encoder number dispatch.

## Verification so far

All forced metadata/JVM production/JVM test-source/JS production-test-source gates passed with Java 25 (`naksha-native-references-v5-compile-summary.json`, separate per-gate logs), using `--rerun-tasks --no-build-cache --continue --console=plain`.

Focused JVM run passed **134 jbon cases plus 12 model cases**, including old geometry/member-book coverage, first-match/missing-slot semantics, root behavior, nested arrays/deep paths, independent encoder state, clear/reuse after exceptions, raw cache edits, same-layout type change with fresh coercion, and concurrent read-only plan publication. Focused Node reference/model run passed **18 cases**, including raw JS index guards. Cache serialization and privately owned snapshots are exercised; source privacy is not a sandbox against hostile same-process reflection/JavaScript. The serialization comparison occurs after proxy-default initialization/cache population; source-level exclusion plus that test is not a hostile-JS privacy boundary.

The broader explicitly enabled `Jbon2Test` Node suite has **12 floating-point failures out of 90 cases**. All twelve were reproduced with the same failed names against saved checkpoint **`0e6f648bd`**, exported read-only into the private temporary directory and independently passing all four compilation gates before its Node run. The numeric dispatch is unchanged; these are confirmed baseline failures, not attributed to this rewrite and not silently disabled. The normal diff/patch suite's existing 14 failures also remain separately disclosed. First draft test issues (incorrect Number→String coercion expectation and unsupported `1n` literal in Kotlin's js parser) were corrected to valid string/int payloads and `BigInt(1)`, without weakening output/type checks.

Independent read-only correctness/security reviews and guard follow-ups found no confirmed blockers. Remaining limits include generic builder fanout/path length resource budgets, source-vs-generated-JS encapsulation and the wider compiled-JS request/subtransaction boundary. Concurrent declaration mutation remains outside the proxy contract; the publication test covers competing builders over primed read-only declarations, not arbitrary mutation races.

## Completed measurement

Final CPU/JFR campaign **`local-tests-20261123_161307`** passed both profiling tests. Before refers to the matching lean callback checkpoint campaign **`local-tests-20261123_145145`**, not the older quadratic-validation baseline. Same fixture shapes, four workers/four partitions, history ON, 8,000 operations per round, one warmup plus three write measurements; last write round profiled. Codec uses three warmups/five profiled measurements. Fixture preparation/seeding and recording startup/dump are excluded; encoding/session/commit are included in request measurements. These are sequential exploratory comparisons, not interleaved capacity estimates.

| Tuple encode metric | Lean callback | Native plan | Change |
| --- | ---: | ---: | ---: |
| Small CPU/feature | 27.13µs | **26.37µs** | −2.8% |
| Small allocation/feature | 9,976 bytes | **7,328 bytes** | **−26.5%** |
| Large CPU/feature | 994.19µs | **939.31µs** | −5.5% |
| Large allocation/feature | 237,728 bytes | **221,936 bytes** | **−6.6%** |

The small raw Tuple allocation is now below the original pre-optimization baseline of 7,616 bytes while retaining the earlier CPU savings. Decoder metrics remain broadly similar and are not optimized here. Stored Tuple bytes remain 174/3,514 and bare JBON bytes 287/8,992 for the fixtures; wire-format/data semantics are checked separately by tests, not established by sizes alone.

Full INSERT/UPDATE/UPSERT worker allocation fell roughly **9–12%** versus the lean snapshot. Full worker CPU and throughput do **not** show a consistent improvement:

| Median features/s | Lean / 100 rows | Native / 100 rows | Lean / 1,000 rows | Native / 1,000 rows |
| --- | ---: | ---: | ---: | ---: |
| INSERT | 5,938 | 6,320 | 5,654 | 5,557 |
| UPDATE | 4,501 | 4,971 | 4,480 | 3,499 |
| Existing-key UPSERT | 4,397 | 3,578 | 4,864 | 4,047 |
| Tombstone DELETE | 4,695 | 6,484 | 8,744 | 6,477 |

For 1,000-row batches, median worker CPU/feature changed **356 → 426µs INSERT**, **341 → 368µs UPDATE**, **416 → 459µs UPSERT**, and **147 → 118µs DELETE**. Thus several measured CPU and throughput cases regressed despite lower allocation. Do not attribute every difference to the rewrite or unrelated noise without controlled A/B repetition; waiting time, machine/JIT state and cache-checking work are all relevant. In final profiled INSERT/UPDATE/UPSERT recordings, `TupleMemberReferenceLayout.matches` consumes about **13/11/12%** inclusive execution samples, while flat child lookup is below 1%. That checking cost replaces much of the old snapshot-construction CPU; removing the callback alone does not remove the metadata work.

Private JFR and full-depth JSON/summary exports are retained under the campaign directory. Inclusive samples overlap and are not exact wall-time shares. First allocation-weight carry-over is excluded from weighted summaries; worker counters above remain the measured allocation totals. No new capacity guarantee or universally faster encoding claim is made.

## Final verification and recommendation

- Final serial campaign **`local-tests-20261123_161635`**: ordinary JVM **1,942 discovered, 1,849 passed, zero failures/errors, 93 skips**; all **85 opt-in PostgreSQL cases across 18 isolated suites passed**. Performance exclusions were not set; non-cron fixtures explicitly use the documented scheduling opt-out.
- Dedicated actual cron-worker integration passed **1/1** in **`cron-tests-20261123_162936`** on the separate disposable cluster; actual PLV8/binary/history/mutation coverage passes in the isolated suites.
- Focused JVM **146/146** and Node **18/18** reference/model cases passed. Expanded Node JBON retains the independently reproduced **12/90 baseline floating-point failures**; default JS diff/patch retains **14/23 failures**. This is not a fully green JS runtime.
- Independent read-only correctness/security reviews and input-guard follow-ups found no confirmed blockers. Concurrent mutation/reentrant clear remain unsupported, generic long-path/fanout resource budgets remain unmeasured, and the full compiled-JS write-request/subtransaction boundary is still separately unverified.

The trial is a **memory improvement, not yet a proven end-to-end speed improvement**. The user approved saving it as a committed/pushed checkpoint before further experiments. Run controlled repeated/interleaved A/B measurements and examine combining content checks with the already required extraction/validation pass, rather than deleting live validation or using unsafe identity-only cache reuse. Subsequent optimization remains separate and uncommitted for review. Existing development databases are not reset; no upgrades, version bump or deployment are added. The user's `911879f18` commit remains preserved.
