# The Naksha Data Model

## Introduction
The Naksha Data Model _(**NDM**)_ is made to exchange data in the form of [GeoJSON] features between different applications, components, services, and storages. The data model is designed to support efficient storage, retrieval, and query of data. It's optimized to exchange the data serialized into [JSON], [GeoJSON], [protobuf], and [JBON]. [JBON] is a special binary encoding, highly compact, mostly immutable, and does not require parsing to read the data, developed specifically for the Naksha data model.

The data model supports operations to manage the data lifecycle, including creation, update, and deletion. It supports in maintaining a history of changes. The data model is also designed to support efficient querying of the data, including queries for specific versions and queries for the latest version _(HEAD)_.

The data model is an abstraction layer that allows to decouple the physical storage from the logical structure of the data. This allows for flexibility in the choice of storage technology and allows for future changes to the storage technology without affecting the logical structure of the data.

## Data
The data class is a central singleton to collect helper methods. Mainly used for interning, but as well things like [JSON] parser and transformer.

```java
public final class Data {
  public static @NotNull Double intern(float value) { /* ... */ }
  public static @NotNull Double intern(double value) { /* ... */ }
  public static @NotNull Double intern(@NotNull Double value) { /* ... */ }
  public static @NotNull Long intern(byte value) { /* ... */ }
  public static @NotNull Long intern(short value) { /* ... */ }
  public static @NotNull Long intern(int value) { /* ... */ }
  public static @NotNull Long intern(long value) { /* ... */ }
  public static @NotNull Long intern(@NotNull Long value) { /* ... */ }
  public static @NotNull Literal intern(@NotNull CharSequence value) { return Literal.of(value); }
  public static @NotNull Literal literal(@NotNull CharSequence value) { return Literal.of(value); }
  // TODO: Add more helpers:
  //       byte asByte(...)
  //       short asShort(...)
  //       int asInt(...)
  //       int asLong(...)
  //       float asFloat(...)
  //       double asDouble(...)
  public static @NotNull String asString(@NotNull CharSequence chars) { return Literal.of(chars).toString(); }
  public static @NotNull String asString(@Nullable CharSequence chars, @NotNull CharSequence alternative) {
    return Literal.of(chars != null ? chars : alternative).toString();
  }
  public static Object parse(@NotNull CharSequence chars, @Nullable ParseOptions options);
  public static Object parse(byte @NotNull [] utf8_bytes, @Nullable ParseOptions options);
  public static byte @NotNull [] serialize(@Nullable Object object, @Nullable SerializeOptions options);
  public static void stringify(@Nullable Object object, @Nullable StringifyOptions options, @NotNull Appendable buffer);
  public static @NotNull String stringify(@Nullable Object object, @Nullable StringifyOptions options);
  public static <T> @NotNull T transform(@NotNull IStruct source, @NotNull Class<T> target);
  // Will throw DataError if the source can't be transformed into a JSON structure.
  public static @NotNull IStruct transform(@NotNull T source);
}
```

## Literals
The JSON map, set and array implementations are optimized for low memory consumption. All keys in the JSON map are interned to guarantee that the same key is not in memory multiple times. This is done by wrapping them into a `Literal`. This is already done by the parser. This feature can be used by the application as well via `Literal.get` calls. The JSON parser itself will intern all keys and values to reduce memory consumption. Beware that interning is only guaranteed for strings, all other data types have just a possibility to be interned, but it is not guaranteed.

```java
// byte[]
//   JVM header = 16 byte
//   int length = 4 byte
//   ... data
//   = 20 byte + n byte data

// String
//   JVM header = 16 byte
//   byte[] value = 28+ byte (8 byte pointer + 20 byte header + `n` byte data)
//   byte coder = 1 byte
//   int hash = 4 byte
//   boolean hashIsZero = 1 byte
//   = 50 byte+ byte

// WeakReference, same applies for Long, Double 
//   JVM header = 16 byte
//   referent/value = 8 byte
//   = 24 byte
public final class Literal implements CharSequence, Comparable<CharSequence> {
  public static @NotNull Literal of(@NotNull CharSequence value) { /* ... */ }
  public static @Nullable Literal get(@Nullable CharSequence value) { /* ... */ }
  Literal(@NotNull String value) { /* ... */ }

  // JVM Header: 16 byte
  public final @NotNull String value; // 8 byte, 50+ byte = 58+ byte
  public final long murmurHash; // 8 byte
  public final @NotNull WeakReference<Literal> weakRef; // 8 byte, 24 byte = 32 byte
} // = 114 byte+ byte
```

Therefore, a string literal adds ~64 byte to the memory consumption of a `String`, which uses 50 byte _(plus characters)_. That means, deduplication is only beneficial to memory consumption when there are at least three usages. However, especially for keys there are potentially many thousands of usages. Next to just the memory consumption, two literals can be compared using the `==` operator, which is much faster than the `equals` method. So, there are more adantages apart from just memory consumption. Another, advantage is that all lierals are binary compatible, because they are always stored in [NFKC] form.

For the long and double values, only certain specific values are being cached. There is no need for weak references, so we just keep a cache table of a certain size and deduplicate what we can. For example really often used values like `0.0`, `0.5`, `1.0`, `-90.0`, `90.0`, `-180.0`, `180.0`, ... are permanently cached. Longs are already caches by the JVM, when `Long.valueOf` is used, but this only works for values between `-128` and `127`, so we extend this range with a dynamic cache. Actually, our dynamic cache will be much more simple.

The `Literal` is mostly used internally within `JsonMap` for keys. However, it can be used by applications as well to speed up access in maps or when they want to compare strings using `==` for performance reasons.

## Error Handling
All methods can throw an `DataError`, which is a `RuntimeException`. Applications are free to catch this exception or to ignore it and leave the error handling to the caller.

```java
package naksha.data;
public class DataError extends RuntimeException {
  // TODO: Add constructors and members!
}
```

## Data Types
To allow interoperability between different storages, applications, modules, and services, the data model supports a set of pre-defined supported data types:

| Java                 | Idx              | Prim | Type-Emum           | Javascript           | Description                                                                                                    |
|----------------------|------------------|------|---------------------|----------------------|----------------------------------------------------------------------------------------------------------------|
| `Undefined`          |                  |      | `UNDEFINED`         | `undefined`          | The undefined type, a singleton in Java.                                                                       |
| `null`               | btree            | yes  | `NULL`              | `null`               | A boolean.                                                                                                     |
| `boolean`            | btree            | yes  | `BOOL`              | `Boolean`            | A boolean.                                                                                                     |
| `byte`               | btree            | yes  | `BYTE`              | `number`             | A 8-bit integer.                                                                                               |
| `short`              | btree            | yes  | `SHORT`             | `number`             | A 16-bit integer.                                                                                              |
| `int`                | btree            | yes  | `INT`               | `number`             | A 32-bit integer.                                                                                              |
| `long`               | btree            | yes  | `LONG`              | `BigInt`             | A 64-bit integer, can be encoded as [JSON] compatible string: `data:application/long,{decimal}`.               |
| `float`              | btree            | yes  | `FLOAT`             | `number`             | A 32-bit floating point number.                                                                                |
| `double`             | btree            | yes  | `DOUBLE`            | `number`             | A 64-bit floating point number.                                                                                |
| `byte[]`             | btree            |      | `BYTEA`             | `Int8Array`          | A byte-array.                                                                                                  |
| `short[]`            |                  |      | `SHORTA`            | `Int16Array`         | A 16-bit integer array.                                                                                        |
| `int[]`              |                  |      | `INTA`              | `Int32Array`         | A 32-bit integer array.                                                                                        |
| `long[]`             |                  |      | `LONGA`             | `BigInt64Array`      | A 64-bit integer array.                                                                                        |
| `float[]`            |                  |      | `FLOATA`            | `Float32Array`       | A 32-bit floating point number array.                                                                          |
| `double[]`           |                  |      | `DOUBLEA`           | `Float64Array`       | A 64-bit floating point number array.                                                                          |
| `Timestamp`          | btree            | yes  | `TIMESTAMP`         | `Date`               | A 48-bit unsigned interger representing a UNIX epoch timestamp in milliseconds.                                |
| `String`             | btree            | yes  | `STRING`            | `String`             | A text of [UNICODE] code-points.                                                                               |
| `Geometry`           |                  |      |                     | `Geometry`           | `org.locationtech.jts.geom.Geometry`; Interface for all geometries, [GeoJSON] compatible.                      |
| `GeometryCollection` |                  |      | `GEO_COLLECTION`    | `GeometryCollection` | `org.locationtech.jts.geom.GeometryCollection`                                                                 |
| `Point`              | spatial          |      | `POINT`             | `Point`              | `org.locationtech.jts.geom.Point`                                                                              |
| `MultiPoint`         | spatial          |      | `MULTI_POINT`       | `MultiPoint`         | `org.locationtech.jts.geom.MultiPoint`                                                                         |
| `LineString`         | spatial          |      | `LINE_STRING`       | `LineString`         | `org.locationtech.jts.geom.LineString`                                                                         |
| `MultiLineString`    | spatial          |      | `MULTI_LINE_STRING` | `MultiLineString`    | `org.locationtech.jts.geom.MultiLineString`                                                                    |
| `Polygon`            | spatial          |      | `POLYGON`           | `Polygon`            | `org.locationtech.jts.geom.Polygon`                                                                            |
| `MultiPolygon`       | spatial          |      | `MULTI_POLYGON`     | `MultiPolygon`       | `org.locationtech.jts.geom.MultiPolygon`                                                                       |
|                      |                  |      |                     |                      |                                                                                                                |
|                      |                  |      |                     |                      | **INTERFACES**                                                                                                 |
|                      |                  |      |                     |                      |                                                                                                                |
| `Proxyable`          |                  |      |                     |                      | An interface that is implemented by all structures that support proxies.                                       |
| `IStruct`            |                  |      |                     |                      | An interface to access general JSON like object that supports proxies.                                         |
| `IArray`             |                  |      |                     |                      | An interface to access general JSON like arrays.                                                               |
| `ISet`               |                  |      |                     |                      | An interface to access general JSON like arrays that contain unique values.                                    |
| `IMap`               |                  |      |                     |                      | An interface to access general JSON like maps.                                                                 |
| `IObject`            |                  |      |                     |                      | An interface to access general JSON like objects _(with keys limited to be strings)_.                          |
| `ITupleNumber`       |                  |      |                     |                      | An interface to access a tuple-number.                                                                         |
|                      |                  |      |                     |                      |                                                                                                                |
|                      |                  |      |                     |                      | **JSON**                                                                                                       |
|                      |                  |      |                     |                      |                                                                                                                |
| `JsonStruct`         |                  |      |                     |                      | The base class for all [JSON] data types that allow proxy linking, implements `IStruct`.                       |
| `JsonArray`          | array/map/object |      | `ARRAY`             |                      | A list of values, extends [JsonStruct], implements mutable `IArray`.                                           |
| `JsonSet`            | array/map/object |      | `SET`               |                      | A list of unique values, not being `null`, extends [JsonStruct], implements mutable `ISet`.                    |
| `JsonMap`            | array/map/object |      | `MAP`               |                      | A set of key-value pairs in insertion order, extends [JsonStruct], implements mutable `IMap`.                  |
| `JsonObject`         | array/map/object |      | `OBJECT`            |                      | A set of key-value pairs with all keys being [strings], extends [JsonStruct], implements mutable `IObject`.    |
|                      |                  |      |                     |                      |                                                                                                                |
|                      |                  |      |                     |                      | **PROXIES**                                                                                                    |
|                      |                  |      |                     |                      |                                                                                                                |
| `Proxy`              |                  |      |                     |                      | Abstract base class for all proxies that can be linked to a [JsonStruct] or [JbonObject].                      |
| `StructProxy<P, O>`  |                  |      |                     |                      | Abstract base class extending [Proxy] with shared methods for extending proxies.                               |
| `ArrayProxy`         |                  |      |                     |                      | A [Proxy] that can be linked to any `IArray` to extend the array with custom functions.                        |
| `TypedArrayProxy<E>` |                  |      |                     |                      | A [Proxy] that can be linked to any `IArray` to view it as a typed-array.                                      |
| `SetProxy`           |                  |      |                     |                      | A [Proxy] that can be linked to any `ISet` to extend the set with custom functions.                            |
| `TypedSetProxy<E>`   |                  |      |                     |                      | A [Proxy] that can be linked to any `ISet` to view it as a typed-set.                                          |
| `PTypedMap`           |                  |      |                     |                      | A [Proxy] that can be linked to any `IMap` to extend the map with custom functions.                            |
| `TypedMapProxy<K,V>` |                  |      |                     |                      | A [Proxy] that can be linked to any `IMap` to view it as a typed-map.                                          |
|                      |                  |      |                     |                      |                                                                                                                |
| `TagsProxy`          |                  |      |                     |                      | A [Proxy] for an `IArray` to be treated as a list of tags, split and made available as a map.                  |
| `VersionProxy`       |                  |      |                     |                      | A [Proxy] for an `IMap` representing a [version] _feature.                                                     |
| `FeatureProxy`       |                  |      |                     |                      | A [Proxy] for an `IMap` representing a [GeoJson] feature.                                                      |
| `PropertiesProxy`    |                  |      |                     |                      | A [Proxy] for an `IMap` representing the properties of a [GeoJson] feature.                                    |
| `DatabaseProxy`      |                  |      |                     |                      | A [Proxy] for an `IMap` representing a [database] _feature_.                                                   |
| `CatalogProxy`       |                  |      |                     |                      | A [Proxy] for an `IMap` representing a [catalog] _feature_.                                                    |
| `CollectionProxy`    |                  |      |                     |                      | A [Proxy] for an `IMap` representing a [collection] _feature_.                                                 |
| `MemberProxy`        |                  |      |                     |                      | A [Proxy] for an `IMap` representing a [member].                                                               |
| `IndexProxy`         |                  |      |                     |                      | A [Proxy] for an `IMap` representing an index above a [member].                                                |
|                      |                  |      |                     |                      |                                                                                                                |
|                      |                  |      |                     |                      | **DATA**                                                                                                       |
|                      |                  |      |                     |                      |                                                                                                                |
| `Option`             |                  |      |                     |                      | A special enumeration implementation that essentially is always encoded as string or long.                     |
| `IndexOption`        |                  |      |                     |                      | An enumeration of all supported indices above [members].                                                       |
| `Bytes`              |                  |      |                     |                      | A static singleton for low-level access to primitive arrays _(`byte[]`, `short[]`, ...)_.                      |
| `Binary`             |                  |      |                     |                      | A helper class for binaries, supports MIME types, parameters, and compression.                                 |
| `TupleId`            |                  |      |                     |                      | The immutable im-memory representation of a unique identifier.                                                 |
| `TupleNumber`        |                  |      |                     |                      | The immutable im-memory representation of a unique identifier.                                                 |
| `Version`            |                  |      |                     |                      | The immutable im-memory representation of a [version].                                                         |
| `Database`           |                  |      |                     |                      | The immutable im-memory representation of a [database].                                                        |
| `Catalog`            |                  |      |                     |                      | The immutable im-memory representation of a [catalog] within a [database].                                     |
| `Collection`         |                  |      |                     |                      | The immutable im-memory representation of a [collection] within a [catalog].                                   |
| `Feature`            |                  |      |                     |                      | The immutable im-memory representation of a [feature] within a [collection].                                   |
| `Tuple`              |                  |      |                     |                      | A wraper around a `JbonTuple` that encodes an [feature].                                                       |
|                      |                  |      |                     |                      |                                                                                                                |
|                      |                  |      |                     |                      | **JBON**                                                                                                       |
|                      |                  |      |                     |                      |                                                                                                                |
| `Jbon`               |                  |      |                     |                      | A wrapper above a bunch of bytes that encode a [JBON].                                                         |
| `JbonEncoder`        |                  |      |                     |                      | A tool to build a [JBON].                                                                                      |
| `JbonBinary`         |                  |      |                     |                      | A [JBON] encoded binary.                                                                                       |
| `JbonArray`          |                  |      |                     |                      | A [JBON] encoded array, implementing read-only `IArray`.                                                       |
| `JbonSet`            |                  |      |                     |                      | A [JBON] encoded set, implementing read-only `ISet`.                                                           |
| `JbonMap`            |                  |      |                     |                      | A [JBON] encoded map, implementing read-only `IMap`.                                                           |
| `JbonTupleNumber`    |                  |      |                     |                      | A [JBON] encoded tuple-number, implementing `ITuple`.                                                          |
| `JbonTuple`          |                  |      |                     |                      | A [JBON] encoded tuple, implementing `ITuple` with root being a `JbonMap`, representing a [GeoJSON] _feature_. |
| `JbonBook`           |                  |      |                     |                      | A [JBON] encoded book.                                                                                         |
|                      |                  |      |                     |                      |                                                                                                                |
|                      |                  |      |                     |                      | **STORAGE**                                                                                                    |
|                      |                  |      |                     |                      |                                                                                                                |
| `TupleStorage`       |                  |      |                     |                      | A stateless storage in which [tuple] can be stored, and from which they can be read.                           |
| `Storage`            |                  |      |                     |                      | Extends `TupleStorage`, an extended storage that manages [database], [catalog], [collection], and [feature].   |
| `ReadSession`        |                  |      |                     |                      | A session into a storage to execute complex read queries.                                                      |
| `FullSession`        |                  |      |                     |                      |                                                                                                                |

All data must be represented using these data types to ensure interoperability between different components, storages, and services.

This design is important for many of the features offered by the data model, for example to be able to effectively calculate the _logical bytes_ of any _**unit**_, so data can be compared. Calculating differences, patches, and applying the patches, and/or merging of arbitrary data requires this design. The data model supports all rudimentary components necessary to build more complex structures:

- Primitives _(see column `Prim`)_
- Arrays
- Sets
- Maps
- Objects
- Geometries

## Indices
When the following sections refer to indexable types, this refers to the data types that have an index-type in the `Idx` column.

### Btree
All primitives and byte-arrays can be indexed using `btree` index.

When a byte-array is compared, then the compare **must** be done byte-by-byte. The smaller byte decides which byte-array is smaller. When the end of a byte-array is reached, and so far all bytes are equal, but one array does have more bytes, then the shorter array is _(by definition)_ less than the longer array. Otherwise, if both arrays are of same length, and all bytes equal, they are equal.

All strings must be interned and encoded in [NFC] form. In memory strings are kept in UTF-16 encoding, in the database they are stored in UFF-8 encoding, and in [JBON] a special encoding is used. Within the database, strings are treated like `C` strings, and are therefore sorted the same way. This can lead to unexpected sorting results.

The data model differentiates mainly between shared immutable binary data, encoded in [JBON], and mutable thread-local data that is represented as [JSON] heap objects. The immutable binary data is used for caching, cross component access, or fast transportation between services, and for very fast lookups _(without the need to decode the [JBON] into [JSON] heap objects)_.

### Spatial
All geometries can be indexed using `spatial` index. The spatial index only operates on 2D _(so longitude and latitude)_.

### Array
The `Map`, `Set`, and `Array` data types can all be indexed as `array`. This basically means to convert the data into a list of primitives, and then to index the values, allowing to search for values or values at position.

- For the `Array`, all elements are converted into an array and indexed.
- For the `Set`, all elements are converted into an array and indexed.
- For the `Map`, the values are converted into an array and indexed.
- For the `Object`, the values are converted into an array and indexed.

**Note**: Entries where the value is no primitive, have undefined behavior.

### Map
The `Map`, `Set`, and `Array` data types can all be indexed as `map`. This basically means to convert the data into a list of entries, each having a key and a value being a primitive. Then indexing the key-value pair, allow to search for key, value, or a combination.

- For the `Array`, all elements are converted into keys with value being the boolean `true`, then indexed.
- For the `Set`, all elements are converted into keys with value being the boolean `true`, then indexed.
- For the `Map`, simply all entries are indexed.
- For the `Object`, simply all entries are indexed.

**Note**: Entries where the key is no string or the value is no primitive, have undefined behavior.

### Object
The `Map`, `Set`, and `Array` data types can all be indexed as `object`. This basically means to convert the data into a list of entries, each having a key being a string and value being a primitive. Then indexing the key-value pair, allow to search for key, value, or a combination.

- For the `Array`, all elements are stringified, then split using [tag split] algorithm, eventually the resulting key-value pairs are indexed.
- For the `Set`, all elements are stringified, then split using [tag split] algorithm, eventually the resulting key-value pairs are indexed.
- For the `Map` all keys are stringified, values are used as is, finally the key-value pairs are indexed.
- For the `Object` all keys are already strings, therefore simply all entries are indexed.

**Note**: Entries where the key is no string or the value is no primitive, have undefined behavior.

### Tag Split
When an `object` index is selected, then arrays and sets are converted into objects using the _tag-split-algorithm_.

This algorithm comes from the past, where a service called _Data-Hub_ supported tags. At that time, `tags` were just an array of strings, with limited allowed characters, and some special rules about normalization.

With `lib-data` we wanted to be downward compatible, while removing support for the proprietary `tags`, replacing it with something more common, that can be supported clearer. Therefore, we have specified the algorithm how a list of strings _(comming either from [array] or [set])_ can be converted into a `Map<String, Primitive>`. Turning an array into a list of strings is strait forward.

Therefore, the _tag-split_ is done like following:

1. Optionally, normalize the string.
2. Optionally, lowercase the string.
3. Optionally, remove all non-ASCII characters.
4. Optionally, split the string into key and value.

The first characters of every string decide about which steps of the above are to be executed:

| prefix     | norm. form | lowercase | remove non ASCII | split |
|------------|------------|-----------|------------------|-------|
| `@`        | [NFKC]     | false     | false            | true  |
| `ref_`     | [NFKC]     | false     | false            | false |
| `~`        | [NFD]      | false     | true             | true  |
| `#`        | [NFD]      | false     | true             | true  |
| `sourceID` | [NFKC]     | false     | false            | false |
| _else_     | [NFD]      | true      | true             | true  |

If not split, the value will be `null`. If split happens, then at the first equal _(`=`)_ sign. If, after the split, the key ends with a colon _(`:`)_, then the value is parsed. Supported types are only `boolean` and `double`. If the value behind an `:=` is no valid _boolean_ or _double_, it is used as string _(fallback)_.

This means for example that `foo:=true` is split into the key `foo` and the _boolean_ `true`, `foo:=1` is split into key `foo` and the double `1.0`, while `foo=1` is split into the key `foo` and the string `1`.

## Interfaces
There are two ways to encode data, as mutable _HEAP_ objects or as immutable binaries in [JBON]. Both should be transparent, when just reading and processing data. Therefore, both support some basic interfaces.

### IProxyable
All objects that support proxies must implement this interface. This interface is implemented by [Proxy], which redirects to the underlying `IStruct`, which is either [JsonStruct] or [JbonObject], both as well implementing [IProxyable].

```java
public interface IProxyable {
  @NotNull <P extends Proxy> proxy(final @NotNull Class<P> proxyClass);
}
```

### IStruct
All object will implement this interface. Within the [JBON] specification these units are _structures_, they only persist out of array, set, map, and object.

```java
public interface IStruct extends Proxyable {
  boolean isArray();
  boolean isSet();
  boolean isMap();
  boolean isObject();
  /** Tests if an invocation of `mutable(true)` will return this (true) or create a new instance (false). */
  boolean isMutable();
  /** Create a mutable clone of this object. If this is immutable, the copy will always be recursive; otherwise, the copy is recursive or not, dependent on the {@code recursive} argument. */
  @NotNull JsonStruct copy(boolean recursive);
  /**
   * Returns this object as mutable instance.
   * <ul>
   * <li>If this structure is mutable, returns this.
   * <li>If this structure is immutable (JBON) and {@code copy} argument is {@code false}, throws an DataError.
   * <li>If this structure is immutable (JBON) and {@code copy} argument is {@code true}, recursively copy this object to <i>HEAP</i> and returns the copy.
   * </ul>
   **/
  @NotNull JsonStruct mut(boolean copy);
}
```

### IArray
An array is a list of child-units.

```java
public interface IArray extends IStruct {
  @Override @NotNull JsonArray mut(boolean copy);
  // TODO: Add 'array' methods.
}
```

### ISet
A set is a sorted list of unique child-units.

```java
public interface ISet extends IStruct {
  @Override @NotNull JsonSet mut(boolean copy);
  // TODO: Add 'set' methods.
}
```

### IMap
A map is an unordered list of entries, each persisting out of a key and a value. Internally, keys being strings are stored as `Literal`. Only [primitives] are allows as keys.

```java
public interface IMap extends IStruct {
  @Override @NotNull JsonMap mut(boolean copy);
  // TODO: Add 'map' methods.
}
```

### IObject
An object is an unordered list of entries, each persisting out of a key and a value. The difference to the `IMap` is that the `IObject` only allows [string] keys. Internally all strings used as keys are stored as `Literal`.

```java
public interface IObject extends IStruct {
  @Override @NotNull JsonObject mut(boolean copy);
  // TODO: Add 'object' methods, basically a map where the key is always a string.
}
```

## Proxyable
The proxyable object is the default implementation of the [IProxyable] interface. This is the abstract base class for [JsonStruct] and [JbonObject].

```java
package naksha.data;

// Base class for JsonStruct and JbonObject. 
public abstract class Proxyable implements IProxyable {
  // We only allow JsonStruct and JbonObject to extend the Proxyable. 
  Proxyable() {}
  
  // Allows applications to define which proxy implementation to use for certain interfaces or abstract classes.
  @SuppressWarnings("rawtypes")
  private static final ConcurrentHashMap<Class, Class> defaultImplementation = new ConcurrentHashMap<>();
  // TODO: Add methods to register interface/abstract class mapping to concrete instances.
  //       We want to implement checks that ensure that everything is compatible, and `defaultImplementation` only contains valid entries.

  // Needed to add proxies to an object.
  private @Nullable WeakReference<Proxy> firstProxy;
  
  @Override public @NotNull <P extends Proxy> proxy(@NotNull Class<P> proxyClass) {
    Proxy proxy = firstProxy != null ? firstProxy.get() : null;
    Proxy lastProxy = proxy;
    // Iterate proxy list, find the requested proxy or remember the last proxy.
    while (proxy != null) {
      Class<?> proxy_class = proxy.getClass();
      if (proxyClass == proxy_class) return proxyClass.cast(proxy);
      if (proxyClass.isInterface() && proxyClass.isAssignableFrom(proxy_class)) return proxyClass.cast(proxy);
      lastProxy = proxy;
      proxy = proxy.nextProxy;
    }
    // No existing proxy found, we need to create a new proxy.
    
    // If the requested proxy is an interface or abstract class, we need a default implementation.
    if (proxyClass.isInterface() || Modifier.isAbstract(proxyClass.getModifiers())) {
      final Class<P> implClass = (Class<P>) defaultImplementation.get(proxyClass);
      if (implClass == null) throw new DataError("Interface or abstract class requested as proxy, but default implementation unknown");
      proxyClass = implClass;
    }
    
    // At this point we know that the proxyClass is instantiable, generally.
    if (this instanceof IArray) {
      if (!ArrayProxy.class.isAssignableFrom(proxyClass)) throw new DataError("The given proxy is no ArrayProxy, but this is an IArray");
      final Constructor<P> proxyConstructor;
      try {
        proxyConstructor = proxyClass.getDeclaredConstructor(IArray.class);
        proxyConstructor.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new DataError("Failed to create proxy, missing constructor: new(IArray)", e);
      }
      proxy = proxyConstructor.newInstance(this);
    } else if (ISet.class.isAssignableFrom(proxyClass)) {
      if (!SetProxy.class.isAssignableFrom(proxyClass)) throw new DataError("The given proxy is no SetProxy, but this is an ISet");
      final Constructor<P> proxyConstructor;
      try {
        proxyConstructor = proxyClass.getDeclaredConstructor(ISet.class);
        proxyConstructor.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new DataError("Failed to create proxy, missing constructor: new(ISet)", e);
      }
      proxy = proxyConstructor.newInstance(this);
    } else if (IMap.class.isAssignableFrom(proxyClass)) {
      if (!MapProxy.class.isAssignableFrom(proxyClass)) throw new DataError("The given proxy is no MapProxy, but this is an IMap");
      final Constructor<P> proxyConstructor;
      try {
        proxyConstructor = proxyClass.getDeclaredConstructor(IMap.class);
        proxyConstructor.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new DataError("Failed to create proxy, missing constructor: new(IArray)", e);
      }
      proxy = proxyConstructor.newInstance(this);
    } else {
      throw new DataError("Invalid proxy implementation, must extend ArrayProxy, SetProxy, or MapProxy, but does not!");
    }
    if (lastProxy == null) {
      // New first proxy.
      firstProxy = new WeakReference<>(proxy);
    } else {
      // Append to proxy list.
      proxy.prevProxy = lastProxy;
      lastProxy.nextProxy = proxy;
    }
    return proxyClass.cast(proxy);
  }
}
```

## Proxies
Having to work with untyped data is extremely error-prone, even while the most flexible thing possible. So close the gap, `lib-data` supports proxies. A proxy is a data-model that can be attached to arbitrary data at runtime _(this allows runtime schema detection)_. All proxies must extend the [Proxyable] base class.

## Json
The standard [JSON] specification does not support sets, primitive-arrays, or maps. Many implementations even do not support 64-bit integers, even while the original specification didn't state that here is a limit, it is more an issue that results from the history of _JavaScript_, and the way it originally was implemented.

We need a solution to be compatible with [JSON] parses that support 64-bit integers, and with _JavaScript_ [JSON] parsers, that do not support this out of the box. Actually, there is no official support in [JSON] for a set, primitive-arrays and maps. Therefore, we first define extended [JSON], which will only be supported by our own parser. Therefore, clients can add into HTTP-header which [JSON] they support, and the corresponding format will be used.

```java
package naksha.data;
import static naksha.data.Data.literal;
import static naksha.data.Const.*;

public class JsonFormat extends Option {
  public JsonFormat(@NotNull Literal value) { super(value); }
  /** Standard JSON without comments, not supporting 64-bit integer */
  public static final JsonFormat JAVASCRIPT_JSON = new JsonFormat(Const.APPLICATION_JSON);
  /** Standard JSON without comments, but with support for 64-bit integer */
  public static final JsonFormat JAVA_JSON = new JsonFormat(Const.APPLICATION_JSON64);
  /** Standard JSON with comments, but without support for 64-bit integer */
  public static final JsonFormat JSONC = new JsonFormat(Const.APPLICATION_JSONC);
  /** Standard JSON with comments, and with support for 64-bit integer */
  public static final JsonFormat JSONC64 = new JsonFormat(Const.APPLICATION_JSONC64);
  /** Standard JSON with comments, 64-bit integer, sets, maps, and primitive-arrays */
  public static final JsonFormat JSONX = new JsonFormat(Const.APPLICATION_JSONX);
}
```

As the parser that comes with `lib-data` supports `JSONX`, which supports all features, it always as well support all lower tier [JSON] formats. So while parsing, there is no need to specify the format. However, when serializing, the target is required to generate the correct format. The default target is `JAVASCRIPT_JSON`, which is most restrictive.

When 64-bit integers are not supported, then they will be encoded into a [data URL] in the format `data:application/long,{decimal}`. This requires some post-processing of the parsed JSON or a special JSON handler while serializing, at least when being in _JavaScript_, so that the parsed [JSON] can be converted into the correct objects. In standard `JSON` sets are encoded as `{"@type": "naksha:Set", elements: [VALUE, ...]}`. Maps are encoded as `{"@type": "naksha.Map", entries:[KEY, VALUE, ...]}`. Primitive arrays are encoded as [data URL], like `data:application/bytea;base64,...`, with `...` being the [base64] encoded binary representation.

The `JSONX` format supports the following extensions:

- C-style comments
  - Line comments: `// line comment`
  - Block level comments: `/* comment */`
  - Bash comments: `# comment line`
- Supports 64-bit integers, like `9223372036854775807`
- Strings can be quoted either with double quotes _(`"`)_ or single quotes _(`"`)_
- C-Escaping can be used everywhere, so a backslash in front of any character not being `0-9a-zA-Z` escapes the character.
  - `\n` is line feed
  - `\r` is carriage return
  - `\b` is bell
  - `\0` is ASCII-0
  - ...
- Empty or duplicate commas in maps and objects are ignored, for example `{a:1,b:2,}` is used as `{a:1,b:2}`
  - In arrays commas are significant, e.g. `[1,2,]` means `[1,2,null]` not `[1,2]`.
- Arrays can optionally be encoded as `@array[VALUE, ...]`
- Sets are encoded as `@set[VALUE, ...]`
- Maps are encoded as `@map[KEY: VALUE, ...]`
  - Beware that the keys must be boolean, double, long, or string.
  - Strings must be quoted, when they conflict with numbers or boolean.
- Objects can optionally be encoded as `@object[KEY: VALUE, ...]`
- Primitive-Arrays are encoded as `@TYPE[VALUE, ...]`.
  - With `TYPE` being `i8`, `i16`, `i32`, `i64`, `f32`, or `f64`
  - Example: `@f64[1, 2, 3]` becomes in _Java_ `double[]{1.0, 2.0, 3.0}`.
  - For byte-arrays a special syntax is supported: `@hex[BYTES]`. The `BYTES` being the hex-encoded bytes, like `0e100047`.

Example JSON:

```
{
  type: Feature,
  properties: {
    name: "Jim",
    tags: @map[
      foo: 5,
      bar: 'Hello World'
      15: no
    ],
    next_version: 9223372036854775807,
  },
}
```

This is a valid extended [JSON], equivalent to the following [JSON] compatible encoding:

```json
{
  "type": "Feature",
  "properties": {
    "name": "Jim",
    "tags": {
      "@type": "naksha:Map",
      "entries": [
        "foo", 5,
        "bar", "Hello World",
        15, "no"
      ]
    },
    "next_version": "data:application/long,9223372036854775807"
  }
}
```

Apart from these hacks, we need more hacks for maps that hold keys not being strings. So there are more [data URL] encodings for `application/boolean`, `application/int`, and `application/double`. We only support primitives as keys, therefore no other hacks are needed.

### Java
The standard [JSON] parse will support _transformation` to convert structures into [POJOs] and vice versa, [POJOs] into structures. It supports Jackson annotation via reflection _(so we do not depend on the annotation, but support them)_. Example:

```java
@JsonTypeName("MyClass") // To be encoded as "@type"
public class MyClass implements MyInterface { }

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = MyClass.class, name = "MyClass"),
    @JsonSubTypes.Type(value = OtherClass.class, name = "OtherClass")
})
public interface MyInterface { }
```

### JsonStruct
The abstract base class of all [JSON] structures.

```java
package naksha.data;
import static naksha.data.Data.literal;

public abstract class JsonStruct extends Proxiable implements IStruct, IProxyable {
  // We only allow JsonArray, JsonSet, and JsonMap to extend this.
  JsonStruct() {}

  private static final long OBJECT = 0; // 00b
  private static final long MAP = 1; // 01b
  private static final long ARRAY = 2; // 10b
  private static final long SET = 3; // 11b
  // encodes type:2, start:30, end:30 _(negative for array, positive for map)_
  private long typeStartAndEnd = 0L;

  // The elements of the array, valid values are located from start to end.
  // For Map: The elements are keys at even positions, and values at odd positions.
  @NotNull Object @NotNull [] data = EMPTY_OBJECT;
  // TODO: We have an implementation, we need to copy code here.
  // TODO: Add implementation of `copy()` and `mut()`, with `mut()` always returning this.
}
```

### JsonArray

```json
[]
```

### JsonSet
To indicate that an array is a set, it wrapped into an object:

```json
{"@type": "naksha:Set", "elements": []}
```

The JSON parser of `lib-data` will, when it encounters such an object, convert it into a `JsonSet`.

**Beware that the wrapper must only have exactly two properties being `type` and `elements` with `elements` being an array and `type` being the string `"Set"`. Only when this is exactly the case, the JSON parser will convert it into a `JsonSet`.

### JsonMap

```json
{"@type": "naksha:Map", "entries": []}
```

### JsonObject

```json
{}
```

## Jbon
Details about [JBON] objects, so `Jbon`, `JbonObject`, `JbonArray`, `JbonSet`, and `JbonMap`, can be found in the [JBON2.md](./JBON2.md), specifically in the [JBON Java Section](./JBON2.md#java).

## Proxy
A base class implementing the [Proxyable] interface, providing a standard implementation of the `proxy()` method. This class is the base for [JsonStruct] and [JbonObject]:

```java
package naksha.data;

public abstract class Proxy implements IProxyable {
  // We only allow StructProxy to extend the Proxy.
  Proxy() {}
  // Needed chain multiple proxies.
  Proxy nextProxy;
  // Needed to prevent that the GC collects proxies while the user holds a reference to one of them.
  Proxy prevProxy;
}
```

### StructProxy
A base class that all proxies must extend. It extends the raw [Proxy] and adds support for object binding, so that either [JsonStruct] or [JbonObject] can be linked to the proxy.

```java
package naksha.data;

public abstract class StructProxy<I extends IStruct, O extends JsonStruct> extends Proxy implements IStruct {
  // We only allow ArrayProxy, SetProxy, and MapProxy to extend this class.
  StructProxy(@NotNull I struct) { this.struct = struct; }
  private final @NotNull I struct;
  protected @NotNull I object() { return object;}
  protected abstract @NotNull O mutable();

  // Proxies redirect proxy requests to the underlying.
  public @NotNull <P extends Proxy> proxy(final @NotNull Class<P> proxyClass) {
    return object.proxy(proxyClass);
  }
}
```

### ArrayProxy
```java
package naksha.data;

public class ArrayProxy extends StructProxy<IArray, JsonArray> {
  public ArrayProxy() { super(new JsonArray()); }
  public ArrayProxy(@NotNull IArray array) { super(array); }
  @Override protected @NotNull JsonArray mutable() {
    if (object() instanceof JsonArray array) return array;
    throw new DataError("The array is immutable");
  }
  // TODO: Add protected methods to read and write the array.
  //       If the object is not mutable, modification will throw an DataError.
}
```

### SetProxy
```java
package naksha.data;

public class SetProxy extends StructProxy<ISet, JsonSet> {
  public SetProxy() { super(new JsonSet()); }
  public SetProxy(@NotNull ISet set) { super(set); }
  @Override protected @NotNull JsonSet mutable() {
    if (object() instanceof JsonSet set) return set;
    throw new DataError("The set is immutable");
  }
  // TODO: Add protected methods to read and write the set.
  //       If the object is not mutable, modification will throw an DataError.
}
```

### MapProxy
```java
package naksha.model;

public class MapProxy extends StructProxy<IMap, JsonMap> {
  public MapProxy() { super(new JsonMap()); }
  public MapProxy(@NotNull IMap map) { super(map); }
  @Override protected @NotNull JsonMap mutable() {
    if (object() instanceof JsonMap map) return map;
    throw new DataError("The map is immutable");
  }
  // TODO: Add protected methods to read and write the map.
  //       If the object is not mutable, modification will throw an DataError.
}
```

### ObjectProxy
Objects are maps with string keys. There is no typed variant, because this is not what objects are used for.

```java
package naksha.model;

public class ObjectProxy extends StructProxy<IObject, JsonObject> {
  public ObjectProxy() { super(new JsonObject()); }
  public ObjectProxy(@NotNull IObject object) { super(object); }
  @Override protected @NotNull JsonObject mutable() {
    if (object() instanceof JsonObject object) return object;
    throw new DataError("The object is immutable");
  }
  // TODO: Add protected methods to read and write the map.
  //       If the object is not mutable, modification will throw an DataError.
}
```

### TypedArrayProxy
Basically the same as an `ArrayProxy`, but implementing the `List` interface, with added type safety for all elements. Used to implement some standard collections via proxies like `StringArray`.

```java
package naksha.data;

public abstract class TypedArrayProxy<E> extends ArrayProxy implements List<E> {
  public TypedArrayProxy() { super(); }
  public TypedArrayProxy(@NotNull IArray array) { super(array); }
  public abstract Class<E> elementClass();
  // TODO: Implement the List interface.
}
```

### TypedSetProxy
Basically the same as an `SetProxy`, but implementing the `Set` interface, with added type safety for all elements. Used to implement some standard sets via proxies like `StringSet`.

```java
package naksha.data;

public abstract class TypedSetProxy<E> extends SetProxy implements Set<E> {
  public TypedSetProxy() { super(); }
  public TypedSetProxy(@NotNull ISet set) { super(set); }
  public abstract Class<E> elementClass();
  // TODO: Implement the Set interface.
}
```

### TypedMapProxy
Basically the same as an `PTypedMap`, but implementing the `Map` interface, with added type safety for all elements. Used to implement some standard sets via proxies like `StringObjectMap` or `StringStringMap`.

```java
package naksha.data;

public abstract class TypedMapProxy<K, V> extends MapProxy implements Map<K, V> {
  public TypedMapProxy() { super(); }
  public TypedMapProxy(@NotNull IMap map) { super(map); }
  public abstract Class<K> keyClass();
  public abstract Class<V> valueClass();
  // TODO: Implement the Map interface.
}
```

### FeatureProxy
The feature proxy provides typing for [GeoJSON] features. 

```java
package naksha.data;

public class FeatureProxy extends MapProxy {
  // TODO: Add setter and getter for:
  //       TupleNumber tupleNumber
  //       String id
  //       Geometry geometry
  //       Point referencePoint
  //       PropertiesProxy properties
}
public class PropertiesProxy extends MapProxy {}
```

### CollectionProxy
### CatalogProxy
### DatabaseProxy
### VersionProxy
### XyzProxy

### Custom Proxies
The following example shows a custom proxy for a simple data model, where a [GeoJSON] feature has a `name` and `age` in the `properties`:

```java
public class ExampleFeature extends FeatureProxy {
  public ExampleFeature() { super(); }
  public ExampleFeature(@NotNull IMap map) { super(map); }
  // TODO: Override setter and getter for properties to return ExampleProperties.
}

public class ExampleProperties extends PropertiesProxy {
  // This constructor is used to create a new Example instance.
  public ExampleProperties() {
    super();
    // We can do normal initialization here, for example setting default values.
    setName("Hello World");
    setAge(18);
  }
  // This constructor is called by the "proxy" method to link a proxy to an existing JsonMap or JbonMap.
  public ExampleProperties(@NotNull IMap map) {
    super(map);
    // We can update internal caches and more, when this happens.
    // It is guaranteed to happen only ones in the lifetime of every object, proxies are never unlinked or relinked!
  }
  
  // The property methods for name.
  public static final String NAME_KEY = Data.intern("name");
  public boolean hasName() { return containsKey(NAME_KEY); }
  public @Nullable String getName() { return getString(NAME_KEY); }
  public @Nullable String setName(@Nullable String name) { return setString(NAME_KEY, name); }
  public @Nullable String removeName() { return removeString(NAME_KEY); }

  // The property methods for age.
  public static final String AGE_KEY = Data.intern("age");
  public boolean hasAge() { return containsKey(AGE_KEY); }
  public int getAge() { return asInt(getLong(AGE_KEY), 0L); }
  public int setAge(int age) { return asInt(setLong(AGE_KEY, age), 0L); }
  public int removeAge() { return asInt(removeLong(AGE_KEY), 0L); }
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

When a read-only [JBON] encoded tuple is backing a proxy, then only wrappers for the values actually being used are created. When no longer needed, the _GC_ will collect the unused wrappers.

## Identifiers
All identifiers of administration objects are restricted, like [database], [catalog], [collection], .... They must be non-empty strings, with a maximum length of 42 byte, matching the regular expression: `^[a-z]+[a-z0-9_:-]*$`. The dollar (`$`) and tilde (`~`) characters are reserved for internal usage. Generally, no uppercase letters are allowed in administration identifiers.

**Note**: The identifiers of features are no administration identifiers, therefore they can be any string.

## ITuple
This is a marker interface implemented by [tuple] and [tuple-number]. It is used to indicate a [tuple] or a reference to a [tuple].

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

## TupleId to TupleNumber
By default, the storage will convert a tuple-id into a tuple-number by looking at the given `id`. If the `id` is a valid unsigned 62-bit integer in decimal notation, so a string between `0` and `4611686018427387903`, it will parse it into a number and use this number as `feature_number`.

If the identifier is not a valid 62-bit unsigned integer in decimal notation, the storage will derive the `feature_number` from the `id` via:

- Generate the [logical bytes] of the `id`, which is actually the UTF-16 code-units in big-endian byte-order.
- Hash the [logical bytes] using a 128-bit [MurMur3] hash, then get the truncated 64-bit value.
- If the identifier contains a dollar `$` or tilde `~` _(so it is an internal identifier)_:
  - Clear the top two bit: `feature_number &= 0x3FFF_FFFF_FFFF_FFFFL`.
  - Set bit #62: `feature_number = hash | 0x4000_0000_0000_0000L`.
  - This results in a `feature_number` between `4,611,686,018,427,387,904` and `9,223,372,036,854,775,807` _(both inclusive)_.
- Otherwise, for custom identifiers:
  - Set the sign bit: `feature_number = hash | 0x8000_0000_0000_0000L`.
- Use this negative number as `feature_number`.

Therefore, a translation between `id` and `feature_number` does not necessarily require any database connection or request, it can be done locally.

However, this means as well that there is a possibility of hash-collisions, even while being very low. To solve this, a conflict resolution need to be implemented.

### Hash collision handling
For internal database objects, like [database], [catalog], and [collection] no collision is allowed. Therefore, two [catalogs] with different identifiers, hashing to the same `feature_number`, can't be created. Beware that the possiblity of this happening is very small. The reason we do not allow collisions handling for internal objects, is that we want to be sure that the `feature_number` can always be reliably derived. Due to the way how internal names are hashed, it is never possible that internal collections collide with custom collections. Actually, custom identifiers always hash to a negative `feature_number`, while internal names _(those including a tilde or dollar sign)_ hash to a positive `feature_number` outside of the allowed custom range.

For features the storage decides if collisions are allowed, or are forbidden. So, storages can rely upon the same behavior used for internal features.

However, if the storage does handle conflicts, there is a reference implementation that the storage must follow. When creating a new feature, the storage will check if there is already a feature with the same `feature_number`. If there is, it will check if the `id` of the existing feature matches the `id` of the new feature, in that case it is the same feature, so it is a normal concurrency conflict. If they do not match, this means we encountered a hash-collision. The storage now will do the following:

1. Clear the sign bit of the feature-number: `feature_number &= 0x7fff_ffff_ffff_ffffL`.
2. Keep a copy of the lowest 16-bit: `long partition = feature_number & 0xffffL`.
3. Add 65536 to the feature-number: `feature_number += 65536` _(storages can modify this part of the algorithm)_.
4. Clear the lowest 16-bit and the sign bit of the feature-number: `feature_number &= 0x7fff_ffff_ffff_0000`.
5. Finally, set the sign bit and add back the partition into lowest 16 bit: `feature_number = feature_number | 0x8000_0000_0000_0000L | partition`.

This results in a new `feature_number` that has the same lowest 16-bit as the originally hashed value. This guarantees that the feature is effectively kept in the same partition as it originally was in. This is important, because clients that do not know that there is a collision, will always look for the feature in this partition, so they will find both features in it. It is as well important to have unique indices in each individual partition, and to ensure that all identifiers stay in the partition of the original hashing.

**NOTE**

Storages may change the collision handling algorithm that turns an `id` into a `feature_number`. However, they **must not** change the lowest 16-bit of the feature-number. So, the only thing that **must** be guaranteed is that the same `id` is always stored in the same partition it would be in, when using [MurMur3] hash above the identifier. Therefore, the lower 16-bit of the feature-number must be the same as the ones generated by hashing the `id` using the truncated 64-bit [MurMur3] hash. This means, all storage implementation must use the reference implementation of [MurMur3] to calculate the hash above the identifier.

**In a nutshell**: The exact way that collisions are handled is storage dependent. The storage may modify step #3 to generate an alternative `feature_number`, but it must not modify the other steps!

## Database
The `Database` represents a unique database, that can be stored at different places. However, only one of the places should be the primary storage. Every storage should know if it is a replication or main storage for a database. Each database has one internal [catalog] named `naksha~admin`. This is a virtual [catalog] that is used to access the management data. This `naksha~admin` [catalog] contains by definition the following [collections]:

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
  Database(long number) { this.number = number; }
  /** The unique number of the database. */
  public final long number;
  /** The weak reference to this database. */
  public @NotNull WeakReference<Database> weakRef();
  /** The admin-catalog of the database. */
  public @NotNull AdminCatalog adminCatalog();
  /** The catalog with the given catalog-number. */
  public @NotNull Catalog catalog(int catalogNumber);
}
public class DatabaseTuple {
  DatabaseTuple(@NotNull JbonTuple tuple) { this.tuple = tuple; }
  /** The underlying JbonTuple of this database tuple. */
  public final @NotNull JbonTuple tuple;
  /** The database represented by this tuple. */
  public @NotNull Database database();
}
public class AdminCatalog extends Catalog {
  public @NotNull Collection meta();
  public @NotNull Collection catalogs();
  public @NotNull Collection versions();
  public @NotNull Collection books();
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
  public @NotNull Collection collections(); // "naksha~collections"
  /** The collection with the given collection-number. */
  public @NotNull Collection collection(int collectionNumber);
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
A feature represents a unique object with a unique `feature_number`, optionally with a unique identifier. It has a chain of mostly immutable states, called [tuple]. Each state is identified by its version, with links to the previous and next version.

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

## Tuple
A tuple is a mostly immutable state of a [feature] stored in a [collection]. It is mostly immutable, because the next version can be modified. A tuple has the following logical structure:

```java
public class Tuple implements ITuple {
  public Tuple(@NotNull Feature feature, byte @NotNull [] bytes, int offset) {
    this.feature = feature;
    this.bytes = bytes;
    this.offset = offset;
    jbonTuple(); // May throw DataError!
  } 
  public final @NotNull Feature feature;
  private final byte @NotNull [] bytes;
  private final int offset;
  private final ThreadLocal<WeakReference<JbonTuple>> localJbonRef;
  /** The JBON encodes feature. Can be queried to avoid conversion into a full JVM heap object, safes memory. */
  public @NotNull JbonTuple jbonTuple() {
    WeakReference<JbonTuple> tupleRef = localJbonRef.get();
    JbonTuple tuple = tupleRef != null ? tupleRef.get() : null;
    if (tuple != null) return tuple;
    final Jbon jbon = new Jbon(bytes, offset);
    final JbonUnit root = jbon.root();
    if (root.struct() instanceof JbonTuple jbonTuple) {
      localJbonRef.set(new WeakReference<>(jbonTuple));
      return jbonTuple;
    } else {
      throw new DataError("The given JBON does not encode a tuple");
    }
  }
  /** The weak reference to this tuple for caching. */
  public final @NotNull WeakReference<Tuple> weakRef = new WeakReference<>(this);
  /** The soft reference to this tuple for caching. */
  public final @NotNull SoftReference<Tuple> softRef = new SoftReference<>(this);
  /** Return the tuple as GeoJSON feature on the JVM heap. The method must not cache the object, every call should create a new feature instance. */
  public @NotNull JsonMap feature();
  /** Decodes the attachment. The method must not cache the attachment, every call should return a new copy. Read-only zero copy access is granted through the JBON tuple. **/
  public @Nullable Binary attachment();
  // TODO: Implement ITuple, just forward calls to jbonTuple().
  // TODO: Add more useful methods on demand.
}
```

Note that the `Tuple` just wraps the `JbonTuple` and links it to the administrative [feature], [collection], [catalog], and [database] objects. This allows in-memory caching and extending the class with specific access methods. It as well simplifies decoding into a [GeoJSON] _feature_.

## Versioning
A version is a 56-bit unsigned integer in the following formats:

```
                                                                   action
          {r}{                automatic version                       }{}
 00000000-000yyyyy-yyyyyyym-mmmddddd--ssssssss-ssssssss-ssssssss-ssssssaa

                                                                   action
          {    r     }{          manual version                       }{}
 00000000-00000000-000vvvvv-vvvvvvvv--vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvaa

          {r}{                       HEAD                               }
 00000000-00011111-11111111-11111111--11111111-11111111-11111111-11111111

          {r}{                       MAX                                }
 00000000-00011111-11111111-11111111--11111111-11111111-11111111-11111011

          {                          NULL                               }
 00000000-00000000-00000000-00000000--00000000-00000000-00000000-00000000
```

Therefore, the version has the following general parts:
- `r`: Reserved bits being fixed to `0`.
- `yyyy-yyyyyyyy`: The year of the version, encoded in 12 bit, a value between `16` and `4095` _(values below 16 collide with manual versions)_.
- `mmmm`: The month of the version, encoded in 4 bit, a value between `1` _(January)_ and `12` _(December)_.
- `ddddd`: The day of the version, encoded in 5 bit, a value between `1` and `31`.
- `ssssss-ssssssss-ssssssss-ssssssss`: The sequence of the version, encoded in 30 bit, so it can represent up to `1,073,741,824` versions per day, starting from `0`.
- `vvv-vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvvv-vvvvvvvv`: The manual version number, encoded in 43 bit, so it can represent up to `8,796,093,022,208` versions, starting from `1` _(`0` collides with NULL)_.
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
The version `9,007,199,254,740,987` _(2^53-5)_ represents the maximal valid version. In _JavaScript_ this matches `Number.MAX_SAFE_INTEGER - 4`.

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

| db-row | id    | version              | next_version | action         |
|--------|-------|----------------------|--------------|----------------|
| 2      | `foo` | 77 (`10011_01b`)     | 590          | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77           | UPDATE (`01b`) |
| 4      | `foo` | 13 (`11_01b`)        | 33           | UPDATE (`01b`) |
| 6      | `foo` | 4 (`10_00b`)         | 13           | CREATE (`00b`) |

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

| db-row | id    | version              | next_version   | action         |
|--------|-------|----------------------|----------------|----------------|
| 2      | `foo` | 77 (`10011_01b`)     | 590            | UPDATE (`01b`) |
| 3      | `foo` | 33 (`1000_01b`)      | 77             | UPDATE (`01b`) |

We additionally can filter on the `action`.

### Version Features
The version itself is as well a [feature] under [database] management. The `feature_number` of the version [feature] matches the `version` that it tracks. The `id` of the version [feature] is just the stringified representation of the version, so it is the decimal representation of the version number. The version can be modified by clients to store transactional information with it. For example, clients may add annotations into the version, for example comments provided by users. The version features can be queried to check which versions do exist in the storage.

Additionally, the version [features] can be used by clients to track the changes in the [database], as each version [feature] stores the [catalogs], [collections], and [features] that were modified. It is as well important for caches to understand which [catalogs], [collections], and [features] have changes since the last cache update. It allows caches and replicas to only download the changes they do not yet know about.

The version collection can be extended by custom fields, if necessary, the same way normal [collections] can be extended.

## Two-Phase-Queries
A major concept of the Naksha data model is that data reading is always split into two phases.

The first phase is to execute a query against the storage to find all [tuple] matching certain criteria. These queries can be done against _HEAD_ or a specific [version]. When the query returns it does not return the actual data, it only returns the [tuple-numbers] of the [tuple] being part of the result-set. This uses _(mostly)_ index-only scans, so normally every index contains the `fn` _(feature-number)_ and `version`, so they can be used as index-only scans.

The second phase is then loading the actual [tuple], because [tuples] are by definition mostly immutable, no [tuple] ever has to be loaded twice, as the [tuple] are cached on the JVM heap by the `lib-data`. This caching is done using [Soft-References], which allows to use all available and free heap for data caching. Additionally, the [tuple] can be cached in external services like [Redis] or simply at ephemeral SSDs _(we should use all resource we can get for cheap local caching)_.

Even while it theoretically would be possible to only load parts of a [tuple], this is not part of the model, because the [tuple] is _immutable_, and caching partial data is much more complicated and less efficient than caching full [tuple]. Therefore, it is generally more efficient to load the whole [tuple] at once, and to cache it intesively.

## References
The Naksha data mode defines two reference formats:

- The Tuple-Number _(`TN`)_ as string: `urn:naksha:tn:{storageNumber}:{catalogNumber}:{collectionNumber}:{recordNumber}[:{version}]`
- The Global Unique Identifier _(`GUID`)_ as string: `urn:naksha:guid:{storageId}:{catalogId}:{collectionId}:{featureId}[:{version}]`

For both variants the _HEAD_ state can be referred by simply omitting the `version`, setting it to `0` or to [HEAD] _(`9,007,199,254,740,991`)_. In the context of reference parsing the [NULL version] is interpreted as [HEAD version] reference.

The storage must internally only operate upon the `TN` variant. Clients are allowed to use the `GUID` variant, because they may create new [tuples] using identifiers only, not yet knowing the [Tuple-Number] or version, when creating the [features], and when adding references to these new [features]. So, the job of the storage is to convert all references given as `GUID` into correct full qualified `TN` variants. It therefore has to search the record and replace all `GUID` references with `TN` references.

In a nutshell, [tuple] read from a storage should never contain references in `GUID` format, they should always have them stored in `TN` format.

## Partitioning
To store big data efficiently, it needs to be partitioned. Within the Naksha data model there are two major logical sections defined: _HEAD_ and _HISTORY_. They store all the [tuple] of the [features]. They are logical concepts that each storage implementation can use to optimize data storage.

The _HISTORY_ section is partitioned logically first by `next_version`, to allow efficient dropping of historic data. We call this **historic partitioning**, and it is always applied if the _HISTORY_ is enabled for a [collection]. It only happens within the _HISTORY_ section. If _HISTORY_ is disabled for a collection, no **historic partitioning** is done, and `next_version` actually does not matter anymore.

Next to the **historic partitioning** there is a general **distribution partitioning**, which we will clarify first.

### Distribution partitioning
The _HEAD_ section and each historic partition are optionally distribution partitioned, if enabled. This is an optional feature that by default is disabled, but can be enabled to store a huge number of [features] and [tuple] in a [collection]. When enabled, we distribute [features] across distribution partitions. The number of distribution partitions can be configured when creating a collection, and defaults to `1` _(so no distribution partitioning)_. To assign [features], and all their [tuple], to the same distribution partition, the lower 16-bit of the record-number are used as **distribution key**. This means, all [tuple] of a [feature] are stored in the same distribution partition. So, when loading a [tuple] of a [feature] in a specific [version], only a single partition has to be accessed. When searching for data, all partitions can be queried in parallel, improving search performance. This layout therefore speeds up searching for data, while making access to known data faster. Loading the _HEAD_ state _([tuple])_ technically means to query a single partition and is therefore rather very fast. Loading multiple tuple can be done in parallel from all partitions they are in. The distribution is simply done by dividing the unsigned lower 16-bit value of the record-number by the amount of distribution partitions _(`n`)_, using the division rest as partition index. For example, assume the distribution key of a [feature] is `1234`, so the unsigned lower 16-bit of the record-number is decimal `1234`, and we have `8` distribution partitions configured in the [collection], then dividing `1234` by `8` gives us `154` with a rest of `2`. Therefore, the partition-number to search in is `2`. This guarantees that we always have a partition index between `0` and `n`-1.

### Historic partitioning
The **historic partitioning** is done by the `next_version` of each [tuple]. All [tuple] with `next_version` being `9,007,199,254,740,991`, are located in the _HEAD_ section, and are only distribution partitioned. When a new [tuple] of a [feature] is created, the current [tuple] in the _HEAD_ section becomes historic data. It now needs to be moved to history, and `next_version` must be set to the version of the new [tuple]. The [tuple] should be relocated into the _HISTORY_, which is where **historic partitioning** happens. It will stay in _HISTORY_ immutable until being purged. The purging is normally done by deletion of complete historic partitions, which is the reason for this design. Beware that formally the immutability of a [Tuple] slightly broken here, because the `next_version` is modified while moving the [Tuple] into history. However, this is the only exception, and a not significant one for the caches. Actually `next_version` is no reliable field, applications should ignore it. The value can be calculated using the back-references from `prev_version`, starting at _HEAD_.

Now, when deciding in which historic partition a [Tuple] should be located a **partition-key** is needed. To generate the **partition-key** the value from `next_version` is used. For this, the `next_version` is bitwise-ANDed with `0x001F_FFFF_FFFF_FFFF` _(effectively clearing the top 12-bit)_. Then value is shifted right by a configured `shift` amount. The `shift` is configured when creating a [collection] and must stay constant for the whole lifetime of a [collection]. The `shift` defaults to `41`, which means we store one historic partition per year. Reducing the `shift` to `37` would result in one historic partition per month, and reducing it to `32` would result in one historic partition per day.

**Note**: When manual versioning is used for a collection, the default `shift` value is reduced to `24` _(so ~16 million versions are stored per partition)_.

## TupleStorage
The abstract `TupleStorage` base class, extended by all [storages] and [caches], representing a sink for [tuples] aka _feature_ states. Only allows to read and store tuples using [tuple-numbers].

## TupleCache
The `TupleCacheManager` is a static in-memory cache for tuples. It holds a certain threshold of soft-references, plus as much as possible weak-references to tuple. The cache is used to speed up access to tuples, and to reduce memory consumption by allowing the garbage collector to reclaim memory when needed.

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
  /** Iterate all caches in order to replace all tuple in the given array with cached versions being available. Stops ones done, optimizes for latency; requests in parallel, when needed.
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

## Other
To manage data, it is split into members that together form the [GeoJSON] _feature_. This is a low level data definition, that is as well replicated in the [JBON] binary encoding.

### Option
Neither [JSON] nor [JBON] are enumeration aware, so there is no explicit enumeration type. The reason is that every enumeration value is actually exactly this: A value. Therefore, within `lib-data` enumeration values are always stored and encoded as values. To have constants while programming, the `Option` was introduced. An `Option` is a way to create uniquely instances for values. The assumption is that there are only a limited amount of possible instances.

Options are always based upon a string representation and a numeric representation, called `ordinal`. The `ordinal` is any arbitrary 32-bit integer, it can be a custom one, otherwise the _Java_ hash of the string is used.

```java
package naksha.data;
public class Option implements CharSequence {
  // TODO: Add code from existing JsEnum.
}
```

### Timestamp
A timestamp is the time as EPOCH in milliseconds. The value is a 48-bit unsigned integer. As [JSON] has no representation for timestamps, they are serialized either as normal double _(losing the type information, but more compatible for custom clients)_ or as [data url] with MIME-type being `application/epoch`, therefore as `data:application/epoch,12345678`.

### Geometry
The geometry uses JTS. In the binary representation it is encoded as [TWKB] with 7 decimal digits.

### IndexOption
An enumeration above all available indices above [members].

```java
package naksha.data;
import static naksha.data.Data.literal;

public class IndexOption extends Option {
  public IndexOption(@NotNull Literal value, @NotNull DataType ... types) { super(value); }
  
  // Index for certain data-types, should host a list of DataType being supported.
  public static final IndexOption BTREE = new IndexOption(literal("btree"));
  public static final IndexOption SPATIAL = new IndexOption(literal("spatial"));
  public static final IndexOption ARRAY = new IndexOption(literal("array"));
  public static final IndexOption MAP = new IndexOption(literal("map"));
  public static final IndexOption OBJECT = new IndexOption(literal("object"));
}
```

### MemberProxy
Members are defined in the [collection] _feature_, and they describe how storages have to split a [GeoJSON] _feature_ into dedicated parts to allow indexing and searching for the data.

Rarely any storage can read the binary encoded [tuple], some will even not store the data in [JBON] encoding. To ensure that data needed for indexing and searching is accessible to the storage, applications must define _**members**_. A _**member**_ is a property extracted from the [tuple]. Only _**member**_ can be searched for, and they are [indexable]. All storages need to return the data read as [tuple], so as [JBON] encoded binary; no matter in which format they actually really store the data. All storages must accept [JSON] features as input, and [tuple] _([JBON] encoded binaries)_  for replication.

The `MemberProxy` is a [proxy] for an `IMap`, so it can be used in the [CollectionProxy].

```java
package naksha.data;
import static naksha.data.Data.literal;

public class MemberProxy extends MapProxy {
  public static final Literal NAME = literal("name");
  // TODO: Add setter/getter for name as String
  public static final Literal TYPE = literal("type");
  // TODO: Add setter/getter for type as DataType
  public static final Literal PATH = literal("path");
  // TODO: Add setter/getter for path as StringArray
}
```

### IndexProxy
To improve query performance above [members], custom indices can be defined above [members].

```java
package naksha.data;
import static naksha.data.Data.literal;

public class IndexProxy extends MapProxy {
  public IndexProxy(@NotNull IMap map) { super(map); }
  public IndexProxy(@NotNull Literal name) { super(map); }
  public static final Literal NAME = literal("name");
  // TODO: Add setter/getter for name as String
  public static final Literal TYPE = literal("type");
  // TODO: Add setter/getter for type as CustomIndexType
  public static final Literal PATH = literal("path");
  // TODO: Add setter/getter for path as StringArray/StringList
}

```

We do not allow secondary unique indices, because this would not work with partitioning. As we partition the data, we can't guarantee uniqueness over secondary members. The reason is that we isolate the partitions, so that when we write, we can only check uniqueness within the partition we use. A secondary unique index would require to crosscheck and update other partitions, which breaks the isolation and would drag the performance down. Therefore, there is only one secondary unique index, being `id`, which is guaranteed to be located in the correct partition using some special rules.

## Mandatory-Members
The members that can not be removed. However, storage do not need to really store all of them exactly as defined here. For example, `lib-psql` does not store the `TupleNumber`, it is deducted from the `feature_number` and `version`, plus from where the _feature_ is stored, so from the [collection], [catalog], and [database] in which it is located. Therefore, this member is not physically stored in this specific implementation. Other implementations may do similar hacks. Still, searching for these mandatory members _(except for `FeatureMember`)_ is mandatory for storage implementations.

### FeatureNumberMember
The `feature_number` as `long`.

### VersionMember
The version of the [tuple] as `long`.

Beware that the version encodes as well the action, which is why there is no explicit action member!

### TupleNumberMember
The full qualified [tuple-number] as `TupleNumber`.

### NextVersionMember
The version of the next [tuple]; if any. The data-type is `long` with [HEAD] representing _HEAD_ state.

### IdMember
The `id` member as data-type `String`. Beware, when the `feature_number` is between `0` and `4,611,686,018,427,387,903` _(including)_, the `id` must be the stringified, and therefore the `id` can be stored as `null`, because it is the same as the `feature_number`, just stringified.

### FeatureMember
Stores all data of the feature that is not extracted into dedicated members. The data-type is `byte[]`.

## Optional-Members
These are optional standard members with some logic of what the represent. They as well have standard [JSON] paths where the information normally can be found.

### GeometryMember
### RefPointMember
### AttachmentMember
### CreatedAtMember
### UpdatedAtMember
### HashMember
### ChangeCountMember
### AppMember
### AuthorMember
### AuthorTsMember
### TagsMember

### OriginMember
The `origin` is an optional value, if enabled it stores a [reference] to the origin of a [feature]. When a [feature] is modified, normally the _metadata_ of the new [tuple] is not modified by the client. Therefore, the moment the new [tuple] is sent to a storage, the storage can check the [tuple-number] of the [tuple], which will refer to the state the client modified. If this state is located outside the [collection] into which the new [tuple] is stored, the storage automatically fills the `origin` field with a reference to the origin state.

The `origin` will stay unchanged until a _REBASE_ is done. When a _REBASE_ is done, the `origin` field is updated to the new foreign state to which the [feature] is rebased.

### ClusterMember
There are reasons to cluster features. Cluster generally means that all features being in the same cluster must only be modified together or not at all. A cluster can be formed cross [collection], even cross [database]. Assume a service read a sign, and modified a road, adding the speed limit into it. Now, the service should put the road and the sign into the same cluster, because they are logically related to each other. Clients should 

### ReplacementMember
A replacement is a special form of clustering. For example, when splitting a topology into two parts, the original [feature] is deleted, and two new [features] are created. Or when a topology is joined, so two [features] are deleted, and a new one is created. All of this should be done in a single version _(transaction)_. This does already indicate a replacement operation. However, as technically multiple replacements can be done in a single transaction, the `replacement_id` can be used to link [feature] together, signaling a replace operation.

, so that it is possible to find all [feature] that belong to the same replacement operation. This is especially useful for tracking the history of changes, and for debugging purposes.

### PubTimeMember
The publication time as `Timestamp`, so actually a `long`. This is set together with [PubVersionMember].

### PubVersionMember
The publication version as `long`.

TODO: Explain why we need a publications version. So, publication versions have no holes, and they are the order in which the data has become visible. They are the only reliable way to replicate the data, as versions can be uncommitted.

### TODO

- `global`: A custom global version, which is a 52-bit unsigned integer.
- `next_global`: The next global version, `null` if this is the latest global version.
- `replacement_id`: A unique string to track replacement operations.
- `rebase_id`: A unique string to track rebase operations.

These fields can be used by applications to track versions with custom information, for example to track replacements. The reason there are pre-defined fields is that the version table can't be created by the client, as it is part of the administrative data of the database, so the client can't define custom fields on it. Therefore, we provide a set of pre-defined fields that can be used for this purpose. The fields are optional, so they can be left `null` if not needed, and will not consume much space.

The `global_version` field can be used to translate global versions into local versions.

#### Feature Members
As mentioned, members are mapped into a [GeoJSON] _feature_ and vice versa, so the [GeoJSON] feature is split into members. The default members are mapped like following:

| Member               | Name         | [JSON] Path      | Relocate | Data-Type   | Description                                                                                        |
|----------------------|--------------|------------------|----------|-------------|----------------------------------------------------------------------------------------------------|
| [FeatureMember]      | feature      | @                | no       | `JsonMap`   | The feature root, decoded from [JBON] into a [JsonMap], then all other members are added.          |
| [TupleNumberMember]  | tn           | `tn`             | yes      | `string`    | The [TN] reference to the [tuple], generated from the tuple-number.                                |
| [VersionMember]      | version      | `version`        | yes      | `uint56`    | The feature version as unsigned 56-bit integer.                                                    |
| [NextVersionMember]  | next_version |                  | yes      | `uint56`    | The next version as unsigned 56-bit integer, normally not exposed.                                 |
| [IdMember]           | id           | `id`             | no       | `string`    | The unique identifier of the feature, either from `id` column or the stringified `feature_number`. |
|                      | type         | `type`           | no       | `string`    | The [GeoJSON] type, always the string `Feature`.                                                   |
|                      |              |                  |          |             |                                                                                                    |
| **Optional Members** |              |                  |          |             |                                                                                                    |
|                      |              |                  |          |             |                                                                                                    |
| [GeometryMember]     | geometry     | `geometry`       | yes      | `Geometry`? | The WGS'84 geometry of the feature.                                                                |
| [RefPointMember]     | ref_point    | `referencePoint` | yes      | `Point`?    | The WGS'84 reference point where to anchor the feature _(when locating it in tiles)_.              |
| [AttachmentMember]   | attachment   | `attachment`     | yes      | `bytea`?    | The attachment, `undefined` when not exposed, `null` when there is no attachment.                  |

All the columns flagged as _relocate_ can be relocated to a different JSON path in the configuration of the collection. Beware, this can be modified later, because it only defines where the values are exposed, when converting the feature into [GeoJSON].

#### Xyz Members
For historic reasons this specification formally defines a standard XYZ column-set. This is a map of dedicated members for all [features] stored in a [collection], following the historic XYZ pattern. In classic systems _metadata_ was exposed in `properties["@ns:com:here:xyz"]`. The pre-defined XYZ column-set is defined as:

| Member              | [JSON] Path                                     | Data-Type         | Description                                                                                                        |
|---------------------|-------------------------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------|
| [TupleNumberMember] | `properties`->`@ns:com:here:xyz`->`uuid`        | `string`          | The [TN] reference to the [tuple] _(redirected from the feature root)_.                                            | 
| [VersionMember]     | `properties`->`@ns:com:here:xyz`->`version`     | `uint56`          | The feature version _(redirected from the feature root)_.                                                          |
| [CreatedAtMember]   | `properties`->`@ns:com:here:xyz`->`createdAt`   | `Timestamp`?      | When the feature was created.                                                                                      |
| [UpdatedAtMember]   | `properties`->`@ns:com:here:xyz`->`updatedAt`   | `Timestamp`?      | When the tuple was created.                                                                                        |
| [HashMember]        | `properties`->`@ns:com:here:xyz`->`hash`        | `int64`?          | The 64-bit [MurMur3] hash of the tuple; using [JBON] to produce it.                                                |
| [ChangeCountMember] | `properties`->`@ns:com:here:xyz`->`changeCount` | `int32`?          | The change count, defaults to `1` _(when created)_.                                                                |
| [AppMember]         | `properties`->`@ns:com:here:xyz`->`app`         | `string`?         | The application-identifier of the application that created this tuple.                                             |
| [AuthorMember]      | `properties`->`@ns:com:here:xyz`->`author`      | `string`?         | The identifier of the user or application that claims authorship of feature.                                       |
| [AuthorTsMember]    | `properties`->`@ns:com:here:xyz`->`authorTs`    | `Timestamp`?      | When the `updated_at` timestamp when the author last changed.                                                      |
| ~~[TagsMember]~~    | `properties`->`@ns:com:here:xyz`->`tags`        | `List\<string\>`? | A list of strings used as tags; deprecated.                                                                        |
| [OriginMember]      | `properties`->`@ns:com:here:xyz`->`origin`      | `string`?         | The [origin] of the feature, [reference] to the source from which the record originates.                           |
| [ClusterMember]     | `properties`->`@ns:com:here:xyz`->`cluster`     | `string`?         | The [reference] to the [cluster] to which the feature belongs, `undefined` if not part of a cluster.               |
| [ReplacementMember] | `properties`->`@ns:com:here:xyz`->`replacement` | `string`?         | The [reference] to the [replacement] group to which the feature belongs, `undefined` if not part of a replacement. |

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

---

## Changes
The following changes have been made to the data model between the original draft and the current version:

- The biggest change is that we now merge `lib-base`, `lib-jbon`, `lib-json`, `lib-geo`, and `lib-model` into one library, being `lib-data`.
  - This simplifies everything and makes it more consistent.
  - Clients that include `lib-data` always have everything they need.
- We made the difference between JSON objects and proxies clear.
  - This allowed us to reduce the reflection calls drastically.
  - It simplifies the creation of proxies drastically.
  - It allows us to use proxies not only for JSON objects, but as well for JBON encoded binaries.
  - After all, clients now can read JBON exactly as they read JSON.
  - All custom proxies will now work as well for JBON encoded binaries.
- We no longer organize data in storages, but rather in databases.
  - This is important to have replication working. We do not care where data is stored, just how it is locically organized and addressed.
- We allow custom members and custom indices.
  - This is important for some internal projects.
- Most members have been made optional.
  - This reduces the mandatory overhead per tuple to only 16 byte in _HEAD_ and 24 byte in _HISTORY_.
  - Again, this is important for internal projects, where we store billions of features.

---

[Indices]: #indices
[indices]: #indices
[Indexes]: #indices
[indexes]: #indices
[Indexable]: #indices
[indexable]: #indices
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
[Tuple-Number]: #tuplenumber
[tuple-number]: #tuplenumber
[tuple-numbers]: #tuplenumber
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
[Data]: #data
[Data Member]: #memberproxy
[data member]: #memberproxy
[data members]: #memberproxy
[member]: #memberproxy
[members]: #memberproxy
[Data Index]: #indexproxy
[data index]: #indexproxy
[data indices]: #indexproxy
[data indexes]: #indexproxy
[index]: #indexproxy
[indices]: #indexproxy
[indexes]: #indexproxy
[Origin]: #originmember
[origin]: #originmember
[Cluster]: #clustermember
[cluster]: #clustermember
[Replacement]: #replacementmember
[replacement]: #replacementmember
[book]: JBON2.md#book
[books]: JBON2.md#book
[Tag Split]: #tag-split
[tag split]: #tag-split
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
