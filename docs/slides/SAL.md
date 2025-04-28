# Naksha Project
- Concepts
- Libraries
- Applications
- Services
- Extensions
- Plugins

---
## What we talk about
- Concepts
  - **_Storage Abstraction Layer_**
  - **_Data Model Abstraction Layer_** (_if time allows_)
- Libraries
  - `lib-model` - _Storage Abstraction Layer_
  - `lib-psql` - _Storage Abstraction Layer implementation_
  - `lib-base` - _Data Model Abstraction Layer_ (_if time allows_)
- Applications
- Services
- Extensions
- Plugins

This presentation will be around 30 mins.

---
## What we do not talk about
- Concepts
  - **_Storage Abstraction Layer_**
  - **_Data Model Abstraction Layer_** (_if time allows_)
- Libraries
  - `lib-model` - _Storage Abstraction Layer_
  - `lib-psql` - _Storage Abstraction Layer implementation_
  - `lib-base` - _Data Model Abstraction Layer_ (_if time allows_)
  - ~~**Views** _(`lib-view`)_~~
    - &nbsp;&nbsp;~~Combine multiple collections into virtual ones~~
  - ~~**Differences** _(`lib-diff`)_~~
    - &nbsp;&nbsp;~~Calculates differences, patches, merges and applies them~~
  - ~~**Authorization** _(lib-auth)_~~
  - ~~**Geometries** _(`lib-geo`)_~~
    - &nbsp;&nbsp;~~Implements [GeoJSON](https://www.rfc-editor.org/rfc/rfc7946) and helpers like Here-Tiles.~~
  - ~~**JBON** _(`lib-jbon`)_~~
    - &nbsp;&nbsp;~~Java Binary Object Notation, binary encoding for JSON~~
    - &nbsp;&nbsp;~~Supports dictionary compression~~
- ~~Applications~~
- ~~Services~~
- ~~Extensions~~
- ~~Plugins~~

---
# `lib-model` - The Storage Abstraction Layer
The Naksha _Storage Abstraction Layer_ logically organizes data in containers, being:

```mermaid
graph LR
	Storage[(\n<b>Storage</b>\n\nid: String\nnumber: Int64)]
	Map[(\n<b>Map</b>\n\nid: String\nnumber: Int64)]
	Collection[(\n<b>Collection</b>\n\nid: String\nnumber: Int64)]
	Feature[(\n<b>Feature</b>\n\nid: String\nnumber: Int64)]
	Tuple[<b>Tuple</b>]
	Storage -.-> Map
	Map -.-> Collection
	Collection -.-> Feature
	Feature -.-> Tuple
	Tuple -.->|next| Tuple
```

---
## `lib-model` - Everything is a feature
- Within the SAL "everything" is a feature _(except for **Tuple**)_.

```mermaid
%%{ init: { "flowchart": { "nodeSpacing": 10, "rankSpacing": 50 } } }%%
graph LR
	Storage(<b>Storage</b>\n\nid: String\nnumber: Int64)
	Map(<b>Map</b>\n\nid: String\nnumber: Int64)
	Collection(<b>Collection</b>\n\nid: String\nnumber: Int64)
	Feature(<b>Feature</b>\n\nid: String\nnumber: Int64)
	Tx(<b>Transaction</b>\n\nid: String\nnumber: Int64)
	Dict(<b>Dictionary</b>\n\nid: String\nnumber: Int64)
	Topo(<b>Topology ...</b>\n\nid: String\nnumber: Int64)
	Tuple[<b>Tuple</b>]
	Storage -.-> Map
	Storage --extend--> Feature
	Map -.-> Collection
	Map --extend--> Feature
	Collection -.-> Feature
	Collection --extend--> Feature
	Tx -.-> Feature
	Tx --extend--> Feature
	Dict -.-> Feature
	Dict --extend--> Feature
	Topo -.-> Feature
	Topo --extend--> Feature
	Feature --> Tuple
	Tuple -->|next| Tuple
```

---
## `lib-model` - Addressing
- Within the _Storage Abstraction Layer_ each feature is a container of **Tuple**.
- A **Tuple** is an immutable state of a feature.
- The feature logically points:
  - to the latest state _(HEAD)_, if being alive
  - to the final state _(DELETED)_, if being dead and activated in collection.
  - to all past states _(HISTORY)_, if activated in collection. 

```mermaid
graph LR
	Feature[(\n<b>Feature</b>\n\nid: String\nnumber: Int64)]
	CreatedTuple[<b>Tuple</b>\n\naction: CREATED\ntupleNumber: bytea]
	UpdatedTuple[<b>Tuple</b>\n\naction: UPDATED\ntupleNumber: bytea]
	DeletedTuple[<b>Tuple</b>\n\naction: DELETED\ntupleNumber: bytea]
	Feature -.head.- CreatedTuple
	Feature -.head.- UpdatedTuple
	Feature -.history.- CreatedTuple
  Feature -.deleted.- DeletedTuple
	CreatedTuple --update--> UpdatedTuple
	UpdatedTuple --update--> UpdatedTuple
	UpdatedTuple --delete--> DeletedTuple
  CreatedTuple --delete--> DeletedTuple
```
- Every **Tuple** is uniquely addressed using a **Tuple-Number**.

---
## `lib-model` - Tuple Addressing
- Each **Tuple** has a worldwide unique identifier called **Tuple-Number**.
```mermaid
classDiagram
	direction LR
	class TupleNumber {
		+int64 storageNumber
		+int32 mapNumber
		+int32 collectionNumber
		+int64 featureNumber
		+int64 version
		+int32 uid
	}
    TupleNumber --> version
	class version {
		+u8 reserved
		+u15 year
		+u4 month
		+u5 day
		+u32 tx-sequence
	}
```
- The **tx-sequence** is a storage unique transaction identifier, reset daily.
- This allows a maximum of ~49,000 transactions per second per storage.
  - Up to the year 4096, the version is 64-bit floating point save _(JavaScript)_.
- The **uid** is a transaction local unique identifier
  - Each transaction can create up to 4 billion new **Tuple**.

---
## `lib-model` - Feature Addressing
- Each **Feature** has a global unique identifier called **GUID**.
- The **GUID** is encoded in the meta-data of a feature.
- The **GUID** is stringified as [URN](https://www.rfc-editor.org/rfc/rfc8141) with three distinct variants:

```text
urn:naksha:guid:{id}[:{stn}:{map}:{col}:{fn}[:{year}:{month}:{day}:{seq}:{uid}]]

edit: urn:naksha:guid:demo
head: urn:naksha:guid:demo:4711:0815:1213:-5386453534
full: urn:naksha:guid:demo:4711:0815:1213:-5386453534:2025:03:20:12:3
```
- **id**: The **id** of the feature.
- **stn**: The **storage-number** of the storage in which the feature is located.
- **map**: The **map-number** of the map in which the feature is located.
- **col**: The **collection-number** of the map in which the feature is located.
- **fn**: The **feature-number** of the feature.
- **year:month:day:seq**: The **version** of the feature.
- **uid**: The transaction local identifier.

**Note**: The encoded date is the day in which the transaction started, that created the referred state _(Tuple)_ of the feature.

---
## `lib-model` - Storage / Maps
All maps always have a virtual collection with **id** `naksha~collection`, used to administrate collections of the map:

```mermaid
%%{ init: { "flowchart": { "nodeSpacing": 10, "rankSpacing": 50 } } }%%
graph LR
	Storage(<b>Storage</b>\nid: <b>osm</b>\nnumber: 123)
	Map(<b>Map</b>\nid: <b>base</b>\nnumber: 456)
	Roads(<b>Collection</b>\nid: <b>roads</b>\nnumber: 67)
	Rest(<b>Collection</b>\nid: <b>restaurants</b>\nnumber: 89)
	Col(<b>Collection</b>\nid: <b>naksha~collections</b>\nnumber: 0)
	Storage --> Map
	Map --> Roads
	Map --> Rest
  Map --> Col
```

---
## `lib-model` - The chicken-egg problem
As **Storage** and **Map** are **Features**, where are they stored? They should be stored in a **Collection**, but each collection must be stored in a **Map**, so, how can a **Map** itself be stored in a **Collection**?

The answer is: In a special virtual-map in the storage: **`naksha~admin`**

```mermaid
graph TB
	Storage[\<b>Storage</b>/]
	Admin[(\n<b>Virtual-Admin-Map</b>\n\nid: <b>naksha~admin</b>\nnumber: 0)]
	Collections[(\nCollection of <b>Collection</b>\n\nid: <b>naksha~collections</b>\nnumber: 0)]
	Tx[(\nCollection of <b>Transaction</b>\n\nid: <b>naksha~transactions</b>\nnumber: 1)]
	Maps[(\nCollection of <b>Map</b>\n\nid: <b>naksha~maps</b>\nnumber: 2)]
	Dict[(\nCollection of <b>Dictionary</b>\n\nid: <b>naksha~dictionaries</b>\nnumber: 3)]
	Admin --> Collections
	Admin --> Tx
	Admin --> Maps
	Admin --> Dict
```

---
## `lib-model` - Feature-Numbers
As every **Feature** has a unique **id** and a unique **number**, the **number** needs to be generated. This happens in two ways:

- If the **id** is a decimal number between `0` and `9,223,372,036,854,775,807` _(`2^63-1`)_, then the number is parsed and used as **feature-number**, always being positive.
- Otherwise, a [md5-hash](https://en.wikipedia.org/wiki/MD5) of the **id** is calculated, and the last 8 byte _(index `8` to `15`)_ are read in [Big-Endian](https://en.wikipedia.org/wiki/Endianness) encoding, setting the sign-bit, becoming the **feature-number**, always being negative.
  - If there is a collision detected `65536` is added, the sign-bit is set again, and the lower 16-bit are restored from the hash. If colliding again, this is repeated until a free number is found.
  - This keeps the lower 16-bit in sync with the original [md5-hash](https://en.wikipedia.org/wiki/MD5).

The **feature-number** of **Map** and **Collection** is limited to 32-bit, therefore in this case the 64-bit number is truncated to 32-bit. The **number** `0` is reserved for internal purpose, and not a valid number for **storage**, **map** or **collection**.
 
Manual **feature-numbers** _(positives)_ and those of **storages**, **maps**, and **collections**, do not implement collision handling. Therefore, should there be two collections with different **id**, but same **feature-number**, they can't be stored, and the **id** needs to be adjusted.

---
## `lib-model` - Performance Partitioning
- The Naksha _Storage Abstraction Layer_ defines how **Tuple** are partitioned.
- The lowest 16-bit of the **feature-number** is by definition the **partition-number** of the feature.
  - This explains why, in the case of a **feature-number** collision, the lower 16-bit are kept in sync with the **id**-hash.
  - We want to ensure, that just by knowing the **id** of a feature, the partition in which all it's **Tuple's** are stored is known.
  - The lower 16-bit are used by intention, to ensure that manually generated sequential **feature-numbers** do partition well, and so that search-results are scattered across partitions by default, to improve read performance _(default order is by **Tuple-Number**)_.
- If a **collection** is not partitioned, then the **partition-number** has no effect
- If a **collection** is partitioned
  - All **Tuple** of a feature are stored in the partition to which their **partition-number** is mapped.
  - Therefore, all states of a **Feature** are guaranteed to be found in the same partition!

---
## `lib-model` - Limitations
The **identifiers** for **Storage**, **Map**, and **Collection** are limited to:

`^[a-z][a-z0-9_:-]{0,41}$`

We do **not** allow upper-case characters _(`A-Z`)_ or dots _(`.`)_ in **identifiers** for **Storage**, **Map**, or **Collection**!

Theoretically, the amount of **Features** is limited to `2^64`, but practically, due to hash collisions, the best one can possibly use is `2^63`, using manual feature numbers _(`9,223,372,036,854,775,807`)_.

The SAL puts a soft-cap on 4 billion features per partition, but generally each collection should be partitioned ones it stores more than 10 million features. It is recommended to create one partition for every 10 million features being expected.

The SAL as well puts a soft-cap to 1000 partitions. Therefore, the soft-cap of the amount of **Features** that can be stored in a collection is limited to around 4 trillion _(`4,294,967,296,000`)_.

These are soft-caps, individual implementations are allowed to have much higher hard-caps, but need to handle collisions accordingly.

---
## `lib-model` - Tuple Details
Each **Tuple** persists out of the following parts:

```mermaid
%%{ init: { "flowchart": { "nodeSpacing": 10, "rankSpacing": 50 } } }%%
graph TB
	Tuple[Tuple]
  Meta[MetaData]
  Geo[Geometry]
  Ref[Reference Point]
  F[Feature]
  A[Attachment]
  Tuple --> Meta
  Tuple --> Geo
  Tuple --> Ref
  Tuple --> F
  Tuple --> A
```

The **Reference Point** can be set, if not set, it is automatically calculated from the **Geometry**. If no **Geometry** is available, it's `null`.

The **Feature** is a JSON of properties, from which **geometry** and **referencePoint** are extracted.

The **MetaData** is read from: `properties->@ns:com:here:xyz`

The **attachment** is a special case, it is an arbitrary binary, not exposed through REST-APIs. It is part of the **Tuple** _(so of the state)_, but handled specially in the SAL.

---
## `lib-model` - Tuple MetaData

```mermaid
%%{ init: { "flowchart": { "nodeSpacing": 10, "rankSpacing": 50 } } }%%
graph TB
  Meta[MetaData]
  R1( )
  R2( )
  R3( )
  R4( )
  R5( )
  R6( )
  cat(createdAt:u48)
  uat(updatedAt:u48)
  ats(authorTs:u48)
  tn(tn:b160)
  prev(prev_tn:b96)
  next{{<b>next_tn:b96</b>}}
  flags(flags:i32)
  cc(cc:i32)
  hash(hash:i32)
  tile(here_tile:i32)
  id(<b>id:text</b>)
  appId(appId:text)
  author(author:text)
  origin(origin:text)
  target(target:text)
  ft(ft:text)
  tags(<b>tags:map</b>)
  cv0(cv0:f64)
  cv1(cv1:f64)
  cv2(cv2:f64)
  cv3(cv3:f64)
  cs0(cs0:text)
  cs1(cs1:text)
  cs2(cs2:text)
  cs3(cs3:text)
  Meta-->R1
  R1-->cat
  R1-->uat
  R1-->ats
  R1-->appId
  R1-->author
  R1-->R2
  R2-->tn
  R2-->next
  R2-->prev
  R2-->R3
  R3-->flags
  R3-->cc
  R3-->hash
  R3-->tile
  R3-->R4
  R4-->id
  R4-->origin
  R4-->target
  R4-->ft
  R4-->R5
  R5-->tags
  R5-->cv0
  R5-->cv1
  R5-->cv2
  R5-->cv3
  R5-->R6
  R6-->cs0
  R6-->cs1
  R6-->cs2
  R6-->cs3
```
- **here_tile**: Calculated a [HERE binary tile-id at level 15](https://www.here.com/docs/bundle/introduction-to-mapping-concepts-user-guide/page/topics/here-tiling-scheme.html) of the **Reference Point**, alternatively as hash above the **id**.
- **cc**: Change-Count, incremented with each update, start at `1` when a feature is created.

---
## `lib-model` - User/Time Tracking
- Naksha intrinsically comes with a feature called user-/ and time-tracking.
- This can be disabled for each collection setting the **`disableUserTracking`** and/or **`disableTimeTracking`** property to `true`.
- If not disabled, each **Tuple** always has a change-log, which is important to understand who did which changes:
  - Every **Tuple** does always have **appId** and **updatedAt** set to the application that produced this **Tuple** and the time it did this.
  - If the application provides an **author**, then **author** is set to this value, and **authorTs** becomes **null**, which means, it matches **updatedAt**.
  - If the application does not provide an **author** _(`null`)_, then the **author** and **updateTs** of the previous state is copied over into the current **Tuple**, if this is the first **Tuple** _(CREATED)_, then the **appId** is used as **author**, and **authorTs** is _(`null`)_.

```mermaid
%%{ init: { "flowchart": { "nodeSpacing": 10, "rankSpacing": 50 } } }%%
graph LR
  A("<b>A</b>\n\nappId + updatedAt\nauthor + authorTs")
  B("<b>B</b>\n\nappId + updatedAt\nauthor + authorTs")
  A-->B
```

---
## `lib-model` - Transactions
- Within a SAL all changes are part of transaction.
- Each transaction does have a unique **transaction-number**, which for transactions is the same as the **version**.
- Each transaction holds details about what changed.
```mermaid
classDiagram
  direction LR
  class NakshaFeature {
    +id: String
    +number: Int64
  }
  class NakshaTx {
    +featuresModified: int32
    +featuresBytes: int32
    +seqNumber: int64
    +seqTs: int64
    +maps: Map~String, NakshaTxMap~
  }
  class NakshaTxMap {
    +id: String
    +number: Int64
    +action: String?
    +collections: Map~String, NakshaTxCollection~
  }
  class NakshaTxCollection {
      +id: String
      +number: Int64
      +action: String?
      +created: Int
      +createdBytes: Int
      +updated: Int
      +updatedBytes: Int
      +deleted: Int
      +featuresByPartition: Map~String, Int~
  }
  NakshaTx -- NakshaFeature
  NakshaTx -- NakshaTxMap
  NakshaTxMap -- NakshaTxCollection
```

---
## `lib-model` - Two Phase Queries
The SAL defines that all queries are done in two phases, and it introduces a Caching Layer. Therefore, by definition, all queries are executed like:

```mermaid
%%{ init: { "flowchart": { "nodeSpacing": 10, "rankSpacing": 50 } } }%%
graph LR
  q("Query")
  r("SuccessResponse\nTuple-Numbers...")
  fetch{{"getFeatures()\ngetTuple()"}}
  cache("Cache")
  storage("Storage")
  q --> r
  r --> fetch
  fetch --> cache
  cache --> storage
```
- Perform the query, e.g.
  - _find all features with a certain id_
  - _find all features in a certain bounding box_
- The storage fulfils this query and returns a list of **Tuple-Numbers**, it does not yet have to return the features.
- When the client needs the **Tuple**, the SAL will first ask the cache to return them from cache. What is not found in cache, is then queries from the storage. The tuples can be read in parallel, except the response is part of a not yet committed write.
- This is possible, because **Tuple** are immutable, therefore, the same **Tuple-Number** guarantees to always return the same information.
- One minor exception: **next_tn**
  - This value is set ones there is a new **Tuple**, so caches may not have this information.

---
## `lib-model` - Caching
- The SAL handles in-memory caching
- The SAL allows to implement additional own caching layers
  - For example, caching in Redis, S3, or on ephemeral SSD
- All storages must put loaded **Tuple** into the SAL cache, which is a simple call:
  - `Naksha.cache.store(tuple)`
  - `Naksha.cache.store(tuple, tuple, tuple)`
  - `Naksha.cache.store(tupleList)`

---
# Questions?

**Next: `lib-psql` _(Storage Abstraction Layer implementation)_**

---
# `lib-psql` - The Implementation
- One implementation of the _Storage Abstraction Layer_ is **`lib-psql`**.
- **`lib-psql`** stores data in a PostgresQL database.
- **Maps** are stored as **schemata**.
- **Collections** are stored as a **set of tables**, being:
  - "`{id}`": The _HEAD_ table, storing the latest **Tuple** of the features.
  - "`{id}$del`": The _DELETED_ table, storing all deleted **Tuple**.
  - "`{id}$hst`": The _HISTORY_ table, storing all past **Tuple** of the feature.
    - "`{id}$hst${year}`": The _HISTORY_ is always partitioned by year, so when the **Tuple-Number** is known, we know in which partition this **Tuple** is stored.

---
## `lib-psql` - Partitioning
The `lib-psql` _SAL_ implementation supports partitioning with up to 1000 partitions per collection. If a collection is partitioned, all its tables are partitioned, for example a collection "`foo`" with 4 partitions:

```mermaid
%%{ init: { "flowchart": { "nodeSpacing": 10, "rankSpacing": 40 } } }%%
graph TB
	Col{{Collection 'foo'}}
	Head(foo)
	HeadP0(<i><b>$p000</b></i>)
	HeadP1($p001)
	HeadP2($p002)
	HeadP3($p003)
	Del(foo$del)
	DelP0(<i><b>$p000</b></i>)
	DelP1($p001)
	DelP2($p002)
	DelP3($p003)
	Hst(foo$hst)
	HstY($y2025)
  HstY26($y2026)
  HstYMore(...)
	HstYP0(<i><b>$p000</b></i>)
	HstYP1($p001)
	HstYP2($p002)
	HstYP3($p003)
	Col --> Head
	Col --> Del
	Col --> Hst
	Head --> HeadP0
	Head --> HeadP1
	Head --> HeadP2
	Head --> HeadP3
	Del --> DelP0
	Del --> DelP1
	Del --> DelP2
	Del --> DelP3
	Hst --> HstY
  Hst --> HstY26
  Hst --> HstYMore
  HstY --> HstYP0
  HstY --> HstYP1
  HstY --> HstYP2
  HstY --> HstYP3
```

Example, a feature with **id** being `HQzBtA1fQmcg`:
- **feature-number**: `-8109559185894210496`
- **partition-number**: `1088` _(`-8109559185894210496 & 65535`)_
- **partition-index**: `0` _(`1088 % 4 = 0`)
- **partitions**: `foo$p000`, `foo$del$p000`, `foo$hst$2025$p000`, `foo$hst$2026$p000`...

---
# Demo
- Show the Naksha _Storage Abstraction Layer_ API
- Show how to actually use the _Storage Abstraction Layer_ API locally for development and debugging.
- **Note**: Naksha team provides and maintains a PostgresQL docker container, that is compatible to AWS Aurora and RDS instances!
