# Custom Members and Indices

## Status

This document turns the notes in `Hiren_talk.txt` and `sample_properties.json` into a structured task proposal. It records confirmed behavior from the source repositories separately from proposed behavior and open design decisions.

No implementation has been agreed yet for the proposed `customIndexMapping` property.

## Sources

### Notes

- `Hiren_talk.txt`
- `sample_properties.json`
- Webex discussion referenced in `Hiren_talk.txt`

### Source of truth

- Naksha repository: `/Users/grabowsk3/IdeaProjects/naksha2/naksha`
- Naksha branch: `v3_CASL_1890_lib_data_default_indices`
- Naksha inspected commit: `de7f8a793` (`CASL-1890: removed unused imports`)
- ext-util repository: `/Users/grabowsk3/IdeaProjects/naksha-ext-util`
- ext-util branch: `v3`
- ext-util inspected commit: `706208c`

## Objective

Allow a Space or a storage Handler to declare JSON properties that should be materialized as custom collection members and indexed automatically when the collection is created.

The complete flow must:

1. Accept and validate the mapping in Space and supported Handler definitions.
2. Convert each mapping into a Naksha collection `Member` and corresponding `Index`.
3. Apply the conversion in a common collection-creation path that can also be reused by extensions such as ext-util.
4. Preserve the collection schema after creation by rejecting incompatible updates.
5. Translate supported property-path searches into member queries so PostgreSQL can use the generated index.
6. Cover the behavior with unit, integration, and end-to-end tests.

## Terminology

- **Custom index mapping**: the proposed high-level configuration containing a JSON path and member data type, with an optional explicit member name.
- **Member**: a typed, materialized collection column represented by `naksha.model.objects.Member`.
- **Index**: a database index over one or more members, represented by `naksha.model.objects.Index`.
- **Native collection definition**: the existing `NakshaCollection.members` and `NakshaCollection.indices` fields.
- **Generated definition**: native members and indices derived from `customIndexMapping`.

## Proposed Configuration

The notes propose putting the mapping inside the existing collection definition. A valid JSON example is:

```json
{
  "id": "space-id",
  "properties": {
    "collection": {
      "id": "table-name",
      "customIndexMapping": [
        {
          "memberName": "cm01",
          "jsonPath": ["properties", "speedLimit"],
          "dataType": "string"
        }
      ]
    }
  }
}
```

The same collection-level shape should be usable by a supported Handler:

```json
{
  "className": "com.here.naksha.lib.handlers.DefaultStorageHandler",
  "properties": {
    "storageId": "storage-id",
    "collection": {
      "id": "table-name",
      "customIndexMapping": [
        {
          "jsonPath": ["properties", "speedLimit"],
          "dataType": "string"
        }
      ]
    }
  }
}
```

### Important naming correction

The names suggested in the notes (`CM01`, `CI01`) are not valid Naksha internal member/index identifiers. Current validation accepts lowercase names matching the internal identifier convention. Generated names should therefore use a convention such as:

- Members: `cm01`, `cm02`, ...
- Indices: `ci01`, `ci02`, ...

## Confirmed Current Behavior

### Collection model

`NakshaCollection` already supports native custom members and indices:

- `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/objects/NakshaCollection.kt`
- `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/objects/Member.kt`
- `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/objects/Index.kt`
- `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/objects/MemberType.kt`

There is currently no `customIndexMapping` model or conversion logic.

Adding a native member does not automatically add an index. Both must currently be supplied explicitly.

When `NakshaCollection.members` is `null`, storage uses the default XYZ member schema. Supplying any explicit member list changes that behavior to mandatory members plus the supplied custom members. This means the conversion must explicitly decide whether custom mappings augment the full XYZ schema or replace its optional members.

### Space collection definition

Space already exposes a typed collection at `Space.properties.collection`:

- `here-naksha-lib-core/src/jvmMain/java/com/here/naksha/lib/core/models/naksha/Space.java`
- `here-naksha-lib-core/src/jvmMain/java/com/here/naksha/lib/core/models/naksha/SpaceProperties.java`

Therefore a collection-level mapping can naturally be shared by Spaces and Handlers instead of adding separate fields to each top-level model.

### Handler collection definition

`DefaultStorageHandlerProperties` already exposes `properties.collection` as a `NakshaCollection`:

- `here-naksha-lib-handlers/src/jvmMain/java/com/here/naksha/lib/handlers/DefaultStorageHandlerProperties.java`

Custom extension handlers have arbitrary property schemas. Naksha cannot assume that every custom Handler uses the standard collection contract. Validation and conversion must either:

- apply only to Handler types that explicitly support the standard collection definition; or
- define an opt-in contract that custom handlers can implement.

### Effective collection precedence

`DefaultStorageHandler.retrieveCollectionFromRequest()` currently chooses the collection in this order:

1. Handler `properties.collection`.
2. Space `properties.collection`.
3. A default collection using the event target ID.

The current implementation does not merge Handler and Space collection definitions. The Handler definition replaces the Space definition.

Source:

- `here-naksha-lib-handlers/src/jvmMain/java/com/here/naksha/lib/handlers/DefaultStorageHandler.java:568-605`

### Collection creation

The CASL-1890 branch already carries a complete `NakshaCollection` into missing-collection creation instead of carrying only its ID.

Relevant methods:

- `DefaultStorageHandler.retrieveCollectionFromRequest()`
- `DefaultStorageHandler.createMissingCollection()`
- `DefaultStorageHandler.createXyzCollection()`
- `DefaultStorageHandler.normalizeWriteRequest()`
- `CollectionIndexPolicy.normalizeForHubCreation()`
- `RequestHelper.createWriteCollectionsRequest()`

`CollectionIndexPolicy.normalizeForHubCreation()` currently:

- deep-copies the supplied collection;
- assigns the effective collection and catalog/map IDs;
- adds the slim default index set only when both members and indices are absent;
- preserves explicit members, explicit indices, and an explicitly empty index list.

It does not derive members or indices from a high-level mapping.

### Collection update safety

PostgreSQL is intended to reject changes to members, indices, partitioning, collection ID, and storage class after collection creation. The enforcement point exists but is not implemented:

- `here-naksha-lib-psql/src/commonMain/kotlin/naksha/psql/PgCollection.kt:393-408`
- `PgCollection.verifyNewHeadState()` currently contains `TODO: Implement me!`.

Consequently, the current storage update path does not provide the required schema immutability guarantee.

### Space update ordering

`NHSpaceStorageWriter.executeUpdateSpace()` currently sends a collection UPSERT before sending the updated Space to admin storage:

- `here-naksha-lib-hub/src/jvmMain/java/com/here/naksha/lib/hub/storages/NHSpaceStorageWriter.java:180-199`

Validation performed only by `IntHandlerForSpaces` would therefore happen too late to protect the collection update. Validation must occur before this collection UPSERT, and storage should still enforce schema immutability as a safety net.

### Admin validation

`AdminFeatureEventHandler` validates each write before persisting it, but it currently:

- distinguishes only DELETE from non-DELETE;
- does not load the existing persisted feature;
- does not expose old/new state comparison for UPDATE or UPSERT.

Relevant classes:

- `here-naksha-lib-handlers/src/jvmMain/java/com/here/naksha/lib/handlers/internal/AdminFeatureEventHandler.java`
- `here-naksha-lib-handlers/src/jvmMain/java/com/here/naksha/lib/handlers/internal/IntHandlerForSpaces.java`
- `here-naksha-lib-handlers/src/jvmMain/java/com/here/naksha/lib/handlers/internal/IntHandlerForEventHandlerConfigs.java`

`IntHandlerForSpaces` does not validate collection members or indices. `IntHandlerForEventHandlerConfigs` performs special validation for known built-in handlers and accepts unknown custom handler classes without inspecting a collection definition.

### Property queries

HTTP property parameters are converted into legacy property-query objects, for example `p.speedLimit` becomes the JSON path `properties.speedLimit`:

- `here-naksha-app-service/src/jvmMain/java/com/here/naksha/app/service/http/ops/PropertyQueryUtil.java`

PostgreSQL query generation uses `ReadFeatures.queryMembers` for member/column predicates. It does not translate the legacy property query into member operations:

- `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/request/ReadFeatures.kt`
- `here-naksha-lib-model/src/commonMain/kotlin/naksha/model/request/ops/QueryConverter.kt`
- `here-naksha-lib-psql/src/commonMain/kotlin/naksha/psql/PgQueryWhereBuilder.kt`

As a result, an API search by the configured JSON property currently does not prove or guarantee use of the custom member index.

### ext-util HeteroStorageHandler

`HeteroStorageHandler` stores complete `NakshaCollection` objects in `momTypeCollectionMap`, but its missing-collection path discards those definitions:

- `src/main/java/com/here/naksha/ext/util/handlers/HeteroStorageHandler.java:132-174`
- `src/main/java/com/here/naksha/ext/util/handlers/HeteroStorageHandler.java:422-443`
- `src/main/java/com/here/naksha/ext/util/handlers/prop/HeteroStorageHandlerProperties.java`
- `src/main/java/com/here/naksha/ext/util/model/LayeredCollections.java`

It collects only collection IDs and creates each collection with:

```java
new NakshaCollection(colId, mapId)
```

Configured members, indices, and other collection metadata are therefore lost during auto-creation.

The ext-util `v3` branch also overrides the old four-argument `createMissingCollection()` method. The Naksha CASL-1890 branch uses a five-argument method that includes the complete collection definition. ext-util must be updated when it adopts the corresponding Naksha version.

## Functional Requirements

### FR1: Shared mapping model

Define a shared collection-level mapping model usable from both Space and supported Handler collection definitions.

Each entry must contain:

- `jsonPath`: non-empty JSON path identifying the source value;
- `dataType`: supported `MemberType` value;
- `memberName`: optional only if deterministic generation is adopted.

Each mapping creates exactly:

- one custom `Member`; and
- one single-member `Index` targeting that member.

Clients should not need to provide a separate custom index definition for these generated members.

### FR2: Deterministic native conversion

Convert the mapping into native `NakshaCollection.members` and `NakshaCollection.indices` before the collection create request is sent to storage.

The conversion must:

- operate on a deep copy and not mutate Space or Handler configuration;
- generate valid, deterministic lowercase names when names are omitted;
- preserve explicitly provided valid names if that option is supported;
- reject duplicate member names, duplicate index names, and conflicting JSON paths;
- preserve required/default collection members and indices according to the agreed schema policy;
- reject unsupported member/index type combinations before database DDL is attempted;
- produce the same native collection definition anywhere collection creation is initiated.

### FR3: Common creation API

Provide a reusable method that accepts a complete collection definition and returns a normalized collection write request. Conceptually:

```java
WriteRequest createCollectionWriteRequest(
    NakshaCollection collection,
    String collectionId,
    String catalogId
)
```

The method should combine:

1. high-level mapping conversion;
2. collection ID/catalog ID normalization;
3. default-index policy;
4. `RequestHelper.createWriteCollectionsRequest()`.

It should build a request only. Transaction ownership must remain with the caller because `HeteroStorageHandler` creates multiple collections in one write session and has custom conflict handling.

The common method must be used by:

- default missing-collection creation;
- direct collection CREATE/UPSERT normalization where applicable;
- ext-util `HeteroStorageHandler` collection auto-creation;
- any other creation path expected to honor the same contract.

### FR4: Space validation

On Space create or first valid collection definition:

- validate mapping structure, names, paths, types, duplicates, and indexability;
- reject invalid configuration before attempting collection creation.

On Space update:

- compare the persisted and proposed effective collection schema;
- reject changes to custom mappings, generated members, or generated indices once the physical collection exists;
- perform this validation before `NHSpaceStorageWriter` sends a collection UPSERT.

The expected behavior for adding `properties.collection` to an existing Space must be explicitly decided because `SpaceApiTest.tc0264_testUpdateSpaceAddingCollectionLater()` currently allows it.

### FR5: Handler validation

For `DefaultStorageHandler` and other handlers that opt into the standard collection contract:

- apply the same mapping validation as for Spaces;
- reject schema-changing updates after collection creation;
- use shared validation logic rather than duplicating Space and Handler rules.

Unknown custom handlers must not be interpreted as standard storage handlers merely because they have arbitrary properties named `collection`.

### FR6: Storage-level immutability

Implement `PgCollection.verifyNewHeadState()` so direct collection writes cannot bypass Space/Handler validation.

At minimum, reject changes that alter the effective physical members or indices. The existing method contract also requires checking shift, ID/collection number, storage class, and partitions.

Comparison should use effective physical schema semantics rather than raw list order.

### FR7: Property-query pushdown

When a property query targets a JSON path represented by a custom member, translate supported operations to `queryMembers` operations against the member name before forwarding the request to storage.

The translator must:

- use the effective collection selected by Handler-over-Space precedence;
- match the complete JSON path exactly;
- preserve AND, OR, and NOT semantics;
- translate only operations supported by the member type and storage operation model;
- avoid partial translation where it changes query semantics, especially within OR expressions;
- preserve a property filter as a correctness check if necessary;
- combine correctly with tags, spatial, and existing member queries.

The conversion is most naturally invoked by `DefaultStorageHandler`, because that handler knows the effective collection definition. The query-tree conversion itself should be reusable and independent of PostgreSQL.

### FR8: ext-util compatibility

Update `HeteroStorageHandler` to:

- override the new complete-collection creation hook;
- iterate complete configured `NakshaCollection` definitions instead of IDs;
- call the shared collection request builder for every collection;
- preserve its single-session multi-collection creation behavior;
- preserve its existing handling of `COLLECTION_EXISTS` conflicts;
- avoid mutating configured collection objects;
- reject conflicting definitions when the same collection ID appears in multiple MOM/layer mappings.

The fallback to `super.createMissingCollection()` must retain the complete collection supplied by the base handler.

## Validation Rules to Define

The shared validator should cover at least:

- mapping list is absent or valid; distinguish `null` from an explicitly empty list if behavior differs;
- `jsonPath` is present and non-empty;
- every path element is a supported string or array index;
- `dataType` maps to a supported `MemberType`;
- the selected type can be indexed by the target storage;
- explicit `memberName`, if allowed, is a valid internal identifier;
- generated names do not collide with standard or custom members;
- generated index names do not collide with standard or custom indices;
- duplicate mappings are rejected;
- an index references exactly the generated member;
- the total member count does not exceed the current 64-member limit;
- Handler and Space definitions obey the selected precedence/conflict policy.

## Recommended Design

### Model ownership

Put the mapping model in `here-naksha-lib-model` as part of `NakshaCollection`, because the mapping describes collection schema and must work equally for Spaces, built-in Handlers, and opted-in extensions.

Do not add separate copies of the model to `SpaceProperties` and `DefaultStorageHandlerProperties`.

### Conversion ownership

Keep deterministic mapping-to-member/index conversion in a shared core/model utility. Extend or compose `CollectionIndexPolicy`; do not implement name generation separately in Space, Handler, PostgreSQL, and ext-util code.

Expose a reusable normalized collection request builder to extensions. Do not expose `DefaultStorageHandler.createXyzCollection()` as the shared API because it owns a session and transaction, while ext-util requires several creates in one session.

### Validation ownership

Use one shared collection-schema validator from both `IntHandlerForSpaces` and `IntHandlerForEventHandlerConfigs`.

Extend the admin validation flow to support update-aware validation with persisted old state, but also fix Space update ordering so validation happens before the custom-space collection UPSERT.

Implement storage-level verification independently as defense in depth.

### Naming

Prefer deterministic generated names and treat `memberName` as optional only if the generated identity is stable across serialization and updates.

If generation depends on list position, reordering the mappings changes identity and can make an otherwise equivalent update look like a schema change. A stable name derived from the path, or an immutable persisted generated name, is safer than regenerating `cm01`, `cm02` solely from iteration order.

If sequential names are required, mapping order must be explicitly defined as schema-significant and must remain immutable.

## Work Breakdown

### Naksha model and policy

1. Add the shared custom mapping model and serialization tests.
2. Define supported `MemberType` values and indexability rules.
3. Implement shared validation and deterministic name generation.
4. Implement mapping-to-native-member/index conversion.
5. Add the normalized collection-create request builder.
6. Integrate it into `DefaultStorageHandler` creation and direct collection-write paths.

### Admin and storage validation

1. Add shared Space/Handler collection configuration validation.
2. Add persisted old/new comparison for updates.
3. Move or add Space validation before the collection UPSERT in `NHSpaceStorageWriter`.
4. Implement `PgCollection.verifyNewHeadState()` as a storage safety net.
5. Define behavior for adding a collection definition to an existing Space.

### Query path

1. Implement exact JSON-path-to-member lookup.
2. Implement supported property-query-to-member-operation conversion.
3. Integrate conversion after effective collection resolution in `DefaultStorageHandler`.
4. Preserve semantics for mixed property, tag, spatial, AND, OR, and NOT queries.
5. Add a test that verifies database index use, not only correct returned rows.

### ext-util

1. Upgrade to the Naksha version containing the new collection hook and shared builder.
2. Adapt `HeteroStorageHandler.createMissingCollection()` to the complete collection signature.
3. Replace ID-only collection gathering with complete definitions keyed by ID.
4. Preserve per-collection members and indices during multi-collection creation.
5. Add duplicate-ID conflict validation.
6. Add unit and PostgreSQL integration tests.

## Test Plan

### Model and conversion tests

- Mapping serializes and deserializes in Space and Handler collection properties.
- Omitted names are generated deterministically.
- Explicit valid names are preserved if supported.
- Invalid uppercase or otherwise invalid names are rejected.
- Invalid/empty paths and unknown data types are rejected.
- Duplicate names, paths, and generated index names are rejected.
- Every mapping generates one member and one index.
- Source configuration is not mutated.
- Default member/index policy is preserved as agreed.

### DefaultStorageHandler tests

- Handler collection mapping takes precedence over Space mapping.
- Space mapping is used when the Handler has no collection definition.
- Missing collection creation contains generated native members and indices.
- Direct collection create/upsert uses the same normalization.
- Existing explicit members/indices interact with generated definitions as specified.
- Unsupported custom handlers are not incorrectly validated as standard storage handlers.

### Update tests

- Updating unrelated Space metadata remains allowed.
- Updating unrelated Handler metadata remains allowed.
- Changing path, type, member name, generated member, or generated index is rejected after creation.
- Reordering behavior follows the agreed naming/schema rule.
- Direct collection update cannot bypass storage immutability validation.
- Adding a collection definition later follows the explicitly agreed policy.

### Query tests

- API query by the mapped property returns correct results.
- The request reaching storage contains a member predicate for the generated member.
- Numeric and string operations use type-correct member operations.
- Unsupported operations safely retain property filtering or are rejected according to policy.
- AND/OR/NOT and mixed indexed/non-indexed predicates preserve semantics.
- PostgreSQL `EXPLAIN`, catalog inspection, or another reliable mechanism confirms that the generated index is used.

### ext-util tests

- Property boxing preserves complete collection members, indices, paths, and types.
- Heterogeneous collections retain distinct definitions during auto-creation.
- Existing and missing collections can be handled in the same transaction.
- `COLLECTION_EXISTS` remains non-fatal.
- Other errors roll back all new collection creates.
- Equal duplicate collection definitions create once.
- Conflicting definitions for one collection ID are rejected.
- The fallback base path preserves the complete collection definition.

## Acceptance Criteria

The task is complete when:

1. A valid mapping can be supplied through both a Space and a supported Handler definition.
2. Collection creation produces the expected materialized member and physical index.
3. Default and ext-util Hetero collection creation use the same shared conversion policy.
4. Invalid mappings are rejected before storage DDL execution.
5. Schema-changing Space, Handler, and direct collection updates are rejected.
6. A supported API property query is pushed down to the generated member predicate.
7. An integration test demonstrates actual use of the generated PostgreSQL index.
8. Existing collection creation, update, and query behavior remains covered by regression tests.

## Open Decisions

These points require agreement before implementation:

1. Is `customIndexMapping` the required public API, or should clients use native `members` and `indices` directly?
2. Do custom mappings augment the full XYZ member/index schema, or switch the collection to mandatory members plus custom members?
3. Is `memberName` allowed, optional, or forbidden?
4. If names are generated, are they path-derived, persisted, or position-derived?
5. Is mapping order schema-significant?
6. Which `MemberType` values are allowed and indexable for this feature?
7. What happens if Handler and Space both define collection mappings: current Handler precedence, merge, or validation error?
8. How does a custom extension Handler explicitly opt into the standard collection contract?
9. Is adding a collection/mapping to an existing Space allowed when the collection may already have been implicitly created?
10. Which property-query operations must support index pushdown in the first version?
11. What is the required fallback for unsupported or partially translatable property queries?
12. Must query pushdown work for all storage implementations or only storages advertising member-query support?
13. How should duplicate collection IDs with different definitions be handled in Hetero configuration?
14. Is compatibility required for external handlers compiled against the old four-argument `createMissingCollection()` hook?

## Existing Tests Worth Extending

### Naksha

- `here-naksha-lib-core/src/jvmTest/java/com/here/naksha/lib/core/util/CollectionIndexPolicyTest.java`
- `here-naksha-lib-handlers/src/jvmTest/java/com/here/naksha/lib/handlers/DefaultStorageHandlerTest.java`
- `here-naksha-lib-handlers/src/jvmTest/java/com/here/naksha/lib/handlers/internal/IntHandlerForSpacesTest.java`
- `here-naksha-lib-model/src/commonTest/kotlin/naksha/model/MemberTest.kt`
- `here-naksha-lib-psql/src/commonTest/kotlin/naksha/psql/CollectionTests.kt`
- `here-naksha-lib-psql/src/commonTest/kotlin/naksha/psql/MemberValueMaterializationTest.kt`
- `here-naksha-lib-psql/src/commonTest/kotlin/naksha/psql/ChainCollectionTest.kt`
- `here-naksha-app-service/src/jvmTest/java/com/here/naksha/app/service/SpaceApiTest.java`
- `here-naksha-app-service/src/jvmTest/java/com/here/naksha/app/service/EventHandlerApiTest.java`
- `here-naksha-app-service/src/jvmTest/java/com/here/naksha/app/service/SearchFeaturesTest.java`

`ChainCollectionTest.shouldFindFeatureByRightFnPropertyFilter()` currently filters in Kotlin after reading features. It does not demonstrate an indexed database lookup and should not be treated as sufficient end-to-end coverage.

### ext-util

- `src/test/java/com/here/naksha/ext/util/HeteroStorageHandlerDBTest.java`
- `src/test/java/com/here/naksha/ext/util/handlers/prop/HeteroStorageHandlerPropertiesTest.java`
- `src/test/java/com/here/naksha/ext/util/model/LayeredCollectionsTest.java`

## Native-member diagnostic checkpoint

This checkpoint deliberately does **not** implement `customIndexMapping`, collection-schema immutability, ext-util integration, migrations, or REST `p.*` query pushdown. It exercises the existing native `NakshaCollection.members` and `NakshaCollection.indices` contract through `DefaultStorageHandler`.

### Tests

- `DefaultStorageHandlerTest` verifies that handler and Space collection definitions preserve custom member names, types, numeric JSON-path segments, and custom indices during missing-collection auto-creation. Handler configuration retains its existing precedence and the source objects are not mutated.
- `CustomMemberQueryCharacterizationTest` records current SQL-builder and JDBC behavior without production fixes.
- `CustomMemberIndexProbeIT` is an opt-in retained-database test. It creates handler and Space configuration through REST, triggers collection creation with an HTTP feature write, performs native `queryMembers` reads through a real `DefaultStorageHandler`, inspects PostgreSQL DDL and plans, and intentionally leaves the data in place.

The retained probe is guarded by environment conditions. It cannot run as part of a normal app-service test invocation.

### Run command

Use a disposable development database. The data schema is reset at test startup and retained after the test. Use a new lowercase run ID for each invocation because the admin Handler and Space definitions are retained too.

```bash
NAKSHA_RUN_CUSTOM_MEMBER_PROBE=true \
NAKSHA_APP_SERVICE_TEST_CONTEXT=LOCAL_STANDALONE \
NAKSHA_CUSTOM_MEMBER_PROBE_RUN_ID=probe_01 \
NAKSHA_TEST_ADMIN_DB_URL='jdbc:postgresql://localhost:5432/naksha_probe_admin?user=postgres&password=password' \
NAKSHA_TEST_DATA_DB_URL='jdbc:postgresql://localhost:5432/naksha_probe_data?user=postgres&password=password' \
gradle :here-naksha-app-service:jvmTest \
  --tests com.here.naksha.app.service.CustomMemberIndexProbeIT
```

The test prints the actual database, schema, storage, Handler, Space, collection, and rejected-collection IDs. Inspect the database before another app-service test run, because `LocalTestContext` resets `naksha_data_schema` at startup.

### Read-only PostgreSQL inspection

The examples below assume `NAKSHA_CUSTOM_MEMBER_PROBE_RUN_ID=probe_01`.

```sql
-- Exact physical columns and types.
SELECT ordinal_position, column_name, data_type, udt_name
FROM information_schema.columns
WHERE table_schema = 'naksha_data_schema'
  AND table_name = 'custom_member_probe_probe_01'
ORDER BY ordinal_position;

-- HEAD and HISTORY index names and definitions.
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'naksha_data_schema'
  AND (
    tablename = 'custom_member_probe_probe_01'
    OR tablename LIKE 'custom_member_probe_probe_01$hst$%'
  )
ORDER BY tablename, indexname;

-- Raw materialized values. JSON paths are not catalog metadata; these values prove path resolution.
SELECT probe_bool, probe_i8, probe_i16, probe_i32, probe_i64,
       probe_f32, probe_f64, probe_label, probe_bytes, probe_tn,
       probe_spatial, probe_tag_map, probe_tag_map_array, probe_tag_list,
       probe_missing, probe_explicit_null, probe_wrong_int, probe_out_of_range_i8
FROM naksha_data_schema.custom_member_probe_probe_01
ORDER BY probe_label;

-- Repeat for other predicates emitted by the test.
SET enable_seqscan = off;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
SELECT *
FROM naksha_data_schema.custom_member_probe_probe_01
WHERE probe_i64 >= 40;
```

### Characterization matrix

| Scenario | Checkpoint expectation |
|---|---|
| Scalar/string custom columns and btree indices | Working; exact name and PostgreSQL type asserted |
| `TAG_LIST` column, containment query, and GIN index | Working |
| `TAG_MAP` DDL, materialization, and GIN index | Working |
| `TAG_MAP_FROM_ARRAY` DDL and GIN index | Working, but an HTTP JSON array currently reaches coercion as `AnyList` and materializes as SQL `NULL` |
| `SPATIAL` DDL and GiST index | Working at DDL/direct-SQL level |
| Nested object path | Working; `properties.details.label` materializes as `probe_label` |
| Numeric array path | Working; `properties.samples[1]` materializes as `probe_i16` |
| Missing path, explicit null, wrong type, out-of-range numeric | Materializes as SQL `NULL` |
| Numeric query argument metadata | Currently recorded as `text` |
| Numeric JDBC scalar binding | Runtime value still uses numeric setters such as `setLong` and `setDouble` |
| `TagMapHasKey` | Expected current failure: raw JSONB `?` is interpreted by PgJDBC as a placeholder; future SQL must emit `??` |
| `TagMapHasAnyOf` / `TagMapHasAllOf` | Expected current mismatch: builder binds inherited map keys instead of `tagKeys`, in addition to the PgJDBC operator issue |
| Custom-member `Intersects` | Expected current mismatch: generated SQL references standard `geo` rather than the operation's `at` member |
| Tag-map value comparisons | Blocked by unfinished `PgType.ofValue` |
| Boolean custom index | Expected explicit collection-creation rejection with no retained table |
| Index referencing an absent member | Expected explicit collection-creation rejection with no retained table |

Runtime PostgreSQL results remain unconfirmed until the opt-in probe is executed against the explicitly configured standalone databases. The ordinary Docker-backed lib-psql tests are not a substitute for this retained checkpoint in the current environment because Testcontainers discovery previously failed before test logic ran.
