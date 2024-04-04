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

| Column     | Type  | RO  | Modifiers | Description                                                                                       |
|------------|-------|-----|-----------|---------------------------------------------------------------------------------------------------|
| created_at | int8  | yes |           | `f.p.xyz->createdAt` - `COALESCE(created_at, updated_at)`                                         |
| updated_at | int8  | yes | NOT NULL  | `f.p.xyz->updatedAt`                                                                              |
| author_ts  | int8  | yes |           | `f.p.xyz->authorTs` - `COALESCE(author_ts, updated_at)`                                           |
| version    | int8  | yes |           | `f.p.xyz->version` - `COALESCE(version, 1)`                                                       |
| txn        | int8  | yes | NOT NULL  | `f.p.xyz->uuid` - Primary row identifier.                                                         |
| txn_next   | int8  | yes |           | `f.p.xyz->uuid_next` - **Only in history**.                                                       |
| ptxn       | int8  | yes |           | `f.p.xyz->puuid` - Row identifier.                                                                |
| uid        | int4  | yes |           | `f.p.xyz->uuid` - Primary row identifier - `COALESCE(uid, 0)`                                     |
| puid       | int4  | yes |           | `f.p.xyz->puuid` - Row identifier                                                                 |
| geo_grid   | int4  | yes |           | `f.p.xyz->grid` - HERE binary quad-key level 15 above `geo_ref`.                                  |
| geo_type   | int2  | no  |           | The geometry type (0 = NULL, 1 = WKB, 2 = EWKB, 3 = TWKB).                                        |
| action     | int2  | yes |           | `f.p.xyz->action` - CREATE (0), UPDATE (1), DELETE (2) - `COALESCE(action, 0)`                    |
| app_id     | text  | yes | NOT NULL  | `f.p.xyz->app_id`                                                                                 |
| author     | text  | yes |           | `f.p.xyz->author` - `COALESCE(author, app_id)`                                                    |
| type       | text  | yes |           | `COALESCE(f.momType, f.type)` - The **type** of the feature, `NULL` means collection.defaultType. |
| id         | text  | no  | NOT NULL  | `f.id` - The **id** of the feature.                                                               |
| feature    | bytea | no  |           | `f` - The Geo-JSON feature in JBON, except for what was extracted.                                |
| tags       | bytea | no  |           | `f.p.xyz->tags`                                                                                   |
| geo        | bytea | no  |           | `f.geometry` - The geometry of the features.                                                      |
| geo_ref    | bytea | no  |           | `f.referencePoint` - The reference point (`ST_Centroid(geo)`) .                                   |

In the table above `f` refers to the feature root, `f.p` refers to the content of the `properties` of the feature, and `f.p.xyz` refers to the `@ns:com:here:xyz` key in the `properties` of the feature (which is called XYZ namespace for historical reason).

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

* _Year_: The year in which the transactions started (e.g. 2023).
* _Month_: The month of the year in which the transaction started (e.g. 9 for September).
* _Day_: The day of the month in which the transaction started (1 to 31).
* _Seq_: The local **sequence-number** in this day.

The local **sequence-number** is stored in a sequence named `naksha_txn_seq`. Every day starts with the sequence-number reset to zero. The final 64-bit value is combined as:

- 13-bit **year**, between 0 and 8191 {_shift-by 51_}.
- 4-bit **month**, between 1 (January) and 12 (December) {_shift-by 47_}.
- 5-bit **day**, between 1 and 31 {_shift-by 42_}.
- 42-bit **seq**uence number.

This concept allows up to 4096 billion transactions per day (between 0 and 2^42-1). It will work up until the year 8191, where it will overflow. Should there be more than 4096 billion transaction in a single day, this will overflow as into the next day and potentially into an invalid day, should it happen at the last day of a given month. We ignore this situation, it seems currently impossible.

The human-readable (Javascript compatible) representation is as a string in the format:

`urn:here:naksha:txn:{storageId}:{year}:{month}:{day}:{seq}`.

Normally, when a new unique identifier is requested, the method `naksha_txn()` will use the next value from the sequence (`naksha_txn_seq`) and verify it, so, if the year, month and day of the current transaction start-time (`transaction_timestamp()`) matches the one stored in the sequence-number. If this is not the case, it will enter an advisory lock and retry the operation, if the sequence-number is still invalid, it will reset the sequence-number to the correct date, with the _sequence_ part being set to `1`, so that the next `naksha_txn()` method that enters the lock or queries the _sequence_, receives a correct number, starting at _sequence_ `1`. The method itself will use the _sequence_ value `0` in the rollover case.

**Note**: We cache the current transaction-number in the session, so that we do not need to perform the above action multiple times per transaction.

## GUID aka UUID
Traditionally XYZ-Hub used UUIDs as state-identifiers, but exposed them as strings in the JSON. Basically all known clients ignore the format of the UUID, so none of them expected to really find a UUID in it. This is good, because in Naksha we decided to change the format, but to stick with the name for downward compatibility.

The new format is called GUID (global unique identifier), returning to the roots of the Geo-Space-API. The syntax for a GUID in the PSQL-storage is:

`urn:here:naksha:guid:{storageId}:{collectionId}:{txn.year}:{txn.month}:{txn.day}:{txn.seq}:{uid}`

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

## Indices and Queries
The indices are all created only directly on the partitions. To keep the documentation, we use the shortcut `xyz` as alias for `jsondata->'properties'->'@ns:com:here:xyz'`.

* The index on `i` is created automatically, because it is a primary key.
  * Used to search for a specific state of a feature, when given the **uuid** of the feature.
  * The **uuid** contains the transaction time, this can be used to optimize the query to only look up the HEAD partition and one specific history partition.
  * `SELECT * FROM ${table} WHERE i = i UNION ALL SELECT * FROM ${table}_hst WHERE txn >= naksha_txn($date,0) AND txn < naksha_txn($date+1, 0) AND i = $i`
* `CREATE INDEX ... USING btree (id text_pattern_ops ASC, xyz.txn DESC, xyz.txn_next DESC)`
  * Used to search features by **id**, optionally in a specific version.
* `CREATE INDEX ... USING btree (xyz.mrid text_pattern_ops ASC, xyz.txn DESC, xyz.txn_next DESC) INCLUDE id`
  * Used to search for all features using either customer or quad ref-ids.
* `CREATE INDEX ... USING btree (xyz.qrid text_pattern_ops ASC, xyz.txn DESC, xyz.txn_next DESC) INCLUDE id`
  * Used to search for features by quad-ref-ids.
  * Used to calculate the optimal partitioning.
* `CREATE INDEX ... USING gist[-sp] (geo, xyz.txn, xyz.txn_next)`
  * Search for features intersecting a geometry, optionally in a specific version.
  * We use `gist-sp` only when there are only point features in the collection.
* `CREATE INDEX ... USING btree (xyz.action text_pattern_ops ASC, xyz.author text_pattern_ops DESC, xyz.txn DESC, xyz.txn_next)`
  * Search for features with a specific action (CREATE, UPDATE or DELETE). Because the cardinality is very low, there are sub-indices.
  * Search for features that were updated by a specific author, optionally limited to a specific version.
* `CREATE INDEX ... USING gin (xyz.tags array_ops, xyz.txn, xyz.txn_next)`
  * Used to search for tags, optionally in a specific version.

### History Queries
The Naksha design allows history queries to directly find the correct features using index-only scans in a couple of tables. This design requires that the `txn_next` value it set for all history records. For example, looking for a specific feature in a specific version means to search for `jsondata->>'id'` match where the `txn` is the closest to the one requested. Assume the following states of the feature "foo":

**Note**: We partition the history based upon `txn_next`, not upon `txn`!

* `{"id":"foo", "speedLimit":10, "txn":20230101000000000, "txn_next":20230102000000000}` partition: 2023_01_02 `txn_next >= 20230102000000000`
* `{"id":"foo", "speedLimit":20, "txn":20230102000000000, "txn_next":20230102000010000}` partition: 2023_01_02 `txn_next >= 20230102000000000`
* `{"id":"foo", "speedLimit":25, "txn":20230102000010000, "txn_next":20230104000000000}` partition: 2023_01_04 `txn_next >= 20230104000000000`
* `{"id":"foo", "speedLimit":40, "txn":20230104000000000, "txn_next":20230115000000000}` partition: 2023_01_05 `txn_next >= 20230105000000000`
* `{"id":"foo", "speedLimit":50, "txn":20230115000000000, "txn_next":0}` partition: HEAD

This is a simplified example to basically show how the queries work. Assume we want to know the version that matches the transaction-number `20230103000000000` (so done on the 3'th January 2023). We expect to get back the version with **speedLimit** being `25` (`txn=20230102000010000`), because it is the latest version before the 3'th January, being the closest to the requested version.

```sql
SELECT * FROM ${table} WHERE xyz.txn <= 20230103000000000 AND jsondata->>'id' = 'foo'
UNION ALL
SELECT * FROM ${table}_hst WHERE xyz.txn <= 20230103000000000 AND txn_next > 20230103000000000 AND jsondata->>'id' = 'foo'
```

The first query will only look into the HEAD table, but the feature there has a `txn` value being bigger than the searched one (`20230103000000000`). This query should hit the `id, txn, txn_next` index and return nothing.

The second query will look into all history tables that can contain features for the requested `txn_next`, so into the partitions 2023_01_04 and 2023_01_05. The queries should as well hit the `id, txn, txn_next` index of each history table.

* 2023_01_05: The version of `foo` (`speedLimit=40`) stored here **does not** match, because `txn=20230104000000000` is bigger than the requested `20230103000000000`
* 2023_01_04: The version of `foo` (`speedLimit=25`) stored here **does** match, because `txn=20230102000010000` is less than the requested `20230103000000000`

Therefore, the union of all the query returns only exactly one feature, the searched one (`foo,speedLimit=25`). This operation does use index-only scans, and is done in parallel for all potential partition.

## Internal tables
For the PostgresQL implementation we follow the basic concept of PostgresQL database and expose all internal data as collection and grant access to all these internal data to clients.

### Transactions Table (`naksha$txn`)
The transaction logs are stored in the `naksha$txn` table. Actually, the only difference to any other table is that the table is partitioned by `txn` and some columns have a different usage:

| Column     | Type  | RO  | Modifiers    | Description                                                                               |
|------------|-------|-----|--------------|-------------------------------------------------------------------------------------------|
| created_at | int8  | yes | **NOT NULL** | `f.p.xyz->createdAt` - The time when the transaction started (`transaction_timestamp()`). |
| updated_at | int8  | yes | **NULLABLE** | `f.p.xyz->updatedAt` - The sequencing time (**set by the sequencer**).                    |
| author_ts  | int8  | yes |              | Always `NULL`.                                                                            |
| version    | int8  | yes |              | `f.p.xyz->version` - The sequencing number (**set by the sequencer**).                    |
| txn        | int8  | yes | NOT NULL     | `f.p.xyz->uuid` - Primary row identifier.                                                 |
| txn_next   | int8  | yes |              | Always `NULL`.                                                                            |
| ptxn       | int8  | yes |              | Always `NULL`.                                                                            |
| uid        | int4  | yes |              | Always `NULL`.                                                                            |
| puid       | int4  | yes |              | Always `NULL`.                                                                            |
| muid       | int4  | yes |              | Always `NULL`.                                                                            |
| geo_grid   | int4  | yes |              | `f.p.xyz->grid` - HERE binary quad-key level 15 above `geo_ref`.                          |
| geo_type   | int2  | no  |              | The geometry type (0 = NULL, 1 = WKB, 2 = EWKB, 3 = TWKB).                                |
| action     | int2  | yes |              | Always `NULL`.                                                                            |
| app_id     | text  | yes | NOT NULL     | `f.p.xyz->app_id`                                                                         |
| author     | text  | yes |              | `f.p.xyz->author`                                                                         |
| type       | text  | yes |              | Always `NULL`, basically translated into `naksha.Transaction`.                            |
| id         | text  | no  | NOT NULL     | `f.id` - The **uuid** of the transaction.                                                 |
| feature    | bytea | no  |              | `f` - The Geo-JSON feature in JBON, except for what was extracted.                        |
| tags       | bytea | no  |              | `f.p.xyz->tags`                                                                           |
| geo        | bytea | no  |              | `f.geometry` - The geometry of the features modified (**set by the sequencer**).          |
| geo_ref    | bytea | no  |              | `f.referencePoint` - The reference point (`ST_Centroid(geo)`, (**set by the sequencer**). |

**Notes**
- The transaction table itself is partitioned by `txn` and organized in years (`naksha$txn_YYYY`). This is mainly helpful to purge transaction-logs and to improve the access speed as it avoids too many partitions.
- More information about Postgres transaction numbers are available in the [documentation](https://www.postgresql.org/docs/current/functions-info.html#FUNCTIONS-PG-SNAPSHOT). We should enable [track-commit-timestamp](https://www.postgresql.org/docs/current/runtime-config-replication.html#GUC-TRACK-COMMIT-TIMESTAMP) so that `pg_commit_ts` holds information when a transaction was committed. This would make our own tracking more reliable.
- To convert from **timestamptz** to 64-bit integer as epoch milliseconds do `SELECT (EXTRACT(epoch FROM ts) * 1000)::int8`, vice versa is `SELECT TO_TIMESTAMP(epoch_ms / 1000.0)`.

### Dictionaries Table (`naksha$dictionaries`)
This table stores dictionaries. It is managed by background jobs that auto-generate optimal dictionaries. The features stored in here will be bound to a collection using the property `collectionId`.

The `collectionId` property is indexed and used to bind the entries in the table to specific collections. When a collection is deleted, all entries for this collections should be deleted as well, except a **truncate** is done. For the truncate use-case only the tables are dropped and re-created, but the dictionaries are left intact.

The `type` of the feature in here is always `naksha.Dictionary`.

### Collections Table (`naksha$collections`)
This internal tables stores the configuration of all collections. The type of the features in this table is always `naksha.Collection`.

### Indices Table (`naksha$indices`)
This internal tables stores the available and supported indices. Currently, no new indices can be created, but maybe in the future manual index creation will be supported. The type for the feature is always `naksha.Index`.

## Sequencer
The sequence is a background job added into the `lib-psql` that will “publish” the transactions. The job will set the `updated_at` to signal the visibility of a transaction and to generate a sequence number, storing it in the `version` and add it to the transaction feature as `seqNumber`. The job guarantees that the sequence number has no holes (is continues) and is unique for every transaction.

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
- [HERE tiling schema](https://www.here.com/docs/bundle/introduction-to-mapping-concepts-user-guide/page/topics/here-tiling-scheme.html)
- https://postgresqlco.nf/doc/en/param/session_replication_role/
- https://www.postgresql.org/docs/16/runtime-config-client.html#GUC-SESSION-REPLICATION-ROLE
- https://foojay.io/today/a-dissection-of-java-jdbc-to-postgresql-connections-part-2-batching/
- https://github.com/PgBulkInsert/PgBulkInsert
- [Lock Management](https://www.postgresql.org/docs/current/runtime-config-locks.html)

