# DATA - PostgresQL Implementation
This part of the documentation explains details about the reference implementation of the Naksha Data Model, based upon a PostgresQL database.

## Introduction
The PostgresQL storage-engine will map:

- `Database` to _database_ _(previously called `Storage`, which was a combination between database and storage)_
- `Catalog` to _schema_ _(previously called `Map`)_
- `Collection` to a set of _tables_
- `Feature` does not exist as standalone storage entity, each tuple is a complete feature.
- `Tuple` is one row of a _table_.

## Databases
Each database is initialized by creating an administration schema named `naksha~admin`, exposed as a `Catalog` with the same name. Within this schema, the following collections are created:

- `naksha~version~seq`: The version sequence, reset daily to `0`.
- `naksha~versions`: This collection holds all versions of the storage, actually a transaction log. Beware that when data is imported, the versions are not created, they have to be imported as well.
- `naksha~catalogs`: This collection holds all catalogs under management.
- `naksha~books`: This collection holds all global books under management.

Additionally, in the `naksha~admin` schema all functions are added. Important functions are `database_number()` and `database_id()`, which returns the hardcoded database-number and identifier. The client connecting to a database need to know these, otherwise access is denied, assuming it was done by error.

## Catalogs
A catalog is a schema, it is initialized by creating an admin-collection named `naksha~collections`, which is used to manage all collections.

## Collections
Within PostgresQL a collection is a set of database tables. All these tables are prefixed by the collection identifier. The tables are:

- `{collection}`: The _HEAD_ table. It is either a plain simple table or a partitioned table. When being partitioned, this is done as `PARTITION BY BY RANGE (fn % {partitions})`.
- `{collection}~{i}`: The _HEAD_ partitions (`PARTITION OF {collection} FOR VALUES WITH (MODULUS {parititions}, REMAINDER {i})`) with `i` being the feature-number modulo the number of partitions.
- `{collection}~hst`: The _HISTORY_ table, this is always a partitioned table, partitioned by `next_version` (`PARTITION BY BY RANGE ((next_version >> ${shift}))`). The `shift` value is configured in the collection feature and defaults to `41`, so that the history is normally the year of the next-version.
- `{collection}~hst~{y}`: The history partition for a specific year (`PARTITION OF {collection}$hst FOR VALUES FROM {y} TO {y+1}`, optionally partitioned again by `fn` (`PARTITION BY BY RANGE (fn % {partitions})`), when partitioning is enabled. The value of `y` is `next_version >> {shift}` with `shift` being configured in the collection, defaulting to `41`.
- `{collection}~hst~{y}~{i}`: The history partition of a specific year, if partitioning is enabled (`PARTITION OF {collection}$hst${y} FOR VALUES WITH (MODULUS {partitions}, REMAINDER {i})`.
- `{collection}~meta`: The meta table that stores metadata.

**Note**

The `next_version` refers to the `version` of the next tuple, which actually encodes the year, month, and day of when the transaction started. This means for _HISTORY_, that the `next_version` encodes when a tuple became history. Therefore, _HISTORY_ is partitioned by change-date, so when a state became history, not when the state was created. This is important, assuming we want to purge all history being older than 1 year, then we do not want to delete data that was created a year ago, we want to delete all changes that are older than 1 year!

The collection names provided by the client must be lower-cased and only persist out of the following characters: `^[a-z][a-z0-9_-:]{0,31}$`. This is as well specified in section [identifiers](LIB_DATA.md#identifiers) of [lib-data](LIB_DATA.md).

The dollar (`$`) and tilde (`~`) are reserved and used internally to avoid collisions, because otherwise a client could name a collection `foo` and another one `foo~hst`, which would cause a collision with the internally generated history table of `foo`.

## Table layout
All tables used in the Naksha PostgresQL implementation have the same general layout:

| Column       | Type  | Java-Constant    | External-Name | Modifiers      | Description                                                                                    |
|--------------|-------|------------------|---------------|----------------|------------------------------------------------------------------------------------------------|
| fn           | int8  | `FnCol`          | `fn`          | NOT NULL       | The feature number.                                                                            |
| version      | int8  | `VersionCol`     | `version`     | NOT NULL       | The feature version.                                                                           |
| next_version | int8  | `NextVersionCol` | `nextVersion` | NOT NULL       | The next-version does not exist in _HEAD_, because it would always be `4,503,599,627,370,495`. |
| prev_version | int8  | `PrevVersionCol` | `prevVersion` |                | The previous version, `null` if this is the first tuple.                                       |
| id           | text  | `IdCol`          | `id`          |                | The _(optional)_ unique identifier of the feature.                                             |
| data         | bytea | `DataCol`        | `data`        |                | The [JBON] encoded tuple.                                                                      |

All other columns are configurable. The queries are already explained very well in the [lib-data documentation](./LIB_DATA.md#querying-a-version). The `id` column is special in that when the _feature-number_ is a positive integer, the `id` must be `null`, and in the [GeoJSON] it is the stringified _feature-number_. Otherwise, the lower 16-bit of the _feature-number_ must match the 64-bit [MurMur3] hash above the `id`. This is as well specified in [lib-data identifiers](./LIB_DATA.md#identifiers). This means, the minimal overhead per row is 24-byte in _HEAD_, and 32-byte in _HISTORY_.

The following table shows the pre-defined columns:

| Column         | Type  | Java-Constant        | External-Name     | Modifiers      | Description                                                                                            |
|----------------|-------|----------------------|-------------------|----------------|--------------------------------------------------------------------------------------------------------|
| created_at     | int8  | `CreatedAtCol`       | `createdAt`       |                | The UNIX epoch time in millis, when the feature was created.                                           |
| updated_at     | int8  | `UpdatedAtCol`       | `updatedAt`       |                | The UNIX epoch time in millis, when this tuple was created.                                            |
| base_version   | int8  | `BaseVersionCol`     | `baseVersion`     |                | When two versions are merged by a client, the base of the two merged changes.                          |
| origin         | text  | `OriginCol`          | `origin`          |                | The origin of the feature, needed for rebase operations.                                               |
| replacement_id | text  | `ReplacementCol`     | `replacement`     |                | The replacement-id, to link multiple changes together as a replacement, needed for rebase.             |
| cluster_id     | text  | `ClusterCol`         | `cluster`         |                | The cluster-id, to link multiple features together as a logical cluster to ensure consistent maps.     |
| author         | text  | `AuthorCol`          | `author`          |                | The UNIX epoch in millis, when the author changed. Must match `updatedAt` of this or a previous state. |
| author_ts      | int8  | `AuthorTsCol`        | `authorTs`        |                | The author of the feature.                                                                             |
| app_id         | text  | `AppIdCol`           | `author`          |                | The application-identifier of the application that created this tuple.                                 |
| attachemnt     | bytea | `AttachmentCol`      | `attachment`      |                | The attachemnt.                                                                                        |
| geo            | bytea | `GeometryCol`        | `geo`             |                | The [TWKB] encoded geometry of the feature _(7 decimal digits precision)_.                             |
| ref_point      | bytea | `RefPointCol`        | `refPoint`        |                | The [TWKB] encoded reference point of the feature _(7 decimal digits precision)_.                      |
| tags           | jsonb | `TagsCol`            | `tags`            |                | Helper with customer key-values that can be indexed using a GIN index. Deprecated.                     |
| pub_number     | int8  | `PubNumberCol`       | `pubNumber`       |                | A publication number assigned by a background job to get versions in sequential publication order.     |
| pub_time       | int8  | `PubTimeCol`         | `pubTime`         |                | A publication time assigned by a background job to get versions in sequential publication order.       |

## Indices
Generally, indices are named like the table plus `${index-name}`.

In the _HEAD_ the primary key is defined as unique index above (`fn`), including `version` as `pkey`. Additionally, a secondary _(none-unique)_ index above (`version`, `fn`) is created as `skey`. All custom indices need to include `fn` and `version` so that queries are always execute as index-only scans.

In the _HISTORY_ the primary key is defined as unique index above (`fn`, `version`), including `next_version` as `pkey`. Additionally, a secondary _(none-unique)_ index above (`version`, `next_version`, `fn`) is created as `skey`. All custom indices need to start with `version`, and they need to include `fn` and `next_version`, so that individual queries are execute as index-only scans. Actually, all queries in _HISTORY_ always require to query for `version`.

All **text** columns and all **btree** indices should always be created with `COLLATE "C"` to ensure deterministic ordering in the table, long term stable determinism and default support for _like_ operation. Basically, `text_pattern_ops` is exactly doing the same thing _(should be set additionally to be explicit about this)_. This improves as well deduplication. All queries should always enforce `COLLATE "C"` too. When text is encoded, we should use `normalize(text, 'NFKC')` to ensure the same binary encoding for all values, no matter if written from Java or directly inside the database. Available collations can be queried using `SELECT * FROM pg_collation;` and `ucs_basic` may be another option, but not recommended so far.

When creating indices for columns being unique, [disabling deduplication](https://www.postgresql.org/docs/18/btree.html#BTREE-DEDUPLICATION) should be done. Actually, because deduplication does not work with _include_, and we need it basically for every index, we should simply always turn it off to not pay any performance, so we will always use `WITH (deduplicate_items=OFF)`.

## Data Management
People are very well able to make a decision if data is expected to be big or not. However, they struggle when they have to make decisions how many partitions should be used. To simplify this, we made the following assumptions:

- We are either on AWS Aurora, on AWS EC2, or on a local system
- Single connections are normally limited to 5 Gbps, which is around 625 MiB/s peak.
  - This is true in AWS and in most local network stacks.
- When partitioning is enabled, and we're not on AWS Aurora, we should create distinct tablespaces for each partition.
  - We should split as well between _HEAD_ and _HISTORY_.
- At AWS, when using EBS, we know that we should stick to:
  - a) `gp3` volumes, which provides 3000 IOPS and 125 MiB/s throughput for $0.08/GB/month.
  - b) ephemeral SSD, if a temporary storage is needed

Considering these values, we need to compile PostgresQL with 32,768 byte per page, when running our own PostgresQL cluster on EC2 or local, so we get a minimum of 96,000 KiB/s _(93.75 MiB/s)_ throughput for the 3000 IOPS. We have four general sections we need to optimize:

- WAL logs
- _HEAD_
- _HISTORY_
- Temporary data

Considering that our goal is to store up to 2 billion tuple per collection, and we want to bulk load that quickly, we need a partitioning that supports this max usage. However, we do not want to make things too complicated to set up, and we want to keep cost for storage low. Generally, we have three distinct setups:

- On AWS Aurora
- On EC2 Instance using docker container
- On local system using docker container

And we have two use cases:

- For temporary storage, data loss is acceptable
- For consistent storage, data loss is not acceptable

We will simplify the whole set up by having a two configuraiton entries:
- `storage`: `docker` or `aurora` _(default)_ 
- `persistence`: `temporary` or `consistent` _(default)_

### Aurora - `aurora`
The default storage configuration, when the user does not select any, is the Aurora storage type. We can not configure the disk layout, WAL log, or any other storage related thing. Therefore, on `auto` storage we simply operate not using tablespaces.

### Docker/EBS - `docker`
We first have to think about the guaranteed throughput we want. When we use 16 partitions, we get 16 * 93.75 MiB/s, resulting in ~1,500 MiB/s. _(~1.46 TiB/s)_. This allows to import 1 TiB of data in around one second, considering overhead. This seems to be fast enough for all our use cases. The generaly data layout for our docker container therefore is:

- `/pgdata/main` for `PGDATA` - `pg_default`
- `/pgdata/head` 
  - `/pgdata/head/main` for not partitioned _HEAD_ - `ts_head`
  - `/pgdata/head/{i}` for 16 _HEAD_ partitions - `ts_head_{i}`
- `/pgdata/hst`
  - `/pgdata/hst/main` for not partitioned _HISTORY_ - `ts_hst`
  - `/pgdata/hst/{i}` for 16 _HISTORY_ partitions - `ts_hst_{i}`
- `/pgdata/wal`
- `/pgdata/tmp` for temp spill files - `ts_tmp`

With `i` being a value between `0` and `15`. We often copy from _HEAD_ to _HISTORY_, therfore, we want that each thread uses a dedicated physical storage for _HEAD_ and _HISTORY_ to avoid that the load of one partition impacts the others.

The WAL log now becomes a bottleneck, when we keep it as is. So we need to mount it to a RAID 0, persisting out of yet another bunch of EBS volumes. When bulk loading, we know that the WAL log can grow fast. We will soft-limit the WAL log to 768 GiB of data, and mount it into a RAID 0 volume with 16 EBS volumes, each being a 64 GiB `gp3` EBS volumes, with around ~1500 MiB/s throughput and a total of 1,024 GiB of disk space. This leaves enough buffer, because the WAL log maximum size is only a soft-cap. We will use `mdadm` to link the EBS volumns together into one big WAL log volume using RAID 0.

This setup allows us to have only one docker container for all environments, including the ephemeral setup, which simply uses `auto` storage type, so the docker works as well on the developer laptop. For the consistent store on EC2, we will need the following volumes:

- `{database-id}_main` - A single EBS volume. Even when partitions are used, should be at least 256 GiB `ext4` volume.
- `{database-id}_wal_{i}` - 16 `gp3` EBS volumes, each being 64 GiB _(therefore 1,024 MiB total, with configure max WAL log size of 768 GiB)_. This costs $81.92/month.
- `{database-id}_head_{i}` - 16 `gp3` EBS volumes, each being 1/8'th of the expected maximum data size, summing to two times the expected data size.
- `{database-id}_hst_{i}` - 16 `gp3` EBS volumes, each being 1/4'rd of the expected maximum data size, summing to four times the expected data size.

This totals to `1280 GiB + 6 times {expected data size in GiB}`. On a _local_ setup, we simply mount `pgdata` into a single directory with `storage` set to `auto`.

For the EC2 Instance use-case this means, that each database persists either out of a local RAID 0, or 49 EBS volumes for consistent data. For the EBS use-case, we will need to reserve 1280 MiB + 6 times the expected data size. Assuming we expect 2 TiB of data, we need to reserve EBS volumes with a total size of `1280 MiB + 6 * 2048 MiB = 7424 MiB`. At cost of \$0.08/GiB/month this sums to ~\$600/month. Adding the needed [r8idn.32xlarge](https://instances.vantage.sh/aws/ec2/r8idn.32xlarge?currency=USD) instance with `$15.006/h on demand * 730h/month = 10,954.38 $/month` cost, this sums to $15,600/month. This would be the cost for a consistent store. For temporary stores the cost are just the instance for the time it is needed, which will be a fixed cost of `$15/hour`. A cheaper alternative can be [r6idn.x32large](https://instances.vantage.sh/aws/ec2/r6idn.32xlarge?currency=USD) instance.

Backups can be made using AWS CLI tool, like `aws ec2 create-snapshots --instance-specification InstanceId=i-xxxxxxxx`. This will make a snapshot of all EBS volumes. It should do the snapshot of all volumes in parallel and as each volume is relatively small, it should be done in a few seconds.

## Partitioning
Generally the partitioning is optimized for bulk operations. Technically, each partition of the _HEAD_ table will be placed into an own dedicated tablespace, so an own physical storage. This allows multiple clients to do parallel modifications, when they understand where a feature is stored. This design physically isolates partitions from each other and therefore allow parallel clients (when they directly write into partitions) to work parallel without any interaction between them. As clarified before, we either have no partitions or 16 partitions, so enabling partitioning causes always to get exactly 16 partitions with PostgresQL implementation. When the client requests more than 1 partition, the PostgresQL client will set partitions to 16, no mater if the client provided 2, 10, or 1000 as configuration value.

The history is partitioned on two levels. First on time (based upon `version_next`). The reason for this is that we want to quickly purge history that is too old. This way we can purge all the history that is older than n-years, by just dropping the corresponding history table, instead that we need to perform an actual query on the history. As the history is always INSERT only, this allows us to fill all rows to 100% and allows us to compact the past year using a **FULL VACUUM**, preserving it.

The second level of the partitioning of the history is in the same way as the _HEAD_ table. If we would not do this, we would basically break the isolation of the _HEAD_ table, because then any `UPDATE` on a _HEAD_ feature would require the corresponding client to touch a _HISTORY_ table, shared with other clients working in parallel. This would share the bandwidth of the underlying physical storage and break the isolation. By storing the _HISTORY_ this way, all clients that have started a transaction at the same year, will write into the same _HISTORY_ root table. However, each feature they process is isolated in the _HEAD_ and the same way in the _HISTORY_, with an own dedicated physical storages for each of the two tables. Eventually this means that each client gets 100% of the disk IO for the _HEAD_ partition, and 100% of the disk IO for the _HISTORY_ partition. So, it can utilize 100% of the available disk IO to perform the action without adding any bottleneck to other clients using other partitions. This concept allow to load data in parallel using 16 clients, when each client imports only into a dedicated partition.

## Versions
All features stored by the Naksha storage engine are part of a version. The version itself is a normal feature, persisted in the internal collection `naksha~versions`. This table is always partitioned with _HISTORY_ being disabled, because of the huge amount of data that normally need to be handled. The feature-number of a version is actually the version divided by 4 aka shifted right by 2, so without `action`. The layout of a version is explained in [lib-data versioning](LIB_DATA.md#versioning).

## Indexing
When the geometry or tags are involved, the queries will never be [index-only-scans](https://www.postgresql.org/docs/16/indexes-index-only-scans.html). For all GIST or GIN indices, and whenever indices are combined, PostgresQL falls back to [index-bitmap-scans](https://www.postgresql.org/docs/current/indexes-bitmap-scans.html). In a nutshell, this means:

- **index-only-scan**: Does only read the index itself.
  - The most efficient, but only works when all read columns are in the index and the [visibility-map](https://www.postgresql.org/docs/current/storage-vm.html) is up-to-date.
  - They are the reason we always add `fn` and `version` into the index.
  - These are preferable over all other scans, as they are by far the fastest we can do.
- **index-scan**: The _index-scan_ consists of two steps, first get the row location from the index, and second, gather the actual data from the _HEAP_.
  - The pro is, that the index warms up the buffer cache, so that reading the data is hot, if all data is in the row.
  - The con is, that all data of a row has to be loaded into memory, except for what is in TOAST (stored away).
  - Note that `geo` and `tags` will always need to be loaded, so we must not TOAST them.
  - We mostly never access the feature binary or attachment, so we should always TOAST them.
- **index-bitmap-scans**: Scans each needed index and prepare a bitmap in memory giving the locations of table rows that are reported as matching that index's conditions. The bitmaps are then ANDed and ORed together as needed by the query. Finally, the actual table rows are visited and returned. The table rows are visited in physical order, because that is how the bitmap is laid out.
  - This requires to read all potential matching rows, so touches the _HEAP_.
  - The pro is, that the index warms up the buffer cache, so that reading the data is hot, if all data is in the row.
  - The con is, that all data of a row has to be loaded into memory, except for what is in TOAST (stored away).
  - **This is a common use-case for Naksha, because it is the only way to combine indices, and the only supported way for GIN, and most GIST operations!**

As we can see, we basically often will have a filtration using indices, but ones potential rows are found, they often need to be read into memory by PostgresQL. This makes it highly preferable to use optimized btree indices for the client's performance critical indices.

In a nutshell:
- When the indices are not able to reduce the cardinality to a size, that allows reading only a limited amount of data, the index is useless.
- The efficiency of this concept is highly dependent on how much data need to be loaded from _HEAP_, which means that TOASTing columns helps.
- However, when reading the real data, TOASTing is not helpful, because more _HEAP_ buffers need to be read!

Sadly we're not the first ones that had the idea to limit the `toast_tuple_target` to a small value, and to try to TOAST away the rest of the data, so that the index access becomes more efficient. In a nutshell, there is a thread from 2022 about [Counterintuitive behavior when toast_tuple_target < TOAST_TUPLE_THRESHOLD](https://postgrespro.com/list/thread-id/2616639). The outcome is, that currently, because the `TOAST_TUPLE_THRESHOLD` is a compile time switch, and defaults to 2 Kib, there is no useful way to reduce the `toast_tuple_target` to a smaller value than 2 KiB. We can do, but it will not have any effect.

We can conclude, we are left with two options:
- We accept that each tuple is up to 2 KiB, and only let PostgresQL move the feature out of sight (TOAST), when being bigger than 2 KiB.
- We split the table to reduce the size of a HEAP tuple to something like 256 byte or less.

Both have advantages and disadvantages. However, assuming the medium size of the data we need is 512 byte (we estimated a maximum of 700 byte), we can conclude: Either we have everything in a row, but be less than 2 KiB, or we have only 512 byte per row on the HEAP, and the feature is TOASTed, so out of line.

**Effectively, the whole system becomes faster and better with more, but smaller objects.**

### Data transfer size
Assuming every row is not used only ones in a client, we should think about data transfer, because transferring all the data multiple times to the client is costly and inefficient. Therefore, when we limit the data to only `fn` and `version`, we need to transfer only 16 byte per found tuple!

### What is the best result-set limit
As discussed in the section before, we need to define what is the biggest result-set we want to utilize.

As we said, that we can reduce the amount of data we transfer to 16-byte per row, this will not become our main problem. The main problem will be that PostgresQL has to read between 512 and 2048 byte per found row from the _HEAP_. Apart from this, the _HEAP_ is organized in pages, each being between 4 KiB and 32 KiB, defaulting to 8 KiB, and only full pages can be loaded to cache. So even with Postgres TOASTing what goes beyond 2 KiB, the worst case is that each tuple is stored in an own page! This means for every million results, the worst case data size _(with 8 KiB pages)_ is around 7.8 GiB, assuming every tuple is located in an own page _(for 32 KiB page size this would be 30.5 GiB)_. The best we can possibly expect is something like only 512 MiB, when each tuple is around 512 byte, and all pages are completely filled. Loading one million result coordinates requires around 16 MiB (16 byte * 1,000,000). However, PostgresQL has to keep up to 8 GiB of buffers in memory, and in the worst case to load them into memory. Assuming the database has a connection to the storage with 40 Gbps bandwidth, it can load 5 GiB of data per second, so loading the data into cache will consume between 100ms (for 512 MiB) and 1600ms (for 8 GiB).

**Therefore, this document recommends to set the absolute limit for all queries to 1,000,000 (hard-cap). If more data is needed, a special streaming mode need to be provided, that reads from multiple partitions in parallel!**

With a limit of 1,000,000 tuple as hard-cap, and for example 30 KiB topology, we already have a result-set of 2 GiB tuple, plus around 28 GiB uncompressed feature JSON (1,000,000 * 30 KiB). Compressed (in the storage), this normally goes down to 4 KiB per feature, so to around 4 GiB, which means 2 GiB tuple plus 4 GiB payload (6 GiB total). This clearly shows, that the data size is one of the biggest factors, and storing features as GZIP bytes is important, so that when they have to be loaded, they do not need any post-processing, like serialization from JSONB into a string.

When the result-set becomes bigger, the best option is to just read the data as a stream and to filter it. This can be done by reading ordered from the primary key mentioned above, so what we can seek through the index. When only reading the necessary reference tuple _(16 byte, `fn` and `version`)_ we are sure that we can perform an index-only scan. Doing so allows the client to utilize memory cache or other forms of caching.

### Member Search
As properties are stored in the `feature` they should never be searched in the database!

This will always be very bad, as it requires to decode the `feature` column. Reading this column means for the topology example result-set, to read 4 GiB of compressed JSON, to decompress it into 28 GiB of JSON, then to parse it into JSONB, and eventually to filter it. Note that indexing will only help to some degree with that, and only for _btree_ indices, because often the HEAP tuple still has to be accessed.

With [JBON](lib_data.md) format we potentially can decrease this effort drastically, because [JBON](lib_data.md) plus GZIP can bring down the binary size drastically, and because [JBON](lib_data.md) allows to extract paths without parsing JSON, but only with seeking in binary data, it requires much less memory and CPU, and can be part of a better solution.

Extracting the JSON for search can potentially decrease the amount of data that has to be transferred to the client, yes, but it produces heavy load on the database side. Even if reducing the amount of rows by 80%, this means that instead of 6 GiB only 1.2 GiB of data have to be transferred. This still is a huge amount of data and will require, on a standard single flow 5 Gbps connection, around 2 seconds to transfer it.

Actually, by moving this processing into the client, and only initially transferring the unique identification tuples (`fn` and `version`), it is possible to reduce the data size that is transferred to 16 MiB, and release the burden from the database, to load, decompress, parse, and filter the data.

For each tuple the client has loaded, it does not need to read it again, it can even load it from alternative sources like a redis cache or S3. By doing so, it is able to read the data using multiple connections in parallel, which lifts the 5 Gbps limit of single flow connections in AWS.

Yes, a part of the search job is now transferred to the client, everything that is not part of `tags`, `geometry` or metadata, but we expect that the biggest goal of the database is to pre-filter by exactly these attributes, so reducing the cardinality to make an efficient client side search possible.

**This is the reason, we split the search into four basic parts, search in metadata, search in geometry, search in tags, and filter by properties!**

## Visibility Map
To really use _index-only_ scans, the visibility map of a table should be frozen and up-to-date. By installing the `pg_visibility` extension and reading the status we can find out the current situation, then eventually use VACUUM command to fix the situation:

```sql
CREATE EXTENSION pg_visibility;
SELECT * FROM pg_visibility_map('{table}');
VACUUM FREEZE ${table};
```

## Internal tables
For the PostgresQL implementation we follow the general concept of PostgresQL database and expose all internal data as collection, granting access to all these internal data to clients. All internal tables use the prefix `naksha~` followed by the unique identifier. All internal table can have additional indices and additional virtual columns, which are stored as part of the root properties of the feature, they will override externally set properties, therefore, clients should only use the `properties` of a transaction feature.

Note that this design allow access to internal data using the same general methods that are used for all other tables too, which simplifies testing, reliability and usage. Only when creating internal tables, some additional special code is requires that creates additional indices needed.

### Versions Table (`naksha~versions`)
The version logs are stored in the `naksha~versions` table. Actually, the only difference to any other table is that the feature-number of the stored features matches the `version` itself, shifted right by 2.

Within _HEAD_ searches are always done using only `version`, because `next_version` is always in [HEAD] state _(intrinsically)_. In _HISTORY_ all queries need to contain `version` and `next_version` for upper-bounds queries. The details are already clarified in [lib-data versioning](LIB_DATA.md#versioning) section.

The `type` of the feature in here is always `naksha.Version`.

### Books Table (`naksha~books`)
This table stores books _(previously called dictionaries)_. It is managed by background jobs that auto-generate optimal book.

The `type` of the feature in here is always `naksha.Book`.

### Collections Table (`naksha~collections`)
This internal tables stores the configuration of all collections. The type of the features in this table is always `naksha.Collection`.

### Locks Table (`naksha~locks`) -DRAFT-
TBD

### Indices Table (`naksha~indices`) -DRAFT-
TODO: Finish me!

This internal tables stores the available and supported indices. Currently, no new indices can be created, but maybe in the future manual index creation will be supported. The type for the feature is always `naksha.Index`.

The idea is, we still have tags, but better use a special map in collection to map certain properties into special purpose columns. So, we add a mapping to the collection `map`, where the key is the columns name (e.g. `cv0`) and the value the property path (e.g. `.properties.name`). This removes the property from the payload, and copy their value into the specific column, which reduces the size of the column and allows an efficient property search for this property. So internally, a reverse map will be generated that maps `.properties.name` to `cv0`.

**Notes**
- The transaction table itself is partitioned by `txn` and organized in years (`naksha~transactions$YYYY`). This is mainly helpful to purge transaction-logs and to improve the access speed as it avoids too many partitions.
- To convert from **timestamptz** to 64-bit integer as epoch milliseconds do `SELECT (EXTRACT(epoch FROM ts) * 1000)::int8`, vice versa is `SELECT TO_TIMESTAMP(epoch_ms / 1000.0)`.
- The feature contains a `seqNumber` that is used to read transactions in order.
- The feature contains a `seqTs` that is used to track when the transaction was sequenced.
- The feature contains a `collections` map that is used to hold the amount of features modified.

## Tuple Loading
To query for tuples, so we can send an array of tuple-numbers to the database and to load all of them at ones, with potentially thousands of tuple-numbers in one query, we have to use the following CTE:

```sql
WITH lookup AS (
  SELECT (
      (get_byte(b,0)::bigint << 56) |
      (get_byte(b,1)::bigint << 48) |
      (get_byte(b,2)::bigint << 40) |
      (get_byte(b,3)::bigint << 32) |
      (get_byte(b,4)::bigint << 24) |
      (get_byte(b,5)::bigint << 16) |
      (get_byte(b,6)::bigint << 8)  |
       get_byte(b,7)::bigint
    ) AS fn, (
      (get_byte(b,8)::bigint << 56) |
      (get_byte(b,9)::bigint << 48) |
      (get_byte(b,10)::bigint << 40)|
      (get_byte(b,11)::bigint << 32)|
      (get_byte(b,12)::bigint << 24)|
      (get_byte(b,13)::bigint << 16)|
      (get_byte(b,14)::bigint << 8) |
       get_byte(b,15)::bigint
    ) AS version
  FROM unnest($1::bytea[]) AS t(b)
)
SELECT t.*
FROM table t
JOIN lookup l
ON (t.fn, t.version) = (l.fn, l.version);
```

## Sequencer
The sequence is a background job added into the `lib-psql` that will “publish” versions. The internal versions table has the custom column `pub_number` and `pub_time`, storing the publication sequence number and publication time. The sequencer job will set the `pub_time` to signal the visibility of a version, and generate a sequence number, storing it in the `pub_number` in the `naksha~versions` collection. The job guarantees that the sequence number has no holes (it is continues) and is unique for every version.

## Psql Error Codes
TODO: Update

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

## Writing
All collections will have a trigger attached the prevents modification. For the _Java_ code to still do modifications, it will disable all triggers. This can be done by settings the [session_replication_role](https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE) to `replica` and later back to `origin`, like `SET SESSION session_replication_role = replica;`.

The session will furthermore, because we use row-level locks for all feature modifications, change the [timeout for locks](https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-LOCK-TIMEOUT) via `SET SESSION lock_timeout=250`. Even while this number appears to be a long time, when used for every row, it is not, because we order all queries by feature-number, and therefore we only wait for the first lock, when we have this, we are more or less sure we will get all others without waiting, because other threads will have to wait for us. This is not fully true, but basically the concept why a higher number will not harm too much.

Writing will implement these steps:

- Gather the `Collection` into which to write
  - This provides information, like if partitioning is supported, if history is enabled, ...
- Ensure we have a version for the current session, if not, acquire one
- Autogenerate feature-numbers for features not yet having some
- Sort all features by partition-number, feature-number
  - **This is very important to avoid deadlocks!**
- Technically we now need to do, in order:
  - Select the current _HEAD_ state of all features to be modified.
  - Copy the _HEAD_ state into _HISTORY_, setting `next_version` to the current version.
  - Insert all tuple that do not exist yet in _HEAD_ table with `version` set to the current version, `prev_version` set to `null`.
    - This includes `DELETE` entries for deleted and purged objects.
    - This should return `action=INSERT`, `fn`, `version`, and `prev_version` as `null`.
  - Update all _HEAD_ rows, copy `version` into `prev_version` and set `version` to current version.
    - This includes `DELETE` entries for deleted and purged objects.
    - This should return `action=UPDATE`, `fn`, `version`, and `prev_version`.
  - Finally, for those that should be purged.
    - Copy the _HEAD_ state into _HISTORY_.
    - Delete the _HEAD_ state.
    - Return `action=PURGE`, `fn`, `version`, and `prev_version`.
  - Eventually, the whole CTE selects the `action`, `fn`, `version`, and `prev_version` from all sub-queries _(UNION ALL)_.
  - The client now compares if all features are modified, and their `version` and `prev_version` is in the expected state.
    - If it is, the client can commit.
    - Otherwise, it needs to roll back and return a conflict for those objects with wrong version.

All the above actions can be implemented using a single CTE.

## Links
- [PostgresQL Presentations with high value](https://momjian.us/main/presentations/)
- [HERE tiling schema](https://www.here.com/docs/bundle/introduction-to-mapping-concepts-user-guide/page/topics/here-tiling-scheme.html)
- https://postgresqlco.nf/doc/en/param/session_replication_role/
- https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE
- https://foojay.io/today/a-dissection-of-java-jdbc-to-postgresql-connections-part-2-batching/
- https://github.com/PgBulkInsert/PgBulkInsert
- [Lock Management](https://www.postgresql.org/docs/current/runtime-config-locks.html)
