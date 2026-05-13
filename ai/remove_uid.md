# Implementation Plan: Remove `uid` from `TupleNumber`

> **Branch:** `remove-uid`  
> **Modules:** `here-naksha-lib-model`, `here-naksha-lib-psql`

---

## How it works today

### TupleNumber — current structure

`TupleNumber` is a 6-field data class (**288 bits / 36 bytes** in full encoding):

| Field            | Type    | Size |
|------------------|---------|------|
| `storageNumber`  | Int64   | 64-bit |
| `mapNumber`      | Int     | 32-bit |
| `collectionNumber` | Int   | 32-bit |
| `featureNumber`  | Int64   | 64-bit |
| `version` (txn)  | Version | 64-bit |
| **`uid`**        | Int     | **32-bit** |

`uid` is a **per-transaction monotonic counter** managed by `UidManager`.  
Its lower 2 bits encode the `Action`:

| Action  | Lower 2 bits |
|---------|-------------|
| CREATED | `00b` = 0   |
| UPDATED | `01b` = 1   |
| DELETED | `10b` = 2   |

`UidManager.next(action)` increments an internal counter by `4 + (action.intValue shr ACTION_SHIFT)` and returns the new value. So:
- First CREATED in a tx → uid = **0** (lower 2 bits = 00)
- First UPDATED in a tx → uid = **1** (lower 2 bits = 01)
- First DELETED in a tx → uid = **2** (lower 2 bits = 10)
- Second CREATED → uid = **4**, second UPDATED → uid = **5**, etc.

The getter `TupleNumber.action` reads: `Action.fromValue((uid and 3) shl ACTION_SHIFT)`.

In SQL (`naksha.sql`), the corresponding function is:
```sql
naksha_tn_action(tn bytea) → int4recv(tn, length(tn) - 4) & 3
```
…reading the last 4 bytes (uid int32) and masking its lower 2 bits.

### Binary variants — current sizes

| Variant | Bytes | Fields encoded (per-entry) |
|---------|-------|---------------------------|
| `B288`  | 36    | storage + map + col + feature + txn + uid |
| `B224`  | 28    | map + col + feature + txn + uid |
| `B192`  | 24    | col + feature + txn + uid |
| `B160`  | 20    | feature + txn + uid |
| `B96`   | 12    | txn + uid |

In **PostgreSQL**, columns use:
- `tn` → **B160** (20 bytes) — primary key for the row
- `next_tn`, `prev_tn`, `base_tn` → **B96** (12 bytes)

### Where uid is generated in psql

```
PgWriter.groupOperations()
  → write.final_uid = tx.uid.next(Action.DELETED)   # for DELETE/PURGE

PgWriterUpsert.plan() (on-conflict UPDATE path)
  SQL: naksha_tn_160(feature_num, txn, ((naksha_tn_uid(new_row.tn) & -4) | 1))
  # clears CREATED bits, sets UPDATED bits in uid

PgWriterDelete.plan()
  SQL: naksha_tn_160(feature_num, txn, query.uid)
       naksha_tn_96(txn, query.uid)
  # uid is passed in UNNEST as a separate column
```

---

## How it will work after

### Core idea

Within a collection, `(featureNumber, version.txn)` is already unique — one feature can be modified **at most once** per transaction. So `uid` is not needed for uniqueness.

**`uid` is removed entirely.** Instead, the `Action` is encoded in the **lower 2 bits of `version.txn`** (i.e., the lower 2 bits of the sequence number):

| Action  | How stored in txn |
|---------|-------------------|
| CREATED | `txn or 0` (no-op, seq already has `00b` lower bits) |
| UPDATED | `txn or 1` |
| DELETED | `txn or 2` |

The DB allocates sequence numbers starting at 0 with step 1. When forming a TupleNumber, the caller ORs the action bits in.  
The getter `TupleNumber.action` will read: `Action.fromValue((version.txn.toInt() and 3) shl ACTION_SHIFT)`.

### Binary variants — new sizes (−4 bytes each)

| Old     | New     | Old bytes | New bytes | Fields encoded (per-entry) |
|---------|---------|-----------|-----------|---------------------------|
| `B288`  | `B256`  | 36        | **32**    | storage + map + col + feature + txn |
| `B224`  | `B192`  | 28        | **24**    | map + col + feature + txn |
| `B192`  | `B160`  | 24        | **20**    | col + feature + txn |
| `B160`  | `B128`  | 20        | **16**    | feature + txn |
| `B96`   | `B64`   | 12        | **8**     | txn |

PostgreSQL columns:
- `tn` → **B128** (16 bytes)
- `next_tn`, `prev_tn`, `base_tn` → **B64** (8 bytes)

### Invariant check update

`TupleNumberVariant`: `check(encodingBytes + sharedBytes == 36)` → `== 32`

---

## Phase 1 — Model layer (`here-naksha-lib-model`)

### ⚠️ Critical: Rename collision

`B192 → B160` collides with the existing `B160` name (which is itself renamed `B160 → B128`).  
**All five renames must be done in a single atomic commit** using find-and-replace across both modules.

Run this before starting: `grep -rn "B96\|B160\|B192\|B224\|B288" --include="*.kt"` to find every usage.

---

### Step 1.1 — `TupleNumberVariant.kt`

Rename all five constants and update `encodingBytes`, `sharedBytes`, `bits`, and the invariant:

| Old name | New name | `encodingBytes` | `sharedBytes` | `bits` |
|----------|----------|-----------------|---------------|--------|
| `B288`   | `B256`   | 32              | 0             | 256    |
| `B224`   | `B192`   | 24              | 8             | 192    |
| `B192`   | `B160`   | 20              | 12            | 160    |
| `B160`   | `B128`   | 16              | 16            | 128    |
| `B96`    | `B64`    | 8               | 24            | 64     |

Change invariant: `check(self.encodingBytes + self.sharedBytes == 36)` → `== 32`

Update `FROM_STRING` and `FROM_VALUE` maps to use new names, byte counts, and bit counts.  
Update KDoc for each constant (remove `uid` from field list).

---

### Step 1.2 — `TupleNumber.kt`

**Remove `@JvmField val uid: Int` from constructor.**

| Member | Current | After |
|--------|---------|-------|
| `uid` field | `@JvmField val uid: Int` | *removed* |
| `action` getter | `(uid and 3) shl ACTION_SHIFT` | `(version.txn.toInt() and 3) shl ACTION_SHIFT` |
| `hashCode()` | `version.hashCode() xor uid` | `version.hashCode()` |
| `equals()` | `… && uid == other.uid` | clause removed |
| `compareTo()` | `i32_diff = uid - other.uid` block | block removed |
| `toString()` | `"…:$version:$uid"` (9 parts) | `"…:$version"` (8 parts) |
| `ALL_PARTS` constant | `9` | `8` |
| `UID` constant | index `8` | *removed* |

`toByteArray()` — remove `dataview_set_int32(view, offset + 8, uid)`. The `version.txn` already carries action bits; only the int64 write remains.

`fromBinary()` — remove `val uid = binary.readInt32()` and drop `uid` from the TupleNumber constructor call.

`fromParts()` — remove `val uid = parts[offset + UID].toInt()` and `uid` argument.

`copy()` companion — remove `uid: Int? = null` parameter and its use.

`HEAD` constant — remove trailing `uid = 0` argument:
```kotlin
// Before:
val HEAD = TupleNumber(Int64(0), 0, 0, Int64(0), Version.HEAD, 0)
// After:
val HEAD = TupleNumber(Int64(0), 0, 0, Int64(0), Version.HEAD)
```

`resolveFeatureNumberConflict()` — remove `uid` from the TupleNumber(...) call.

Rename binary helpers:
- `toB288()` → `toB256()`, `fromB288` → `fromB256`
- `toB224()` → `toB192()`, `fromB224` → `fromB192`
- `toB192()` → `toB160()`, `fromB192` → `fromB160`
- `toB160()` → `toB128()`, `fromB160` → `fromB128`
- `toB96()` → `toB64()`, `fromB96` → `fromB64`

Update KDoc: string format is now 8 parts: `{storage-number}:{map-number}:{collection-number}:{feature-number}:{year}:{month}:{day}:{seq}`

---

### Step 1.3 — `IMetadata.kt`

Remove `val uid: Int` property (line 58) and its KDoc comment.

---

### Step 1.4 — `Metadata.kt`

Remove `override val uid: Int get() = tupleNumber.uid` (lines 63–64).

---

### Step 1.5 — `StorageTx.kt`

Remove `open val uid: UidManager = UidManager()` (line 104).  
Remove `import naksha.base.AtomicInt` if present; remove `import naksha.model.UidManager`.

In `metadataOf()` (line 134), replace:
```kotlin
val tn = TupleNumber(storageNumber, map.number, collection.number,
                     feature.featureNumber, version, uid.next(action))
```
with:
```kotlin
val actionBits = (action.intValue shr ACTION_SHIFT).toLong()  // yields 0, 1, or 2
val tn = TupleNumber(storageNumber, map.number, collection.number,
                     feature.featureNumber, Version(version.txn or actionBits))
```

---

### Step 1.6 — `IWriteSession.kt`

Remove `val uid: AtomicInt` property (lines 21–24) and its KDoc.  
Remove `import naksha.base.AtomicInt`.

---

### Step 1.7 — `TupleNumberBinaryArray.kt`

Remove the `private val uidOffset: Int` field (line 75).

In the `init` block, remove all `uidOffset = N` assignments for all 5 subtypes (lines 85, 100, 115, 130, 145).  
Reduce each `entrySize` by 4:

| Subtype | Old `entrySize` | New `entrySize` |
|---------|-----------------|-----------------|
| 0 (B256)| 36              | 32              |
| 1 (B192)| 28              | 24              |
| 2 (B160)| 24              | 20              |
| 3 (B128)| 20              | 16              |
| 4 (B64) | 12              | 8               |

Fix hardcoded minimum size on line 156:
```kotlin
// Before:
private val last: Int = if (length <= 0 || bytes.size < 36) 0 else ...
// After:
private val last: Int = if (length <= 0 || bytes.size < 32) 0 else ...
```

In `get(index)` (line 223–224), remove:
```kotlin
val uid = dataview_get_int32(view, offset + uidOffset)
```
and remove `uid` from the `TupleNumber(...)` call.

Remove `getUid(index: Int): Int` method (lines 310–313).

In `indexOf` and `lastIndexOf`, remove the `&& element.uid == getUid(i)` condition.

---

### Step 1.8 — `TupleNumberList.kt`

Update import block: replace `B96, B160, B192, B224, B288` with `B64, B128, B160, B192, B256`.

Update byte-size comments (lines 126–130):
```
// Before:
// 36 byte = storage:8, map:4, collection:4, feature:8, version:8, uid:4
// 28 byte = map:4, collection:4, feature:8, version:8, uid:4
// 24 byte = collection:4, feature:8, version:8, uid:4
// 20 byte = feature:8, version:8, uid:4
// 12 byte = version:8, uid:4
// After:
// 32 byte = storage:8, map:4, collection:4, feature:8, txn:8
// 24 byte = map:4, collection:4, feature:8, txn:8
// 20 byte = collection:4, feature:8, txn:8
// 16 byte = feature:8, txn:8
//  8 byte = txn:8
```

In `toByteArray()` (lines 173–174), remove:
```kotlin
dataview_set_int32(view, i, tupleNumber.uid)
i += 4
```

The `dataview_set_int64(view, i, tupleNumber.txn)` stays; txn already carries action bits.

Fix `check(i == variant.sharedBytes)` (line 152) — this checks the shared header size; only the per-entry uid removal matters; the shared header check remains correct since sharedBytes values also shift.

Note: there is a bug on line 138 (`i + 8` should be `i += 8`) and line 150 (`i + 8` should be `i += 8`) — fix these while in the file.

---

### Step 1.9 — `XyzNs.kt`

`val uid: Int? get() = guid?.tupleNumber?.uid` (lines 552–553) references a field that no longer exists.

**Check all callsites of `XyzNs.uid` first:**
```bash
grep -rn "\.uid" --include="*.kt" | grep -v "tupleNumber.uid\|meta.uid\|UidManager"
```

If no external callers need the uid value, remove the property entirely.  
If callers need the action, add: `val action: Action? get() = guid?.tupleNumber?.action` instead.

---

### Step 1.10 — Delete `UidManager.kt`

Verify no call-sites remain:
```bash
grep -rn "UidManager\|uid\.next\|uid\.reset" --include="*.kt"
```
Then delete the file.

---

## Phase 2 — PostgreSQL layer (`here-naksha-lib-psql`)

### Step 2.1 — `LibPsql.kt`

Remove (line 119–121):
```kotlin
internal const val COL_UID = "uid"
internal const val COL_PUID = "puid"
```
Remove them from any `COL_ALL` or `allColumns` list.

---

### Step 2.2 — `PgColumn.kt`

Update imports (lines 13–14): `B96 → B64`, `B160 → B128`.

Update byte-layout comments at lines 288, 307, 324, 349 — remove `uid: 32` line and reduce total bit count.

Update line 686: remove reference to `uid` in the column description.

No DDL changes — columns remain `bytea`; only their content size changes.

---

### Step 2.3 — `PgSession.kt`

Remove `override val uid: AtomicInt = AtomicInt(0)` (line 187).  
Remove `uid.set(0)` call (line 267).  
Remove `import naksha.base.AtomicInt`.

Update `toB160()` call (line 418): `it.tupleNumber!!.toB160()` → `it.tupleNumber!!.toB128()`.

---

### Step 2.4 — `PgWrite.kt`

Remove `var final_uid: Int? = null` (line 40) and its KDoc.

---

### Step 2.5 — `PgWriter.kt`

Remove both occurrences (lines 168 and 173):
```kotlin
write.final_uid = tx.uid.next(Action.DELETED)
```

---

### Step 2.6 — `PgWriterDelete.kt`

**`init` block:**
- Remove `inRows.addColumn("uid", PgType.INT)` (line 21).
- Remove `inRows.set(row, "uid", write.final_uid)` (line 27).

**SQL `plan()` method:**

1. UNNEST input drops `uid` column (line 46):
   ```sql
   -- Before:
   SELECT * FROM UNNEST($1, $2, $3) AS t(id, version, uid)
   -- After:
   SELECT * FROM UNNEST($1, $2) AS t(id, version)
   ```

2. Tombstone CTE (lines 78–79) — embed action bits directly in txn:
   ```sql
   -- Before:
   naksha_tn_160(naksha_tn_feature_number(head_row.tn), ${tx.version.txn}::int8, query.uid) AS ${PgColumn.tn},
   naksha_tn_96(${tx.version.txn}::int8, query.uid) AS ${PgColumn.next_tn},
   -- After:
   naksha_tn_128(naksha_tn_feature_number(head_row.tn), (${tx.version.txn}::int8 | 2)) AS ${PgColumn.tn},
   naksha_tn_64((${tx.version.txn}::int8 | 2)) AS ${PgColumn.next_tn},
   ```

3. Version check — mask action bits on both sides:
   ```sql
   -- Before:
   (query.version) = (naksha_tn_version(head.tn))
   -- After:
   (query.version & -4) = (naksha_tn_version(head.tn) & -4)
   ```
   This allows a client-provided version with action bits set to still match correctly.

Result reading (lines 208, 212, 226): rename `getB160` → `getB128`.

---

### Step 2.7 — `PgWriterUpsert.kt`

Line 99 — on-conflict UPDATE `head_updated` CTE:
```sql
-- Before:
naksha_tn_160(naksha_tn_feature_number(new_row.tn),
              naksha_tn_version(new_row.tn),
              ((naksha_tn_uid(new_row.tn) & -4) | 1)) AS ${PgColumn.tn},
-- After:
naksha_tn_128(naksha_tn_feature_number(new_row.tn),
              (naksha_tn_version(new_row.tn) & -4) | 1) AS ${PgColumn.tn},
```

Result reading (lines 175, 179): `getB160` → `getB128`.  
Line 185: `getB96` → `getB64`.

---

### Step 2.8 — `PgWriterUpdate.kt`

Line 171: `TupleNumber.fromB160(...)` → `TupleNumber.fromB128(...)`.  
Any SQL that does an atomic version check — apply `& -4` mask (see Step 2.6 note on consistent masking).

---

### Step 2.9 — `PgColumnRows.kt`

Rename methods and update imports:
- `getB96()` → `getB64()`; update `TupleNumber.fromB96(...)` → `fromB64(...)`; import `B96 → B64`
- `getB160()` → `getB128()`; update `TupleNumber.fromB160(...)` → `fromB128(...)`; import `B160 → B128`
- Lines 202–208: update all `B160`, `B96` variant arguments to `B128`, `B64`
- Line 278: `meta.tupleNumber.toB160()` → `meta.tupleNumber.toB128()`

---

### Step 2.10 — `PgQueryWhereBuilder.kt`

Line 56: `toByteArray(TupleNumberVariant.B160)` → `toByteArray(TupleNumberVariant.B128)`.

---

### Step 2.11 — `PgReader.kt`

Line 65: `TupleNumber.fromB160(...)` → `TupleNumber.fromB128(...)`.

---

### Step 2.12 — `naksha.sql` — rename functions and update offsets

#### Rename constructor functions (drop `uid int4` param and `int4send(uid)` from body)

| Old | New |
|-----|-----|
| `naksha_tn_288(storage_num, map_num, col_num, feature_num, txn, uid)` | `naksha_tn_256(storage_num, map_num, col_num, feature_num, txn)` |
| `naksha_tn_224(map_num, col_num, feature_num, txn, uid)` | `naksha_tn_192(map_num, col_num, feature_num, txn)` |
| `naksha_tn_192(col_num, feature_num, txn, uid)` | `naksha_tn_160(col_num, feature_num, txn)` |
| `naksha_tn_160(feature_num, txn, uid)` | `naksha_tn_128(feature_num, txn)` |
| `naksha_tn_96(txn, uid)` | `naksha_tn_64(txn)` |
| `naksha_tn_96(any_tn bytea)` extract overload | `naksha_tn_64(any_tn bytea)` — body: `substring(any_tn FROM length(any_tn) - 7 FOR 8)` |

New body example for `naksha_tn_128`:
```sql
CREATE OR REPLACE FUNCTION naksha_tn_128(feature_num int8, txn int8) RETURNS bytea AS $$
  SELECT int8send(feature_num) || int8send(txn)
$$ LANGUAGE 'sql' IMMUTABLE PARALLEL SAFE STRICT;
```

#### Update extractor offsets (all shift by −4)

| Function | Old offset | New offset |
|----------|------------|------------|
| `naksha_tn_storage_number` | `length(tn) - 36` | `length(tn) - 32` |
| `naksha_tn_map_number` | `length(tn) - 28` | `length(tn) - 24` |
| `naksha_tn_collection_number` | `length(tn) - 24` | `length(tn) - 20` |
| `naksha_tn_feature_number` | `length(tn) - 20` | `length(tn) - 16` |
| `naksha_tn_partition_number` | `length(tn) - 14` | `length(tn) - 10` |
| `naksha_tn_partition_index` | `length(tn) - 14` | `length(tn) - 10` |
| `naksha_tn_version` | `length(tn) - 12` | `length(tn) - 8` |
| `naksha_tn_year` | `length(tn) - 11` | `length(tn) - 7` |

#### Remove `naksha_tn_uid(tn bytea)` entirely.

#### Update `naksha_tn_action(tn bytea)`:

```sql
-- Before (reads lower 2 bits of uid int32):
SELECT int4recv(tn, length(tn) - 4) & 3

-- After (reads lower 2 bits of txn int64):
SELECT (int8recv(tn, length(tn) - 8) & 3)::int4
```

---

## Phase 3 — Tests

### Step 3.1 — `UpdateFeatureTest.kt`

Replace uid assertions (lines 128–131):
```kotlin
// Before:
assertEquals(1, updatedTuple.meta.uid)
assertEquals(0, createdTuple.meta.uid)
assertEquals(1, updatedTuple.tupleNumber.uid)
assertEquals(0, createdTuple.tupleNumber.uid)
// After:
assertEquals(Action.UPDATED, updatedTuple.tupleNumber.action)
assertEquals(Action.CREATED, createdTuple.tupleNumber.action)
```

---

### Step 3.2 — `TupleNumberPersistenceTest.kt`

Remove:
- `import naksha.model.UidManager` (line 10)
- `val uidManager = UidManager()` (line 106)
- `val expectedUid = uidManager.next(Action.CREATED)` (line 110)
- `.sortedBy { it.tupleNumber.uid }` (line 114)
- `expectedUids.remove(tuple.tupleNumber.uid)` assertion (line 121)

Replace with direct action check:
```kotlin
assertEquals(Action.CREATED, tuple.tupleNumber.action)
```

---

### Step 3.3 — New unit tests in `here-naksha-lib-model` (`commonTest`)

Add in `GuidTest.kt` or a new `TupleNumberTest.kt`:

1. **Action encoding in txn** — construct TupleNumber with `Version(txn or 0/1/2)`, verify `.action`:
   ```kotlin
   val base = Version.now(Int64(100))
   assertEquals(Action.CREATED, TupleNumber(..., Version(base.txn or 0)).action)
   assertEquals(Action.UPDATED, TupleNumber(..., Version(base.txn or 1)).action)
   assertEquals(Action.DELETED, TupleNumber(..., Version(base.txn or 2)).action)
   ```

2. **Binary round-trip** — all 5 new variants (B64, B128, B160, B192, B256):  
   `TupleNumber → toB256() → fromBinary() == original`

3. **String round-trip** — 8-part format:  
   `TupleNumber → toString() → fromString() == original`

4. **equals/hashCode ignores action bits** — two TupleNumbers differing only in action bits should NOT be equal (different version.txn, different identity).

---

### Step 3.4 — New integration tests in `here-naksha-lib-psql`

Extend existing tests to assert action visibility:

1. After INSERT → `tupleNumber.action == Action.CREATED`
2. After UPDATE → `tupleNumber.action == Action.UPDATED`
3. After DELETE → tombstone `tupleNumber.action == Action.DELETED`
4. Atomic DELETE: version check must accept version with action bits set (`& -4` mask)

---

## Important considerations

### Version.seq after the change

`Version.seq` currently returns `txn and SEQ_MAX` (all 32 lower bits). After this change, the lower 2 bits of a TupleNumber's `version.txn` will carry action bits. Code comparing versions for **ordering** should use `version.txn` directly (which preserves chronological order since action bits only occupy bits 0–1 and seq is monotonically increasing). Code comparing versions for **equality ignoring action** should mask: `(a.txn and -4L) == (b.txn and -4L)`.

The `Version.compareTo()` compares the full `txn`, meaning CREATED(txn=X) < UPDATED(txn=X|1) < DELETED(txn=X|2) — this is correct and desirable.

### Variant rename collision

Doing the five renames one-by-one will break compilation mid-way. Do all five in a **single find-and-replace** pass:

```
B288 → B256
B224 → B192  (careful: this creates a new B192 while old B192 is still in scope)
B192 → B160  (the old B192; the new B192 from above is fine)
B160 → B128
B96  → B64
```

The safest approach: rename all to temporary names (`_B256`, `_B192`, etc.) first, then strip the prefix.

### Backward compatibility of stored data

PostgreSQL `bytea` rows written under the old scheme are 12–36 bytes; the new extractor functions expect 8–32 bytes. The `length(tn) - N` offset calculation will produce wrong results on old rows.

**Mitigation:** Gate behind a new storage schema version. The DB migration script must re-encode existing `tn`/`next_tn`/`prev_tn`/`base_tn` columns by dropping the trailing 4 bytes from each stored value.

### Consistent version masking in all writers

The `& -4` mask for atomic version checks (Step 2.6) must also be applied in `PgWriterUpdate.kt` for the same reason: a client may provide a version with action bits set (read from a previous response), and the comparison must still work.
