# PostgresQL Reference Implementation

This part of the documentation explains details about the reference Naksha Storage-API implementation based upon PostgresQL database.

## Basics

The admin database of Naksha will (currently) be based upon a PostgresQL database. For users, we will allow the usage of the same implementation for their collections that the Naksha-Hub itself uses to store administrative features.

## Collections

Within PostgresQL a collection is a set of database tables. All these tables are prefixed by the `collection` identifier. The tables are:

- `{collection}`: The HEAD table (`PARTITION BY LIST naksha_partition_number(naksha_feature_id(feature))`) with all features in their live state. This table is the only one that should be directly accessed for manual SQL queries and has triggers attached that will ensure that the history is written accordingly.
- `{collection}_p[n]`: The 256 HEAD partitions (`PARTITION OF {collection} FOR VALUES WITH (MODULUS 256, REMAINDER {n})`), beware that the name of the partition is taken from `naksha_partition_id('{collection}')`, which will return a string of the length 3 (`000` to `255`).
- `{collection}_del`: The HEAD deletion table holding all features that are deleted from the HEAD table. This can be used to read zombie features (features that have been deleted, but are not yet fully unrecoverable dead).
- `{collection}_hst`: The history table, this is partitioned table, partitioned by `txn_next`.
- `{collection}_hst_{YYYY}_{MM}_{YY}`: The history partition for a specific day (`txn_next >= naksha_txn('2023-09-29',0) AND txn_next < naksha_txn('2023-09-30',0)`.
- `{collection}_meta`: The meta-data sub-table, used for arbitrary meta-information and statistics.

**Notes:**

- The collection names must be lower-cased and only persist out of the following characters: `^[a-z][a-z0-9_-:]{0,31}$`.
- The partitioning in the history is based upon `txn_next`. The reason is, because `txn_next` basically is the time when the change was moved into history. The `txn` is the time when a state was originally created. So, at a first view it might be more logical to partition by `txn`, so when a state was created. However, by doing so we run into one problem, assume we decide to keep the history for one year, what do we want? We want to be able to revert all changes that have been done in the last year. Assume a feature is created in 2010 and then stays unchanged for 13 years. In 2023 this feature is modified. If we partition by the time when the state was created, this feature would be directly garbage collected, because it is in a partition being older than one year. However, this is not what we want! We want this feature to stay here until 2024, which means, we need to add it into the partition of `txn_next`, which will link to the today state, so the 2023 state, and therefore it will be added into the 2023 partition.
- Even while the partitioning based upon `txn_next` is first counter-intuitive, it still is necessary.
- The partitioning of the HEAD table can be used to bulk-load data. As the history is not written for the first insert, it is possible to load all data into 16 tables in parallel and then to index them later in parallel and eventually to simply add them together to the HEAD table. This does not break the history, nor does it require any trigger, when the client manually performs the necessary steps to create a valid XYZ-namespace, instead of relying on the trigger.

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

The human-readable (Javascript compatible) representation is as a string in the format `{storageId}:txn:{year}:{month}:{day}:{seq}:0`.

Normally, when a new unique identifier is requested, the method `naksha_txn()` will use the next value from the sequence (`naksha_txn_seq`) and verify it, so, if the year, month and day of the current transaction start-time (`transaction_timestamp()`) matches the one stored in the sequence-number. If this is not the case, it will enter an advisory lock and retry the operation, if the sequence-number is still invalid, it will reset the sequence-number to the correct date, with the _sequence_ part being set to `1`, so that the next `naksha_txn()` method that enters the lock or queries the _sequence_, receives a correct number, starting at _sequence_ `1`. The method itself will use the _sequence_ value `0` in the rollover case.

**Note**: We cache the current transaction-number in the session, so that we do not need to perform the above action multiple times per transaction.

## GUID [`uuid`]

Traditionally XYZ-Hub used UUIDs as state-identifiers, but exposed them as strings in the JSON. Basically all known clients ignore the format of the UUID, so none of them expected to really find a UUID in it. This is good, because in Naksha we decided to change the format, but to stick with the name for downward compatibility.

The new format is called GUID (global unique identifier), returning to the roots of the Geo-Space-API. The syntax for a GUID in the PSQL-storage is:

`{storageId}:{collectionId}:{txn.year}:{txn.month}:{txn.day}:{txn.seq}:{uid}`

**Note**: This format holds all information needed for Naksha to know in which storage a feature is located, of which it only has the _GUID_. The PSQL storage knows from this _GUID_ exactly in which database table the features is located, even taking partitioning into account. The reason is, that partitioning is done by transaction start date, which is contained in the _GUID_. Therefore, providing a _GUID_, directly identifies the storage location of a feature, which in itself holds the information to which transaction it belongs to (`txn`). Beware that the transaction-number as well encodes the transaction start time and therefore allows as well to know exactly where the features of a transaction are located (including finding the transaction details itself).

## Global dictionaries (`naksha_global`)
As the PostgresQL code switched to [JBON](./JBON.md) encoding, a table for the global dictionaries is needed with the following layout:

| Column | Type  | Modifiers                              | Description                              |
|--------|-------|----------------------------------------|------------------------------------------|
| id     | text  | PRIMARY KEY NOT NULL                   | The unique identifier of the dictionary. |
| data   | bytea | NOT NULL                               | The dictionary JBON data.                |

## Collection table layout

The table layout for all tables:

| Column     | Type  | RO  | Modifiers | Description                                                        |
|------------|-------|-----|-----------|--------------------------------------------------------------------|
| txn_next   | int8  | yes | NOT NULL  | `f.p.xyz->txn_next` - Only needed in history                       |
| txn        | int8  | yes | NOT NULL  | `f.p.xyz->uuid` - Primary row identifier                           |
| uid        | int4  | yes | NOT NULL  | `f.p.xyz->uuid` - Primary row identifier                           |
| version    | int4  | yes | NOT NULL  | `f.p.xyz->version`                                                 |
| created_at | int8  | yes | NOT NULL  | `f.p.xyz->createdAt`                                               |
| updated_at | int8  | yes | NOT NULL  | `f.p.xyz->updatedAt`                                               |
| author_ts  | int8  | yes | NOT NULL  | `f.p.xyz->authorTs`                                                |
| action     | int2  | yes | NOT NULL  | `f.p.xyz->action` - CREATE (0), UPDATE (1), DELETE (2)             |
| geo_type   | int2  | no  | NOT NULL  | The geometry type (0 = NULL, 1 = WKB, 2 = EWKB, 3 = TWKB).         |
| puid       | int4  | yes |           | `f.p.xyz->puuid`                                                   |
| ptxn       | int8  | yes |           | `f.p.xyz->uuid`                                                    |
| author     | text  | yes |           | `f.p.xyz->author`                                                  |
| app_id     | text  | yes |           | `f.p.xyz->app_id`                                                  |
| geo_grid   | text  | yes | NOT NULL  | `f.p.xyz->grid`                                                    |
| id         | text  | no  |           | `f.id` - The **id** of the feature.                                |
| tags       | bytea | no  |           | `f.p.xyz->tags`                                                    |
| geo        | bytea | no  |           | `f.geometry` - The geometry of the features.                       |
| feature    | bytea | no  | NOT NULL  | `f` - The Geo-JSON feature in JBON, except for what was extracted. |

The **xyz** namespace is split into parts, to avoid unnecessary work for triggers and functions. Therefore, the **tags** columns contains the tags extracted from the XYZ namespace, while the rest of the XYZ namespace, managed by Naksha, is stored in individual columns. The **txn_next** is as well managed internally and only used for the history (actually, this is the only value that need to be adjusted, when a row is moved into history). All columns should be merged together into the Geo-JSON feature. This merging is left to the client and not part of the database code.

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

## Transaction Table (`naksha_txn`)

The transaction logs are stored in the `naksha_txn` table. Each transaction persists out of **signals**, grouped by the transaction-number (`tnx`). Note that there is a `naksha_txn_uid_seq` encoded into the `txn`. The table layout is:

| Column     | Type        | Modifiers            | Description                                                                                |
|------------|-------------|----------------------|--------------------------------------------------------------------------------------------|
| uid        | int8        | PRIMARY KEY NOT NULL | Primary unique signal identifier.                                                          |
| txn        | int8        | NOT NULL             | The transaction-number.                                                                    |
| ts         | timestamptz | NOT NULL             | The time when the transaction started (`transaction_timestamp()`).                         |
| xact_id    | int8        | NOT NULL             | The PostgresQL transaction id. Can be used to detect consistency (`pg_current_xact_id()`). |
| app_id     | text        |                      | The application identifier used for the transaction.                                       |
| author     | text        |                      | The author used for the transaction.                                                       |
| details    | bytea       |                      | The details of what happened as part of this transaction (`XyzTxDetails`).                 |
| seq_id     | int8        |                      | The sequencing identifier, set by the sequencer of `lib-naksha-psql`.                      |
| seq_ts     | timestamptz |                      | The sequencing time, set by the sequencer of `lib-naksha-psql`.                            |
| version    | int8        |                      | An arbitrary version tag that can be set.                                                  |
| attachment | bytea       |                      | An arbitrary attachment as JBON feature.                                                   |

**Note**: The transaction table itself is partitioned by `txn`, **not by** `txn_next`, but except for this the same way the history of the collections is partitioned (`naksha_txn_YYYY_MM_DD`). This is mainly helpful to purge transaction-logs and to improve the access speed.

**Note**: More information about Postgres transaction numbers are available in the [documentation](https://www.postgresql.org/docs/current/functions-info.html#FUNCTIONS-PG-SNAPSHOT). We should enable [track-commit-timestamp](https://www.postgresql.org/docs/current/runtime-config-replication.html#GUC-TRACK-COMMIT-TIMESTAMP) so that `pg_commit_ts` holds information when a transaction was committed. This would make our own tracking more reliable.

## History creation

To manage the history the Naksha PostgresQL library will add a “before” trigger, and an “after” trigger to all main HEAD tables. The “before” trigger will ensure that the XYZ namespace is filled correctly, while the “after” trigger will write the transaction into the history, update the transaction table and move deleted features into the deletion HEAD table.

The history can be enabled and disabled for every space using the following methods:

- `naksha_collection_disable_history('collection')`
- `naksha_collection_enable_history('collection')`

## Sequencer

The last step is a background job added into the `lib-naksha-psql` that will “publish” the transactions. The job will set the `publish_id` and `publish_ts` to signal the visibility of a transaction and to generate a sequential numeric identifier. The job guarantees that the `pubish_id` has no holes (is continues) and is unique for every transaction. Note that the `publish_id` itself is not unique, multiple events in the transaction table can belong to the same `publish_id`. 

The author and application identifier must be set by the client before starting any transaction via `SELECT naksha_tx_start('{app_id}', '{author}');`. Note that the **author** is optional and can be `null`, but the application identifier **must not** be `null` or an empty string. If the author is `null`, then the current author stays the author for all updates or deletes. New objects in this case, are set to the application identifier, so that the application gains authorship.

In the context of [HERE](https://here.com), the **author** and **app_id** are set to the **UPM user-identifier** as received from the **Wikvaya** service, therefore coming from the **UPM** (*User Permission Management*). Technically the `lib-naksha-psql` will treats all these values just as strings and does not imply and meaning to them, so the service can be used for any other authentication system. However, in the context of [HERE](https://here.com) it is a requirement to use UPM-identifiers.

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
