# The Naksha Data Model
The Naksha Data Model _(**NDM**)_ is made to exchange data in the form of [GeoJSON] features between different applications, components, services, and storages. The data model is designed to support efficient storage, retrieval, and query of objects. It's optimized to exchange the data serialized into [JSON], [GeoJSON], [protobuf], and [JBON]. [JBON] is a special binary encoding, highly compact, mostly immutable, and that does not require parsing to read the data, developed specifically for the Naksha data model.

The data model supports operations to manage the data lifecycle, including creation, update, and deletion. It supports in maintaining a history of changes. The data model is also designed to support efficient querying of the data, including queries for specific versions and queries for the latest version _(HEAD)_.

The data model is an abstraction layer that allows to decouple the physical storage from the logical structure of the data. This allows for flexibility in the choice of storage technology and allows for future changes to the storage technology without affecting the logical structure of the data.

## Data Types
To allow interoperability between different storages, applications, modules, and services, the data model supports a set of pre-defined supported data types:

| Java                 | Index | Type-Emum _(Name)_  | Javascript      | Description                                                                                                                         |
|----------------------|-------|---------------------|-----------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `Undefined`          |       | `UNDEFINED`         | `undefined`     | The undefined type, a singleton in Java.                                                                                            |
| `null`               |       | `NULL`              | `null`          | A boolean.                                                                                                                          |
| `boolean`            | yes   | `BOOL`              | `Boolean`       | A boolean.                                                                                                                          |
| `byte`               | yes   | `BYTE`              | `number`        | A 8-bit integer.                                                                                                                    |
| `short`              | yes   | `SHORT`             | `number`        | A 16-bit integer.                                                                                                                   |
| `int`                | yes   | `INT`               | `number`        | A 32-bit integer.                                                                                                                   |
| `long`               | yes   | `LONG`              | `BigInt`        | A 64-bit integer.                                                                                                                   |
| `float`              | yes   | `FLOAT`             | `number`        | A 32-bit floating point number.                                                                                                     |
| `double`             | yes   | `DOUBLE`            | `number`        | A 64-bit floating point number.                                                                                                     |
| `byte[]`             | yes   | `BYTEA`             | `Int8Array`     | A byte-array.                                                                                                                       |
| `short[]`            |       | `SHORTA`            | `Int16Array`    | A 16-bit integer array.                                                                                                             |
| `int[]`              |       | `INTA`              | `Int32Array`    | A 32-bit integer array.                                                                                                             |
| `long[]`             |       | `LONGA`             | `BigInt64Array` | A 64-bit integer array.                                                                                                             |
| `float[]`            |       | `FLOATA`            | `Float32Array`  | A 32-bit floating point number array.                                                                                               |
| `double[]`           |       | `DOUBLEA`           | `Float64Array`  | A 64-bit floating point number array.                                                                                               |
| `String`             | yes   | `STRING`            | `String`        | A text of [UNICODE] code-points.                                                                                                    |
| `Geometry`           |       |                     |                 | `org.locationtech.jts.geom.Geometry` - Interface for all geometries, [GeoJSON] compatible.                                          |
| `GeometryCollection` |       | `GEO_COLLECTION`    |                 | `org.locationtech.jts.geom.GeometryCollection`                                                                                      |
| `Point`              | yes   | `POINT`             |                 | `org.locationtech.jts.geom.Point`                                                                                                   |
| `MultiPoint`         | yes   | `MULTI_POINT`       |                 | `org.locationtech.jts.geom.MultiPoint`                                                                                              |
| `LineString`         | yes   | `LINE_STRING`       |                 | `org.locationtech.jts.geom.LineString`                                                                                              |
| `MultiLineString`    | yes   | `MULTI_LINE_STRING` |                 | `org.locationtech.jts.geom.MultiLineString`                                                                                         |
| `Polygon`            | yes   | `POLYGON`           |                 | `org.locationtech.jts.geom.Polygon`                                                                                                 |
| `MultiPolygon`       | yes   | `MULTI_POLYGON`     |                 | `org.locationtech.jts.geom.MultiPolygon`                                                                                            |
| `JsonObject`         |       |                     |                 | The base class for all [JSON] data types that allow proxy linking.                                                                  |
| `JsonArray`          |       | `ARRAY`             |                 | A list of values, extends [JsonObject].                                                                                             |
| `JsonMap`            |       | `MAP`               |                 | A set of key-value pairs in insertion order, extends [JsonObject].                                                                  |
|                      |       |                     |                 |                                                                                                                                     |
| `JsonProxy`          |       |                     |                 | Abstract base class for all proxies that can be linked to a [JsonObject] to extend the object with custom functions.                |
| `JsonMapProxy`       |       |                     |                 | A [JsonProxy] that can be linked to a [JsonMap] to extend the map with custom functions.                                            |
| `JsonArrayProxy`     |       |                     |                 | A [JsonProxy] that can be linked to a [JsonList] to extend the list with custom functions.                                          |
| `JsonTags`           | _yes_ |                     |                 | A [JsonProxy] for a set of "flat" key-value pairs, linked to [JsonMap] or [JsonList]. The values must be [indexable].               |
| `JsonFeature`        |       |                     |                 | A [JsonProxy] representing a mutable [GeoJSON] feature linked to a [JsonMap].                                                       |
| `JsonBytes`          |       |                     |                 | A wrapper for `byte[]`, `short[]`, `int[]`, `long[]`, `float[]`, or `double[]`, granting low level access, implementing [NdmBytes]. |
|                      |       |                     |                 |                                                                                                                                     |
| `NdmBytes`           | _yes_ |                     |                 | An interface for low-level access to primitive arrays _(`byte[]`, `short[]`, `int[]`, `long[]`, `float[]`, or `double[]`)_.         |
| `NdmTupleId`         |       |                     |                 | The immutable im-memory representation of a unique identifier of a single [tuple]; wraps a [string].                                |
| `NdmTupleNumber`     |       |                     |                 | The immutable im-memory representation of a unique identifier of a single [tuple]; wraps [NdmBytes].                                |
| `NdmVersion`         |       |                     |                 | The im-memory representation of a [version] within a [database]; wraps a [Tuple].                                                   |
| `NdmDatabase`        |       |                     |                 | The im-memory representation of a [database].                                                                                       |
| `NdmMap`             |       |                     |                 | The im-memory representation of a [catalog] within a [database].                                                                    |
| `NdmCollection`      |       |                     |                 | The im-memory representation of a [collection] within a [catalog].                                                                  |
| `NdmFeature`         |       |                     |                 | The im-memory representation of a [record] within a [collection].                                                                   |
| `NdmTuple`           |       |                     |                 | The im-memory representation of a [tuple] within a [record].                                                                        |
| `NdmBook`            |       |                     |                 | The im-memory representation of a [book].                                                                                           |
| `NdmKind`            |       |                     |                 | The im-memory representation of a [kind].                                                                                           |
| `NdmMember`          |       |                     |                 | The im-memory representation of a [member].                                                                                         |
|                      |       |                     |                 |                                                                                                                                     |
| `JsonEnum`           |       |                     |                 | A special enumeration implementation that essentially is always encoded as string or number.                                        |
| `JsonVersion`        |       |                     |                 | The mutable [version] representation as [JsonProxy] linked to a [JsonMap].                                                          |
| `JsonDatabase`       |       |                     |                 | The mutable [database] as [JsonProxy] linked to a [JsonMap].                                                                        |
| `JsonCatalog`        |       |                     |                 | The mutable [catalog] as [JsonProxy] linked to a [JsonMap].                                                                         |
| `JsonCollection`     |       |                     |                 | The mutable [collection] as [JsonProxy] linked to a [JsonMap].                                                                      |
| `JsonTuple`          |       |                     |                 | The mutable [tuple] as [JsonProxy] linked to a [JsonMap].                                                                           |
| `JsonBook`           |       |                     |                 | The mutable [book] as [JsonProxy] linked to a [JsonMap].                                                                            |
| `JsonKind`           |       |                     |                 | The mutable [kind] as [JsonProxy] linked to a [JsonMap].                                                                            |
| `JsonMember`         |       |                     |                 | The mutable [member] as [JsonProxy] linked to a [JsonMap].                                                                          |

All data must be represented using these data types to ensure interoperability between different components, storages, and services.

This design is important for many of the features offered by the data model, for example to be able to effectively calculate the _logical bytes_ of any _**unit**_, so data can be compared. Calculating differences, patches, and applying the patches, and/or merging of arbitrary data requires this design. The data model supports all rudimentary components necessary to build more complex structures:

- Primitives _(boolean, integer, float)_
- Strings
- Maps
- Arrays
- Geometries

### Indexable
The following sections will sometimes refer to `indexable` types, this refers to one of the above indexable data types.

When a byte-array is compared, then the compare **must** be done byte-by-byte. The smaller byte decides which byte-array is smaller. When the end of a byte-array is reached, and so far all bytes are equal, but one array does have more bytes, then the shorter array is _(by definition)_ less than the longer array. Otherwise, if both arrays are of same length, and all bytes equal, they are equal.

All strings must be interned and encoded in [NFC] form. In memory strings are kept in UTF-16 encoding, in the database they are stored in UFF-8 encoding, and in [JBON] a special encoding is used. Within the database, strings are treated like `C` strings, and are therefore sorted the same way. This can lead to unexpected sorting results.

The data model differentiates mainly between shared immutable binary data, encoded in [JBON], and mutable thread-local data that is represented as [JSON] heap objects. The immutable binary data is used for caching, cross component access, or fast transportation between services, and for very fast lookups _(without the need to decode the [JBON] into [JSON] heap objects)_.

## Proxies
Having to work with unstructured data is extremely error-prone. Therefore, accessing your own data using `lib-data` supports proxies. A proxy is a data-model that can be added to arbitrary data at runtime. The following example shows a proxy for a simple data model, where a [GeoJSON] feature has a `name` and `age` in the `properties`:

```java
package naksha.data;

public class Example extends JsonMapProxy {
  // This constructor is used to create a new Example instance.
  public Example() {
    super(new JsonMap());
    // We can do normal initialization here, for example setting default values.
    setName("Hello World");
    setAge(18);
  }
  // This constructor is called by the "proxy" method to link a proxy to an existing JsonMap, for example when deserializing from JSON.
  public Example(@NotNull JsonMap map) {
    super(map);
    // We can update internal caches and more, when this happens.
    // It is guaranteed to happen only ones in the lifetime of every object, proxies are never unlinked or relinked!
  }
  private static final String NAME_KEY = Data.intern("name");
  private static final String AGE_KEY = Data.intern("age");
  public boolean hasName() { return this.map.containsKey(NAME_KEY); }
  public @Nullable String getName() { return this.map.getString(NAME_KEY); }
  public @Nullable String setName(@Nullable String name) { return this.map.setString(NAME_KEY, name); }
  public @Nullable String removeName() { return this.map.removeString(NAME_KEY); }
  public boolean hasAge() { return this.map.containsKey(AGE_KEY); }
  public @Nullable Integer getAge() { return this.map.getInt(AGE_KEY); }
  public @Nullable Integer setAge(@Nullable Integer age) { return this.map.setInt(AGE_KEY, name); }
  public @Nullable Integer removeAge() { return this.map.removeInt(AGE_KEY); }
}
public class ExampleUsage {
  public static void demo(@NotNull JsonFeature feature) {
     JsonMap properties = feature.getMap(Const.PROPERTIES);
     assert properties != null;
     final Example example = properties.proxy(Example.class);
     // Proxies are cached, so the same proxy instance is returned for the same JsonMap.
     assert example == properties.proxy(Example.class);
     String name = example.getName();
     Integer age = example.getAge();
     // Do something with name and age ...
  }
}
```

## Database
The `Database` represents a unique database, that can be stored at different places. However, only one of the places should be the primary storage, so every storage should know if it is a replication or main storage. Each database has one internal [catalog] named `naksha~admin`. This is a virtual [catalog] that is used to access the management data. This `naksha~admin` [catalog] contains by definition the following [collections]:

- Meta _(`naksha~meta`)_: A collection that stores internal metadata, for example the [record] of the database configuration itself _(i.e. if this is a replica)_.
- Catalogs _(`naksha~catalogs`)_: A collection that stores all the [records] of all [catalogs].
- Versions _(`naksha~versions`)_: A collection that stores all the [records] of all [versions], so basically a transaction history.
- Books _(`naksha~books`)_: A collection that stores `global` [books].

The admin [catalog] may contain more [collections], but these are the only mandatory ones.

```java
package naksha.data;

public class Database {
  Database(long number) { this.number = number; }
  /** The unique number of the database. */
  public final long number;
  /** The weak reference to this database. */
  public @NotNull WeakReference<Database> weakRef();
  /** The admin-catalog of the database. */
  public @NotNull AdminCatalog adminCatalog(); // "naksha~admin": 0
  /** The catalog with the given catalog-number. */
  public @NotNull DataCatalog catalog(int catalogNumber); // 1+
}
public class AdminCatalog extends DataCatalog { // "naksha~admin": 0
  AdminCatalog(@NotNull Database db) { super(db, 0); }
  public @NotNull DataCollection meta(); // "naksha~meta": 0
  public @NotNull DataCollection catalogs(); // "naksha~catalogs": 1
  public @NotNull DataCollection versions(); // "naksha~versions": 2
  public @NotNull DataCollection books(); // "naksha~books": 3
}
```

Only a [storage] can create a database.

## Catalog
A catalog is sub-set of data within each [database]. The catalog represents for example a map, region, or some other organizational unit. Each catalog contains [collections], a deeper sub-set of data organization. The catalog itself is as well a [record] that is tracked in the admin-catalog of the database.

Within every catalog there is one internal [collection] named `naksha~collections`. This is a special [collection] that is used to store the [records] of all [collections] of the [catalog]. It is used for administration.

```java
package naksha.data;
public class DataCatalog {
  DataCatalog(@NotNull Database db, int number) { this.db = db; this.number = number; }
  /** The database to which the catalog belongs. */
  public final @NotNull Database db;
  /** The unique number of the catalog. */
  public final int number;
  /** The weak reference to this catalog. */
  public final @NotNull WeakReference<DataCatalog> weakRef = new WeakReference<>(this);
  /** The collection storing the collection records of all collections of this catalog, excluding the collections collection itself. */
  public @NotNull DataCollection collections(); // "naksha~collections": 0
  /** The collection with the given collection-number. */
  public @NotNull DataCollection collection(int collectionNumber); // 1+
}
```

## Collection
A collection is a set of [records]. All of them share the same structure. A collection is logically split into _HEAD_ and _HISTORY_. The collection does maintain indices to efficiently query the [records]. The _HEAD_ section of the collection contains only the latest [tuple] of each [record], while the _HISTORY_ section contains all older [tuples] _(states)_.

Beware that replicas do not need to have the same structure as the source, so replication is done logical. The objects stored will always have the same hash, and the same content, but they can be encoded differently, with different indices.

```java
package naksha.data;
public final class DataCollection {
  DataCollection(@NotNull DataCatalog catalog, int number) { this.catalog = catalog; this.number = number; }
  /** The catalog to which the collection belongs. */
  public final @NotNull DataCatalog catalog;
  /** The unique number of the collection. */
  public final int number;
  /** The weak reference to this collection. */
  public final @NotNull WeakReference<DataCollection> weakRef = new WeakReference<>(this);
  /** The record with the given record-number. */
  public @NotNull DataRecord record(int recordNumber);
}
```

## Record
A database record represents a unique object with a unique record-number, optionally with a unique identifier. It is a container for the chain of mostly immutable temporal states called [tuple]. Each state is identified by its version, with links to the previous and next version. The record shares the database-number, catalog-number, collection-number, record-number, identifier, and created-at timestamp with all its [tuple].

There is a logical representation of a record within the data model, and in memory, but the same is not necessarily true for within the [storage]. Storages can extrapolate the logical record from the stored [tuple].

```java
package naksha.data;
public class DataRecord {
  DataRecord(@NotNull DataCollection collection, long number) { this.collection = collection; this.number = number; }
  /** The collection to which the record belongs. */
  public final @NotNull DataCollection collection;
  /** The unique number of the record. */
  public final long number;
  /** The weak reference to this record. */
  public final @NotNull WeakReference<DataRecord> weakRef = new WeakReference<>(this);
}
```

## ITupleAddress
A pure marker interface implemented by [tuple-id] and [tuple-number]. It is mainly used to call `resolve` on a storage, which converts the [tuple-id] into a [tuple-number].

## ITuple
This is a marker interface implemented by [tuple] and [tuple-number]. It is used to indicate a [tuple] or a _reference_ to a [tuple].

```java
package naksha.data;
public interface ITuple {
  /** The database number of the tuple. */
  long databaseNumber();
  /** The catalog number of the tuple. */
  int catalogNumber();
  /** The collection number of the tuple. */
  int collectionNumber();
  /** The record number of the tuple. */
  long recordNumber();
  /** The version of the tuple. */
  long version();
}
```

## TupleId
A tuple-id is a unique reference to a [tuple] using string identifiers. The tuple-id is as well called Global Unique Identifier _(`GUID`)_, it is a string that uniquely identifies a [tuple] within the whole data model. The structure of the tuple-id is like following:

```urn:here:naksha:guid:{database-id}:{catalog-id}:{collection-id}:{record-id}[:{version}]```

Where the `version` is optional, if the `version` is omitted, it refers to the _HEAD_ state of the record _(`0`)_.

```java
public final class TupleId implements ITuple {
  public TupleId(@NotNull String urn) {
    // TODO: Implement parsing of the urn, and validation of the format, throw DataError in case of error.
  }
  public TupleId(@NotNull DataCollection collection, @NotNull String recordId) {
    this(collection, recordId, 0L);
  }
  public TupleId(@NotNull DataCollection collection, @NotNull String recordId, long version) {
    // TODO: Implement.
  }
  public TupleId(@NotNull String databaseId, @NotNull String catalogId, @NotNull String collectionId, @NotNull String recordId, long version) {
    this.databaseId = databaseId;
    this.catalogId = catalogId;
    this.collectionId = collectionId;
    this.recordId = recordId;
    this.version = version;
  }
  /** The database id of the tuple. */
  public final @NotNull String databaseId;
  /** The catalog id of the tuple. */
  public final @NotNull String catalogId;
  /** The collection id of the tuple. */
  public final @NotNull String collectionId;
  /** The record id of the tuple. */
  public final @NotNull String recordId;
  /** The version of the tuple. */
  public final long version;
}
```

Converting a tuple-id into a [tuple-number] requires to invoke the `resolve` method of a storage that stores the corresponding tuple. Caches are not guaranteed to be able to convert a tuple-id into a [tuple-number], because they do not always have the necessary indices.

## TupleNumber
All [tuple] are addressed using a unique tuple-number which is 256-bit _(32 byte)_ long in full representation. The structure is like following:

| Bits         | Size | Value               | Description                                                                                                                       |
|--------------|------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `0`..`63`    | 64   | `database_number`   | The unique identifier of the database.                                                                                            |
| `64`..`95`   | 32   | `catalog_number`    | The identifier of the [catalog] within the [database] in which the [tuple] can be found.                                          |
| `96`..`127`  | 32   | `collection_number` | The identifier of the [collection] within the [catalog] of the [database] in which the [tuple] can be found.                      |
| `128`..`191` | 64   | `record_number`     | The identifier of the [record] within the [collection], within the [catalog] of the [database] in which the [tuple] can be found. |
| `192`..`203` | 12   | _reserved_          | Always `0`.                                                                                                                       |
| `204`..`255` | 52   | `version`           | The version of the [tuple] _(`{year:12}{month:4}{day:5}{sequence:29}{action:2}`)_.                                                |

The [tuple-number] can be compressed. When all [tuple] are stored in the same [database], the `database_number` can be shared, the same is true for the [catalog], [collection], and [record]. Therefore, the smallest encoding uses only 52-bit per [tuple].

## Action
The `action` is encoded into the `version`. Whenever a new version is generated, the lowest two bit are set _(`11b`)_ to signal that this is a pure `VERSION`. For every [record] being part of that version, the lower two bit are then adjusted to the actual `action` applied to the [record], which can be `CREATE` _(00b)_, `UPDATE` _(01b)_, or `DELETE` _(10b)_.

```java
public enum Action {
  CREATE, // 00b
  UPDATE, // 01b
  DELETE, // 10b
  VERSION; // 11b
  public static @NotNull Action of(int ordinal) {
    return switch (ordinal & 3) {
      case 0 -> CREATE;
      case 1 -> UPDATE;
      case 2 -> DELETE;
      default -> VERSION;
    };
  }
  public static @NotNull Action of(long version) {
    return of((int)version);
  }
}
```

The reason that the action is encoded into the version is, that it does not harm, and it improves certain queries, plus it is important in views.

In views data is layered on top of each other. When a storage is queried for data, it will return only the [tuple-number]'s of the found [tuple]. Now, when being in a view, the data of a [tuple] does not need to be loaded, when the top most layer contains the record in a `DELETED` state; except deleted data should be shown as well. Therefore, having the `action` in the [tuple-number] does save data loading in views.

When data is queried, having the `action` in the `version` is helpful to not return [records] being deleted. This only requires an additional filter to `version`, so we can directly remove all tuple-numbers that are in deleted state.

```java
public final class TupleNumber implements ITuple, ITupleAddress, Comparable<TupleNumber> {
  public TupleNumber(@NotNull String urn) {
    // TODO: Implement parsing of the urn, and validation of the format, throw DataError in case of error.
  }
  public TupleNumber(@NotNull DataRecord rec) {
    this(rec.collection.catalog.db.number, rec.collection.catalog.number, rec.collection.number, rec.number, 0L);
  }
  public TupleNumber(@NotNull DataRecord rec, long version) {
    this(rec.collection.catalog.db.number, rec.collection.catalog.number, rec.collection.number, rec.number, version);
  }
  public TupleNumber(long databaseNumber, int catalogNumber, int collectionNumber, long recordNumber, long version) {
    this.databaseNumber = databaseNumber;
    this.catalogNumber = catalogNumber;
    this.collectionNumber = collectionNumber;
    this.recordNumber = recordNumber;
    this.version = (version & 0x000F_FFFF_FFFF_FFFFL);
    // version should contain action, technically (version & 3 == 3) is therefore invalid in a tuple-number!
  }
  public TupleNumber(long databaseNumber, int catalogNumber, int collectionNumber, long recordNumber, long version, int action) {
    this.databaseNumber = databaseNumber;
    this.catalogNumber = catalogNumber;
    this.collectionNumber = collectionNumber;
    this.recordNumber = recordNumber;
    this.version = (version & 0x000F_FFFF_FFFF_FFFCL) | (long)(action & 3);
    // version should contain action, technically (version & 3 == 3) is therefore invalid in a tuple-number!
  }
  public TupleNumber(long databaseNumber, int catalogNumber, int collectionNumber, long recordNumber, long version, @NotNull Action action) {
    this.databaseNumber = databaseNumber;
    this.catalogNumber = catalogNumber;
    this.collectionNumber = collectionNumber;
    this.recordNumber = recordNumber;
    this.version = (version & 0x000F_FFFF_FFFF_FFFCL) | (long)action.ordinal();
    // version should contain action, technically (version & 3 == 3) is therefore invalid in a tuple-number!
  }
  /** The database number of the tuple. */
  public final long databaseNumber;
  /** The catalog number of the tuple. */
  public final int catalogNumber;
  /** The collection number of the tuple. */
  public final int collectionNumber;
  /** The record number of the tuple. */
  public final long recordNumber;
  /** The version of the tuple. */
  public final long version;
  // TODO: Implement the other methods of the ITuple interface.
  // TODO: Implement the compareTo method, so that tuples are sorted by 
  //       database_number, then catalog_number, then collection_number, then record_number, then version, and finally action.
  // TODO: Implement equals, hashCode, and toString _(which should return a URN)_.
}
```

A tuple-number can stringified into a [URN]:

```urn:here:naksha:tn:{database-number}:{catalog-number}:{collection-number}:{record-number}[:{version}]```

When the `version` is omitted, it refers to the _HEAD_ state of the record, which is effectively the same as setting `version` to zero _(`0`)_.

## TupleId to TupleNumber
By default, the storage will convert a tuple-id into a tuple-number by looking at the given `id`. If the `id` is a valid unsigned 63-bit integer in decimal notation, so a string between `0` and `9223372036854775807`, it will parse it into a number and use this number as _record-number_.

If the identifier is not a valid 63-bit unsigned integer in decimal notation, the storage will generate the _**secondary logical bytes**_ of the `id`, which is actually the UTF-16 code-units in big-endian byte-order. Then it will hash these bytes using a 64-bit [MurMur3] hash, and use the lowest 63-bit of the hash as _record-number_, with the sign bit being set, so that the number becomes negative.

Therefore, a translation between `id` and `record_number` does not require any database access, and can be done locally. However, it means that there is a possibility of hash-collisions, even while being very low. To solve this, conflict resolution need to be implemented by the storage. The storage will handle conflicts, when the `id` and `record_number` do not match. When creating a new record, the storage will check if there is already a record with the same `record_number`. If there is, it will check if the `id` of the existing record matches the `id` of the new record. If they do not match, and the `record_number` is negative, it will do the following:

- Clear the sign bit of the record-number: `record_number &= 0x7fff_ffff_ffff_ffff`.
- Keep a copy of the lowest 16-bit: `var partition = record_number & 0xffff`.
- Add 65536 to the record-number: `record_number += 65536`.
- Clear the lowest 16-bit and the sign bit of the record-number: `record_number &= 0x7fff_ffff_ffff_0000`.
- Finally, set the sign bit and add back the partition into lowest 16 bit: `record_number = record_number | 0x8000_0000_0000_0000 | partition`.

This results in a new `record_number` that has the same lowest 16-bit as the originally hashed value. This guarantees that the record is effectively kept in the same partition as it originally was in. This is important, because clients that do not know that there is a collision, will always look for the object in this partition, so they will find both objects in it. It is as well important to have unique indices in each individual partition, and to ensure that all identifiers stay in the partition of the original hashing.

**NOTE**

Storages may change the algorithm to turn an `id` into a record-number. However, they **must not** change the lowest 16-bit of the record-number. So, the only thing that **must** be guaranteed is that the same `id` is always stored in the same partition it would be in, when using [MurMur3] hash above the identifier. Therefore, the lower 16-bit of the record-number must be the same as the ones generated by hashing the `id` using 64-bit [MurMur3] hash. This means, all storage implementation must use [MurMur3] to calculate the hash above the identifier. The exact way that collisions are handled is storage dependend.

## Tuple
A tuple is a mostly immutable state of a [record] _(mostly immutable, because the `next_version` can be modified)_. A tuple has the following logical structure:

```java
public class Tuple implements ITuple, ITupleAddress {
  public Tuple(@NotNull DataRecord rec, @NotNull JbonTuple jbon) {
    super(bytes);
    this.rec = rec;
    this.jbon = jbon;
  }
  /** The record to which the tuple belongs. */
  public final @NotNull DataRecord rec;
  /** The JBON that represents the tuple. */
  public final @NotNull JbonTuple jbon;
  /** The weak reference to this tuple. */
  public final @NotNull WeakReference<Tuple> weakRef = new WeakReference<>(this);
  /** The soft reference to this tuple. */
  public final @NotNull SoftReference<Tuple> softRef = new SoftReference<>(this);
}
```

Note that the `Tuple` just links the `JbonTuple` with the in-memory cache. The in-memory cache will hold a certain threshold of soft-references, plus as much as possible weak-references to tuple.

## Versioning
A version is a 52-bit unsigned integer in the following formats:

```
                                                                   action
              {               automatic version                       }{}
 00000000-0000yyyy-yyyyyyyy-mmmmdddd--dsssssss-ssssssss-ssssssss-ssssssaa

                                                                   action
                       {         manual version                       }{}
 00000000-00000000-0000vvvv-vvvvvvvv--vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvaa

              {                      HEAD                               }
 00000000-00001111-11111111-11111111--11111111-11111111-11111111-11111111

              {                      MAX                                }
 00000000-00001111-11111111-11111111--11111111-11111111-11111111-11111011

 {                                   NULL                               }
 00000000-00000000-00000000-00000000--00000000-00000000-00000000-00000000
```

Therefore, the version has the following general parts:
- `yyyy-yyyyyyyy`: The year of the version, encoded in 12 bit, a value between `16` and `4095`.
- `mmmm`: The month of the version, encoded in 4 bit, a value between `1` _(January)_ and `12` _(December)_.
- `ddddd`: The day of the version, encoded in 5 bit, a value between `1` and `31`.
- `sssss-ssssssss-ssssssss-ssssssss`: The sequence of the version, encoded in 29 bit, so it can represent up to `536,870,912` versions per day, starting from `0`.
- `vv-vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvvv`: The version number, encoded in 42 bit, so it can represent up to `17,592,186,044,416` versions, starting from `0`.
- `aa`: The action of the version, encoded in 2 bit.

The lowest two bit of all valid versions are always used to encode the `action`, therefore all version must have them always set to `11b` for pure versions. This is done by left shifting the version by 2, and ORing the version with 3.

### Automatic Version
The **default** versioning, when nothing else is selected, is _automatic version_. This is a database local version that uses a sequential counter in the database, being reset every day to 0. The sequence is shift left by 2, then encoded in the lower 31 bit of the version. This means every day provides up to `536,870,912` versions _(~5326 versions per second)_. The upper 33 bit of the version are used to store the year, month, and day. This is important to organize _HISTORY_.

### Manual Version
For manual versioning the client needs to come up with some own useful bit pattern, related to history partitioning _(see `shift`ing)_. They are generally simple positive numbers between `1` and `17,592,186,044,416` _(excluding)_.

### HEAD Version
The version `4,503,599,627,370,495` _(2^52-1)_ represents the _HEAD_ version, which is the latest version available in the storage. This is a special version that is only used for `next_version` to signal that a [tuple] is in the _HEAD_ state. Clients can use the value to signal, that they want data in the latest available version.

### MAX Version
The version `4,503,599,627,370,491` _(2^52-5)_ represents the maximal valid version.

### NULL Version
The version `0` is a special version, being used as replacement for the `null`. This can have a bunch of implications, when `null` has a special meaning.

### Querying a version
We use [SQL] to demonstrate the general concept how versions are searched for.

Before any search can start, the query-version needs to be fixed to a valid value. The maximum version that can be queried is `4,503,599,627,370,491` _(2^52-5)_ and the minimal version that can be queried for is `3`. Therefore, any client request for a version need to be clipped into the range of `3` till `4,503,599,627,370,492` _(excluding)_. Be aware that the lowest two bit of the version must always set to `11b`, therefore the version provided to search for must be logically ORed with `3`. So, we do:

```javascript
var query_version = Math.max(3, Math.min(requested_version, 4503599627370491)) | 3;
```

To search for [tuple] in the _HEAD_ state a general query looks like:

```sql
SELECT * FROM table WHERE version <= 4503599627370491 AND next_version > 4503599627370491 AND {other-condition};
```

This will return the _HEAD_ [tuple] of the searched [record]. This can be generalized into the common query form:

```sql
SELECT * FROM table WHERE version <= $version AND next_version > $version AND {other-condition};
```

**Note**: It requires a clipped version between `3` and `4,503,599,627,370,492` _(excluding)_, and the version must be logically ORed with `3` to ensure that the lowest two bit are set.

This general query will only return one [tuple] with the latest state of the [record] that belongs to this [version]. Beware, the returned [tuple] can be in a lower version, this query just ensured that the [tuple] that belongs logically to the queried [version] of the [database] is returned. Let's review this, assume we have the following data:

| db-row | id    | version              | next_version     | prev_version | action         |
|--------|-------|----------------------|------------------|--------------|----------------|
| 1      | `foo` | 590 (`10010011_10b`) | 4503599627370495 | 77           | DELETE (`10b`) |
| 2      | `foo` | 77 (`10011_01b`)     | 590              | 33           | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | 13           | UPDATE (`01b`) |
| 4      | `foo` | 13 (`11_01b`)        | 33               | 4            | UPDATE (`01b`) |
| 6      | `foo` | 4 (`10_00b`)         | 13               | 0            | CREATE (`00b`) |

**Note**: The lower two bit of the version of a [tuple] encodes the action!

Now, assume the client asks for version `500` for object `foo`. To search for this, the `action` need to be set to `VERSION`, so `version` OR `3`, so `500` it becomes `503`. Then the query can be executed:

```sql
SELECT * FROM table WHERE version <= 503 AND next_version > 503 AND id = 'foo';
```

The only row that matches these criteria is row `#2` in version `77`, so only this will be returned _(it is the closed to the searched version `503`)_. Ones the client requests version `75` _(`10010_11b`)_, it will return row `#3` in version `13`. We can as well select the _HEAD_ version:

Assume we do not want deleted objects, and we query for version `500` again, then we have to adjust the version number to `503`, as usual, but we add a secondary version filter to avoid getting deleted states:

```sql
SELECT * FROM table WHERE version <= 503 AND next_version > 503 AND (version & 3) < 2 AND id = 'foo';
```

This will be a pure index-only scan, and it will return row `#2`, because the lowest two bit of the version are `1` _(update)_ and therefore less than `2` _(delete)_. However, when we change the query to _HEAD_:

```sql
SELECT * FROM table WHERE version <= 4503599627370491 AND next_version > 4503599627370491 AND (version & 3) < 2 AND id = 'foo';
```

We can see, that the version condition will select row `#1`, but the added secondary version filter will exclude the row, because the lowest two bit of the version is `2` _(deleted)_. Therefore, this query does not return any row, because in that version the record is deleted.

### Query multiple versions
Assuming the same data state as above:

| db-row | id    | version              | next_version     | prev_version | action         |
|--------|-------|----------------------|------------------|--------------|----------------|
| 1      | `foo` | 590 (`10010011_10b`) | 4503599627370495 | 77           | DELETE (`10b`) |
| 2      | `foo` | 77 (`10011_01b`)     | 590              | 33           | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | 13           | UPDATE (`01b`) |
| 4      | `foo` | 13 (`11_01b`)        | 33               | 4            | UPDATE (`01b`) |
| 6      | `foo` | 4 (`10_00b`)         | 13               | 0            | CREATE (`00b`) |

We can search for multiple versions of a feature, an only limit the lower or upper end. So, search for all version till version `500`:

```sql
SELECT * FROM table WHERE version <= 503 AND id = 'foo';
```

Result is:

| db-row | id    | version              | next_version     | prev_version | action         |
|--------|-------|----------------------|------------------|--------------|----------------|
| 2      | `foo` | 77 (`10011_01b`)     | 590              | 33           | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | 13           | UPDATE (`01b`) |
| 4      | `foo` | 13 (`11_01b`)        | 33               | 4            | UPDATE (`01b`) |
| 6      | `foo` | 4 (`10_00b`)         | 13               | 0            | CREATE (`00b`) |

To query for all versions beyond version `500`:

```sql
SELECT * FROM table WHERE version > 503 AND id = 'foo';
```

| db-row | id    | version              | next_version     | prev_version | action         |
|--------|-------|----------------------|------------------|--------------|----------------|
| 1      | `foo` | 590 (`10010011_10b`) | 4503599627370495 | 77           | DELETE (`10b`) |

Query for all versions in the range of version `15` and `503`:

```sql
SELECT * FROM table WHERE version <= 503 AND version > 15 AND id = 'foo
```

| db-row | id    | version              | next_version     | prev_version | action         |
|--------|-------|----------------------|------------------|--------------|----------------|
| 2      | `foo` | 77 (`10011_01b`)     | 590              | 33           | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | 13           | UPDATE (`01b`) |

We additionally can filter on the `action`.

### Version Records
The version itself is as well a [record] under [database] management. The `record_number` of the version [record] matches the `version` that it tracks. The `id` of the version [record] is just the stringified representation of the version, so it is the decimal representation of the version number. The version can be modified by clients to store transactional information with it. For example, clients may add annotations into the version, for example comments provided by users. The version features can be queried to check which versions do exist in the storage.

Additionally, the version [records] can be used by clients to track the changes in the [database], as each version [record] stores the [catalogs], [collections], and [records] that were modified. It is as well important for caches to understand which [catalogs], [collections], and [records] have changes since the last cache update. It allows caches and replicas to only download the changes they do not yet know about.

The version collection can be extended by custom fields, if necessary, the same way normal [collections] can be extended.

## Two-Phase-Queries
A major concept of the Naksha data model is that data reading is always split into two phases.

The first phase is to execute a query against the storage to find all [tuple] matching certain criteria. These queries can be done against _HEAD_ or a specific [version]. When the query returns, it does not return the actual data, it only returns the [tuple-numbers] of the [tuple] being part of the result-set. This uses _(mostly)_ index-only scans, so normally every index contains the `record_number`, `version`, and `next_version`.

The second phase is then loading the actual [tuple], because [tuples] are by definition mostly immutable, no [tuple] ever has to be loaded twice, as the [tuple] are cached on the JVM heap by the `lib-data`. This caching is done using [Soft-References], which allows to use all available and free heap for data caching. Additionally, the [tuple] can be cached in external services like [Redis] or simply at ephemeral SSDs _(we should use all resource we can get for cheap local caching)_.

Even while it theoretically would be possible to only load parts of a [tuple], this is not part of the model, because the [tuple] is _immutable_, and caching partial data is much more complicated and less efficient than caching full [tuple]. Therefore, it is generally more efficient to load the whole [tuple] at once, and to cache it intesively.

## References
The Naksha data mode defines two reference formats:

- The Tuple-Number _(`TN`)_ as string: `urn:naksha:tn:{storageNumber}:{catalogNumber}:{collectionNumber}:{recordNumber}[:{version}]`
- The Global Unique Identifier _(`GUID`)_ as string: `urn:naksha:guid:{storageId}:{catalogId}:{collectionId}:{recordId}[:{version}]`

For both variants the _HEAD_ state can be referred by simply omitting the `version`, setting it to `0` or _HEAD_ _(`4,503,599,627,370,495`)_.

The storage must internally only operate upon the `TN` variant. Clients are allowed to use the `GUID` variant, because they may create new [tuples] using identifiers only, not yet knowing the [Tuple-Number] or version, when creating the [records], and when adding references to these new [records]. So, the job of the storage is to convert all references given as `GUID` into correct full qualified `TN` variants. It therefore has to search the record and replace all `GUID` references with `TN` references.

In a nutshell, [tuple] read from a storage should never contain references in `GUID` format, they should always have them stored in `TN` format.

## Partitioning
To store big data efficiently, it needs to be partitioned. Within the Naksha data model there are two major logical sections defined: _HEAD_ and _HISTORY_. They store all the [tuple] of the [records]. They are logical concepts that each storage implementation can use to optimize data storage.

The _HISTORY_ section is partitioned logically first by `next_version`, to allow efficient dropping of historic data. We call this **historic partitioning**, and it is always applied if the _HISTORY_ is enabled for a [collection]. It only happens within the _HISTORY_ section. If _HISTORY_ is disabled for a collection, no **historic partitioning** is done, and `next_version` actually does not matter anymore.

Next to the **historic partitioning** of the _HISTORY_ section, there is a general **distribution partitioning**, which we will clarify first.

The _HEAD_ section and each historic partition are optionally distribution partitioned, if enabled. This is an optional feature that by default is disabled, but can be enabled to store a huge number of [records] and [tuple] in a [collection]. When enabled, we distribute [records] across distribution partitions. The number of distribution partitions can be configured when creating a collection, and defaults to `1` _(so no distribution partitioning)_. To assign [records], and all their [tuple], to the same distribution partition, the lower 16-bit of the record-number are used as **distribution key**. This means, all [tuple] of a [record] are stored in the same distribution partition. So, when loading a [tuple] of a [record] in a specific [version], only a single partition has to be accessed. When searching for data, all partitions can be queried in parallel, improving search performance. This layout therefore speeds up searching for data, while making access to known data faster. Loading the _HEAD_ state _([tuple])_ technically means to query a single partition and is therefore rather very fast. Loading multiple tuple can be done in parallel from all partitions they are in. The distribution is simply done by dividing the unsigned lower 16-bit value of the record-number by the amount of distribution partitions _(`n`)_, using the division rest as partition index. For example, assume the distribution key of a [record] is `1234`, so the unsigned lower 16-bit of the record-number is decimal `1234`, and we have `8` distribution partitions configured in the [collection], then dividing `1234` by `8` gives us `154` with a rest of `2`. Therefore, the partition-number to search in is `2`. This guarantees that we always have a partition index between `0` and `n`-1.

The **historic partitioning** is done by the `next_version` of each [tuple]. All [tuple] with `next_version` being `4,503,599,627,370,495`, are located in the _HEAD_ section, and are only distribution partitioned. When a new [tuple] of a [record] is created, the current [tuple] in the _HEAD_ section becomes historic data. It now needs to be moved to history, and `next_version` must be set to the version of the new [tuple]. The [tuple] should be relocated into the _HISTORY_, which is where **historic partitioning** happens. It will stay in _HISTORY_ immutable until being purged. The purging is normally done by deletion of complete historic partitions, which is the reason for this design. Beware that formally the immutability of a [Tuple] slightly broken here, because the `next_version` is modified while moving the [Tuple] into history. However, this is the only exception, and a not significant one for the caches. Actually `next_version` is no reliable field, applications should ignore it. The value can be calculated using the back-references from `prev_version`, starting at _HEAD_.

Now, when deciding in which historic partition a [Tuple] should be located a **partition-key** is needed. To generate the **partition-key** the value from `next_version` is used. For this, the `next_version` is bitwise-ANDed with `0x000F_FFFF_FFFF_FFFF` _(effectively clearing the top 12-bit)_. Then value is shifted right by a configured `shift` amount. The `shift` is configured when creating a [collection] and must stay constant for the whole lifetime of a [collection]. The `shift` defaults to `40`, which means we store one historic partition per year. Reducing the `shift` to `36` would result in one historic partition per month, and reducing it to `31` would result in one historic partition per day.

**Note**: When manual versioning is used for a collection, it is strongly recommended to adjust the `shift` to a more useful value, because very likely `40` is not the best choice.

## JsonFeature

## Tags
The `tags` field is a special field that can be used to store tags for a [record]. The tags are stored as a JSON array of strings, so they can be indexed and searched for. The storage can use the tags to optimize search queries, for example by using an inverted index. The client can use the tags to store any kind of metadata that is useful for searching and indexing. For example, the client can use the tags to store the type of the record, or to store any other kind of classification.

## XYZ namespace
For historic reasons this specification formally specifies the so called XYZ namespace. This is a flat map of all dedicated members of all [records] stored in the [collection]. The client may add or remove members from the XYZ namespace, except for the mandatory ones. In classic systems this map is exposed in `root.properties["@ns:com:here:xyz"]`.

It is the job of the client to copy values from somewhere into the XYZ namespace, and to move the data back. Within the [Naksha-Hub] this is done by adding a corresponding handler directly in front of the storage.

Each [collection] has a set of pre-defined members, which are stored in dedicated indexed places of the storage.

The minimal **mandatory** members that are defined by this specification for all [collections] are:

- `number`: `int64` - The record number. If negative, the `id` of the record is stored in the `meta` section of the collection. If positive, the `id` of the record is the number as decimal string.
- `version`: `uint52` - The version of the record.
- `prev_version`: `uint52` - The previous version of the record, defaulting to [NULL].
- `next_version`: `uint52` - The next version of the record, defaulting to [HEAD].
- `global_book_id`: `uint32?` - The _(optional)_ identifier into the constants of this collection that store the identifier of the global book to use for this record.
- `uuid`: `string` - The _(optional)_ identifier into the constants of this collection that store the identifier of the global book to use for this record.

The pre-defined extended set of members that are defined by this specification for all [collections] are:

- `created_at`: `uint48?` - The _(optional)_ UNIX Epoch timestamp in milliseconds of when the record was first created.
- `updated_at`: `uint48?` - The _(optional)_ UNIX Epoch timestamp in milliseconds of when the record was last modified.
- `hash`: `int64?` - The _(optional)_ 64-bit [MurMur3] hash above the actual object, using [JBON] to produce it.
- `cc`: `int32?` - The _(optional)_ change count, defaults to `1` _(when created)_.
- `origin`: `string?` - The _(optional)_ origin of the record, a reference to the source from which the record originates.
- `app`: `int64?` - The _(optional)_ identifier within the `meta` section of the collection to store the application-identifier of the application that created this tuple.
- `author`: `int64?` - The _(optional)_ identifier within the `meta` section of the collection to store the author-identifier of the application or user that claims authorship of the record.
- `tags`: `jsontags?` - The _(optional)_ tags, so that they can be indexed and searched for _(if the storage supports tags)_.
- `object`: `bytea?` - The _(optional)_ object as [JBON] tuple, basically only to top bytes, so `lead-in`, `byte_size`, `object`, and `local_book`.
- `attachment`: `bytea?` - The _(optional)_ attachment.
- `geo`: `bytea?` - The _(optional)_ geometry of the tuple as [TWKB] binary.
- `ref_point`: `bytea?` - The _(optional)_ reference point of the tuple as [TWKB] binary.


Beware that all these values are basically extracted from the object map.
of the [tuple] of the [record]. These fields are used to store the metadata of the [record], and to store some additional information that can be used for indexing and searching. The fields are defined in the XYZ namespace, which is a flat map of all storage members. The XYZ namespace is defined as follows:


## Origin
The `origin` is an optional value, if enabled it stores a [reference] to the origin of a [record]. When a [record] is modified, normally the _metadata_ of the new [tuple] is not modified by the client. Therefore, the moment the new [tuple] is sent to a storage, the storage can check the [tuple-number] of the [tuple], which will refer to the state the client modified. If this state is located outside the [collection] into which the new [tuple] is stored, the storage automatically fills the `origin` field with a reference to the origin state.

The `origin` will stay unchanged until a _REBASE_ is done. When a _REBASE_ is done, the `origin` field is updated to the new foreign state to which the [record] is rebased.

## Replacement
A replacement is i.e. when splitting a topology into two parts, the original [record] is deleted, and two new [records] are created. Or when a topology is joined, so two [records] are deleted, and a new one is created. All of this should be done in a single version _(transaction)_. This does already indicate a replacement operation. However, as technically multiple  the `replace_id` can be used to link the three [record] together, so that it is possible to find all [record] that belong to the same replacement operation. This is especially useful for tracking the history of changes, and for debugging purposes.

TODO

- `global`: A custom global version, which is a 52-bit unsigned integer.
- `next_global`: The next global version, `null` if this is the latest global version.
- `replacement_id`: A unique string to track replacement operations.
- `rebase_id`: A unique string to track rebase operations.

These fields can be used by applications to track versions with custom information, for example to track replacements. The reason there are pre-defined fields is that the version table can't be created by the client, as it is part of the administrative data of the database, so the client can't define custom fields on it. Therefore, we provide a set of pre-defined fields that can be used for this purpose. The fields are optional, so they can be left `null` if not needed, and will not consume much space.

The `global_version` field can be used to translate global versions into local versions.

## Java
This section documents the Java API for the **Naksha Data Model**.

```java
package naksha.data;
```

```java
package naksha.data;
public class DataType {
  private DataType() {}
  public static final class Indexable extends DataType {
    private Indexable() {}
    @Override
    public boolean isIndexable() { return true; }
  }

  /** If this data-type is indexable. */
  public boolean isIndexable() { return false; }

  public static final DataType UNDEFINED = new DataType();
  public static final DataType NULL = new DataType();
  public static final Indexable BOOL = new Indexable();
  public static final Indexable BYTE = new Indexable();
  public static final Indexable SHORT = new Indexable();
  public static final Indexable INT = new Indexable();
  public static final Indexable LONG = new Indexable();
  public static final Indexable FLOAT = new Indexable();
  public static final Indexable DOUBLE = new Indexable();
  public static final Indexable BYTEA = new Indexable();
  public static final DataType SHORTA = new DataType();
  public static final DataType INTA = new DataType();
  public static final DataType LONGA = new DataType();
  public static final DataType FLOATA = new DataType();
  public static final DataType DOUBLEA = new DataType();
  public static final Indexable STRING = new Indexable();
  public static final DataType GEO_COLLECTION = new DataType();
  public static final Indexable POINT = new Indexable();
  public static final Indexable MULTI_POINT = new Indexable();
  public static final Indexable LINE_STRING = new Indexable();
  public static final Indexable MULTI_LINE_STRING = new Indexable();
  public static final Indexable POLYGON = new Indexable();
  public static final Indexable MULTI_POLYGON = new Indexable();
  public static final DataType MAP = new DataType();
  public static final DataType ARRAY = new DataType();
}
```

## DataError
All methods can throw the `RuntimeException` named `DataError`. Applications are free to catch this exception or to ignore it and leave the error handling to the caller.

```java
package naksha.data;

public class DataError extends RuntimeException {
  // TODO: Document me!
}
```

## DataManager
A data-manager is a needed root object. An application can just have one _(as static singleton)_ or use multiple. The data-manager is the main entry point to access the data model, it is used by [storages] and the application. It provides methods to access the [storages], [databases], [catalogs], [collections], [records], and [tuples].

```java
package naksha.data;

/**
 * The data-manager is the main entry point to access the data model, it is used by the application. The data-manager is by itself a virtual tuple-store.
 * 
 * <p>When the data-manager itself is asked to read tuple, it will first query through all caches in order, then eventually through all storages in order, to read the tuple.
 * <p>When the data-manager itself is asked to store tuple, it will forward the request to all caches, it will not send tuples to storages.
 */
public class DataManager extends DataTupleStorage{
  /** The list of caches that are registered with this data-manager. */
  public @NotNull List<@NotNull DataTupleStorage> getCaches();

  /**
   * Can be used by applications to update the list of caches, add a new storage, remove an existing one, or to just reorder storages. The method will return {@code true} if the update was successful, and {@code false} otherwise. The method will only update the list of caches if the existing list of aches is the same as the provided list of existing caches, so that concurrent updates can be prevented.
   * @param existingCaches The list as returned by {@link #getCaches()}.
   * @param newCaches An array of the new caches to be set, will be converted to a list internally. The array must not contain {@code null} values, and the caches must be unique, so that no cache is contained more than once, nor must two caches have the same identifier.
   * @return {@code true} if the update was successful, {@code false} otherwise.
   * @throws DataError If the give list of caches contains {@code null} or a cache is contained more than once, or two caches have the same identifier.
   */
  public boolean compareAndSetCaches(@NotNull List<@NotNull DataTupleStorage> existingCaches, @NotNull DataTupleStorage @NotNull [] newCaches);

  /**
   * Returns the cache with the given identifier, or {@code null} if no such cache exists.
   * @param cacheId The cache identifier, must not be {@code null}.
   * @return The cache with the given identifier, or {@code null} if no such cache exists.
   */
  public @Nullable DataTupleStorage getCache(@NotNull String cacheId);

  /**
   * Adds the given cache to the list of caches, auto-sorting by {@link DataStorage#latencyInNanos()}, if there is no cache with the same identifier already in the list.
   * @param cache The cache to add.
   * @return {@code true} if the cache was added successfully, {@code false} if this cache is already in the list.
   * @throws DataError If the given cache is {@code null}, or a cache with the same identifier already exists in the list of caches.
   */
  public boolean addCache(@NotNull DataTupleStorage cache);

  /**
   * Removes the given cache from the list of caches.
   * @param cache The cache to remove.
   * @return {@code true} if the cache was removed successfully, {@code false} if this cache is not in the list.
   */
  public boolean removeCache(@NotNull DataTupleStorage cache);

  /**
   * Returns the cache with the given identifier, or {@code null} if no such cache exists.
   * @param cacheId The cache identifier, must not be {@code null}.
   * @return The cache with the given identifier, or {@code null} if no such cache exists.
   */
  public @Nullable DataTupleStorage readTuple(@NotNull String cacheId);

  /** The list of storages that are registered with this data-manager. */
  public @NotNull List<@NotNull DataStorage> getStorages();

  /**
   * Can be used by applications to update the list of storages, add a new storage, remove an existing one, or to just reorder storages. The method will return {@code true} if the update was successful, and {@code false} otherwise. The method will only update the list of storages if the existing list of storages is the same as the provided list of existing storages, so that concurrent updates can be prevented.
   * @param existingStorages The list as returned by {@link #getStorages()}.
   * @param newStorages An array of the new storages to be set, will be converted to a list internally. The array must not contain {@code null} values, and the storages must be unique, so that no storage is contained more than once, nor must two storages have the same identifier.
   * @return {@code true} if the update was successful, {@code false} otherwise.
   * @throws DataError If the give list of storages contains {@code null} or a storage is contained more than once, or two storages have the same identifier.
   */
  public boolean compareAndSetStorages(@NotNull List<@NotNull DataStorage> existingStorages, @NotNull DataStorage @NotNull [] newStorages);

  /**
   * Returns the storage with the given identifier, or {@code null} if no such storage exists.
   * @param storageId The storage identifier, must not be {@code null}.
   * @return The storage with the given identifier, or {@code null} if no such storage exists.
   */
  public @Nullable DataStorage getStorage(@NotNull String storageId);

  /**
   * Adds the given storages to the list of storages, auto-sorting by {@link DataStorage#latencyInNanos()}, if there is no storage with the same identifier already in the list.
   * @param storage The storage to add.
   * @return {@code true} if the storage was added successfully, {@code false} if this storage is already in the list.
   * @throws DataError If the given storage is {@code null}, or a storage with the same identifier already exists in the list of storages.
   */
  public boolean addStorage(@NotNull DataStorage storage);

  /**
   * Removes the given storage from the list of storages.
   * @param storage The storage to remove.
   * @return {@code true} if the storage was removed successfully, {@code false} if this storage is not in the list.
   */
  public boolean removeStorage(@NotNull DataStorage storage);

}
```

## DataTupleStorage
The base class for [storages] and caches.

```java
package naksha.data;

/**
 * Abstract base class for all data storages, be it caches or full storages.
 */
public abstract class DataTupleStorage {
  /** Returns the identifier of the tuple storage. */
  public abstract @NotNull String id();

  /** Returns the expected latency in nanoseconds when reading tuple. */
  public long latencyInNanos();

  /**
   * Read a single tuple, if it exists in the storage.
   * @param dataNumber The tuple-number of the tuple.
   * @return The tuple, or {@code null}, if it does not exist in the storage.
   * @throws DataError When an error occurs while reading the tuple.
   */
  public abstract @Nullable DataTuple readTuple(@NotNull DataNumber dataNumber);
  
  /**
   * Read a single tuple in a specific version, if it exists in the storage.
   * @param dataNumber The tuple-number of the tuple.
   * @param version The version to read the tuple in.
   * @return The tuple, or {@code null}, if it does not exist in the storage.
   * @throws DataError When an error occurs while reading the tuple.
   */
  public abstract @Nullable DataTuple readTuple(@NotNull DataNumber dataNumber, long version);

  /**
   * Read tuples from the storage. Actually, every entry in the given array that is a {@link TupleNumber} or {@link TupleId} is replaced with the corresponding {@link Tuple}, if it exists in the storage, otherwise it is left as is.
   * @param tuples The tuples to be loaded.
   * @return the amount of tuple that have been loaded successfully, so the amount of entries in the given array that have been replaced with the corresponding tuple.
   * @throws DataError When an error occurs while reading the tuple.
   */
  public abstract int readTuples(@Nullable ITuple @NotNull [] tuples);

  /**
   * Read tuples from the storage. Actually, every entry in the given array that is a {@link TupleNumber} or {@link TupleId} is replaced with the corresponding {@link Tuple}, if it exists in the storage, otherwise it is left as is. This method actually ignores the {@code version} encoded within the {@link TupleNumber} or {@link TupleId}, and instead uses the given {@code version} to read the tuple, so that the same tuple can be read in different versions.
   * @param tuples The tuples to be loaded.
   * @param version The version in which the tuple should be read. If the tuple does not exist in the given version, they are left as is.
   * @return the amount of tuple that have been loaded successfully, so the amount of entries in the given array that have been replaced with the corresponding tuple.
   * @throws DataError When an error occurs while reading the tuple.
   */
  public abstract int readTuples(@Nullable ITuple @NotNull [] tuples, long version);

  /**
   * Store a single tuple in the storage. The method returns instantly, the actual write is queued.
   * @param tuple The {@link Tuple} to write.
   * @param errorHandler The <i>(optional)</i> error handler that is called when an error occurs while writing the tuple.
   * @return {@code true} if the tuple was accepted for writing, {@code false} otherwise.
   */
  boolean storeTuple(@NotNull Tuple tuple, @Nullable BiConsumer<@NotNull DataError, @NotNull Tuple> errorHandler);
  
  /**
   * Store tuples in the storage. The method returns instantly, the actual write is queued.
   * @param tuples The {@link Tuple}s to write.
   * @param errorHandler The <i>(optional)</i> error handler that is called when an error occurs while writing the tuple.
   * @throws DataError When an error occurs while writing the tuple, for example when the given array contains {@code null} values.
   * @return {@code true} if the tuples were accepted for writing, {@code false} otherwise.
   */
  boolean storeTuples(@NotNull Tuple @NotNull [] tuples, @Nullable BiConsume<@NotNull DataError, @NotNull Tuple @NotNull []> errorHandler) throws DataError;
}
```

Technically, every [tuple] has a [tuple-number] that allow the storage to find the tuple. Normally, only caches will support to store tuples.

## DataStorage
The storage class must be extended by all storages that want to support the Naksha data model. The storage is responsible for managing the data, including the [database], [catalog], [collection], [record], [tuple], and [books]. The storage is defined as:

```java
public abstract class DataStorage extends DataTupleStorage {
  // Database handling
  @NotNull List<@NotNull Database> listDatabases(long version) throws StorageError;
  @Nullable Database getDatabase(@NotNull String databaseId, long version, boolean includeDeleted) throws StorageError;
  @Nullable Database getDatabase(@NotNull long databaseNumber, long version, boolean includeDeleted) throws StorageError;
  @NotNull Database createDatabase(@NotNull JsonDatabase database) throws StorageError;
  @NotNull Database updateDatabase(@NotNull JsonDatabase database) throws StorageError;
  void deleteDatabase(@NotNull JsonDatabase database) throws StorageError;

  // Only a storage can translate a tuple-identifier into a tuple-number.
  @Nullable TupleNumber resolve(@NotNull TupleId tupleId);
  @Nullable TupleNumber @NotNull [] resolve(@NotNull TupleId @NotNull [] tupleId);
  
  @NotNull SessionInfo @NotNull listSessions() throws StorageError;
  @NotNull ReadSession openReadSession(@NotNull Database database, @NotNull SessionOptions options) throws StorageError;
  @NotNull Session openSession(@NotNull Database database, @NotNull SessionOptions options) throws StorageError;
}
record SessionOptions(
    @Nullable String appId,
    @Nullable String author,
    boolean enableTracking
) {
}
interface SessionInfo {
  @NotNull Storage storage();
  @NotNull Database database();
  @NotNull SessionOptions options();
}
interface ReadSession extends SessionInfo, AutoClosable {
  long head() throws StorageError;
  long version() throws StorageError;
  @NotNull ReadSession useVersion(long version) throws StorageError;
  
  // Version methods
  @NotNull Version getVersion(long version) throws StorageError;
  
  // Catalog methods
  boolean isCatalog(@NotNull Tuple tuple) throws StorageError;
  @NotNull Catalog toCatalog(@NotNull Tuple tuple) throws StorageError;
  @NotNull Catalog refresh(@NotNull Catalog catalog) throws StorageError;
  @Nullable Catalog getCatalog(@NotNull String catalogId, boolean includeDeleted) throws StorageError;
  @Nullable Catalog getCatalog(@NotNull int catalogNumber, boolean includeDeleted) throws StorageError;
  @Nullable Catalog createCatalog(@NotNull JsonCatalog catalog) throws StorageError;
  @Nullable Catalog updateCatalog(@NotNull JsonCatalog catalog) throws StorageError;
  void deleteCatalog(@NotNull JsonCatalog catalog) throws StorageError;

  // Collection methods
  @NotNull List<@NotNull Collection> listCollections(@NotNull Catalog catalog) throws StorageError;
  boolean hasCollection(@NotNull Catalog catalog, @NotNull String collectionId) throws StorageError;
  boolean hasCollection(@NotNull Catalog catalog, @NotNull int collectionNumber) throws StorageError;
  @NotNull Collection refresh(@NotNull Catalog catalog) throws StorageError;
  @Nullable Collection getCollection(@NotNull Catalog catalog, @NotNull String collectionId, boolean includeDeleted) throws StorageError;
  @Nullable Collection getCollection(@NotNull Catalog catalog, @NotNull int collectionNumber, boolean includeDeleted) throws StorageError;
  @Nullable Collection createCollection(@NotNull JsonCatalog catalog) throws StorageError;
  @Nullable Collection updateCollection(@NotNull JsonCatalog catalog) throws StorageError;
  void deleteCatalog(@NotNull JsonCatalog catalog) throws StorageError;
  
  // Search methods
  @NotNull TupleNumber @NotNull [] query(@NotNull Query query, @NotNull QueryOptions options) throws StorageError;
  
  void close();
}
class Query {
  
}
interface Session extends ReadSession {
  
  @NotNull Session commit();
  @NotNull Session rollback();
}
```

Creating the storage is out of scope of the data model, the application need to create the storage and manage its lifecycle.

However, all storages must support a readonly virtual administration [database] that contains the management data of the storage.

Each storage has to provide an internal administration [database]. This is virtual [database] contains a [catalog] named `naksha` that is used to access the management data. This `naksha` [catalog] contains the following [collections]:

- `books`: A storage for the `global` [books].
- `databases`: A storage for the [database] objects.


---
As the collections defines the structure of all contained [records], it defines as well the metadata properties above which can be searched. A collection allows to add and remove custom properties, but dependent on the implementation and current data size, different cost come by modifying the structure. Some implementations may even reject structure changes, after the collection was crated _(their will throw a `DataError` exception)_. All custom properties are nullable, when being `null` _(which is the default value for a new custom property)_, they are not indexed. So filtering on custom properties is only possible for set values, because to find those that have `null` values, a full data scan is needed.

## Changes
The following changes have been made to the data model between the original draft and the current version:

- We no longer track storages, but rather databases.
  - This is important to have replication working. We do not care where data is stored, just how it is locically organized and addressed.
- We allow custom members and custom btree indices.
  - This is important for some internal projects.
- Most members have been made optional, so that they can be left `null` if not needed, and will not consume much space.

[Indexable]: #indexable
[indexable]: #indexable
[Catalog]: #catalog
[catalog]: #catalog
[catalogs]: #catalog
[Collection]: #collection
[collection]: #collection
[collections]: #collection
[Record]: #record
[record]: #record
[records]: #record
[Tuple]: #tuple
[tuple]: #tuple
[tuples]: #tuple
[Tuple-Number]: #tuple-number
[tuple-number]: #tuple-number
[tuple-numbers]: #tuple-number
[Reference]: #references
[reference]: #references
[References]: #references
[references]: #references
[Versioning]: #versioning
[versioning]: #versioning
[HEAD]: #head-version
[MAX]: #max-version
[NULL]: #null-version
[Version]: #versioning
[version]: #versioning
[versions]: #versioning
[Partitioning]: #versioning
[partitioning]: #versioning
[Origin]: #origin
[origin]: #origin
[Replacement]: #replacement
[replacement]: #replacement
[book]: ./JBON.md#books
[books]: ./JBON.md#books
[JSON]: https://www.rfc-editor.org/rfc/rfc8259
[GeoJSON]: https://datatracker.ietf.org/doc/html/rfc7946
[TWKB]: https://github.com/TWKB/Specification/blob/master/twkb.md
[JBON]: ./JBON.md
[UNICODE]: https://home.unicode.org/
[SQL]: https://en.wikipedia.org/wiki/Sql
[Redis]: https://redis.io/
[Soft-References]: https://www.baeldung.com/java-soft-references
[JTS]: https://github.com/locationtech/jts
[data URL scheme]: https://www.rfc-editor.org/rfc/rfc2397
[data-url]: https://www.rfc-editor.org/rfc/rfc2397
[data-urls]: https://www.rfc-editor.org/rfc/rfc2397
[protobuf]: https://developers.google.com/protocol-buffers
[UNICODE normalization]: https://www.unicode.org/reports/tr15/#Norm_Forms
[NFC]: https://www.unicode.org/reports/tr15/#Norm_Forms
[NFD]: https://www.unicode.org/reports/tr15/#Norm_Forms
[NFKC]: https://www.unicode.org/reports/tr15/#Norm_Forms
[NFKD]: https://www.unicode.org/reports/tr15/#Norm_Forms
[MurMur3]: https://en.wikipedia.org/wiki/MurmurHash
[murmur3]: https://en.wikipedia.org/wiki/MurmurHash

