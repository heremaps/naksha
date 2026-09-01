# Architecture Overview

## Architecture Overview

The codebase follows a **two-layer abstraction** pattern:

### Layer 1: `lib-model` — Storage-agnostic interfaces
- **`ISession`** → core session API with `execute(request: Request): Response`
- **`IStorage`** → storage lifecycle, session creation
- **`IWriteSession`** → extends read session with `commit()`, `rollback()`, `useTransaction()`
- **`StorageTx`** → tuple encoding/decoding, member building
- **`AbstractStorage`** → base class all storages must extend (caching, lifecycle)

### Layer 2: `lib-psql` — PostgreSQL implementation
- **`PgSession`** → implements `ISession`, manages PG connections
- **`PgStorage`** → extends `AbstractStorage`, manages PG connection pools
- **`PgWriter`** → stateful writer, dispatches to operation-specific classes
- **`PgWriterInsert/Upsert/Update/Delete`** → SQL generation per operation

## Code Flow: Writing a New Feature

```
Client Code
    │
    ├── 1. Build Write instruction
    │   Write().createFeature(collection, feature)
    │   Write().upsertFeature(mapId, colId, feature)
    │   └── Sets: mapId, collectionId, op=CREATE/UPSERT, feature
    │
    ├── 2. Wrap in WriteRequest
    │   WriteRequest().add(write)
    │
    ├── 3. Execute on session
    │   session.execute(writeRequest)
    │       │
    │       ├── PgSession.execute() routes to writer
    │       │   writer = PgWriter(session, useSavepoint)
    │       │   writer.execute(request.writes)
    │       │       │
    │       │       ├── prepareWrite()
    │       │       │   ├── Resolves mapId → PgMap (from adminMap cache/DB)
    │       │       │   ├── Resolves colId → PgCollection
    │       │       │   ├── For map/collection creates: calls createPgMap/createPgCollection
    │       │       │   └── For features: builds PgWrite wrapper
    │       │       │
    │       │       ├── groupOperations()
    │       │       │   ├── Groups writes by map → collection → partition → op
    │       │       │   └── For CREATE/UPSERT/UPDATE: calls StorageTx.created()/updated()
    │       │       │       └── Builds Tuple:
    │       │       │           ├── buildMembers() → IBook with metadata (updated_at, author, hash, etc.)
    │       │       │           ├── Encodes feature (Naksha.encodeFeature → JBON/JSON bytes)
    │       │       │           ├── Encodes geometry (Naksha.encodeGeometry → TWKB bytes)
    │       │       │           └── Returns Tuple(storage#, map#, col#, fn, version, members, feature)
    │       │       │
    │       │       └── executeWrite(map, col, partition, byOp)
    │       │           ├── PgWriterInsert.execute(conn)  → INSERT SQL
    │       │           ├── PgWriterUpsert.execute(conn)   → CTE-based UPSERT SQL
    │       │           ├── PgWriterUpdate.execute(conn)   → UPDATE with version check
    │       │           └── PgWriterDelete.execute(conn)   → tombstone/PURGE SQL
    │       │
    │       └── Returns SuccessResponse with tuple numbers
    │
    └── 4. Commit
        session.commit()
            ├── Persists transaction record to admin map
            └── conn.commit()
```

## Key Abstractions for New Features

| Concept | File | Purpose |
|---------|------|---------|
| **`Write`** | `lib-model/..request/Write.kt` | DSL for CRUD ops: `createFeature()`, `upsertFeature()`, etc. |
| **`WriteOp`** | `lib-model/..request/WriteOp.kt` | Enum: CREATE, UPSERT, UPDATE, DELETE, PURGE |
| **`Tuple`** | `lib-model/../Tuple.kt` | Immutable feature state: address (storage/map/col/fn/version) + members + feature bytes |
| **`StorageTx`** | `lib-model/../StorageTx.kt` | Builds Tuples from features: `created()`, `updated()`, `deleted()` |
| **`IMemberProcessor`** | `lib-model/../IMemberProcessor.kt` | Extension point for pre-persistence member mutation |
| **`PgWriter`** | `lib-psql/../PgWriter.kt` | Groups writes, dispatches to op-specific writers |
| **`PgWriterInsert`** | `lib-psql/../PgWriterInsert.kt` | SQL INSERT generation |
| **`PgWriterUpsert`** | `lib-psql/../PgWriterUpsert.kt` | CTE-based conditional insert/update |
| **`PgSession`** | `lib-psql/../PgSession.kt` | Connection management, `execute()` routing |

## Extension Points

1. **New storage backend**: Extend `AbstractStorage`, implement `ISession`, `PgWriter`-equivalent classes
2. **Custom member processing**: `session.addMemberProcessor(memberName, processor)` — hooks into pre-persistence pipeline
3. **New write operations**: Add to `WriteOp` enum, create new `PgWriter*` class, add dispatch in `PgWriter.executeWrite()`

## Members Extraction: From NakshaFeature to PostgreSQL Columns

### Three-Stage Write Path

**Stage 1: `StorageTx.buildMembers()` → `IBook` (in-memory members dict)**

File: `lib-model/../StorageTx.kt:114-162`

During `PgWriter.groupOperations()`, each write calls `tx.created()`/`tx.updated()`/`tx.deleted()` which invokes `buildTuple()` → `buildMembers()`. A `HeapBook` is created with all standard members extracted from the `NakshaFeature`:

```
NakshaFeature
  ├── feature.id                          → StandardMembers.Id
  ├── feature.geometry                    → StandardMembers.Geometry (TWKB bytes)
  ├── feature.referencePoint              → StandardMembers.ReferencePoint (TWKB bytes)
  ├── feature.properties.xyz.updatedAt    → StandardMembers.UpdatedAt
  ├── feature.properties.xyz.createdAt    → StandardMembers.CreatedAt
  ├── feature.properties.xyz.author       → StandardMembers.Author
  ├── feature.properties.xyz.authorTs     → StandardMembers.AuthorTimestamp
  ├── feature.properties.xyz.appId        → StandardMembers.AppId
  ├── feature.properties.xyz.changeCount  → StandardMembers.ChangeCount (+1)
  ├── feature.properties.xyz.tags         → StandardMembers.Tags (JSON string)
  ├── feature.properties.xyz.hash         → StandardMembers.Hash (computed)
  ├── feature.properties.xyz.hereTile     → StandardMembers.HereTile (computed)
  ├── feature.properties.xyz.featureType  → StandardMembers.FeatureType
  ├── feature.properties.xyz.cv0-3        → StandardMembers.CustomValue0-3
  ├── feature.properties.xyz.cs0-3        → StandardMembers.CustomString0-3
  └── attachment                          → StandardMembers.Attachment
```

The resulting `IBook` is stored on `Tuple.members`.

**Stage 2: `PgColumnRows[row] = tuple` → Members into column arrays**

File: `lib-psql/../PgColumnRows.kt:384-417`

In the `PgWriterInsert`/`PgWriterUpsert`/`PgWriterUpdate` constructors, the `inRows` (`PgColumnRows`) is populated:

```kotlin
// PgWriterInsert.kt:30-37
for (write in writes) {
    val tuple = write.tuple
    if (tuple != null) {
        inRows[i] = tuple                          // extracts IBook → column arrays
        inRows.setCustomMembers(i, write.feature, members)  // custom members
        i++
    }
}
```

`PgColumnRows.set(row, tuple)` walks `tuple.members` by name and assigns each value into the corresponding typed column array (e.g., `set(row, PgColumn.updated_at, members.getByName("updated_at") as? Int64)`).

**Stage 3: `inRows.values()` → PostgreSQL UNNEST**

File: `lib-psql/../PgWriterInsert.kt:43-98`

The column arrays are passed as prepared statement parameters to a multi-row `UNNEST` INSERT:

```sql
WITH new_row AS (
  SELECT * FROM UNNEST($1, $2, $3, ...) AS t(fn, version, id, feature, ...)
)
INSERT INTO head_table (fn, version, id, feature, ...)
SELECT * FROM new_row
```

### Custom Members Flow

File: `lib-psql/../PgCustomMemberValues.kt`

For user-declared custom members on the collection:

1. **`PgWriterInsert.init`** → `inRows.addCustomMembers(collection.head.members)` — adds column entries for each custom member
2. **`PgColumnRows.setCustomMembers(row, feature, members)`** — walks the feature using each member's `effectivePath()` via `PgCustomMemberValues.walkFeature()`, coerces the type via `PgCustomMemberValues.coerce()`, sets the column value
3. **`PgColumnRows[row] = tuple`** does NOT handle custom members — only the built-in `StandardMembers`

### Members Book Creation — All Locations

| Location | File:Line | Purpose |
|---------|-----------|---------|
| `StorageTx.buildMembers()` | `StorageTx.kt:139` | **Write path** — creates `HeapBook` from `NakshaFeature`, called during tuple construction in `PgWriter.groupOperations()` |
| `Naksha.decodeTuple()` | `Naksha.kt:520` | **Read path** — creates `HeapBook` when decoding a `Tuple` back into a `NakshaFeature` |
| `Naksha.decodeTuple()` | `Naksha.kt:581` | **Read path (alt)** — second decode path for `Tuple` → `NakshaFeature` |
| `PgColumnRows.getTuple()` | `PgColumnRows.kt:304` | **Read path** — creates `PgRowDict` (implements `IBook`) wrapping DB row columns, assigned to `Tuple.members` |

### End-to-End Data Flow

```
                    WRITE PATH                          READ PATH
              ┌─────────────────────┐            ┌──────────────────────┐
 NakshaFeature│  StorageTx          │            │  PgRowDict           │
    properties│  .buildMembers()    │            │  (PgColumnRows[row]) │
              │  ─────────────────  │            │  ─────────────────── │
              │  xyz.updatedAt ─────┼─→ IBook    │  column "updated_at" │──→ Tuple.members.getByName()
              │  xyz.author  ───────┼─→  .put()  │  column "author"     │──→ NakshaFeature.xyz.author
              │  geometry    ───────┼─→  .put()  │  column "geo"        │──→ NakshaFeature.geometry
              │  feature blob       │            │  column "feature"    │──→ NakshaFeature (decode)
              └────────┌────────────┘            └──────────┬───────────┘
                       │                                    │
                       ▼                                    ▲
              ┌─────────────────────┐            ┌──────────────────────┐
              │  PgColumnRows       │            │  PgColumnRows        │
              │  .set(row, tuple)   │──→ SQL     │  .add(cursor)        │
              │  .setCustomMembers()│  UNNEST    │  .getTuple(row)      │
              └─────────────────────┘            └──────────────────────┘
                       │                                    ▲
                       ▼                                    │
              ┌─────────────────────┐            ┌──────────────────────┐
              │  PostgreSQL         │            │  PostgreSQL          │
              │  HEAD table         │  ────┐     │  HEAD/HISTORY        │
              │  (fn, version, id,  │       │     │  SELECT ...          │
              │   feature, geo,     │       │     │  FROM head_table     │
              │   author, ...)      │       │     │  WHERE ...           │
              └─────────────────────┘       │     └──────────────────────┘
                                           │
                              PostgreSQL DB │
                                           │
```
