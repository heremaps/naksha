# The Naksha Data Model

## Introduction
The Naksha Data Model _(**NDM**)_ is made to exchange data in the form of [GeoJSON] features between different applications, components, services, and storages. The data model is designed to support efficient storage, retrieval, and query of objects. It's optimized to exchange the data serialized into [JSON], [GeoJSON], [protobuf], and [JBON]. [JBON] is a special binary encoding, highly compact, mostly immutable, and does not require parsing to read the data, developed specifically for the Naksha data model.

The data model supports operations to manage the data lifecycle, including creation, update, and deletion. It supports in maintaining a history of changes. The data model is also designed to support efficient querying of the data, including queries for specific versions and queries for the latest version _(HEAD)_.

The data model is an abstraction layer that allows to decouple the physical storage from the logical structure of the data. This allows for flexibility in the choice of storage technology and allows for future changes to the storage technology without affecting the logical structure of the data.

## Literals
The JSON map and array implementations are optimized for low memory consumption. All keys in the JSON map are interned to guarantee that the same key is not in memory multiple times. This is done by wrapping them into a `Literal`. This is already done by the parser. This feature can be used by the application as well via `Literal.get` calls. The JSON parser itself will intern all keys and values to reduce memory consumption. Beware that interning is only guaranteed for strings, all other data types have just a possibility to be interned, but it is not guaranteed.

```java
// byte[]
//   JVM header = 16 byte
//   int length = 4 byte
//   ... data
//   = 20 byte + n byte data

// String
//   JVM header = 16 byte
//   byte[] value = 28+ byte (8 byte + 20 byte + n byte character data)
//   byte coder = 1 byte
//   int hash = 4 byte
//   boolean hashIsZero = 1 byte
//   = 50 byte+ byte

// WeakReference, same applies for Long, Double 
//   JVM header = 16 byte
//   referent/value = 8 byte
//   = 24 byte

public final class Literal implements CharSequence, Comparable<CharSequence> {
  public static @NotNull Double of(float value) { /* ... */ }
  public static @NotNull Double of(double value) { /* ... */ }
  public static @NotNull Double of(@NotNull Double value) { /* ... */ }
  public static @NotNull Long of(byte value) { /* ... */ }
  public static @NotNull Long of(short value) { /* ... */ }
  public static @NotNull Long of(int value) { /* ... */ }
  public static @NotNull Long of(long value) { /* ... */ }
  public static @NotNull Long of(@NotNull Long value) { /* ... */ }
  public static @NotNull Literal of(@NotNull CharSequence value) { /* ... */ }
  public static @Nullable Literal get(@Nullable CharSequence value) { /* ... */ }
  Literal(@NotNull String value) { /* ... */ }

  // JVM Header: 16 byte
  public final @NotNull String value; // 8 byte, 50+ byte = 58+ byte
  public final long murmurHash; // 8 byte
  public final @NotNull WeakReference<Literal> weakRef; // 8 byte, 24 byte = 32 byte
} // = 114 byte+ byte
```

Therefore, a string literal adds ~64 byte to the memory consumption of a `String`, which uses 50 byte _(plus characters)_. That means, deduplication is only beneficial to memory consumption, when there are least three usages. However, especially for keys there are potentially many thousands of usages. Next to just the memory consumption, two literals can be compared using the `==` operator, which is much faster than the `equals` method.

For the long and double values, only certain specific values are being cached. There is no need for weak references, so we just keep a cache table of a certain size and deduplicate what we can. For example really often used values like `1.0` or `0.0`. Longs are already caches by the JVM, when `Long.valueOf` is used, but this only works for values between `-128` and `127`, so we extend this range with a dynamic cache.

The `Literal` is mostly used internally within `JsonMap` for keys. However, it can be used by applications as well to speed up access in maps.

## Error Handling
All methods can throw an `LibDataError`, which is a `RuntimeException`. Applications are free to catch this exception or to ignore it and leave the error handling to the caller.

## Data Types
To allow interoperability between different storages, applications, modules, and services, the data model supports a set of pre-defined supported data types:

| Java                 | Idx    | Type-Emum _(Name)_  | Javascript           | Description                                                                                                                |
|----------------------|--------|---------------------|----------------------|----------------------------------------------------------------------------------------------------------------------------|
| `Undefined`          |        | `UNDEFINED`         | `undefined`          | The undefined type, a singleton in Java.                                                                                   |
| `null`               |        | `NULL`              | `null`               | A boolean.                                                                                                                 |
| `boolean`            | yes    | `BOOL`              | `Boolean`            | A boolean.                                                                                                                 |
| `byte`               | yes    | `BYTE`              | `number`             | A 8-bit integer.                                                                                                           |
| `short`              | yes    | `SHORT`             | `number`             | A 16-bit integer.                                                                                                          |
| `int`                | yes    | `INT`               | `number`             | A 32-bit integer.                                                                                                          |
| `long`               | yes    | `LONG`              | `BigInt`             | A 64-bit integer.                                                                                                          |
| `float`              | yes    | `FLOAT`             | `number`             | A 32-bit floating point number.                                                                                            |
| `double`             | yes    | `DOUBLE`            | `number`             | A 64-bit floating point number.                                                                                            |
| `byte[]`             | yes    | `BYTEA`             | `Int8Array`          | A byte-array.                                                                                                              |
| `short[]`            |        | `SHORTA`            | `Int16Array`         | A 16-bit integer array.                                                                                                    |
| `int[]`              |        | `INTA`              | `Int32Array`         | A 32-bit integer array.                                                                                                    |
| `long[]`             |        | `LONGA`             | `BigInt64Array`      | A 64-bit integer array.                                                                                                    |
| `float[]`            |        | `FLOATA`            | `Float32Array`       | A 32-bit floating point number array.                                                                                      |
| `double[]`           |        | `DOUBLEA`           | `Float64Array`       | A 64-bit floating point number array.                                                                                      |
| `String`             | yes    | `STRING`            | `String`             | A text of [UNICODE] code-points.                                                                                           |
| `Geometry`           |        |                     | `Geometry`           | `org.locationtech.jts.geom.Geometry` - Interface for all geometries, [GeoJSON] compatible.                                 |
| `GeometryCollection` |        | `GEO_COLLECTION`    | `GeometryCollection` | `org.locationtech.jts.geom.GeometryCollection`                                                                             |
| `Point`              | yes    | `POINT`             | `Point`              | `org.locationtech.jts.geom.Point`                                                                                          |
| `MultiPoint`         | yes    | `MULTI_POINT`       | `MultiPoint`         | `org.locationtech.jts.geom.MultiPoint`                                                                                     |
| `LineString`         | yes    | `LINE_STRING`       | `LineString`         | `org.locationtech.jts.geom.LineString`                                                                                     |
| `MultiLineString`    | yes    | `MULTI_LINE_STRING` | `MultiLineString`    | `org.locationtech.jts.geom.MultiLineString`                                                                                |
| `Polygon`            | yes    | `POLYGON`           | `Polygon`            | `org.locationtech.jts.geom.Polygon`                                                                                        |
| `MultiPolygon`       | yes    | `MULTI_POLYGON`     | `MultiPolygon`       | `org.locationtech.jts.geom.MultiPolygon`                                                                                   |
|                      |        |                     |                      |                                                                                                                            |
|                      |        |                     |                      | JSON                                                                                                                       |
|                      |        |                     |                      |                                                                                                                            |
| `JsonObject`         |        |                     |                      | The base class for all [JSON] data types that allow proxy linking.                                                         |
| `JsonArray`          | string | `ARRAY`             |                      | A list of values, extends [JsonObject], implements mutable `IArray`.                                                       |
| `JsonMap`            | flat   | `MAP`               |                      | A set of key-value pairs in insertion order, extends [JsonObject], implements mutable `IMap`.                              |
|                      |        |                     |                      |                                                                                                                            |
| `Proxy`              |        |                     |                      | Abstract base class for all proxies that can be linked to a [JsonObject] to extend the object with custom functions.       |
| `MapProxy`           |        |                     |                      | A [Proxy] that can be linked to any `IMap` to extend the map with custom functions.                                        |
| `ArrayProxy`         |        |                     |                      | A [Proxy] that can be linked to any `IArray` to extend the list with custom functions.                                     |
| `Option`             |        |                     |                      | A special enumeration implementation that essentially is always encoded as string or long.                                 |
|                      |        |                     |                      |                                                                                                                            |
| `JsonTupleNumber`    |        |                     |                      | Wraps a string as `ITupleNumber`, cached inside of arrays and maps.                                                        |
| `JsonVersion`        |        |                     |                      | The mutable variant of an `Version` tuple, as [Proxy] linked to an `IMap`.                                                 |
| `JsonDatabase`       |        |                     |                      | The mutable variant of an `Database` tuple, as [Proxy] linked to an `IMap`.                                                |
| `JsonCatalog`        |        |                     |                      | The mutable variant of an `Catalog` tuple, as [Proxy] linked to an `IMap`.                                                 |
| `JsonCollection`     |        |                     |                      | The mutable variant of an `Collection` tuple, as [Proxy] linked to an `IMap`.                                              |
| `JsonFeature`        |        |                     |                      | The mutable variant of an `Feature` tuple, as [Proxy] linked to an `IMap`.                                                 |
| `JsonTags`           |        |                     |                      | A [Proxy] to manage a list of tags as "flat" key-value pairs, linked to an `IArray`.                                       |
|                      |        |                     |                      |                                                                                                                            |
|                      |        |                     |                      | DATA                                                                                                                       |
|                      |        |                     |                      |                                                                                                                            |
| `IObject`            |        |                     |                      | An interface to access general JSON like object that supports proxies.                                                     |
| `IArray`             |        |                     |                      | An interface to access general JSON like arrays, implements by `JsonArray` and `JbonArray`.                                |
| `IMap`               |        |                     |                      | An interface to access general JSON like maps, implements by `JsonMap` and `JbonMap`.                                      |
| `ITupleNumber`       |        |                     |                      | An interface to access a tuple-number.                                                                                     |
|                      |        |                     |                      |                                                                                                                            |
| `Bytes`              |        |                     |                      | A static singleton for low-level access to primitive arrays _(`byte[]`, `short[]`, ...)_.                                  |
| `Binary`             |        |                     |                      | A helper class for binaries, supports MIME types, parameters, and compression.                                             |
| `TupleId`            |        |                     |                      | The immutable im-memory representation of a unique identifier.                                                             |
| `TupleNumber`        |        |                     |                      | The immutable im-memory representation of a unique identifier.                                                             |
| `Version`            |        |                     |                      | The immutable im-memory representation of a [version].                                                                     |
| `VersionProxy`       |        |                     |                      | A [Proxy] for either a `JsonMap` or a `JbonMap`, providing access to a [version] _feature_.                                |
| `Database`           |        |                     |                      | The immutable im-memory representation of a [database].                                                                    |
| `DatabaseProxy`      |        |                     |                      | Extends [Proxy], a wrapper around a `JbonTuple` of a [database] _feature_.                                                 |
| `Catalog`            |        |                     |                      | The immutable im-memory representation of a [catalog] within a [database].                                                 |
| `CatalogTuple`       |        |                     |                      | Extends [Proxy], a wrapper around a `JbonTuple` of a [catalog] _feature_.                                                  |
| `Collection`         |        |                     |                      | The immutable im-memory representation of a [collection] within a [catalog].                                               |
| `CollectionProxy`    |        |                     |                      | Extends [Proxy], a wrapper around a `JbonTuple` of a [collection] _feature_.                                               |
| `Feature`            |        |                     |                      | The immutable im-memory representation of a [feature] within a [collection].                                               |
| `Tuple`              |        |                     |                      | A wraper around a `JbonTuple` that encodes an arbitrary [feature].                                                         |
|                      |        |                     |                      |                                                                                                                            |
|                      |        |                     |                      | JBON                                                                                                                       |
|                      |        |                     |                      |                                                                                                                            |
| `Jbon`               |        |                     |                      | A wrapper above a bunch of bytes that encode a [JBON].                                                                     |
| `JbonEncoder`        |        |                     |                      | A tool to build a [JBON].                                                                                                  |
| `JbonBinary`         |        |                     |                      | A wrapper above a `Jbon` positioned at bytes that encodes a [JBON] binary.                                                 |
| `JbonArray`          |        |                     |                      | A wrapper above a `Jbon` positioned at bytes that encodes a [JBON] array, implementing read-only `IArray`.                 |
| `JbonMap`            |        |                     |                      | A wrapper above a `Jbon` positioned at bytes that encodes a [JBON] map, implementing read-only `IMap`.                     |
| `JbonTupleNumber`    |        |                     |                      | A wrapper above a `Jbon` positioned at bytes that encodes a [JBON] tuple-number.                                           |
| `JbonTuple`          |        |                     |                      | A wrapper above a `Jbon` positioned at bytes that encodes a [JBON] tuple, implementing read-only `IMap` for the _feature_. |
| `JbonBook`           |        |                     |                      | A wrapper above a `Jbon` positioned at bytes that encodes a [JBON] book.                                                   |
| `JbonAnnotation`     |        |                     |                      | A wrapper above a `Jbon` positioned at bytes that encodes a [JBON] annotation.                                             |
|                      |        |                     |                      |                                                                                                                            |
|                      |        |                     |                      | STORAGE                                                                                                                    |
|                      |        |                     |                      |                                                                                                                            |
| `TupleStorage`       |        |                     |                      |                                                                                                                            |
| `Storage`            |        |                     |                      |                                                                                                                            |
| `ReadSession`        |        |                     |                      |                                                                                                                            |
| `FullSession`        |        |                     |                      |                                                                                                                            |

All data must be represented using these data types to ensure interoperability between different components, storages, and services.

This design is important for many of the features offered by the data model, for example to be able to effectively calculate the _logical bytes_ of any _**unit**_, so data can be compared. Calculating differences, patches, and applying the patches, and/or merging of arbitrary data requires this design. The data model supports all rudimentary components necessary to build more complex structures:

- Primitives _(boolean, integer, float)_
- Strings
- Maps
- Arrays
- Geometries

## Indexable
The following sections will sometimes refer to `indexable` types, this refers to the data types that are marked as `idx`.

When a byte-array is compared, then the compare **must** be done byte-by-byte. The smaller byte decides which byte-array is smaller. When the end of a byte-array is reached, and so far all bytes are equal, but one array does have more bytes, then the shorter array is _(by definition)_ less than the longer array. Otherwise, if both arrays are of same length, and all bytes equal, they are equal.

All strings must be interned and encoded in [NFC] form. In memory strings are kept in UTF-16 encoding, in the database they are stored in UFF-8 encoding, and in [JBON] a special encoding is used. Within the database, strings are treated like `C` strings, and are therefore sorted the same way. This can lead to unexpected sorting results.

The data model differentiates mainly between shared immutable binary data, encoded in [JBON], and mutable thread-local data that is represented as [JSON] heap objects. The immutable binary data is used for caching, cross component access, or fast transportation between services, and for very fast lookups _(without the need to decode the [JBON] into [JSON] heap objects)_.

The `Map` is marked as _flat_ indexable. This means, maps are indexable, but not recursively. So, all keys of a map, where the value is an indexable type, are `indexable`. For this the storage need to have some mechanism. **Beware that this indexing is potentially very expensive and should be avoided!**

The `Array` is marked as _string_ indexable. This means, for downward compatibility, arrays that only contain strings are as well `indexable`. They are converted into a _flat_ `Map` following this algorithm:

**TODO: Write down the algorithm used in previous Naksha and Wikvaya released, how tags are parsed into maps.**

## Proxies
Having to work with unstructured data is extremely error-prone, even while the most flexible thing possible. Therefore, `lib-data` supports proxies. A proxy is a data-model that can be added to arbitrary data at runtime _(this allows runtime schema detection)_. The following example shows a proxy for a simple data model, where a [GeoJSON] feature has a `name` and `age` in the `properties`:

```java
package naksha.data;

public class ExampleType extends MapProxy {
  // This constructor is used to create a new Example instance.
  public ExampleType() {
    super(new JsonMap());
    // We can do normal initialization here, for example setting default values.
    setName("Hello World");
    setAge(18);
  }
  // This constructor is called by the "proxy" method to link a proxy to an existing JsonMap, for example when deserializing from JSON.
  public Example(@NotNull IMap map) {
    super(map);
    // We can update internal caches and more, when this happens.
    // It is guaranteed to happen only ones in the lifetime of every object, proxies are never unlinked or relinked!
  }
  
  // The property methods for name.
  public static final String NAME_KEY = Data.intern("name");
  public boolean hasName() { return map.containsKey(NAME_KEY); }
  public @Nullable String getName() { return map.getString(NAME_KEY); }
  public @Nullable String setName(@Nullable String name) { return map.setString(NAME_KEY, name); }
  public @Nullable String removeName() { return map.removeString(NAME_KEY); }

  // The property methods for age.
  public static final String AGE_KEY = Data.intern("age");
  public boolean hasAge() { return map.containsKey(AGE_KEY); }
  public int getAge() { return map.asInt(map.getLong(AGE_KEY), 0); }
  public int setAge(int age) { return map.asInt(map.setLong(AGE_KEY, age), 0); }
  public int removeAge() { return map.asInt(map.removeLong(AGE_KEY), 0); }
}

// Usage example:
public class ExampleUsage {
  public static void demo(@NotNull JsonMap feature) {
     JsonMap properties = feature.getMap(Const.PROPERTIES);
     assert properties != null;
     // Request a proxy for the properties, this will link the schema to the data.
     final Example example = properties.proxy(Example.class);
     // Proxies are cached, so requesting the same proxy again, returns the same instance.
     assert example == properties.proxy(Example.class);
     // Now we can use the proxy to access the data in a type-safe way.
     String name = example.getName();
     int age = example.getAge();
     // Do something with name and age ...
  }
}
```

Beware that proxies are not thread-safe them self, the same way the `JsonMap` is not thread-safe. Therefore, only one thread should access the same proxy instance at the same time.

## Identifiers
The identifiers of the major administration objects, like [database], [catalog], [collection], ..., are restricted. They must be non-empty strings, with a maximum length of 30 byte, only using characters `0-9`, `a-z`, `-`, or `:` or `_`. The dollar (`$`) and tilde (`~`) characters are reserved for internal usage, and no uppercase letters are allowed.

## ITuple
This is a marker interface implemented by [tuple] and [tuple-number]. It is used to indicate a [tuple] or a _reference_ to a [tuple].

```java
package naksha.data;
public interface ITuple {
  /** The database-number of the tuple. */
  long databaseNumber();
  /** The catalog-number of the tuple. */
  int catalogNumber();
  /** The collection-number of the tuple. */
  int collectionNumber();
  /** The feature-number of the tuple. */
  long featureNumber();
  /** The version of the tuple. */
  long version();
}
```

## Tuple
A tuple is a mostly immutable state of a [feature] _(mostly immutable, because the `next_version` can be modified)_. A tuple has the following logical structure:

```java
public class Tuple implements ITuple {
  public Tuple(@NotNull JbonTuple jbon) { this.jbon = jbon; }
  /** The JBON encodes feature. Can be queried to avoid conversion into a full JVM heap object, safes memory. */
  public final @NotNull JbonTuple jbon;
  /** The weak reference to this tuple for caching. */
  public final @NotNull WeakReference<Tuple> weakRef = new WeakReference<>(this);
  /** The soft reference to this tuple for caching. */
  public final @NotNull SoftReference<Tuple> softRef = new SoftReference<>(this);
  /** Return the tuple as GeoJSON feature on the JVM heap. The method must not cache the object, every call should create a new feature instance. */
  public @NotNull JsonMap feature();
  /** Decodes the attachment. The method must not cache the attachment, every call should return a new copy. Read-only zero copy access is granted through the JBON tuple. */
  public @Nullable Binary attachment();
}
```

Note that the `Tuple` just wraps the `JbonTuple` to allow in-memory caching and extending the class with specific access methods. It as well simplifies decoding into a [GeoJSON] _feature_.

## Database
The `Database` represents a unique database, that can be stored at different places. However, only one of the places should be the primary storage, so every storage should know if it is a replication or main storage. Each database has one internal [catalog] named `naksha~admin`. This is a virtual [catalog] that is used to access the management data. This `naksha~admin` [catalog] contains by definition the following [collections]:

- Meta _(`naksha~meta`)_: A collection that stores internal metadata, for example the [feature] of the database configuration itself _(i.e. if this is a replica)_.
- Catalogs _(`naksha~catalogs`)_: A collection that stores all the [features] of all [catalogs].
- Versions _(`naksha~versions`)_: A collection that stores all the [features] of all [versions], so basically a transaction history.
- Books _(`naksha~books`)_: A collection that stores `global` [books].

The admin [catalog] may contain more [collections], but these are the mandatory ones.

Creating a new [catalog] feature in the `naksha~catalogs` collection creates a new catalog in the database.   is done by creating a new [feature] in the `naksha~catalogs` collection of the admin [catalog]. Deleting a [catalog] is done by deleting the corresponding [feature] from the `naksha~catalogs` collection. The same applies for [versions].

### Java

```java
package naksha.data;
public class Database {
  Database(@NotNull String id, long number) { this.number = number; }
  /** The unique identifier of the database. */
  public final @NotNull String id;
  /** The unique number of the database. */
  public final long number;
  /** The weak reference to this database. */
  public @NotNull WeakReference<Database> weakRef();
  /** The admin-catalog of the database. */
  public @NotNull AdminCatalog adminCatalog(); // "naksha~admin": 0
  /** The catalog with the given catalog-number. */
  public @NotNull Catalog catalog(int catalogNumber); // 1+
}
public class DatabaseTuple {
  DatabaseTuple(@NotNull JbonTuple tuple) { this.tuple = tuple; }
  /** The underlying JbonTuple of this database tuple. */
  public final @NotNull JbonTuple tuple;
  /** The database represented by this tuple. */
  public @NotNull Database database();
}
```

## Catalog
The catalog is sub-set of data within a [database]. The catalog represents for example a map, region, or some other higher organizational unit. Each catalog contains [collections] of [feature]. The catalog itself is as well a [feature] that is tracked in the admin-catalog of the database.

Within every catalog there is one mandatory [collection] named `naksha~collections`. This is a special [collection] that is used to store the [features] of the [collections] of the [catalog] them self. It is used for administration. Creating a new feature in this collection creates a new [collection] in the [catalog], and deleting a feature from this collection deletes the corresponding [collection] from the [catalog].

```java
package naksha.data;
public class Catalog {
  Catalog(@NotNull Database db, int number) { this.db = db; this.number = number; }
  /** The database to which the catalog belongs. */
  public final @NotNull Database db;
  /** The unique number of the catalog. */
  public final int number;
  /** The weak reference to this catalog. */
  public final @NotNull WeakReference<Catalog> weakRef = new WeakReference<>(this);
  /** The collection storing the collection records of all collections of this catalog, excluding the collections collection itself. */
  public @NotNull Collection collections(); // "naksha~collections": 0
  /** The collection with the given collection-number. */
  public @NotNull Collection collection(int collectionNumber); // 1+
}
```

## Collection
A collection is a set of [features]. All of them share the same structure. A collection is logically split into _HEAD_ and _HISTORY_. The collection does maintain indices to efficiently query the [features]. The _HEAD_ section of the collection contains only the latest [tuple] of each [feature], while the _HISTORY_ section contains all older [tuples] _(states)_.

Beware that replicas do not need to have the same structure as the source, so replication is done logical. The objects stored will always have the same hash, and the same content, but they can be encoded differently, with different indices.

```java
package naksha.data;
public final class Collection {
  Collection(@NotNull Catalog catalog, int number) { this.catalog = catalog; this.number = number; }
  /** The catalog to which the collection belongs. */
  public final @NotNull Catalog catalog;
  /** The unique number of the collection. */
  public final int number;
  /** The weak reference to this collection. */
  public final @NotNull WeakReference<Collection> weakRef = new WeakReference<>(this);
  /** The feature with the given feature-number. */
  public @NotNull Feature feature(long featureNumber);
}
```

## Feature
A feature represents a unique object with a unique feature-number, optionally with a unique identifier. It has a chain of mostly immutable states, called [tuple]. Each state is identified by its version, with links to the previous and next version.

```java
package naksha.data;
public class Feature {
  Feature(@NotNull Collection collection, long number) { this.collection = collection; this.number = number; }
  /** The collection to which the record belongs. */
  public final @NotNull Collection collection;
  /** The unique number of the feature. */
  public final long number;
  /** The weak reference to this feature. */
  public final @NotNull WeakReference<Feature> weakRef = new WeakReference<>(this);
}
```

## TupleId
A tuple-id is a unique reference to a [tuple] using string identifiers. The tuple-id is as well called Global Unique Identifier _(`GUID`)_, it is a string that uniquely identifies a [tuple] within the whole data model. The structure of the tuple-id is like following:

```urn:here:naksha:guid:{databaseId}:{catalogId}:{collectionId}:{featureId}[:{version}]```

Where the `version` is optional, if the `version` is omitted, it refers to the [HEAD] state of the feature.

```java
public final class TupleId {
  public TupleId(@NotNull String urn) {
    // TODO: Implement parsing of the urn, and validation of the format, throw DataError in case of error.
  }
  public TupleId(@NotNull Collection collection, @NotNull String featureId) {
    this(collection, featureId, 0L);
  }
  public TupleId(@NotNull Collection collection, @NotNull String featureId, long version) {
    // TODO: Implement.
  }
  public TupleId(@NotNull String databaseId, @NotNull String catalogId, @NotNull String collectionId, @NotNull String featureId, long version) {
    this.databaseId = databaseId;
    this.catalogId = catalogId;
    this.collectionId = collectionId;
    this.featureId = featureId;
    this.version = version;
  }
  /** The database-id of the tuple. */
  public final @NotNull String databaseId;
  /** The catalog-id of the tuple. */
  public final @NotNull String catalogId;
  /** The collection-id of the tuple. */
  public final @NotNull String collectionId;
  /** The feature-id of the tuple. */
  public final @NotNull String featureId;
  /** The version of the tuple. */
  public final long version;
}
```

Converting a tuple-id into a [tuple-number] requires to invoke the `resolve` method of a storage that stores the corresponding tuple. Caches are not guaranteed to be able to convert a tuple-id into a [tuple-number], because they do not always have the necessary indices.

## TupleNumber
All [tuple] are addressed using a unique tuple-number which is 256-bit _(32 byte)_ long in full representation. The structure is like following:

| Bits         | Size | Value               | Description                                                                                                                        |
|--------------|------|---------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `0`..`63`    | 64   | `database_number`   | The unique identifier of the database.                                                                                             |
| `64`..`95`   | 32   | `catalog_number`    | The identifier of the [catalog] within the [database] in which the [tuple] can be found.                                           |
| `96`..`127`  | 32   | `collection_number` | The identifier of the [collection] within the [catalog] of the [database] in which the [tuple] can be found.                       |
| `128`..`191` | 64   | `feature_number`    | The identifier of the [feature] within the [collection], within the [catalog] of the [database] in which the [tuple] can be found. |
| `192`..`203` | 12   | _reserved_          | Always `0`.                                                                                                                        |
| `204`..`255` | 52   | `version`           | The version of the [tuple].                                                                                                        |

The [tuple-number] can be compressed. When all [tuple] are stored in the same [database], the `database_number` can be shared, the same is true for the [catalog], [collection], and [feature]. Therefore, the smallest encoding uses only 52-bit per [tuple].

## Action
The `action` is encoded in the lower two bit of the `version`. Whenever a new version is generated, the lowest two bit are set _(`11b`)_ to signal that this is a pure `VERSION`. For every [feature] being part of that version, the lower two bit are then adjusted to the actual `action` applied to the [feature], which can be `CREATE` _(00b)_, `UPDATE` _(01b)_, or `DELETE` _(10b)_.

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

The reason that the action is encoded into the version is that it does not harm, but improves certain queries drastically, plus it optimizes views. In views data is layered on top of each other. When a storage is queried for data, it will return only the [tuple-number]'s of the found [tuple]. Now, when being in a view, the data of a [tuple] does not need to be loaded, when the top most layer contains the record in a `DELETED` state; except deleted data should be shown as well. Therefore, having the `action` in the [tuple-number] does save data loading in views.

When data is queried, having the `action` in the `version` is helpful to not return [features] being deleted. This only requires an additional filter to `version`, so we can directly remove all tuple-numbers that are in deleted state.

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

A tuple-number can stringified into a [URN], see [references].

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

## Versioning
A version is a 56-bit unsigned integer in the following formats:

```
                                                                   action
          {r}{                automatic version                       }{}
 00000000-000yyyyy-yyyyyyym-mmmddddd--ssssssss-ssssssss-ssssssss-ssssssaa

                                                                   action
          { reserved }{          manual version                       }{}
 00000000-00000000-000vvvvv-vvvvvvvv--vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvaa

          {r}{                       HEAD                               }
 00000000-00011111-11111111-11111111--11111111-11111111-11111111-11111111

          {r}{                       MAX                                }
 00000000-00011111-11111111-11111111--11111111-11111111-11111111-11111011

          {                          NULL                               }
 00000000-00000000-00000000-00000000--00000000-00000000-00000000-00000000
```

Therefore, the version has the following general parts:
- `yyyy-yyyyyyyy`: The year of the version, encoded in 12 bit, a value between `16` and `4095`.
- `mmmm`: The month of the version, encoded in 4 bit, a value between `1` _(January)_ and `12` _(December)_.
- `ddddd`: The day of the version, encoded in 5 bit, a value between `1` and `31`.
- `ssssss-ssssssss-ssssssss-ssssssss`: The sequence of the version, encoded in 30 bit, so it can represent up to `1,073,741,824` versions per day, starting from `0`.
- `vvv-vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvvv`: The version number, encoded in 43 bit, so it can represent up to `8,796,093,022,208` versions, starting from `0`.
- `aa`: The action of the version, encoded in 2 bit.

The lowest two bit of all valid versions are always used to encode the `action`, therefore all version must have them always set to `11b` for pure versions. This is done by left shifting the version by 2, and ORing the version with 3.

The proof that this is compatible with JavaScript:

- `9006786937880575`: `((4095n << 41n)+(12n << 37n)+(31n << 32n)+4294967295n) <= BigInt(Number.MAX_SAFE_INTEGER)`: _true_ 
- `9007199254740992`: `(4096n << 41n) <= BigInt(Number.MAX_SAFE_INTEGER)`: _false_

The values between `9006786937880575` and `9007199254740992` are by specification invalid states.

### Automatic Version
The **default** versioning, when nothing else is selected, is _automatic version_. This is a database local version that uses a sequential counter in the database, being reset every day to 0. The sequence is shift left by 2, then encoded in the lower 31 bit of the version. This means every day provides up to `536,870,912` versions _(~5326 versions per second)_. The upper 33 bit of the version are used to store the year, month, and day. This is important to organize _HISTORY_.

### Manual Version
For manual versioning the client needs to come up with some own useful bit pattern, related to history partitioning _(see `shift`ing)_. They are generally simple positive numbers between `1` and `8,796,093,022,208` _(excluding)_.

### HEAD Version
The version `9,007,199,254,740,991` _(2^53-1)_ represents the _HEAD_ version, which is the latest version available in the storage. This is a special version that is only used for `next_version` to signal that a [tuple] is in the _HEAD_ state. Clients can use the value to signal, that they want data in the latest available version. In _JavaScript_ this maches `Number.MAX_SAFE_INTEGER`.

### MAX Version
The version `9,007,199,254,740,987` _(2^53-5)_ represents the maximal valid version. In _JavaScript_ this matches `Number.MAX_SAFE_INTEGER-4`.

### NULL Version
The version `0` is a special version, being used as replacement for the `null`. This can have a bunch of implications, when `null` has a special meaning.

### Querying a version
We use [SQL] to demonstrate the general concept how versions are searched for.

Before any search can start, the query-version needs to be fixed to a valid `VERSION` value. The maximum version that can be queried is `9,007,199,254,740,987` _(2^53-5)_ and the minimal version that can be queried for is `3`. Therefore, any client request for a version need to be clipped into the range of `3` till `9,007,199,254,740,988` _(excluding)_. Be aware that the lowest two bit of the version must always set to `11b`, therefore the version provided to search for must be logically ORed with `3`. So, we do:

```javascript
var query_version = Math.max(3, Math.min(requested_version, 9007199254740987)) | 3;
```

To search for [tuple] in the _HEAD_ state a general query looks like:

```sql
SELECT * FROM table WHERE version <= 9007199254740987 AND next_version > 9007199254740987 AND {other-condition};
```

This will return the _HEAD_ [tuple] of the searched [feature]. This can be generalized into the common query form:

```sql
SELECT * FROM table WHERE version <= $version AND next_version > $version AND {other-condition};
```

**Note**: It requires a clipped version between `3` and `9,007,199,254,740,988` _(excluding)_, and the version must be logically ORed with `3` to ensure that the lowest two bit are set.

This general query will only return one [tuple] with the latest state of the [feature] that belongs to this [version]. Beware, the returned [tuple] can be in a lower version, this query just ensured that the [tuple] that belongs logically to the queried [version] of the [database] is returned. Let's review this, assume we have the following data:

| db-row | id    | version              | next_version     | action         |
|--------|-------|----------------------|------------------|----------------|
| 1      | `foo` | 590 (`10010011_10b`) | 9007199254740991 | DELETE (`10b`) |
| 2      | `foo` | 77 (`10011_01b`)     | 590              | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | UPDATE (`01b`) |
| 4      | `foo` | 13 (`11_01b`)        | 33               | UPDATE (`01b`) |
| 6      | `foo` | 4 (`10_00b`)         | 13               | CREATE (`00b`) |

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
SELECT * FROM table WHERE version <= 9007199254740987 AND next_version > 9007199254740987 AND (version & 3) < 2 AND id = 'foo';
```

We can see, that the version condition will select row `#1`, but the added secondary version filter will exclude the row, because the lowest two bit of the version is `2` _(deleted)_. Therefore, this query does not return any row, because in that version the record is deleted.

### Query multiple versions
Assuming the same data state as above:

| db-row | id    | version              | next_version     | action         |
|--------|-------|----------------------|------------------|----------------|
| 1      | `foo` | 590 (`10010011_10b`) | 9007199254740991 | DELETE (`10b`) |
| 2      | `foo` | 77 (`10011_01b`)     | 590              | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | UPDATE (`01b`) |
| 4      | `foo` | 13 (`11_01b`)        | 33               | UPDATE (`01b`) |
| 6      | `foo` | 4 (`10_00b`)         | 13               | CREATE (`00b`) |

We can search for multiple versions of a feature, an only limit the lower or upper end. So, search for all version till version `500`:

```sql
SELECT * FROM table WHERE version <= 503 AND id = 'foo';
```

Result is:

| db-row | id    | version              | next_version     | action         |
|--------|-------|----------------------|------------------|----------------|
| 2      | `foo` | 77 (`10011_01b`)     | 590              | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | UPDATE (`01b`) |
| 4      | `foo` | 13 (`11_01b`)        | 33               | UPDATE (`01b`) |
| 6      | `foo` | 4 (`10_00b`)         | 13               | CREATE (`00b`) |

To query for all versions beyond version `500`:

```sql
SELECT * FROM table WHERE version > 503 AND id = 'foo';
```

| db-row | id    | version              | next_version     | action         |
|--------|-------|----------------------|------------------|----------------|
| 1      | `foo` | 590 (`10010011_10b`) | 9007199254740991 | DELETE (`10b`) |

Query for all versions in the range of version `15` and `503`:

```sql
SELECT * FROM table WHERE version <= 503 AND version > 15 AND id = 'foo
```

| db-row | id    | version              | next_version     | action         |
|--------|-------|----------------------|------------------|----------------|
| 2      | `foo` | 77 (`10011_01b`)     | 590              | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77               | UPDATE (`01b`) |

We additionally can filter on the `action`.

### Version Records
The version itself is as well a [feature] under [database] management. The `record_number` of the version [feature] matches the `version` that it tracks. The `id` of the version [feature] is just the stringified representation of the version, so it is the decimal representation of the version number. The version can be modified by clients to store transactional information with it. For example, clients may add annotations into the version, for example comments provided by users. The version features can be queried to check which versions do exist in the storage.

Additionally, the version [features] can be used by clients to track the changes in the [database], as each version [feature] stores the [catalogs], [collections], and [features] that were modified. It is as well important for caches to understand which [catalogs], [collections], and [features] have changes since the last cache update. It allows caches and replicas to only download the changes they do not yet know about.

The version collection can be extended by custom fields, if necessary, the same way normal [collections] can be extended.

## Two-Phase-Queries
A major concept of the Naksha data model is that data reading is always split into two phases.

The first phase is to execute a query against the storage to find all [tuple] matching certain criteria. These queries can be done against _HEAD_ or a specific [version]. When the query returns it does not return the actual data, it only returns the [tuple-numbers] of the [tuple] being part of the result-set. This uses _(mostly)_ index-only scans, so normally every index contains the `fn` _(feature-number)_ and `version` to they can be used as index-only scans.

The second phase is then loading the actual [tuple], because [tuples] are by definition mostly immutable, no [tuple] ever has to be loaded twice, as the [tuple] are cached on the JVM heap by the `lib-data`. This caching is done using [Soft-References], which allows to use all available and free heap for data caching. Additionally, the [tuple] can be cached in external services like [Redis] or simply at ephemeral SSDs _(we should use all resource we can get for cheap local caching)_.

Even while it theoretically would be possible to only load parts of a [tuple], this is not part of the model, because the [tuple] is _immutable_, and caching partial data is much more complicated and less efficient than caching full [tuple]. Therefore, it is generally more efficient to load the whole [tuple] at once, and to cache it intesively.

## References
The Naksha data mode defines two reference formats:

- The Tuple-Number _(`TN`)_ as string: `urn:naksha:tn:{storageNumber}:{catalogNumber}:{collectionNumber}:{recordNumber}[:{version}]`
- The Global Unique Identifier _(`GUID`)_ as string: `urn:naksha:guid:{storageId}:{catalogId}:{collectionId}:{featureId}[:{version}]`

For both variants the _HEAD_ state can be referred by simply omitting the `version`, setting it to `0` or [HEAD] _(`9,007,199,254,740,991`)_. In the context of reference parsing the [NULL version] is interpreted as [HEAD version] reference.

The storage must internally only operate upon the `TN` variant. Clients are allowed to use the `GUID` variant, because they may create new [tuples] using identifiers only, not yet knowing the [Tuple-Number] or version, when creating the [features], and when adding references to these new [features]. So, the job of the storage is to convert all references given as `GUID` into correct full qualified `TN` variants. It therefore has to search the record and replace all `GUID` references with `TN` references.

In a nutshell, [tuple] read from a storage should never contain references in `GUID` format, they should always have them stored in `TN` format.

## Partitioning
To store big data efficiently, it needs to be partitioned. Within the Naksha data model there are two major logical sections defined: _HEAD_ and _HISTORY_. They store all the [tuple] of the [features]. They are logical concepts that each storage implementation can use to optimize data storage.

The _HISTORY_ section is partitioned logically first by `next_version`, to allow efficient dropping of historic data. We call this **historic partitioning**, and it is always applied if the _HISTORY_ is enabled for a [collection]. It only happens within the _HISTORY_ section. If _HISTORY_ is disabled for a collection, no **historic partitioning** is done, and `next_version` actually does not matter anymore.

Next to the **historic partitioning** of the _HISTORY_ section, there is a general **distribution partitioning**, which we will clarify first.

The _HEAD_ section and each historic partition are optionally distribution partitioned, if enabled. This is an optional feature that by default is disabled, but can be enabled to store a huge number of [features] and [tuple] in a [collection]. When enabled, we distribute [features] across distribution partitions. The number of distribution partitions can be configured when creating a collection, and defaults to `1` _(so no distribution partitioning)_. To assign [features], and all their [tuple], to the same distribution partition, the lower 16-bit of the record-number are used as **distribution key**. This means, all [tuple] of a [feature] are stored in the same distribution partition. So, when loading a [tuple] of a [feature] in a specific [version], only a single partition has to be accessed. When searching for data, all partitions can be queried in parallel, improving search performance. This layout therefore speeds up searching for data, while making access to known data faster. Loading the _HEAD_ state _([tuple])_ technically means to query a single partition and is therefore rather very fast. Loading multiple tuple can be done in parallel from all partitions they are in. The distribution is simply done by dividing the unsigned lower 16-bit value of the record-number by the amount of distribution partitions _(`n`)_, using the division rest as partition index. For example, assume the distribution key of a [feature] is `1234`, so the unsigned lower 16-bit of the record-number is decimal `1234`, and we have `8` distribution partitions configured in the [collection], then dividing `1234` by `8` gives us `154` with a rest of `2`. Therefore, the partition-number to search in is `2`. This guarantees that we always have a partition index between `0` and `n`-1.

The **historic partitioning** is done by the `next_version` of each [tuple]. All [tuple] in the _HEAD_ section have no `next_version` (it is intrinsically [HEAD], `9,007,199,254,740,991`, and is therefore not stored in HEAD rows). When a new [tuple] of a [feature] is created, the current [tuple] in the _HEAD_ section becomes historic data. It is moved into _HISTORY_, and at insertion time `next_version` is set to the version of the new [tuple]. From that point on the [tuple] is immutable in _HISTORY_ until being purged. The purging is normally done by deletion of complete historic partitions, which is the reason for this design. The walk-back from a known _HEAD_ to its predecessors is performed via `next_version`, by searching _HISTORY_ for the row whose `next_version` matches the known later version.

Now, when deciding in which historic partition a [Tuple] should be located a **partition-key** is needed. To generate the **partition-key** the value from `next_version` is used. For this, the `next_version` is bitwise-ANDed with `0x001F_FFFF_FFFF_FFFF` _(effectively clearing the top 12-bit)_. Then value is shifted right by a configured `shift` amount. The `shift` is configured when creating a [collection] and must stay constant for the whole lifetime of a [collection]. The `shift` defaults to `41`, which means we store one historic partition per year. Reducing the `shift` to `37` would result in one historic partition per month, and reducing it to `32` would result in one historic partition per day.

**Note**: When manual versioning is used for a collection, the default `shift` value is reduced to `24` _(so ~16 million versions are stored per partition)_.

## TupleStorage
The abstract `TupleStorage` base class, extended by all [storages] and [caches], representing a sink for [tuples] aka _feature_ states. Only allows to read and store tuples using [tuple-numbers].

## TupleCache
The `TupleCache` is a static in-memory cache for tuples. It holds a certain threshold of soft-references, plus as much as possible weak-references to tuple. The cache is used to speed up access to tuples, and to reduce memory consumption by allowing the garbage collector to reclaim memory when needed.

### Java

```java
package naksha.data;
public class TupleCache implements TupleStorage {
  public static final long IN_MEMORY_LATENCY = 10L; // 10 nanoseconds, this is just an estimation, it can be adjusted based on the actual performance of the cache implementation.
  public static final long DISK_LATENCY = 1_000_000L; // 1 millisecond, this is just an estimation, it can be adjusted based on the actual performance of the disk storage implementation.
  public static final long NETWORK_LATENCY = 10_000_000L; // 10 milliseconds, this is just an estimation, it can be adjusted based on the actual performance of the network storage implementation.
  private static final AtomicReference<@NotNull List<@NotNull TupleCache>> ALL = new AtomicReference(List.of(new TupleCache()));
  public static @NotNull List<@NotNull TupleCache> all() { return ALL.get(); }
  public static boolean compareAndSet(@NotNull List<@NotNull TupleCache> existing, @NotNull List<@NotNull TupleCache> updated) {
    final ArrayList<@NotNull TupleCache> copy = new ArrayList<>(updated);
    Collections.sort(copy);
    final List<@NotNull TupleCache> immutable = List.copyOf(copy);
    return ALL.compareAndSet(existing, immutable);
  }
  protected TupleCache() {}
  /** Iterate all caches in order to return a tuple from the cache, or null if it is not in any cache. */
  public static @Nullable Tuple get(@NotNull ITuple tupleId) { /* ... */ }
  /** Iterate all caches in order to replace all tuple in the given array with cached versions being available. Stops ones done, optimizes for latency, requests in parallel, when needed.
   * Returns the amount of elements that a `Tuple`, so that `tuples.size() - result` is the amount of not loaded tuple. */
  public static int load(@NotNull List<@Nullable ITuple> tuples) { /* ... */ }
  // The put methods invokes store at all caches, which work asynchronously, so the put method can return immediately. Errors are logged.
  public static void put(@NotNull Tuple tuple) { /* ... */ }
  public static void putAll(@NotNull List<@Nullable Tuple> tuples) { /* ... */ }
}
```

## Storage
Every storage implementation must extend the abstract `Storage` class, which extends the `TupleStorage`. The storage is responsible for managing the data in some storage, including the [databases], [catalogs], [collections], [features], [tuples], [versions], and [books]. It provides better methods to search for data in the storage, and allows data management _(if it is not read-only)_.

All operations return the result of the operation, for example when creating a new [catalog], the created [catalog] is returned as [tuple]. When updating a [catalog] the updated [catalog] is returned as [tuple]. When deleting a [catalog], the deleted [catalog] is returned as [tuple]. Beware, that delete can modify the state of the feature, because the deleted state is a valid tombstone state. The `PURGE` operation moves the deletion state form the _HEAD_ section of the collection to the _HISTORY_ section, so the deleted state is not visible anymore, when reading _HEAD_.

Creating the storage is out of scope of the data model, the application need to create the storage and manage its lifecycle.

However, all storages must support a readonly virtual administration [database] _(`naksha~admin`)_ that contains the management data of the storage.

### Java

```java
package naksha.data;

public abstract class Storage extends TupleStorage {
  // The databases them self are not versioned, they exist only in _HEAD_ state.
  public abstract @NotNull List<@NotNull Database> listDatabases(long version);
  public abstract @Nullable DatabaseTuple getDatabase(@NotNull String id, long version);
  public abstract @Nullable DatabaseTuple getDatabase(@NotNull long number, long version);
  public abstract @NotNull DatabaseTuple createDatabase(@NotNull JsonDatabase database);
  public abstract @NotNull DatabaseTuple updateDatabase(@NotNull JsonDatabase database);
  public abstract @NotNull DatabaseTuple deleteDatabase(@NotNull JsonDatabase database);

  public abstract @NotNull List<@NotNull SessionInfo> listSessions();
  public abstract @NotNull ReadSession readSession(@NotNull Database database, @NotNull SessionOptions options);
  public abstract @NotNull FullSession fullSession(@NotNull Database database, @NotNull SessionOptions options);
}
public record SessionOptions(@Nullable String appId, @Nullable String author, boolean enableTracking) {}
public interface SessionInfo {
  @NotNull Storage storage();
  @NotNull Database database();
  @NotNull SessionOptions options();
}
public interface ReadSession extends AutoClosable {
  @NotNull SessionInfo info();

  // Only a storage can translate a tuple-identifier into a tuple-number.
  @Nullable TupleNumber resolve(@NotNull TupleId tupleId);
  @Nullable ITuple @NotNull [] resolve(@NotNull TupleId @NotNull [] tupleIds);

  // Version methods
  @NotNull Version getVersion(long version);
  
  // Admin methods
  @NotNull Collection getCollections(@NotNull Database database);
  
  // Catalog methods
  boolean isCatalog(@NotNull Tuple tuple);
  @NotNull JsonCatalog toCatalogJson(@NotNull Tuple tuple);
  @NotNull Catalog refresh(@NotNull Catalog catalog);
  @Nullable Catalog getCatalog(@NotNull String catalogId, boolean includeDeleted);
  @Nullable Catalog getCatalog(@NotNull int catalogNumber, boolean includeDeleted);
  @Nullable Catalog createCatalog(@NotNull JsonCatalog catalog);
  @Nullable Catalog updateCatalog(@NotNull JsonCatalog catalog);
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

## Custom Members
Not all storages can read the binary encoded [tuple]. To ensure that data needed for indexing and searching is accessible to the storage, applications can define custom members. A custom member is an [indexable] property extracted from the [tuple]. Only members can be searched for, see [indexable].

Not all implementations may need to extract the member from the data, some may be able to directly index properties from the data. Not all implementations may actually store [JBON] encoded binaries. However, all storages need to return [JBON] encoded binaries as [tuple], no matter in which format the data is actually stored, and accept [JsonFeature] objects with [Binary] as input. The storage can use the default [JBON] encode to generate it.

### Java
The `CustomMember` class is a [proxy] for a [JsonMap].

```java
package naksha.data;
public class CustomMember extends MapProxy {
  public static final Literal NAME = Literal.of("name");
  // TODO: Add setter/getter for name as String
  public static final Literal TYPE = Literal.of("type");
  // TODO: Add setter/getter for type as DataType
  public static final Literal PATH = Literal.of("path");
  // TODO: Add setter/getter for path as StringArray/StringList
}
```

## Custom Indices
To improve query performance above [custom members], custom indices can be defined above members.

### Java

```java
package naksha.data;
public class CustomIndexType extends JsEnum {
  public CustomIndexType(@NotNull String value, @NotNull DataType ... types) { super(value); }
  
  // Index for certain data-types, should host a list of DataType being supported.
  public static final CustomIndexType BTREE = new CustomIndexType("btree");
  public static final CustomIndexType SPATIAL = new CustomIndexType("spatial");
  public static final CustomIndexType TAGS = new CustomIndexType("tags");
  public static final CustomIndexType FLAT = new CustomIndexType("flat");
}
public class CustomIndex extends MapProxy {
  public static final Literal NAME = Literal.of("name");
  // TODO: Add setter/getter for name as String
  
  public static final Literal TYPE = Literal.of("type");
  // TODO: Add setter/getter for type as CustomIndexType
  
  public static final Literal PATH = Literal.of("path");
  // TODO: Add setter/getter for path as StringArray/StringList
}
```

We do not allow custom unique indices, because they do not work with partitioning _(as we partition the data, we can't guarantee uniqueness)_. We can think about some additional mechanism to guarantee uniqueness of custom members.

## JsonFeature

## Tags
The `tags` field is a special field that can be used to store tags for a [feature]. The tags are stored as a JSON array of strings, so they can be indexed and searched for. The storage can use the tags to optimize search queries, for example by using an inverted index. The client can use the tags to store any kind of metadata that is useful for searching and indexing. For example, the client can use the tags to store the type of the record, or to store any other kind of classification.

## XYZ namespace
For historic reasons this specification formally specifies the so called XYZ namespace. This is a flat map of all dedicated members of all [features] stored in the [collection]. The client may add or remove members from the XYZ namespace, except for the mandatory ones. In classic systems this map is exposed in `root.properties["@ns:com:here:xyz"]`.

It is the job of the client to copy values from somewhere into the XYZ namespace, and to move the data back. Within the [Naksha-Hub] this is done by adding a corresponding handler directly in front of the storage.

Each [collection] has a set of pre-defined members, which are stored in dedicated indexed places of the storage.

The minimal **mandatory** members that are defined by this specification for all [collections] are:

- `number`: `int64` - The record number. If negative, the `id` of the record is stored in the `meta` section of the collection. If positive, the `id` of the record is the number as decimal string.
- `version`: `uint52` - The version of the record.
- `next_version`: `uint52` - The next version of the record, defaulting to [HEAD]. Only stored in _HISTORY_ — in _HEAD_ this is intrinsically [HEAD].
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
of the [tuple] of the [feature]. These fields are used to store the metadata of the [feature], and to store some additional information that can be used for indexing and searching. The fields are defined in the XYZ namespace, which is a flat map of all storage members. The XYZ namespace is defined as follows:

## Origin
The `origin` is an optional value, if enabled it stores a [reference] to the origin of a [feature]. When a [feature] is modified, normally the _metadata_ of the new [tuple] is not modified by the client. Therefore, the moment the new [tuple] is sent to a storage, the storage can check the [tuple-number] of the [tuple], which will refer to the state the client modified. If this state is located outside the [collection] into which the new [tuple] is stored, the storage automatically fills the `origin` field with a reference to the origin state.

The `origin` will stay unchanged until a _REBASE_ is done. When a _REBASE_ is done, the `origin` field is updated to the new foreign state to which the [feature] is rebased.

## Replacement
A replacement is i.e. when splitting a topology into two parts, the original [feature] is deleted, and two new [features] are created. Or when a topology is joined, so two [features] are deleted, and a new one is created. All of this should be done in a single version _(transaction)_. This does already indicate a replacement operation. However, as technically multiple  the `replace_id` can be used to link the three [feature] together, so that it is possible to find all [feature] that belong to the same replacement operation. This is especially useful for tracking the history of changes, and for debugging purposes.

TODO

- `global`: A custom global version, which is a 52-bit unsigned integer.
- `next_global`: The next global version, `null` if this is the latest global version.
- `replacement_id`: A unique string to track replacement operations.
- `rebase_id`: A unique string to track rebase operations.

These fields can be used by applications to track versions with custom information, for example to track replacements. The reason there are pre-defined fields is that the version table can't be created by the client, as it is part of the administrative data of the database, so the client can't define custom fields on it. Therefore, we provide a set of pre-defined fields that can be used for this purpose. The fields are optional, so they can be left `null` if not needed, and will not consume much space.

The `global_version` field can be used to translate global versions into local versions.

## DataManager
A data-manager is a needed root object. An application can just have one _(as static singleton)_ or use multiple. The data-manager is the main entry point to access the data model, it is used by [storages] and the application. It provides methods to access the [storages], [databases], [catalogs], [collections], [features], and [tuples].

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

## Changes
The following changes have been made to the data model between the original draft and the current version:

- We no longer track storages, but rather databases.
  - This is important to have replication working. We do not care where data is stored, just how it is locically organized and addressed.
- We allow custom members and custom btree indices.
  - This is important for some internal projects.
- Most members have been made optional, so that they can be left `null` if not needed, and will not consume much space.

[Indexable]: #indexable
[indexable]: #indexable
[Storage]: #storage
[storage]: #storage
[storages]: #storage
[Database]: #database
[database]: #database
[databases]: #database
[Catalog]: #catalog
[catalog]: #catalog
[catalogs]: #catalog
[Collection]: #collection
[collection]: #collection
[collections]: #collection
[Feature]: #feature
[feature]: #feature
[features]: #feature
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
[HEAD version]: #head-version
[MAX]: #max-version
[MAX version]: #max-version
[NULL]: #null-version
[NULL version]: #null-version
[Version]: #versioning
[version]: #versioning
[versions]: #versioning
[Partitioning]: #versioning
[partitioning]: #versioning
[Origin]: #origin
[origin]: #origin
[Replacement]: #replacement
[replacement]: #replacement
[book]: JBON2.md#books
[books]: JBON2.md#books
[JSON]: https://www.rfc-editor.org/rfc/rfc8259
[GeoJSON]: https://datatracker.ietf.org/doc/html/rfc7946
[TWKB]: https://github.com/TWKB/Specification/blob/master/twkb.md
[JBON]: JBON2.md
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



```
	1.	Lead contact of RCA? - Alexander Lowey-Weber
	2.	Likelihood of reoccurrence (Low/Medium/High)? - Low
	3.	Mitigation plan in case of reoccurrence? - None
	4.	Preliminary root cause (Fault Diagnosis and Initial Root Cause Analysis) - Network issue, connection failed to HERE account and AWS S3 at the same time, very likely, and no errors in HA logs.
	5.	Corrective Actions? (What happened and what action resolved the issue? - None
	6.	Lessons Learned/Recommendations? - None
	7.	Impact Statement (Customer impact---What couldn't the customers do or receive?) - None, because related to HERE internal network
	8.	Start Time and End Time in UTC - ?
	9.	Was the manual failover implemented within first 10 mins (Yes/No). If no, why not? (NOT APPPLICABLE) 
 10.  If Incident caused by a change, were all the change deployment checklist items complied with? What checklist item(s) were missed that lead to this incident? No Change

```