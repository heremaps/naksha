# PostgresQL Reference Implementation
This part of the documentation explains details about the reference Naksha Storage-API implementation based upon PostgresQL database.

## Basics
The admin database and most data storages of Naksha will be using a PostgresQL database. For users, we will allow the usage of the same implementation for their own storages, or they are free to implement an own storage engine. Generally, Naksha Storage-API need to refer to all objects as Geo-JSON features. They have the following general layout:

```
{
  "id": {id},
  "type": {type},
  "momType": {type},
  "geometry": {Geo-JSON geometry object},
  "referencePoint": {Geo-JSON geometry object},
  "properties": {
    "@ns:com:here:xyz": {Internal state values}
  }
}
```

Actually, the storage engine will split this information apart as show in the following table layout.

## Table layout
All tables used in the Naksha PostgresQL implementation have the same general layout, what simplifies access:

| Column       | Type  | RO  | Modifiers | Description                                                                   |
|--------------|-------|-----|-----------|-------------------------------------------------------------------------------|
| col_id       | int8  | yes |           | `f.p.xyz->colId` - Unique collection id, lower 8-bit are partition number     |
| updated_at   | int8  | yes | NOT NULL  | `f.p.xyz->updatedAt`                                                          |
| created_at   | int8  | yes |           | `f.p.xyz->createdAt` - `COALESCE(created_at, updated_at)`                     |
| author_ts    | int8  | yes |           | `f.p.xyz->authorTs` - `COALESCE(author_ts, updated_at)`                       |
| txn_next     | int8  | yes |           | `f.p.xyz->nextVersion` - The next version, if there is any.                   |
| txn          | int8  | yes | NOT NULL  | `f.p.xyz->version` - Transaction number.                                      |
| ptxn         | int8  | yes |           | `f.p.xyz->prevVersion` - Transaction number of the previous state.            |
| rowid        | int8  | yes |           | Unique row identifier (col_id:64, tnx:64, uid:32)                             |
| uid          | int4  | yes | NOT NULL  | Transaction local unique ID                                                   |
| puid         | int4  | yes |           | Transaction local unique ID - `COALESCE(puid, 0)`                             |
| hash         | int4  | yes |           | `f.p.xyz->hash` - Hash above feature, tags, geometry and geo_ref bytes (TBD). |
| change_count | int4  | yes |           | `f.p.xyz->changeCount` - `COALESCE(version, 1)`                               |
| geo_grid     | int4  | yes |           | `f.p.xyz->grid` - HERE binary quad-key level 15 above `geo_ref`.              |
| flags        | int4  | no  |           | Options like feature, geometry encoding, and the action.                      |
| app_id       | text  | yes | NOT NULL  | `f.p.xyz->appId`                                                              |
| author       | text  | yes |           | `f.p.xyz->author` - `COALESCE(author, app_id)`                                |
| id           | text  | no  | NOT NULL  | `f.id` - The **id** of the feature.                                           |
| tags         | bytea | no  |           | `f.p.xyz->tags` - Tags are labels attached to features to filter features.    |
| geo          | bytea | no  |           | `f.geometry` - The geometry of the features.                                  |
| geo_ref      | bytea | no  |           | `f.referencePoint` - The reference point (`ST_Centroid(geo)`).                |
| feature      | bytea | no  |           | `f` - The Geo-JSON feature in JBON, except for what was extracted.            |

In the table above `f` refers to the feature root, `f.p` refers to the content of the `properties` of the feature, and `f.p.xyz` refers to the `@ns:com:here:xyz` key in the `properties` of the feature (which is called XYZ namespace for historical reason).

The type is effectively: `COALESCE(f.momType, f.p.featureType, f.type)`. If the type is the same as the default type of the collection, it is set to _null_.

The **origin** is set automatically, if a feature is inserted into a collection with a `uuid` that refers to another collection or where the `id` of the feature changed (the GUID contains the **id**, therefore this change can be detected). This is used for get 3-way-merge, which is essential for automatic re-basing. Assume a topology is split in the editor into two parts, the editor should clone the original topology two times, then modify the geometry and properties (this is basically the natural thing expected). Actually the original topology will be deleted. When storing these three features (delete for the cloned one and the two new ones), all of them will have the same cloned XYZ namespace with the same `uuid`. The `lib-psql` will detect this situation and copy the `uuid` into the `origin` column. This is done before updating the other XYZ properties (in the before-trigger). If the original topology is modified later, all related features need to be re-based accordingly, this can be done by searching for all features having the same `origin` as the modified topology. Actually, first search for the prefix `urn:here:naksha:guid:{storage}:{collection}:{id}:`. All found features then need to be updated, for this purpose the concrete state based upon which the new feature state was generated is fetched, then a 3-way-merge is done and applied, last the `origin` is adjusted to the `uuid` of the new state upon which the rebase was done, so that it is clear that the feature is now up-to-date.

All **text** columns and all **btree** indices should always be created with `COLLATE "C"` to ensure deterministic ordering in the table, long term stable determinism and default support for _like_ operation. Basically, `text_pattern_ops` is exactly doing the same thing (can be set additionally to be explicit about this). This improves as well deduplication. All queries should always enforce `COLLATE "C"` too. When text is encoded, we should use `normalize(text, 'NFKC')` to ensure the same binary encoding for all values, no matter if written from Java or directly inside the database. Available collations can be queried using `SELECT * FROM pg_collation;` and `ucs_basic` may be another option, but not recommended so far.

As being documented in the **Description** column, every feature is split into parts, when stored. The reason is performance and efficiency. This is done to avoid unnecessary work, for example when rows need to be moved into the history. Therefore, the **tags** columns contains the tags extracted from the XYZ namespace, while the rest of the XYZ namespace, managed by the storage engine, is stored in individual columns. The **txn_next** is as well managed internally and only used for the history (actually, this is the only value that need to be adjusted, when a row is moved into history). All columns should be merged together into the Geo-JSON feature. This merging is left to the client and not part of the database code (it is done by the higher level **lib-psql**).

All indices that use columns where _NULL_ has a default meaning, should use `COALESCE`, for example `COALESCE(updated_at, created_at)`, or little more complicated `COALESCE(author_ts, COALESCE(updated_at, created_at))`.

When creating indices for columns being unique, [disabling deduplication](https://www.postgresql.org/docs/current/btree-implementation.html#BTREE-DEDUPLICATION) can be helpful, but this only effects a small amount of indices. For this, we will use `with (deduplicate_items=OFF)` in such cases.

## Collections
Within PostgresQL a collection is a set of database tables. All these tables are prefixed by the collection identifier. The tables are:

- `{collection}`: The HEAD table. It is either a plain simple table or a partitioned table. When being partitioned, this is done as `PARTITION BY BY RANGE (naksha_partition_number(id))`.
  - `{collection}$p[n]`: The HEAD partitions (`PARTITION OF {collection} FOR VALUES WITH (MODULUS {n}, REMAINDER {i})`), beware that the name of the partition is taken from `naksha_partition_id('{collection}')`, which will return a two digit value (`00..31`).
- `{collection}$del`: The DELETION table holding all features that are deleted from the HEAD table. This can be used to read zombie features (features that have been deleted, but are not yet fully unrecoverable dead). When being partitioned, this is done as `PARTITION BY BY RANGE (naksha_partition_number(id))`.
  - `{collection}$del_p[n]`: The DELETION partitions (`PARTITION OF {collection}$del FOR VALUES WITH (MODULUS {n}, REMAINDER {i})`), beware that the name of the partition is taken from `naksha_partition_id('{collection}')`, which will return a two digit value (`00..31`).
- `{collection}$hst`: The history table, this is always a partitioned table, partitioned by `txn_next` (`PARTITION BY BY RANGE (txn_next)`).
  - `{collection}$hst_{YYYY}`: The history partition for a specific year (`PARTITION OF {collection}$hst_{YYYY} FOR VALUES FROM naksha_txn(2023) TO naksha_txn(2024)`, optionally partitioned again by `id` (`PARTITION BY BY RANGE (naksha_partition_number(id))`), when partitioning is enabled.
    - `{collection}$hst_{YYYY}_$p[n]`: The history partition of a specific year, if partitioning is enabled (`PARTITION OF {collection}$hst_{YYYY} FOR VALUES WITH (MODULUS {m}, REMAINDER {n})`.
- `{collection}$meta`: The meta table that stores cached data.

**Notes:**

- The collection names must be lower-cased and only persist out of the following characters: `^[a-z][a-z0-9_-:]{0,31}$`.
- The dollar is reserved and used internally to avoid collisions, because otherwise a client could name a collection `foo` and another one `foo_hst`, which would cause a collision with the internally managed history table.

## Partitioning
Generally the partitioning is optimized for bulk operations. Technically, each partition of the **HEAD** table will be placed into an own dedicated tablespace, so an own physical storage. This allows multiple clients to do parallel modifications, when they understand where a feature is stored. This design physically isolates partitions from each other and therefore allow parallel clients (when they directly write into partitions) to work parallel without any interaction between them.

Not that the history is partitioned on two levels. First on time (based upon `txn_next`). The reason for this is that we want to quickly purge history that is too old. This way we can purge all the history that is older than n-years, by just dropping the corresponding history table, instead that we need to perform an actual query on the history. As the history is always INSERT only, this allows us to fill all rows to 100% and allows us to compact the past year using a **FULL VACUUM**, preserving it.

The second level of the partitioning of the history is in the same way as the **HEAD** table. If we would not do this, we would basically break the isolation of the **HEAD** table, because then any **UPDATE** on a **HEAD** feature would require the corresponding client to touch a history table, that is shared with other clients. This would share the bandwidth of the underlying physical storage and break the isolation. By storing the history this way, all clients that have started a transaction at the same year, will write into the same history root table. However, each feature they process is isolated in the **HEAD** and the same way in the history, with own dedicated physical storages for each of the two tables. Eventually this means, that each client gets 100% of the disk IO for the **HEAD** partition, and 100% of the disk IO for the history partition. So, it can utilize 100% of the available disk IO to perform the action without any bottleneck anywhere.

## History partitioning on `txn_next`
The partitioning in the history is a two level partitioning, based first upon `txn_next`. The reason is, because `txn_next` basically is the time when the change was moved into history. The `txn` is the time when a state was originally created. So, at a first view it might be more logical to partition by `txn`, so when a state was created. However, by doing so we run into one problem, assume we decide to keep the history for one year, what do we want? We want to be able to revert all changes that have been done in the last year. Assume a feature is created in 2010 and then stays unchanged for 13 years. In 2023 this feature is modified. If we partition by the time when the state was created, this feature would be directly garbage collected, because it is in a partition being older than one year. However, this is not what we want! We want this feature to stay here until 2024, which means, we need to add it into the partition of `txn_next`, which will link to the today state, so the 2023 state, and therefore it will be added into the 2023 partition. Even while the partitioning based upon `txn_next` is first counter-intuitive, it still is necessary.

## Triggers
Naksha PSQL-Storage will add two triggers to the HEAD table (or partitions) to ensure the desired behavior, even when direct SQL queries are executed. One trigger is added _before_ `INSERT` and `UPDATE`, the other _after_ all. The triggers implement the following behavior (it basically exists in two variants: with history enabled or disabled):

* **before** `INSERT` and `UPDATE`
  * Fix the XYZ namespace `txn=naksha_txn(), txn_next=0, ...` (full update)
* **after** `INSERT`, `UPDATE` or `DELETE`
  * Update the transaction-log
  * if not `disableHistory`
    * Update XYZ namespace of OLD: `txn_next=naksha_txn()` (only this!)
    * INSERT a copy of OLD into `{collection}_hst`
    * This basically creates a backup of the old state (in action UPDATE or CREATE), linked to the new HEAD state.
  * if `INSERT` or `UPDATE`
    * Delete the feature (by id) from the `{collection}_del`
  * if `DELETE`
    * Update XYZ namespace of OLD: `action=DELETED, txn_next=0, ...` (full update)
    * INSERT a copy of OLD state into the `{collection}_del`
      * This boils down to creating a new state and then copy it into the deletion table
      * Therefore: When the client requests deleted features, we can simply read them from the deletion table
    * if not `disableHistory`
      * INSERT a copy of OLD into `{collection}_hst`
        * This basically creates a backup of the deleted state we just inserted into the "del" table.

Naksha will implement a special PURGE operation, which will remove elements from the special deletion table (which is a special HEAD table).

## Transaction-Number [`txn`]
All features stored by the Naksha storage engine are part of a transaction. The data is stored partitioned, because of the huge amount of data that normally need to be handled. To instantly know where a feature is located, so in which partition, we need to ensure that the unique identifier of a transaction holds the partition key. Partitioning is done using the _next transaction-number_.

The transaction-number is a 64-bit integers, split into four parts:

* _Year_: The year in which the transactions started (e.g. 2024).
* _Month_: The month of the year in which the transaction started (e.g. 9 for September).
* _Day_: The day of the month in which the transaction started (1 to 31).
* _Seq_: The local **sequence-number** in this day.

The local **sequence-number** is stored in a sequence named `naksha_txn_seq`. Every day starts with the sequence-number reset to zero. The final 64-bit value is combined as:

- 23-bit **year**, between 0 and 8388607 {_shift-by 41_}.
- 4-bit **month**, between 1 (January) and 12 (December) {_shift-by 37_}.
- 5-bit **day**, between 1 and 31 {_shift-by 32_}.
- 32-bit **seq**uence number.

This concept allows up to 4 billion transactions per day (between 0 and 4,294,967,295, _2^32-1_). It will overflow in browsers in the year 4096, because in that year the transaction number needs 53-bit to be encoded, which is beyond the precision of a double floating point number. Should there be more than 4 billion transaction in a single day, this will overflow into the next day and potentially into an invalid day, should it happen at the last day of a given month. We ignore this situation, it seems currently impossible. Check in the browser:

- `((4095n << 41n)+(12n << 37n)+(31n << 32n)+4294967295n) <= BigInt(Number.MAX_SAFE_INTEGER)`: _true_
- `(4096n << 41n) <= BigInt(Number.MAX_SAFE_INTEGER)`: _false_

The human-readable (Javascript compatible) representation is as a string in the format:

`urn:here:naksha:txn:{storageId}:{year}:{month}:{day}:{seq}`.

Normally, when a new unique identifier is requested, the method `naksha_txn()` will use the next value from the sequence (`naksha_txn_seq`) and verify it, so, if the year, month and day of the current transaction start-time (`transaction_timestamp()`) matches the one stored in the sequence-number. If this is not the case, it will enter an advisory lock and retry the operation, if the sequence-number is still invalid, it will reset the sequence-number to the correct date, with the _sequence_ part being set to `1`, so that the next `naksha_txn()` method that enters the lock or queries the _sequence_, receives a correct number, starting at _sequence_ `1`. The method itself will use the _sequence_ value `0` in the rollover case.

**Note**: We cache the current transaction-number in the session, so that we do not need to perform the above action multiple times per transaction.

## GUID aka UUID
Traditionally XYZ-Hub used UUIDs as state-identifiers, but exposed them as strings in the JSON. Basically all known clients ignore the format of the UUID, so none of them expected to really find a UUID in it. This is good, because in Naksha we decided to change the format, but to stick with the name for downward compatibility.

The new format is called GUID (global unique identifier), returning to the roots of the Geo-Space-API. The syntax for a GUID in the PSQL-storage is:

`urn:here:naksha:guid:{storageId}:{collectionId}:{id}:{txn.year}:{txn.month}:{txn.day}:{txn.seq}:{uid}`

**Note**: This format holds all information needed for Naksha to know in which storage a feature is located, of which it only has the _GUID_. The PSQL storage knows from this _GUID_ exactly in which database table the features is located, even taking partitioning into account. The reason is, that partitioning is done by transaction start date, which is contained in the _GUID_. Therefore, providing a _GUID_, directly identifies the storage location of a feature, which in itself holds the information to which transaction it belongs to (`txn`). Beware that the transaction-number as well encodes the transaction start time and therefore allows as well to know exactly where the features of a transaction are located (including finding the transaction details itself).

## Dictionary addressing
To address global dictionaries they are addressed using a URN with the syntax: `urn:here:naksha:dict:{storage-id}:{collection-id}:{dictionary-id}`. To save space, the prefix `urn:here:naksha:dict:{storage-id}:{collection-id}` is internally not stored. It means you need to remember from where the feature came, but the XYZ namespace contains this information encoded in the `txn` and `uuid`.

## Collection-Info
All collections do have a comment on the HEAD table, which is a JSON objects with the following setup:

| Property              | Type    | Meaning                                                                                   |
|-----------------------|---------|-------------------------------------------------------------------------------------------|
| id                    | String  | The collection name again.                                                                |
| estimatedFeatureCount | long    | The estimated total amount of features in the collection.                                 |
| byteSize              | long    | The maximum size of the raw JSON of features in this collection.                          |
| compressedSize        | long    | The maximum compressed size of the JSON of features in this collection.                   |
| maxAge                | long    | The maximum amount of days of history to keep (defaults to 36,500 which means 100 years). |
| historyEnabled        | bool    | If `true`, history will be written (defaults to _true_).                                  |
| author                | String? | If not `null` and a feature is inserted without an author, this value will be used.       |
| optimalPartitioning   | bool    | If _true_, then optimal partitioning is enabled (default to _false_).                     |

## Optimal Partitioning
Naksha supports optimal partitioning. It creates a background job to do a statistic for every new version appearing in the transaction table. The statistic basically creates an optimal partitioning distribution. It will try to distribute features partitions of equals size, so that not more than 10mb of raw-json is stored in each partition (using the `byteSize` information of the _collection-info_ to decide for the optimal number of features per partition). It will perform the partitioning based upon the spatial distribution, using the `qrid`. As the `crid` is an unknown format, a spatial partitioning can't be guaranteed and `crid` is ignored in this case.

The algorithm will have two input parameters:

- **bbox** = Bounding box of the whole planet earth (initially start with the whole world)
- **MAX** = Maximal number of features per partition, calculated via `Math.min(10MiB / byteSize, 1_000_000)`.

The steps are:

1. Query all features in the **bbox**. Limit the query by the partition size (**MAX**). Only read the ids.
2. If less than **MAX** ids are returned, create the feature count, _done_.
3. Otherwise, create four sub-tasks that each get a new **bbox**, being a fourth of the current **bbox** (so top-left, top-right, bottom-left and bottom-right).
  - Every child should use an own database connection.
  - This can be optimized, by creating 4 read-replicas and use them round-robin, when we have too many features.
  - This will as well distribute the load fair between the four replicas.
4. Start all four sub-tasks, each will start over again at (1) using the new **bbox**.

This results in an optimal partitioning, basically just a special feature that is stored within the meta-table of a collection. The properties of this features should hold a property `by_qrid`, which is a map like `Map<HereRefIdPrefix, FeatureCount>`.

## Queries
In this section, we described why queries are done the way they are.

### Storage size
First, calculate the size of each database row:

| column       |   bytes | comment                                                         |
|--------------|--------:|-----------------------------------------------------------------|
| col_id       |       8 |                                                                 |
| create_at    |       8 |                                                                 |
| update_at    |       8 |                                                                 |
| author_ts    |       8 |                                                                 |
| txn_next     |       8 |                                                                 |
| txn          |       8 |                                                                 |
| ptxn         |       8 |                                                                 |
|              |      56 | 7 x 8 byte = 56 byte                                            |
| uid          |       4 |                                                                 |
| puid         |       4 |                                                                 |
| hash         |       4 |                                                                 |
| change_count |       4 |                                                                 |
| geo_grid     |       4 |                                                                 |
| flags        |       4 |                                                                 |
|              |      80 | 56 + 6 x 4 byte = 80 byte                                       |
| id           |    ~ 64 | `urn:here::here:support.Violation:some-longer-unique-id`        |
| appId        |    ~ 12 |                                                                 |
| author       |    ~ 12 |                                                                 |
|              |   ~ 168 | This is Metadata (with col_id, txn, and uid merged into rowid)! |
| tags         |   ~ 300 | We need to build an index                                       |
| geo          |   ~ 200 | We need to build an index                                       |
| geo_ref      |    ~ 30 |                                                                 |
|              |   ~ 700 |                                                                 |
| feature      | ~ 2000+ |                                                                 |

The first thing to consider is that most index queries will not be [index-only-scans](https://www.postgresql.org/docs/16/indexes-index-only-scans.html), most will be [index-bitmap-scans](https://www.postgresql.org/docs/current/indexes-bitmap-scans.html), for example, when the `geo` or `tags` columns are involved. This is because GIST rarely, and GIN never support index-only scans. Basically, we can summarize that there are three ways to use indices:

- **index-only-scan**: Does only read the index itself.
  - The most efficient, but rarely usable, and only works when all columns are in the index and the [visibility-map](https://www.postgresql.org/docs/current/storage-vm.html) is up-to-date.
  - In practise, we can forget them, except for special queries, like the index: `id ASC, txn DESC, uid DESC INCLUDE (col_id, ptxn, puid)`
  - In this situation we can read `col_id`, `txn`, and `uid` from it by `id` using index-only scan.
  - So, when we have `id` and want to get the unique caching identifier only, we can use it.
  - This as well allows to query quickly the HEAD version and then get `n` previous versions using a CTE and the `id`, `ptxn`, `puid`.
- **index-scan**: The _index-scan_ consists of two steps, first get the row location from the index, and second, gather the actual data from the HEAP.
  - The pro is, that the index warms up the buffer cache, so that reading the data is hot, if all data is in the row.
  - The con is, that all data of a row has to be loaded into memory, except for what is in TOAST (stored away).
- **index-bitmap-scans**: Scans each needed index and prepare a bitmap in memory giving the locations of table rows that are reported as matching that index's conditions. The bitmaps are then ANDed and ORed together as needed by the query. Finally, the actual table rows are visited and returned. The table rows are visited in physical order, because that is how the bitmap is laid out.
  - This requires to read all potential matching rows, so touches the HEAP.
  - The pro is, that the index warms up the buffer cache, so that reading the data is hot, if all data is in the row.
  - The con is, that all data of a row has to be loaded into memory, except for what is in TOAST (stored away).
  - _This is most common use-case for Naksha, because it is the only way to combine indices, and the only supported way for GIN, and most GIST operations!_

**As we can see, we basically always will have a filtration using indices, but ones potential rows are found, they need to be read into memory by PostgresQL.**

This means:
- When the indices are not able to reduce the cardinality to a size, that allows reading only a limited amount of data, this is useless.
- The efficiency of this concept is highly dependent on how much data need to be loaded from HEAP, which means that TOASTing columns helps.
- However, when reading the real data, TOASTing is not helpful, because more HEAP buffers need to be read!

Sadly we're not the first ones that had the idea to limit the `toast_tuple_target` to a small one, and to try to TOAST away the rest of the data, so that the index access becomes more efficient. In a nutshell, there is a thread from 2022 about [Counterintuitive behavior when toast_tuple_target < TOAST_TUPLE_THRESHOLD](https://postgrespro.com/list/thread-id/2616639). The outcome is, that currently, because the `TOAST_TUPLE_THRESHOLD` is a compile time switch, and defaults to 2 Kib, there is no useful way to reduce the `toast_tuple_target` to a smaller value than 2 KiB, we can do, but it will not have any effect.

We can conclude, we are left with two options:
- We accept that each tuple is up to 2 KiB, and only let PostgresQL move the feature out of sight (TOAST), when being bigger than 2 KiB.
- We split the table to reduce the size of a HEAP tuple to something like 256 byte or less.

Both have advantages and disadvantages. However, assuming the medium size of the data we need is 512 byte (we estimated a maximum of 700 byte), we can conclude: Either we have everything in a row, but be less than 2 KiB, or we have only 512 byte per row on the HEAP, and the feature is TOASTed, so out of line.

### Data transfer size
Assuming every row is not used only ones in a client, we should think about data transfer, because transferring all the data multiple times to the client is costly and inefficient. Therefore, when we limit the data to only `col_id`, `txn`, and `uid`, we need to transfer only 20 byte per found tuple!

This allows us to aggregate the results into a single byte-array, and compress it via `SELECT gzip(array_agg(int8send(col_id)||int8send(txn)||int4send(uid))) AS rowid FROM ...`. This returns the whole result-set as one `bytea` array, where each 20 byte hold one entry. Note that these functions use _Big Endian_ byte order. If the storage does not support native GZIP functions, we should not compress it until we wrote our own compression function in `plrust`, and installed it. We can use a JavaScript version meanwhile, but it will be slower, it will be as fast as the native GZIP "C" function, so even in Aurora or RDS we will be able to use this technique in the future!

The big advantage here is that the result-set is transferred to the client as one BLOB (**B**inary **L**arge **OB**ject). This result-set can now be cached in memory, in external storage systems, and seeking in the result-set, or creating handles from it is easy. Apart from this, the actual payload can be cached in memory, which becomes more valuable, when the same features are part of multiple result-sets. It as well allows multi-level caching of data by just transferring exactly these identifiers, and only let the client request the data, when it needs it.

Note, we can as well compress the whole metadata, by using ASCII-zero as delimiter between the strings, which must not be contained in strings. For example `SELECT gzip(string_agg(int8send(col_id)||int8send(txn)||int4send(uid)||id::bytea||'\x00'::bytea||..., '\x00'::bytea)) ...`. The resulting byte-array can be parsed by reading the static bytes first, and then everything until reaching final terminating ascii-zero, or the end of the byte-stream. Actually, each string is simply zero-terminated (like in `C`).

This is helpful to load all metadata into a binary. We can not read the whole row as pure binary, because we do not know if for example the geometry does not contain binary data, but we do not need this, because the rest of the data is anyway already compressed binary, out-of-the-box.

### What is the best result-set limit
As discussed in the section before, we need to define what is the biggest result-set we want to utilise.

As we said, that we can reduce the amount of data we transfer to 20-byte per row, and we can even compress it, this will not become our main problem. The main problem will be that PostgresQL has to read between 512 and 2048 byte per found row from the HEAP. Beware, Postgres will surely encode more than the metadata in the HEAP tuple, it will only TOAST what goes beyond 2 KiB! This means for each one million results, between 512 MiB and 2 GiB of data need to be loaded into the cache. Reaching this one million result coordinates requires around 20 MiB (20 byte * 1,000,000), but because we GZIP it, very likely ony 10 MiB. However, PostgresQL has to keep 2 GiB of buffers in memory, and in the worst case to load them into memory. Assuming the database has a connection to the storage with 40 Gbps bandwidth, it can load 5 GiB of data per second, so loading the data into cache will consume between 100ms (for 512 MiB) and 400ms (for 2 GiB).

**Therefore, this document recommends to set the absolute limit for all queries to 1,000,000 (hard-cap). If more data is needed, a special streaming mode need to be provided, that reads from multiple partitions in parallel!**

With a limit of 1,000,000 features as hard-cap, and for example 30 KiB topology, we already have a result-set of 2 GiB tuple, plus around 28 GiB uncompressed feature JSON (1,000,000 * 30 KiB). Compressed (in the storage), this normally goes down to 4 KiB per feature, so to around 4 GiB, which means 2 GiB tuple plus 4 GiB payload (6 GiB total). This clearly shows, that the data size is one of the biggest factors, and storing features as GZIP bytes is important, so that when they have to be loaded, they do not need any post-processing, like serialization from JSONB into a string.

When the result-set becomes bigger, the best option is to just read the data as a stream and to filter it. This can be done by reading ordered from the `id` index mentioned above, so what we can seek through the index. When only reading the necessary reference tuple (20 byte of `col_id`, `txn`, and `uid`) we are sure that we can perform an index-only scan. Doing so allows the client to utilise memory cache or other forms of caching.

### Property Search
As properties are stored in the `feature` they should never be searched in the database!

This will always be very bad, as it requires to decode the `feature` column. Reading this column means for the topology example result-set, to read 4 GiB of compressed JSON, to decompress it into 28 GiB of JSON, then to parse it into JSONB, and eventually to filter it. Note that indexing will only help to some degree with that, and only for _btree_ indices, because often the HEAP tuple still has to be accessed.

With [JBON](JBON.md) format we potentially can decrease this effort drastically, because [JBON](JBON.md) plus GZIP can bring down the binary size drastically, and because [JBON](JBON.md) allows to extract paths without parsing JSON, but only with seeking in binary data, it requires much less memory and CPU, and can be part of a better solution.

Extracting the JSON for search can potentially decrease the amount of data that has to be transferred to the client, yes, but it produces heavy load on the database side. Even if reducing the amount of rows by 80%, this means that instead of 6 GiB only 1.2 GiB of data have to be transferred. This still is a huge amount of data and will require, on a standard single flow 5 Gbps connection, around 2 seconds to transfer it.

Actually, by moving this processing into the client, and only initially transferring the unique identification tuples (`col_id`, `txn`, and `uid`), it is possible to reduce the data size that is transferred to 20 MiB (possibly 10 MiB compressed), and release the burden from the database, to load, decompress, parse, and filter the data.

For each tuple the client has loaded, it does not need to read it again, it can even load it from alternative sources like a redis cache or S3. By doing so, it is able to read the data using multiple connections in parallel, which lifts the 5 Gbps limit of single flow connections in AWS.

Yes, a part of the search job is now transferred to the client, everything that is not part of `tags`, `geometry` or metadata, but we expect that the biggest goal of the database is to pre-filter by exactly these attributes, so reducing the cardinality to make an efficient client side search possible.

**This is the reason, we split the search into four basic parts, search in metadata, search in geometry, search in tags, and filter by properties!**

When installing the Naksha-Hub on a [r6idn.metal](https://instances.vantage.sh/aws/ec2/r6idn.metal) instance, with 1 GiB memory, 200 Gbps network bandwidth, and 100 Gbps of EBS throughput, we can cache all this data as binaries on disk (L2 cache of the memory cache). With the right setup, we can attach 16 [EBS gp3 volumes](https://docs.aws.amazon.com/ebs/latest/userguide/general-purpose.html#gp3-ebs-volume-type), 1 TB size each, to the Hub. This cache will cost around $1800 per month (`1024 x $0.08 + 825 x $0.04 ~= $115/month x 16 = $1840`), but we can read with 12.5 GiB per second, it is restored after a reboot, and can store cache data very long term. In a nutshell, as reserved instance this costs around $3800 for the instance pus $1800 for the cache, so a total of $5600 a month. However, the search is done in the database, but the actual data is processed on the Hub itself. One such instance (with on in standby) will be enough to process all map data in real time.

### General Query Solution
All database queries are generally split into the following phases:

- Execute the query in the database, but read only the `col_id`, `txn`, and `uid` columns.
  - If a soft-cap (_limit_) was provided by the client, and no handle or ordering requested, add it to the database query.
  - Otherwise, add the hard-cap (_1,000,000_) to the database query.
  - If the database supports it, compress the result bytea-array using GZIP.
- After the client has a list of `col_id`, `txn`, and `uid` tuples, it knows which rows are needed, and can load them into a memory cache.
  - Loading can be done from any resource, but as last resource from the database itself.
  - If loading from the database is done, one connection per partition can be used, because we know which row is in which partition.
  - This allows to load data parallel from the database and bypass the 5 Gbps single flow limit.
- If additional filtering is needed, the filter can now be run above the result-set.
- Finally, if ordering was requested, the result-set (which is just a list of rows), can be done in Java.
- Eventually the search query is split into the one that was executed in the database, and the property search, and serialized into JSON, encoded into the _handle_, together with a cache-identifier.
  - If the client wants to read more data, we should ensure that it reaches the same server.
  - Another solution is to keep the rows cached in some REDIS, together with the generates result-set.
  - In worst case, the result-set can be restored from the handle by decoding the query, and repeating it.

### History Queries
The Naksha design allows history queries to directly find the correct features using index-only scans in a couple of tables. This design requires that the `txn_next` value it set for all history records. For example, looking for a specific feature in a specific version means to search for an `id` match, where the `txn` is the closest to the one requested. Assume the following states of the feature "foo":

**Note**: We partition the history based upon `txn_next`, not upon `txn`!

* `{"id":"foo", "speedLimit":10, "txn":2023_01_01_000, "txn_next":2023_01_02_000}` partition: 2023_01_02 `txn_next >= 2023_01_02_000`
* `{"id":"foo", "speedLimit":20, "txn":2023_01_02_000, "txn_next":2023_01_02_100}` partition: 2023_01_02 `txn_next >= 2023_01_02_000`
* `{"id":"foo", "speedLimit":25, "txn":2023_01_02_100, "txn_next":2023_01_04_000}` partition: 2023_01_04 `txn_next >= 2023_01_04_000`
* `{"id":"foo", "speedLimit":40, "txn":2023_01_04_000, "txn_next":2023_01_15_000}` partition: 2023_01_05 `txn_next >= 2023_01_05_000`
* `{"id":"foo", "speedLimit":50, "txn":2023_01_15_000, "txn_next":0}` partition: HEAD

This is a simplified example to basically show how the queries work. Assume we want to know the version that matches the transaction-number `2023_01_03_000` (so done on the 3'th January 2023). We expect to get back the version with **speedLimit** being `25` (`txn=2023_01_02_100`), because it is the latest version before the 3'th January, being the closest to the requested version.

```sql
SELECT txn, uid, flags FROM ${table} WHERE txn <= '2023_01_03_000' AND id = 'foo'
UNION ALL
SELECT txn, uid, flags FROM ${table}_hst WHERE txn <= '2023_01_03_000' AND txn_next > '2023_01_03_000' AND id = 'foo'
```

The first query will only look into the HEAD table, but the feature there has a `txn` value being bigger than the searched one (`2023_01_03_000`). This query should hit the `id, txn, uid` index and return nothing.

The second query will look into all history tables that can contain features for the requested `txn_next`, so into the partitions 2023_01_04 and 2023_01_05. The queries should hit the `id, txn, txn_next, uid` index of each history table.

* 2023_01_05: The version of `foo` (`speedLimit=40`) stored here **does not** match, because `txn=2023_01_04_000` is bigger than the requested `2023_01_03_000`
* 2023_01_04: The version of `foo` (`speedLimit=25`) stored here **does** match, because `txn=2023_01_02_100` is less than the requested `2023_01_03_000`

Therefore, the union of all the query returns only exactly one feature, the searched one (`foo,speedLimit=25`). This operation does use index-only scans, and is done in parallel for all potential partitions.

**Note**: The queries given above can used as well in combination with other queries, for example, when geometry is searched, this can be combined, it will no longer use an _index-only_ scan, but still an _index-scan_ or _index-bitmap-scan_, and as we just learned, this is not that bad, when not reading all the data:

```sql
SELECT txn, uid, flags FROM ${table}
WHERE txn <= '2023_01_03_000' AND id = 'foo' and ST_Intersects(geo, some_geometry)
UNION ALL
SELECT txn, uid, flags FROM ${table}_hst
WHERE txn <= '2023_01_03_000' AND txn_next > '2023_01_03_000' AND id = 'foo' and ST_Intersects(geo, some_geometry)
```

### Visibility Map
To really use _index-only_ scans, the visibility map of a table should be frozen and up-to-date. By installing the `pg_visibility` extension and reading the status we can find out the current situation, then eventually use VACUUM command to fix the situation:
```sql
CREATE EXTENSION pg_visibility;
SELECT * FROM pg_visibility_map('{table}');
VACUUM FREEZE ${table};
```

## Internal tables
For the PostgresQL implementation we follow the general concept of PostgresQL database and expose all internal data as collection, granting access to all these internal data to clients. All internal tables use the prefix `naksha~` followed by the unique identifier. All internal table can have additional indices and additional virtual columns, which are stored as part of the root properties of the feature, they will override externally set properties, therefore, clients should only use the `properties` of a transaction feature.

Note that this design allow access to internal data using the same general methods that are used for all other tables too, which simplifies testing, reliability and usage. Only when creating internal tables, some additional special code is requires that creates additional indices needed.

### Transactions Table (`naksha~transactions`)
The transaction logs are stored in the `naksha~transactions` table. Actually, the only difference to any other table is that the table is partitioned by `txn` and some columns have a different meaning:

| Column     | Type  | RO  | Modifiers | Description                                                                               |
|------------|-------|-----|-----------|-------------------------------------------------------------------------------------------|
| created_at | int8  | yes |           | `f.p.xyz->createdAt` - The time when the transaction started (`transaction_timestamp()`). |
| updated_at | int8  | yes | NOT NULL  | `f.p.xyz->updatedAt` - The time when the transaction feature was last modified.           |
| author_ts  | int8  | yes |           | Always `NULL`.                                                                            |
| txn_next   | int8  | yes |           | Always `NULL`.                                                                            |
| txn        | int8  | yes | NOT NULL  | `f.p.xyz->uuid` - Primary row identifier.                                                 |
| ptxn       | int8  | yes |           | Always `NULL`.                                                                            |
| uid        | int4  | yes |           | Always `NULL`.                                                                            |
| puid       | int4  | yes |           | Always `NULL`.                                                                            |
| fnva1      | int4  | yes |           | Always `NULL`.                                                                            |
| version    | int4  | yes |           | Always `NULL`.                                                                            |
| geo_grid   | int4  | yes |           | `f.p.xyz->grid` - HERE binary quad-key level 15 above `geo_ref`.                          |
| flags      | int4  | no  |           | Always `NULL` (TWKB).                                                                     |
| origin     | text  | no  |           | Always `NULL`.                                                                            |
| app_id     | text  | yes | NOT NULL  | `f.p.xyz->app_id`                                                                         |
| author     | text  | yes |           | `f.p.xyz->author`                                                                         |
| type       | text  | yes |           | Always `NULL`, basically translated into `naksha.Transaction`.                            |
| id         | text  | no  | NOT NULL  | `f.id` - The **uuid** of the transaction.                                                 |
| feature    | bytea | no  |           | `f` - The Geo-JSON feature in JBON, except for what was extracted.                        |
| tags       | bytea | no  |           | `f.p.xyz->tags`                                                                           |
| geo        | bytea | no  |           | `f.geometry` - The geometry of the features modified (**set by the sequencer**).          |
| geo_ref    | bytea | no  |           | `f.referencePoint` - The reference point (`ST_Centroid(geo)`, (**set by the sequencer**). |

**Notes**
- The transaction table itself is partitioned by `txn` and organized in years (`naksha~transactions$YYYY`). This is mainly helpful to purge transaction-logs and to improve the access speed as it avoids too many partitions.
- To convert from **timestamptz** to 64-bit integer as epoch milliseconds do `SELECT (EXTRACT(epoch FROM ts) * 1000)::int8`, vice versa is `SELECT TO_TIMESTAMP(epoch_ms / 1000.0)`.
- The feature contains a `seqNumber` that is used to read transactions in order.
- The feature contains a `seqTs` that is used to track when the transaction was sequenced.
- The feature contains a `collections` map that is used to hold the amount of features modified.

### Dictionaries Table (`naksha~dictionaries`)
This table stores dictionaries. It is managed by background jobs that auto-generate optimal dictionaries. The features stored in here will be bound to a collection using the property `collectionId`.

The `collectionId` property is indexed and used to bind the entries in the table to specific collections. When a collection is deleted, all entries for this collections should be deleted as well, except a **truncate** is done. For the truncate use-case only the tables are dropped and re-created, but the dictionaries are left intact.

The `type` of the feature in here is always `naksha.Dictionary`.

### Collections Table (`naksha~collections`)
This internal tables stores the configuration of all collections. The type of the features in this table is always `naksha.Collection`.

### Locks Table (`naksha~locks`) -DRAFT-
TBD

### Indices Table (`naksha~indices`) -DRAFT-
This internal tables stores the available and supported indices. Currently, no new indices can be created, but maybe in the future manual index creation will be supported. The type for the feature is always `naksha.Index`.

## Sequencer
The sequence is a background job added into the `lib-psql` that will “publish” the transactions. The job will set the `updated_at` to signal the visibility of a transaction and to generate a sequence number, storing it in the `seqNumber` property of the transaction feature. The job guarantees that the sequence number has no holes (is continues) and is unique for every transaction.

The author and application identifier must be set by the client before starting any transaction. The **author** is optional and can be _null_, but the application identifier **must not** be _null_ or an empty string. If the author is _null_, then the current author stays the author for all updates or deletes. New objects in this case do not have an author.

In the context of [HERE](https://here.com), the **author** and **app_id** are set to the **UPM user-identifier** as received from the **Wikvaya** service, therefore coming from the **UPM** (*User Permission Management*). Technically the `lib-psql` will treats all these values just as strings and does not imply any meaning to them, so the library can be used for any other authentication system too. However, in the context of [HERE](https://here.com) it is a requirement to use UPM-identifiers.

## Psql Error Codes
Operations executed on DB might fail with error. When multi-feature write operation was executed operation may fail partially, it means, that one or few features were not created/updated while rest of them succeeded. In such case response contains error details on two levels:
- result level (single error details) - good to describe global errors like "session not initiated" or "collection/table not exists"
- row/feature level (details of feature write error) - errors like "unique key violation" that don't affect other features processing

Psql Error Codes are specific to `lib-naksha-psql` library and should be mapped to domain errors as follows:

| PSQL Code | XyzError             | Example                                             |
|-----------|----------------------|-----------------------------------------------------|
| N0000     | EXCEPTION            | Uninitialized session before write operation        |
| N0001     | CONFLICT             | Requested collection (CREATE) already exists        |
| N0002     | COLLECTION_NOT_FOUND | Requested collection doesn't exist                  |
| 23514     | EXCEPTION            | Violation check, i.e. invalid schema                |
| 22023     | ILLEGAL_ARGUMENT     | null `geometry_arr` provided                        |
| 23505     | CONFLICT             | Requested feature CREATE but feature already exists |
| 02000     | NOT_FOUND            | Requested feature UPDATE but feature doesn't exist  |
| ?ANY?     | ?ANY?                | Any other code will map to XyzError with that code  |

## Performance insights
For optimal performance we want to reduce the amount of data being transferred to the database, and at the same time, reduce the amount things the database need to do (CPU load). Technically, this section discusses how we can avoid running any code in the database. We still want to have the database code being set up, but mainly to prevent that someone, executing changes using an arbitrary SQL client, breaks the storage state.

Therefore, this document describes what need to be done to have a 100% client side implementation, and what parts need to be installed as database code using [PLV8](https://plv8.github.io/), to prevent breaking modifications and protect the store.

### startSession / naksha_start_session
There are two ways to access the database, but both require a **NakshaSession**. Using the Java client, a new client-side session can be started via:

```kotlin
val env = JvmPlv8Env.get()
val session = env.startSession(conn, schema, appName, streamId, appId, author)
```

When using an arbitrary SQL client, this session need to be started server side via:

```sql
SELECT naksha_start_session(appName, streamId, appId, author);
```

Both use-cases do have a session now. For the _Java_ implementation it will disable triggers. This can be done by settings the [session_replication_role](https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE) to `replica` and later back to `origin`, like `SET SESSION session_replication_role = replica;`. The server side session relies upon the triggers to ensure history and other things. When the session ends, it will re-enable triggers via `SET SESSION session_replication_role = origin;`.

The session will furthermore, because we use row-level locks for all feature modifications, change the [timeout for locks](https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-LOCK-TIMEOUT) via `SET SESSION lock_timeout=250`. Even while this number appears to be a long time, when used for every row, it is not, because we order all queries by feature-id, and therefore we only wait for the first lock, when we have this, we are more or less sure we will get all others without waiting, because other threads will have to wait for us. This is not fully true, but basically the concept why a higher number will not harm too much.

From this point on, all further operations should be executed against the session. In the database there will be SQL functions prefixed with `naksha_`, having the same name as the corresponding counterparts in the **NakshaSession**. For example, there is a `naksha_write_collections` in SQL which actually supports exactly the same as the `session.writeCollections` function call in _Java_.

### writeCollections / naksha_write_collections
This function is used to create, update and delete collections fulfilling the standard Naksha _IStorage_ contract.

### writeFeatures / naksha_write_features
This function is used to perform bulk operations to create, update or delete features. It fulfills the Naksha _IStorage_ contract. The method automatically rollback failed operations and is always atomic.

In the success case, it will allow to decide between `commit` and `rollback`, but it is highly recommended to make this decision instant, because meanwhile it will keep locks in the database.

The write will implement these steps:

- Fetch details about the collections into which to write
  - This provides information, if partitioning is supported
- Ensure we have a transaction number (`txn()`)
- Autogenerate ids for features not yet having some
- Sort all features by partition-number and id
- Query for all features using:
  - Optimization: Do we need action?
  - Optimization: When we have an author given (not null), we do not need to fetch author, we anyway override
  - `SELECT id, txn, uid, action, version, created_at, author, author_ts FROM table WHERE id = ANY(?) FOR UPDATE NOWAIT`
  - This will acquire row level locks, but does not wait, fail when locking fails
- Modify UPSERT operations into either INSERT or UPDATE, based upon the result above
- Ensure that all INSERT operations are possible (feature does not exist), otherwise fail
- Ensure that all UPDATE and DELETE operations
  - Fail for UPDATE or DELETE operations, when there is no head state.
  - Fail for atomic UPDATE or DELETE operations, where the head state is not the expected one (_ptxn_ and _puid_ match given _txn_ and _uid_)
- ----------------------------------------------------------------------------------------
- Create batch statements with order:
  - Note: For each operation we need to calculate all states upfront!
  - (1) delete feature from del-table
  - (2) insert deleted into del-table
  - (3) upsert feature in del-table
  - (4) update feature in head, set txn_next=txn (move the feature into history)
  - (5) insert deleted into history
  - (6) insert feature into head
  - (7) purge feature from del-table
- If the feature is created, do:
  - (1) delete feature from del-table (only if del-table enabled)
  - (6) insert feature into head
- If the feature is updated, do:
  - (1) delete feature from del-table (only if del-table enabled)
  - (4) update feature in head, set txn_next=txn (only if history enabled, move the feature into history)
  - (6) insert feature into head
- If the feature is deleted, do:
  - (4) update feature in head, set txn_next=txn (only if history enabled, move the feature into history)
  - create deleted version of feature
  - (3) upsert feature in del-table (only if del-table enabled)
  - (5) insert deleted into history
- If the feature action is purge do:
  - do steps for action DELETE
  - (7) remove feature from del-table
- Execute all batches in order (1-6)

### Global dictionary training (draft)
Add support for automatic dictionary training. This is done using `pg_cron` and should run ones a day. It will check all collections and update the statistics. Additionally, it will update the `default_feature` and `default_tags` tags. These are tags that refer to the latest dictionary to be used for encoding a _feature_ or _tags_ [JBON](./JBON.md)'s. So, when encoding new features or tags for a collection, the default behavior should be to read the latest version and encode with this.

This job may as well delete no longer used dictionaries, if the corresponding history entries are deleted.

## Links
- [PostgresQL Presentations with high value](https://momjian.us/main/presentations/)
- [HERE tiling schema](https://www.here.com/docs/bundle/introduction-to-mapping-concepts-user-guide/page/topics/here-tiling-scheme.html)
- https://postgresqlco.nf/doc/en/param/session_replication_role/
- https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE
- https://foojay.io/today/a-dissection-of-java-jdbc-to-postgresql-connections-part-2-batching/
- https://github.com/PgBulkInsert/PgBulkInsert
- [Lock Management](https://www.postgresql.org/docs/current/runtime-config-locks.html)

