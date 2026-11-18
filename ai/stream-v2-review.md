# Review: `v3_streaming_reader_v2` vs `v3` — StreamV2

Scope: `here-naksha-cli/.../stream/StreamV2.kt` against the contract in `here-naksha-lib-model/.../streaming/Stream.kt`, plus the supporting files `StreamReadSessionV2.kt`, `MainStreamingV2.kt`, and the providers.

## High

1. **Sequential mode and transaction ordering are not enforced.** See `StreamV2.kt:301-327` (`next`, `next(timeout)`).
   - `request.sequential` is never checked. `next()` never waits for `acknowledge`, so it violates the rule that a sequential stream **must** wait for `acknowledge`.
   - In transaction mode, overlapping transactions (the same feature in several un-acked txns) are handed out in parallel. `StreamTuple.nextVersion` is never set (`-1`), so the contract does not allow parallel delivery here.
   - `_pending` has no upper bound. A slow or stalled writer causes unbounded memory growth (see the TODO at `:335`).

2. **Off-by-one on `minVersion` in transaction mode.** See `StreamV2.kt:80` and `:214` (`txn > $1`).
   - `StreamRequest.minVersion` is inclusive, but the txn SQL uses `>`, so the first transaction at `minVersion` is skipped on the first open.
   - The same field is reused as an exclusive "last acked" position for recovery (`:339`, `:451`), so the two meanings clash.
   - Ignore-transactions mode is consistent (`>=` plus `+1` at `:101`).

3. **`request.version` is ignored in transaction mode.** See `buildTxnSql` at `StreamV2.kt:190-205`. No upper bound is applied, so the stream reads past the requested version up to HEAD.

4. **`request.version` and `minVersion` use the wrong units in ignore-transactions mode.** See `StreamV2.kt:80`, `:152-154`, and `:63`.
   - `i <= request.version` compares the raw V2 `i` sequence with a V3 `Version` number.
   - `minVersion` is taken as a raw `i` and exposed as a `Version` through the `minVersion` getter, so the value has the wrong meaning for callers.

5. **`queryHistory=false` with `version < HEAD` does not give a point-in-time snapshot.** See `StreamV2.kt:139-143` and `:152`. Only head rows with `i <= version` are read. Features updated after `version`, whose state at that version lives in `_hst`, are missed.

6. **A null geometry crashes the read.** See `StreamV2.kt:254` and `:265`. `cursor["geo"]` is cast to a non-null `ByteArray`, so features without geometry cause an NPE in `GeoUtil.fromEWKB`. The whole chunk fails.

7. **`next()` has no timeout.** See `StreamV2.kt:306` (`readChunk(0)` means unlimited). The contract requires `stmtTimeout` to be used. Also, `StreamReadSessionV2.kt:34` opens the connection with `SessionOptions()`, so the passed `options` (timeouts, appId) are ignored.

8. **`closeForRecovery` can throw.** See `StreamV2.kt:416-437`.
   - `PsqlPlan.execute`/`close` map SQL errors to `NakshaException`, so `catch (SQLException)` at `:422` is dead code.
   - A `NakshaException` (for example from `plan.close()` at `:385`) escapes. This breaks the contract that the method "only fails when not supported".

9. **DB errors are not turned into `StreamException`, and the stream is not marked broken.** See `StreamV2.kt:240-245` and `:275-277`. A non-cancel SQL error or disconnect throws a plain `NakshaException` with no `recoveryRequest`. The stream stays "open", so a later `next()` retries on a possibly dead connection.

## Medium

10. **Legacy txn sequence may not fit in 30 bits.** See `StreamV2.kt:41-46`. `legacy % 1e11` can be up to about 1e11. `Version.auto` throws for values above `0x3FFF_FFFF`. This affects `readChunk` (`:262`) and also the `minVersion` getter (`:63`).

11. **Tuple versions do not match the transaction version.** See `StreamV2.kt:271`. The tuple version uses the txn date plus the feature `i`, while `StreamTransaction.version` uses the txn date plus the txn seq (`:285`). Tuples in one transaction do not carry that transaction's version, and tuples from different same-day txns are ordered by `i`, not by txn.

12. **The resume position depends on the UUID suffix, not on `i`.** See `StreamV2.kt:258` and `:101`.
    - The SQL filters and orders by the `i` column, but the resume point is parsed from the last UUID segment. If they ever differ, the stream skips or loops.
    - The outer SELECT drops `i` (`:138`); it should return `i` directly.
    - `require(featureSeq <= 2^30)` (`:264`) also caps collections at about 1.07B rows.

13. **The recovery request stores a legacy txn above 2^53 as `minVersion`.** See `StreamV2.kt:451`. The value is not JS-safe and is not a valid `Version` (the constructor rejects values above `MAX_SAFE_INTEGER`). `toLegacyTxn` already accepts dated versions, so storing the V3 version would be consistent with the `minVersion` getter.

14. **Empty chunks are delivered at the end of the stream.** See `StreamV2.kt:285-294`.
    - Transaction mode always returns a final empty `StreamTransaction` with `version = 0`.
    - Ignore-transactions mode does the same when the last chunk has exactly `chunkSize` rows.
    - `hasNext()` gives no look-ahead.

15. **`txn == Version.HEAD.number` is meaningless.** See `StreamV2.kt:294`. It compares a legacy txn with the V3 HEAD constant.

16. **Shared connection and cancel side effects.** See `StreamReadSessionV2.kt:28-38`. Every stream from one session shares one `PsqlConnection`. A PG cancel (`StreamV2.kt:376`) cancels whatever that backend is running, which may be another stream's query.

17. **Session-wide `SET enable_sort = off`.** See `StreamReadSessionV2.kt:38`. Together with the expression filter `(jsondata->...->'txn')::bigint` (`StreamV2.kt:208-217`), this can produce very bad plans if no matching expression index exists on head, `_hst`, and `_del`. Each txn chunk runs two scans.

18. **Unbounded transaction size.** See `appendTxnSelect` at `StreamV2.kt:219-225`. A transaction has no LIMIT, so a bulk-import txn loads every row into memory as a single chunk.

19. **Schema and table names are not quoted.** See `StreamV2.kt:137` and `:191`. `catalogId` and `collectionId` are concatenated raw into the SQL. Identifiers with uppercase letters, `-`, or other special characters break, and the code is open to SQL injection.

## Low

20. **`next(timeoutMillis <= 0)` always returns `null`.** See `StreamV2.kt:313`. Non-blocking polling never makes progress.

21. **A second `close()` does not throw `CLOSED` for unread chunks.** See `StreamV2.kt:366-369`. It only re-checks the failure, so the first and second calls behave differently.

22. **Visibility race on `_hasNext`.** See `StreamV2.kt:105`. `_hasNext` is written under `_stateLock` but read without it and is not `@Volatile`.

23. **O(n²) acknowledgement bookkeeping.** See `StreamV2.kt:332-338` and `:67`. `in`/`remove` on `ArrayDeque`/`ArrayList` are linear.

24. **`isAcknowledged` returns `false` on failure even when nothing is pending.** See `StreamV2.kt:342-349`.

25. **`MainStreamingV2` is broken.**
    - `:43` calls `.get("v2")`, but the provider name is `"naksha-v2"` (`V2StorageProvider.kt`).
    - `:58` asserts `next(100) == null` after `close()`, but it throws `CLOSED` (as `StreamV2Test` shows).
    - The session is never closed.

26. **`readOnly` is inconsistent.** `V2ConfigBuilder` sets `withReadOnly(false)`, but the session always opens `readOnly = true` (`StreamReadSessionV2.kt:35`).
