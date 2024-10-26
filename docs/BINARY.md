# Binary
To efficiently store data in-memory, at disk, in remote storage, like s3 buckets, or redis, Naksha defines a binary format for tuples, metadata, and tuple-numbers.

## Endianness
Generally, all multi-byte values are encoded in network byte-order, so in [Big-Endian byte-order](https://en.wikipedia.org/wiki/Endianness) encoded.

This is very important, because this allows us to order by tuple-number, which would not be possible in [Little-Endian byte-order](https://en.wikipedia.org/wiki/Endianness)!

## Features
Naksha manages features, they are stored in **storages**.

- There are up to _9,223,372,036,854,775,806_ (`2^63-2`) **storages** per environment.
- Each storage holds up to _4,294,967,295_ (`2^32-1`) **maps**.
- Each map holds up to _4,294,967,295_ (`2^32-1`) **collections**.
- Each collection holds up to _4,294,967,296_ (`2^32`) **features**.

The maximum number of features per collection can be increased by partitioning the collection. Therefore, the total number of features in a single collection can be increased to 1,099,511,627,776 (`2^40`). The total number of features in administration can be even bigger, when history is considered.

Note that dictionaries, transaction, basically everything in Naksha is a feature and follows the same lifecycle.

## Feature Lifecycle
The lifecycle of a feature is tracked from its creation to its deletion. Each modification of a feature shall create a new unique immutable state, which we call **Tuple**. A _Tuple_ is always created by a storage, and signed-off by that storage with its own _storage-number_. One exception are temporary tuples, so states that are not immutable, and that are not persisted anywhere, that are only created temporarily in memory, share the same special storage-number `0`. States that are persisted, need to be signed-off by a storage that persists them. This guarantees that there are no two state with the same unique identifier, called **Tuple-Number**. This allows all participants to cache all states of all feature of all sources indefinitely.

## Binary Header
When data is binary encoded in-memory, a header is not needed, as it is clear what is encoded, therefore this header is not mandatory. However, when not encoded in-memory, the binary encoding needs headers to know what is encoded. For this purpose, Naksha binary defines a common object/array header as being:

- has-extensions: u1
- type: u3
- subtype: u4
- length: u24 (BE encoded)
- size: u32 (BE encoded)
- { type-specific-header ... }
- { extensions ... }
- { payload ... }

The length describes the amount of elements encoded, the size is the total size in byte, that belong to this binary. This includes the header size itself, an empty object/array has a minimal header of 16-byte, the size is therefore 16. A size less than 16 is an invalid header. The type is one of the following values:

- `0`: Tuple-Number-Array
- `1`: Metadata-Object
- `2`: Metadata-Array
- `3`: Tuple-Object
- `4`: Tuple-Array
- `5..7`: Reserved

The subtype is dependent on the type, and the possible values are described when the types are described. Each type can have additional _type-specific-header_ values, they are described with the type.

### Extensions
Extensions have been added as optional header information. They are here to allow extra headers with context information, as well as allowing applications to store own arbitrary metadata. The extension block has a single small header, being:

- type: u8
- size: u24
- total-size: u32

The first extension is called the _standard-extension_. Its meaning is part of this document, currently the only defined type is `0`, which means that there is no _standard-extension_, therefore _size_ must be `0` as well.

The _total-size_ stores the total size of the extension segment, including this header, which must be at least `8`. The rest of the extension memory, so the difference between the _total-size_ and the _size_ is used by application specific extensions.

**The total size must be a multiple of `8` (aligned to 64-bit), so padding bytes may have to be added at the end, which must be filled with zero-bytes.** 

Readers of the binary, that are unaware of the extensions, will simply ignore this information, and skip over the extensions by adding the _total-size_ of the extensions to known header size.

For example, when tiles are encoded, their payload is maybe a _Tuple-Array_, but additionally there may be an extension header that describes the tile-id, and other details, maybe a map between feature-id and the offset where the feature is encoded, to quicker find entries. Readers can use the data without these details, they may just not have all necessary information to act optimal.

Each custom extension in the extension section, must have a simple header being, so allow multiple different custom extension in a binary:

- custom-type: u40 (u64 read shr 24 & 1099511627775)
- custom-size: u24 (u64 read & 16777215)
- { extension data ... }

Applications can encode application specific data in the custom-extensions. There is one private custom-type that is shared between all applications, being `0`. This can be used, when there is no need to share the extension information with other services or clients (for example caching service can use this to encode arbitrary information). All other types have to be registered with Naksha, and the _type-numbers_ will be assigned like _storage-numbers_. Applications are free to split the shared private extension into further subtypes, using any additional values encoded in the payload of their custom data block.

Every _custom-type_ must only occur ones in the extension block!

## Tuple-Number
As said, a **Tuple** is an immutable state of a feature. To address tuples, unique identifier are needed, they are called **Tuple-Number**, and are encoded like following:

- storage-number: u64 _(optional in array)_
- map-number: u32 _(optional in array)_
- collection-number: u32 _(optional in array)_
- version: u56 _(u64 read shr 8, year:u15, month:u4, day:u5, sequence:u32)_
- partition-number: u8
- uid: u32

When multiple tuple-numbers are encoded in an array, then the optional values (storage-, map-, and collection-number) can be shared in the header, so the array header can declare for each of them a shared value that is valid for all encoded tuple-numbers. This can reduce the storage size. If encoded standalone, all values need to be encoded, which makes a tuple-number a 224-bit value (28-byte). In an array, each tuple-number can be reduced to 92-bit (12-byte), when they come from the same storage, map and collection.

The **storage-number**, **map-number**, and **collection-number** are just unique identifiers of the storage, map, and collection in which all tuples of the feature, to which this tuple-number belongs, are stored.

The **partition-number** identifies the partition in which all tuples of a feature should be stored. It is the first byte of the [MD5](https://en.wikipedia.org/wiki/MD5) hash above the feature-id, and ensures that all states of a feature are always stores in the same partition. The storage can decide how many partitions are used, and then divide the partition-number by the number of used partitions, the rest is the effective partition index:

`partitionIndex = partitionNumber % partitionCount`

The **version** encodes the year, month, and day when the transaction started (UTC) that was used to create this tuple/state, plus a unique sequence-number of the transaction within that day. The version `0` is equivalent to `null`, and represents **the** temporary version, that is shared by all tuples that are not persisted, and are only build in memory, for example for testing, or as inbetween states.

The **uid** is the transaction local unique state identifier, if multiple new states are created within a single transaction. It is forbidden to generate multiple states of the same feature within a single transaction, the reason is the meaning of the _next-version_ property, see below. Tuples are generated in order, so they can be timely ordered by _uid_. This allows to order all changes by _version_ and _uid_ to get a reliable order, which is important for paging algorithm or to split big transactions into chunks.

## Tuple-Number-Array
When tuple-numbers are persisted, they are always encoded in arrays, even when only a single tuple-number need to be stored. They are encoded like following:

- has-extension: u1 {u32 read shr 31 & 1}
- type: u3 _(`0`)_ {u32 read shr 28 & 7}
- subtype: u4 {u32 read shr 24 & 15}
- length: u24 {u32 read & 16777215}
- size: u32
- **storage-number**: u64 (optional, only when subtype > 0)
- **map-number**: u32 (optional, only when subtype > 1)
- **collection-number**: u32 (optional, only when subtype > 2)
- { extensions ... }
- { tuple-numbers ... }

The subtype is defined as:

- `0`: All tuple-numbers are full encoded (224-bit, 28-byte, encoding).
- `1`: The storage-number is shared, and stored in the header (160-bit, 20-byte encoding).
- `2`: The storage-, and map-number are shared, and stored in the header (128-bit, 16-byte encoding).
- `3`: The storage-, map-, and collection-number are shared, and stored in the header (96-bit, 12-byte encoding).

The last variant is generally used within storages, when a single collection is read, to reduce the amount of data that need to be transferred to the client.

When result-sets are encoded (for example persisting handles), they are encoded as tuple-arrays, with an extension header that stores the metadata, like the handle-id, and maybe validation state of this result-set, if not all tuples have been filters, in that case optionally the filter to apply.

## Metadata
The metadata is encoded like following:

- { tuple-number }
- flags: u32
- next_version: u64 (optional, flags bit)
- updated_at: u48
- created_at: u48 (optional, flags bit)
- author_ts: u48 (optional, flags bit)
- hash: u32
- geoGrid: u32
- changeCount: u32
- { previous tuple-number, optional, flags bit }
- id: cstring
- appid: cstring
- author: cstring
- type: cstring
- origin: cstring
- EOF (end of metadata): u8 (ascii-0)

Each metadata encodes at its start the tuple-number in 224-bit (28-byte).

## Metadata-Object
Each tuple has a pre-defined set of metadata. Optionally, metadata can have an object header like:

- has-extension: u1 {u32 read shr 31 & 1}
- type: u3 _(`1`)_ {u32 read shr 28 & 7}
- subtype: u4 _(`0`)_ {u32 read shr 24 & 15}
- length: u24 _(`1`)_ {u32 read & 16777215}
- size: u32
- _{ extensions ... }_
- _{ metadata ... }_

This object header is normally not used, except the metadata need to appear in any binary, where the type need to be detected at runtime.

## Metadata-Array
When multiple metadata are stored in an array, the binary object has a header that is encoded like following:

- has-extension: u1 {u32 read shr 31 & 1}
- type: u3 _(`2`)_ {u32 read shr 28 & 7}
- subtype: u4 {u32 read shr 24 & 15}
- length: u24 {u32 read & 16777215}
- size: u32
- **storage-number**: u64 (BE read, optional, only when subtype > 0)
- **map-number**: u32 (BE read, optional, only when subtype > 1)
- **collection-number**: u32 (BE read, optional, only when subtype > 2)
- _{ extensions ... }_
- _{ metadata ... }_

**Note**: The array does not store metadata-objects, only the metadata, so without object header!

The subtype is defined the same way it is done for the [Tuple-Number-Array](#Tuple-Number-Array):

- `0`: All tuple-numbers are full encoded (224-bit, 28-byte, encoding).
- `1`: The storage-number is shared, and stored in the header (160-bit, 20-byte encoding).
- `2`: The storage-, and map-number are shared, and stored in the header (128-bit, 16-byte encoding).
- `3`: The storage-, map-, and collection-number are shared, and stored in the header (96-bit, 12-byte encoding).

This means, the tuple-number at the start of each encoded metadata can be reduced to only 96-bit (12-byte), which saves some memory for bigger arrays.

## Tuple-Object
Encoding a full tuple requires header, because it is a complex object. Each tuple is encoded like:

- has-extension: u1 {u32 read shr 31 & 1}
- type: u3 _(`3`)_ {u32 read shr 28 & 7}
- subtype: u4 _(`0`)_ {u32 read shr 24 & 15}
- length: u24 _(`1`)_ {u32 read & 16777215}
- size: u32
- **metadata_size**: u16
- **ref_point_size**: u16
- **geometry_size**: u32
- **tags_size**: u32
- **feature_size**: u32
- **attachment_size**: u32
- _{ extensions ... }_
- _{ metadata }_
- _{ reference-point }_
- _{ geometry }_
- _{ tags }_
- _{ feature }_
- _{ attachment }_

**Note**: The tuple does not store metadata-object, only the metadata, so without object header!

## Tuple-Array
If multiple tuples should be encoded in a single byte-stream, they should have yet another header, being:

- has-extension: u1 {u32 read shr 31 & 1}
- type: u3 _(`4`)_ {u32 read shr 28 & 7}
- subtype: u4 _(`0`)_ {u32 read shr 24 & 15}
- length: u24 {u32 read & 16777215}
- size: u32
- _{ extensions ... }_
- _{ tuple-objects ... }_

Note that this array allows iteration, but not direct seeking, so basically the same way that [JBON](./JBON.md) does.

It is recommended to not compress the individual parts of a tuple, when converting into the binary form, but rather to compress the whole array eventually, to increase the compression rate.

## Dictionaries
There is no specific format for dictionaries, they can be encoded either as [Tuple-Array](#Tuple-Array), or as features. If encoded as features, the recommendation is to encode them as:

```js
dict = {
  id: "{dictionary-id}",
  properties: {
    // All custom properties (optional)
  },
  dictionaries: [
      // Encode all values in order.
      // This means, we can stored strings, objects, arrays, ...
  ]
}
```

## Compression
Technically the binary format defines how the data is encoded raw, if the data is stored compressed or not is not part of this specification. Generally it is considered a very good idea to compress all arrays, as this can save a lot of additional space.

## Conclusions
If we look at how the tuple-numbers and the binary format helps, we first need to look at how a search in the Postgresql database is done.

The following query is how Naksha `lib-psql` will perform a search in the database:

```sql
WITH query AS (
 (SELECT ${col_number} as col_num, id, tuple_number FROM ${col_name} WHERE ...)
 UNION ALL
 (SELECT ${col_number} as col_num, id, tuple_number FROM ${col_name} WHERE ...)
 UNION ALL
 ...
), result AS (
  SELECT col_num, tuple_number
  FROM query
  ORDER BY col_num, id, tuple_number
  LIMIT 16777215
)
SELECT gzip( -- compress the binary
 int4send((2 << 24)|sum(1)::int)|| -- type (2), subtype (0), length
 int4send(20 + sum(1)::int*16)|| -- size
 int8send(${storage_number})|| -- shared storage-number
 int4send(${map_number})|| -- shared map-number
 bytea_agg(int4send(col_num)||tuple_number) -- aggregate all tuple-number
) AS rs FROM result;
```
This query guarantees, that all tuples are ordered by collection, feature-id, version, uid.

We need to limit the result to `2^24-1`, because this is the maximum length we can encode in the binary. This leads to a maximum result size of `20 + (16777215 * 16)`, which is 256 MiB, what as well protects us in producing or reading too big result-sets.

If the HISTORY need to be queried too, then every feature will be found multiple times in different versions. To reduce this to the latest _n_ versions, we modify the query into:

```sql
WITH query AS (
 (SELECT ${col_number} as col_num, id, tuple_number, txn FROM ${col_name} WHERE ...)
 UNION ALL
 (SELECT ${col_number} as col_num, id, tuple_number, txn FROM ${col_name} WHERE ...)
 UNION ALL
 ...
), query_with_v AS (
  SELECT
    col_num,
    id,
    tuple_number,
    ROW_NUMBER() OVER (PARTITION BY id ORDER BY txn DESC) AS v
  FROM query
), result AS (
  SELECT col_num, tuple_number
  FROM query_with_v
  WHERE v <= 2 -- this selects the latest n version!!!
  ORDER BY col_num, id, tuple_number
  LIMIT 16777215
)
SELECT gzip( -- compress the binary
 int4send((2 << 24)|sum(1)::int)|| -- type (2), subtype (0), length
 int4send(20 + sum(1)::int*16)|| -- size
 int8send(${storage_number})|| -- shared storage-number
 int4send(${map_number})|| -- shared collection-number
 bytea_agg(int4send(col_num)||tuple_number) -- aggregate all tuple-number
) AS rs FROM result;
```
Note, it is strongly recommended to increment the `work_mem` to `1G`, so that we can be sure that all the selections, and the sort, can be done in memory. Postgres will need the memory only for a short moment, because we eventually create one big byte-array. This basically means, while the data is in transfer, Postgres only need to keep this compressed binary in memory. Postgres does not need to keep a cursor hanging around, with multiple round trips to be done by the client to fetch all the rows, as there is only one row and one column returns, it's an all at ones operation!

**We transfer up to 16.7 millions rows at ones!**

Beware, as we order by feature-id, and the same feature-id always produce the same partition-number, this means, the partition-number in the tuple-number does not affect ordering. This only works, because numbers are stored in [Big-Endian byte-order](https://en.wikipedia.org/wiki/Endianness).

This query allows to search for tuples in HEAD, HISTORY, DELETED, and in multiple collections at the same time. It does not allow to query multiple maps at ones. The query returns the results compressed, and compression will have a big impact on size, because we know that at least the collection-number repeats itself often, and we know that very likely the version, and partition-number, will repeat them self as well. Testing showed that GZIP reduces the result to less than 25% of the original, sometimes even less. This means that for the maximum of 16,777,215 features in a result-set, we have a raw size of 256 MiB, which can be compressed down to less than 64 MiB, case dependent. As the results are ordered, we can store the result-set in some cache, and then allow iteration using a handle.

The time this data need to be transferred to a client depends on the bandwidth, but knowing that in AWS a [single connection is limited to 5 Gbps](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html), we can make an educated guess. Assuming the maximum result size with 16.7 million found features, compressed down to 64 MiB, this will take around `(64*2^20)/(5*2^27)*1000` milliseconds, so 100ms. This time seems to be okay, but we need to add some time for Postgres to collect the data, and to create the binary. We can assume that eventually such a big result can take up to 500 millis, or more. Still a very good number, compared to the shire size of the result. 

If the client does not need all results, the limit can be decreased from the _16,777,215_ to whatever is needed. Technically, we could even seek in the result, using _offset_ and _limit_, but this can become very expensive, more expensive than transferring just all tuple-numbers at ones, cache the result-set in Java, and eventually implement the seeking in Java (optionally copy the result-set into some cache).

This has another big advantage in a multi-service environment. It allows to calculate results, or the content of tiles, and to transfer only the tuple-numbers, without actually transferring the feature payload. This is helpful in queues, when sending results between services, and basically everywhere, where the data may have security implications. When a service receives such a result-set, it can fetch the actual payload from the source, or any cache. This as well allows to perform security checks, if the receiver of the data has the right to access it. As the data is immutable, the whole process is idempotent, and therefore repeatable in a crash case.

Now, finally, lets have a look on how the tuples are eventually fetched from the database, when the client really need them, and can't find them in any cache (we do not allow cross map selection):

```sql
WITH source AS (
  -- Select all tuples needed from all collections.
  -- We can read all tuples using paging
  -- Then we order by tuple_number, and use offset/limit here!
  -- This must only be done in a single table, but nothing else changes.
  -- Note that using tuple_number will perform an index scan, its ordered already.
  (SELECT ${col_number} as col_num, * FROM ${col_name} WHERE tuple_number = ANY($1))
  UNION ALL
  ...
), meta_with_rest AS (
  -- Compose metadata binary, and add the other binary columns.
  SELECT bytea_agg(
    int8send(${storage_number})
    ||int4send(${map_number})
    ||int4send(col_num)
    ||tuple_number -- 12 byte
    ||int4send(flags) -- 4 byte, we're aligned to 64-bit again
    ||coalesce(int8send(txn_next),''::bytea)
    ||substring(int8send(updated_at), 3) -- u48
    ||coalesce(substring(int8send(created_at),3),''::bytea) -- u48
    ||coalesce(substring(int8send(author_ts),3),''::bytea) -- u48
    ||int4send(coalesce(change_count, 1))
    ||int4send(coalesce(hash, 0))
    ||int4send(coalesce(geo_grid,0))
    ||coalesce(prev_tuple_number,''::bytea)
    ||id::bytea||'\x00'::bytea
    ||coalesce(app_id,'')::bytea||'\x00'::bytea
    ||coalesce(author,'')::bytea||'\x00'::bytea
    ||coalesce(type,'')::bytea||'\x00'::bytea
    ||coalesce(origin,'')::bytea||'\x00'::bytea
  ) as meta, ref_point, geo, tags, feature, attachment
  FROM source
), tuple_objects_without_header AS (
  -- Create a Tuple-Objects without header.
  SELECT bytea_agg(
     int4send((octet_length(meta) << 16)|octet_length(coalesce(ref_point,''::bytea)))
     ||int4send(octet_length(coalesce(geo,''::bytea)))
     ||int4send(octet_length(coalesce(tags,''::bytea)))
     ||int4send(octet_length(coalesce(feature,''::bytea)))
     ||int4send(octet_length(coalesce(attachment,''::bytea)))
     ||meta
     ||coalesce(ref_point,''::bytea)
     ||coalesce(geo,''::bytea)
     ||coalesce(tags,''::bytea)
     ||coalesce(feature,''::bytea)
     ||coalesce(attachment,''::bytea)
    ) as obj
), result AS (
  -- Join all tuple-objects, adding the headers, count amount of tuples.
  SELECT sum(1)::int as len, bytea_agg(
    int4send((3 << 28)|1) -- type 3, length 1
    ||int4send(8 + octet_length(obj)) -- size
    ||obj
  ) as all_obj
  FROM tuple_objects_without_header
  LIMIT 16777215
)
-- Create the Tuple-Array, compress it.
SELECT gzip(bytea_agg(
    int4send((4 << 28)|len) -- type 4
    ||int4send(8 + octet_length(all_obj)) -- size
    ||all_obj
)) FROM result
```
This is again highly efficient, because, even while Postgres has to perform a couple of byte-array aggregations, that cost some CPU time and memory, eventually we only return one row with one column, compressed. This reduced not only the amount of byte transferred, it as well avoids that a client has to perform multiple reads, therefore avoids that Postgres need to create a cursor, and letting the client fetch row by row.

As shown here, the query, and tuple loading from the database, is solved efficiently.

## Appendix
This document formalizes the binary representation of the in-memory data, so that all caches, and services use the same data format. This allows an efficient binary exchange of data.

### Partitioning
It is strongly recommended to start partitioning early on, said otherwise, it is not recommended to store more than 10 million features per partition. If it is expected to store up to a billion features in a certain collection, it is recommended to create around 100 partitions. More partitions improve the performance when multiple clients concurrently access the collection. While partitioning does have a positive effect on parallel read and write performance, it negatively effects single threaded performance. Having too many partitions will as well decrease the query (search) performance. Having too few partitions however, will have a bad effect on read, write, and query performance. It is important to select the right number of partitions.

### Conclusion
The concept is designed so that every mobile phone, every car, every device, can be an own storage, and that users can split each storage logically into many maps. For example, a car company could acquire a storage-number from Naksha for every car-model they have, then manage all cars of this model as individual maps. On the other hand, they can create a new storage-number for every car, and synchronize this with a car identifier, or they share a storage-number for all consumers, so that each consumer has an own map in a virtual consumer storage, still each consumer can create billions of collections with data.

The concept is to link all productive entities together into one cloud, where it is always clear which data record comes from which device, but to decouple the devices, so that a device does not need to synchronize with other devices, before it modifies map data. For example, a car can collect data in a collection locally, and then synchronize it back, when there is a good and cheap internet connection available into the cloud, fetching new map data from the cloud.

### Storage-Numbers
All storage-numbers between `0` and `9223372036854775807` are reserved for private usage, which means every vendor (like [HERE Technologies](https://www.here.com/)) can make an own dedicated namespace, and privately distribute storage-numbers to devices, services, or whatever.

The storage-numbers between `-1` and `-9223372036854775808` are reserved for a global public namespace. As every storage always has an `id` and `number`, the idea is to create some form of public DNS for storages, maybe in cooperation with the [OpenStreetMap Foundation](https://osmfoundation.org/). So that everybody can register namespaces the same way that domains can be registered.
