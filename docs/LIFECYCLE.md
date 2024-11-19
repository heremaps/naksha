# Naksha Feature Lifecycle
Naksha comes with a specialized life-cycle for map-objects to perfectly support map related use cases. It actually was design from ground up with features like rebasing in mind.

## Features, Tuples, and Tuple-Numbers
All map-objects stored in Naksha are called **features**. Each feature is a stream of immutable states, called _Tuple_. All _Tuple_ of a features are timely ordered, forming a linked list. Each _Tuple_ is therefore just one state in the life-cycle of a Naksha feature (map-object).

Naksha organises features in **storages**, which have containers called **maps**, which have child containers called **collections**. Each collection has virtual child-containers, that store deleted tuples, historic tuples and meta-data (statistics and arbitrary meta-objects).

- There are up to _9,223,372,036,854,775,806_ (`2^63-2`) **storages** per environment.
- Each storage holds up to _4,294,967,296_ (`2^32-2`) **maps**.
- Each map holds up to _4,294,967,040_ (`2^32-256`) **collections**.
- Each collection holds up to _4,294,967,296_ (`2^32`) **features**.

The maximum number of features per collection can be increased by partitioning the collection. Therefore, the total number of features in a single collection can be increased to 1,099,511,627,776 (`2^40`). The total number of features in administration can be even bigger, when history is considered.

Note that dictionaries, transaction, basically everything in Naksha is a feature and follows the same lifecycle.

### Tuple-Numbers
As said, a **Tuple** is an immutable state of a feature. To address these states, unique identifier are needed, which are called **Tuple-Number**. A tuple-number persists out of the following logical parts:

- storage-number: u64
- map-number: u32
- collection-number: u32 
- version: u56
- partition-number: u8
- uid: u32

The **storage-number**, **map-number**, and **collection-number** are just unique identifiers of the storage, map, and collection in which all tuples of the feature, to which this tuple-number belongs, are stored.

The **partition-number** identifies the partition in which all tuples of a feature should be stored. It is the first byte of the [MD5](https://en.wikipedia.org/wiki/MD5) hash above the feature-id, and ensures that all states of a feature are always stores in the same partition. The storage can decide how many partitions are used, and then divide the partition-number by the number of used partitions, the rest is the effective partition index:

`partitionIndex = partitionNumber % partitionCount`

The **version** encodes the year, month, and day when the transaction started (UTC) that was used to create this tuple/state, plus a unique sequence-number within that day. The version `0` is equivalent to `null`, and represents the temporary version, a version shared by all tuples that are not persisted, and are only build in memory, for example for testing, or as inbetween states.

The **uid** is the transaction local unique ordering tuple identifier, if multiple new tuples are created within a single transaction. It is forbidden to generate multiple tuples of the same feature within the same transaction. The reason is how the history is organized (_next-version_). Tuples are generated in order, so they can be timely ordered by _uid_. This allows to order all changes by _version_ and _uid_ to get a reliable order, which is important for paging algorithm, to split big transactions into chunks, or when ordering in queued.

This way of creating the tuple-numbers, allows us to only make one call into the database to query a new transaction number from a sequence, after this we can create new world-wide unique tuple-numbers, without any need for the database. Even this initial call is a non-blocking operation, so that two transactions never need any synchronization, we use optimistic locking, if two clients modify the same data, the conflict is later resolved ([see auto-merge](#merged)).

### GUID
A _GUID_ is a combination of a feature-id with a tuple-number.

When exposing features to the public, what is actually exposed are tuples, so specific states of a feature. When the exposed state was modified, and is sent back to Naksha to persist it, Naksha need to understand which tuple was the base of the change. Therefore, the _GUID_ is added into the export at:

`properties->@ns:com:here:xyz->uuid`

The path and name have historic reasons, and is kept for downward compatibility. The value is the stringified _GUID_, being in the format:

`urn:here:naksha:guid:{feature-id}:{storage-number}:{map-number}:{collection-number}:{partition-number}:{year}:{month}:{day}:{seq}:{uid}`

When clients create new tuples, these tuples are not yet persisted in any storage, therefore they do not know the tuple-number the new _Tuple_ will become, except they are created in a client that directly uses the _storage_, and therefore has access to transactions, which is not the case for REST clients. However, even REST clients sometimes need to create references to new states (_tuples_), for this purpose it is allowed to shorten the _GUID_, so that it refers simply to the latest state:

`urn:here:naksha:guid:{feature-id}`

This _GUID_ simply refers to the _HEAD_ state of a feature in any storage. Parsing such a _GUID_ results in the tuple-number being `TupleNumber.ANY_HEAD`.

### Versions and Transactions
As already described, all features are a timeline of states, called _Tuple_, and each state is assigned a globally unique immutable identifier, called _Tuple-Number_. Each storage is assigned a globally unique storage-number, so a storage does never need to contact any other storage, when it generates new _Tuple_ with new _Tuple-Numbers_.

The _Tuple-Number_ stores next to the _storage-number_, _map-number_, _collection-number_, _partition-number_, **version**, and an **uid**.

When a client wants to create a new _Tuple_ (feature state), it needs to assign this _Tuple_ a global unique identifier (_Tuple-Number_), before writing it into the storage. Therefore, the client need to have an ability to generate globally unique _Tuple-Numbers_ upfront. To enable this, every client can ask the storage to allocate a _transaction-number_ (`txn`) to it, which is as well called **version**.

Using this **version**, the client can generate version-local **uids**, which are a 32-bit unsigned integer. This means, every client can create up to 4 billion new _Tuple_ within a single transaction on itself, it only needs one pre-flight request to allocate a transaction-number. The client can further split the **uid** into chunks to run multiple worker threads in parallel, before sending the final request to the _storage_.

The transaction-number is a 56-bit integers, split into four parts:
- _Year_: The year in which the transactions started (e.g. 2024).
- _Month_: The month of the year in which the transaction started (e.g. 9 for September).
- _Day_: The day of the month in which the transaction started (1 to 31).
- _Seq_: The local unique  **sequence-number** in this day.

By definition, every day starts with the sequence-number reset to zero. The final 64-bit value is combined as:
- 8-bit **reserved**, always 0.
- 15-bit **year**, between 0 and 32768 {_shift-left 41_}.
- 4-bit **month**, between 1 (January) and 12 (December) {_shift-left 37_}.
- 5-bit **day**, between 1 and 31 {_shift-left 32_}.
- 32-bit **seq**uence number.

Note that the **month** and **day** can not be zero, therefore the version `0` is formally invalid, and used to temporary in-memory states.

This concept allows up to 4 billion transactions per day (between 0 and 4,294,967,295, _2^32-1_). It will overflow in browsers in the year 4096, because in that year the transaction number needs 53-bit to be encoded, which is beyond the precision of a double floating point number. Should there be more than 4 billion transaction in a single day, this will overflow into the next day and potentially into an invalid day, should it happen at the last day of a given month. We ignore this situation, it seems currently impossible. Check in the browser:

- `((4095n << 41n)+(12n << 37n)+(31n << 32n)+4294967295n) <= BigInt(Number.MAX_SAFE_INTEGER)`: _true_
- `(4096n << 41n) <= BigInt(Number.MAX_SAFE_INTEGER)`: _false_

Technically this means, the maximum number of transactions per second per storage is around 49,000.

## The Life-Cycle
The life-cycle of a features persists out of _actions_ and _operations_.

### Actions
The first action is always `CREATED`, then optionally one to _n_ `UPDATED`, and finally it one `DELETED`.

Additionally, next to these actions, an **_operation_** is stored, which is mainly improving the search. It is advantageous to reduce the cardinality when performing a [rebase](#rebased), so to have one index on the _operation_, rather than making combined queries between _action_ and other indices like _origin_, because _action_ basically will not reduce cardinality a lot, there are so many `CREATED`, `UPDATED`, and `DELETED` actions.

The following explains what _actions_ are assigned to which _operations_. Note, there are far more _operations_ than _actions_, so the assignment is `1 : n`. This document uses the terms _client_, _server_, and _storage_. The _storage_ is the low-level driver that is used by the _server_ (e.g. Naksha-Hub) to interact with a physical storage (the implementation of the `IStorage` interface). The term _client_ refers to a client performing some action by sending instructions to the _server_, no matter if this is a Web-UI, CLI-tool, or another server/service.

### Foreign Features
Generally, a feature is called **_foreign feature_**, when its ID changes, or when it is copied from one storage, map, or collection into another one. We always keep track of foreign features using the `origin` property in the XYZ namespace, which will store the _GUID_ of the origin, so from where the feature comes. This can be the same storage, map, and collection, if only the ID changed, but it can be as well be a complete different storage.

The `origin`, and `target` are sticky, so when updating the feature, the `origin`, and `target` stay the same.

This is actually important for [rebasing](#rebased).

### Upsert
Technically the _storage_ supports an _UPSERT_ operation, but eventually it is just automatically resolved by the _storage_ into either create or update, depending on the feature exists already, or is new. The _UPSERT_ is no real action nor an operation, it always will result in one of the other actions and operations.

## Operations

### CREATED  
When a feature is created, the `action` is set to `CREATED`.

This requires that the feature does not have a `uuid`, then the _operation_ is set as well to `CREATED`.

### FORKED
When a _client_ copies a feature from one collection into another, or changes the _ID_ of a feature in a collection, the _storage_ will detect that this happened, because the feature does have a not matching `uuid`. It will decode the _GUID_ stored in the `uuid` of the feature, and recognize, that the _GUID_ refers to a foreign feature. The storage will copy the _GUID_ read from the `uuid` of the originating state into the `origin` property to keep track of this situation. This mechanism is important to [rebase](#rebased).

While the action is set to `CREATED`, the _operation_ is set to `FORKED`, so originates from a different source, or the _ID_ was changed.

A client may manually set an `origin`, the storage will detect if this is a valid _GUID_ (syntactically valid), and when it is, it will switch the operation to `FORKED` as well, this is necessary to perform [rebasing](#rebased) later. If the `origin` is set to a value that is no valid _GUID_ the behavior is undefined, but the `origin` should still be persisted.

### UPDATED
When a feature is updated by the client, the `action` is set to `UPDATED` by the client.

If a client wants to update a feature, it should read the feature, then modify it, and then send the modified feature back, without changing the XYZ namespace, except for the `tags`. When it does this, the change is performed atomically safe, because the `uuid` will hint the server which version was modified by the client, and is expected as current _HEAD_. If the feature was updated meanwhile by another client, the _server_ can try to perform an [auto-merge](#merged), otherwise it will respond with a conflict. This is what the low-level _storage_ will always do, no _storage_ does implement the auto-merging, because it would be unnecessary code replication, complicate the storage implementation, and actually will not allow business use-cast adjustment.

An `UPDATE` will never change the `origin`, or `target`, they must store the same value as before, so as the previous state referred by `prev_tn`.

### MERGED
For updates, the exact behavior is like following:

The client reads the latest version (_HEAD_ state) of the feature it wants to modify, we call this state _BASE_, because it is the state on which the changes the client did are _based_ upon. Now, the client modifies it into some _NEW_ state, and tries to save its changes. This only succeeds when _HEAD_ and _BASE_ are still the same. Assuming another client did the same concurrently, a conflict arises. The other client have read the same _BASE_ state, done changes, and then updated the feature. Now, the feature is in a new _HEAD_ state, with _BASE_ being a preceding state. There can be multiple changes between _BASE_ and _HEAD_ now.

Therefore, the _NEW_ state the client created is based upon an older state, being _BASE_, which does not reflect the changes the other clients did meanwhile, which is only contained in _HEAD_.

In this situation an automatic [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge) can be done. The changes of the other clients are calculated as difference between _HEAD_ and _BASE_, and the changes the client did is calculated as difference between _NEW_ and _BASE_. Then the difference are added to a patch. This can fail, if both clients changes the same properties, what will cause a conflict. If successful, the patch is applied to _BASE_, and will produce a new _NEW2_ state, that actually contains the changes the client did, plus the changes of the other clients did. This _NEW2_ state can be written into the storage, and it can refer via `prev_tn` to _HEAD_, and should have `base_tn` referring to _BASE_. This can fail again, if other clients were faster in doing the same, but is an idempotent operation, and can simply be repeated until either conflicting, timeout or successfully done.

Note, the _server_ may allow to influence the merging algorithm, but this requires knowledge about the data, so-called domain knowledge. For example, even while normally auto-merging a name change done by two clients concurrently is impossible (`bar` and `foo` are not addable), there can be a rule to simply use the latest value, or there can be some other rules that allow to automatically resolve conflicts. However, they are strongly dependent on domain knowledge, and therefore need to be done by the _server_ using some extension, and customized auto-merge handler.

This technically allows later to calculate back, what the client (and or merge code) actually modified. For this, the difference between _CURRENT_ and _BASE_ (`base_tn`) is calculated, and then the difference between _HEAD_ (`prev_tn`) and _BASE_ is subtracted, resulting in a patch that can be applied to _BASE_ (`base_tn`) to receive the original _NEW_ state the client had in memory and wanted to persist. This difference will as well document which properties were changed by the client.

Eventually the action will always be `UPDATED`, while the operation will be either `UPDATED` or `MERGED`, dependent on if an auto-merge was done by the _server_. The storage detects this on the `base_tn` being set.

The same rules apply to an auto-merge, that apply to a normal `UPDATED`, this means neither `origin` nor `target` must change, they are required to be same as the ones referred previously to (via `prev_tn`).

### DELETED
When a feature is deleted by the client, the `action` is set to `DELETED` by the client.

A deletion is a tombstone state, changing a feature into this state will not allow any data modification. The only modification automatically done, is the change of the _action_, when copying the state away from the _head_, and that the `txn_next` (_next-version_) will be set to the `txn` (transaction-number aka _version_), which means it forms a loop, referring to itself (so, there is no future state).

It is allowed to store deleted features in a different (delta) collection. In this case, the storage will detect that the `uuid` is from a foreign storage, and store a deletion tombstone in the deletion table, and in the history. This is done, even when no such map-object ever existed in the collection (there is no _HEAD_, so the new state need to be inserted). This is needed to support views, in a view, all features that are deleted, should actually be removed from the view.

### SPLIT
If the client need to _split_ a feature into multiple ones, it must clone the original feature, and modify the clones, without changing the XYZ namespace, except for _tags_. Eventually it should delete the original feature.

It can perform this across storages, maps, and collections.

The storage now has one feature that is `DELETED`, and multiple new features that are `CREATED`, but all have the same `uuid`, it can deduct that this is a _split_, and the feature that is deleted is the one that was split, while the new features are those being created from it.

It will set the operation for the `DELETED`, and `CREATED` feature to `SPLIT`, copy the `uuid` into the `origin` for all involved features, so that the split features, and all parts, are referring to the `origin`. Note, that even when the feature is split within the same collection, still `origin` needs to refer to the original state, because the feature states will be modified from here on.

Assume, the foreign feature `FOO` should be split, a new deleted version `FOO'` is created from `FOO`, additionally to the new features `A`, `B`, and `C` were created, then the resulting features in the target collection will look like:

- `FOO'`: operation = `SPLIT`, action = `CREATED`, origin = `FOO`, target = `null`
- `A`: operation = `SPLIT`, action = `DELETED`, origin = `FOO`, target - `null`
- `B`: operation = `SPLIT`, action = `DELETED`, origin = `FOO`, target - `null`
- `C`: operation = `SPLIT`, action = `DELETED`, origin = `FOO`, target - `null`

This behavior is essential later when [rebasing](#rebased).

### JOINED
If the client need to _join_ multiple features together to a single one, it should delete all features that should be joined, and create a new merged feature. The client need to advertise that this is a join, by settings the `target` on all the features to the `uuid` of the new merged feature.

However, because the new feature is not yet stored, it does not have a `uuid`, the client can create a _HEAD_ _GUID_ for this case, which basically is `urn:here:naksha:guid:{feature-id}`. The storage will detect the situation and resolve it.

So, when the storage finds a set of features that all have the `target` set to same `uuid`, it deducts that this is a join. It will update operation of the `DELETED`, and `CREATED` features to `JOINED`. The _storage_ will adjust the `target` to the real `uuid` of the created merged feature.

Assuming the features `A`, `B`, and `C` should be joined into a new feature `FOO`, the client would need to create the deleted features `A'`, `B'`, and `C'`, and set the target to `urn:here:naksha:guid:FOO`, and it would do the same for `FOO`, eventually resulting in the following features written into the target collection: 

- `FOO`: operation = `JOINED`, action = `CREATED`, origin = null, target = `FOO`
- `A'`: operation = `JOINED`, action = `DELETED`, origin = `A`, target - `FOO`
- `B'`: operation = `JOINED`, action = `DELETED`, origin = `B`, target - `FOO`
- `C'`: operation = `JOINED`, action = `DELETED`, origin = `C`, target - `FOO`

### REBASED
To recap, whenever a feature is copied from a foreign storage, map, or collection, or the feature-id is changed in an update, the `origin` refers to the originating feature. When a feature is split, the `origin` refers to the feature that was split, and when features are joined, the origin refers to the features that were joined, while `target` refer to the new feature that replaces the joined ones.

Eventually `origin`, and `target` are used to perform rebasing. A rebase is a complex [three-way-merge](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge). Assume a feature is modified, then it is possible to search for all features in other storages, maps, and collections that have `origin` set to this feature.

Normally a rebase is a pull-request, so a client that has a delta collection with changes, requests a rebase. This causes a job to search for all features that have an `origin` set, and then check the origin state, if any rebasing is needed.

When a feature was split, all parts of that split derive the `origin` of the feature that was split, therefore it is quite easy to find all parts.

For a join, only one part of the join may have an `origin`. But the other parts are still needed, and have the `target` set to the same target of the join, so all _Tuple_ related to this operation can be found by searching for the `origin`, and the for the missing features using `target`.

### Rebase UPDATE
This section describes what a client need to do, to perform a rebase of a feature that was only copied in a new collection (possibly a delta collection), and the continuously updated, but never split or merged.

First the history of the forked feature need to be searched backwards, until the moment is found when the feature was forked (operation `FORKED`) or the last rebase was done (operation `REBASED`). This state becomes the _FORK-BASE_ state. Then the latest version of the feature is looked up, and read as _FORK-HEAD_. This allows to calculate the difference, being _FORK-DIFF_.

Then the client will find the latest state of the originating feature, _ORIGIN-HEAD_. It will read the state that the `origin` of _FORK-BASE_ refers to as _ORIGIN-BASE_, calculating a difference between the _ORIGIN-HEAD_ and the _ORIGIN-BASE_, being _ORIGIN-DIFF_.

Now, we have two differences, the _ORIGIN-DIFF_, describing what was changed in the origin since the feature was forked or rebased the last time, and _FORK-DIFF_, which describes what was changed in the collection that we want to rebase, so since the feature was forked or rebased last. This allows to add both differences into a _REBASE-PATCH_ (the same way any other three-way-merge works). This may cause a conflict, if both collections modified the same properties. Some of these conflicts can be automatically solved by one wins over the other, or by implementing a special custom rebase algorithm, that has domain knowledge, but this is out of scope of this document.

Eventually, when successfully created the _REBASE_PATCH_, this patch can be applied to _FORK-BASE_ to produce a _FORK-NEW_ state, that has _FORK-HEAD_ as precedence state (`prev_tn`). When this new state is created, the `origin` need to be updated to _ORIGIN-HEAD_, so to the one to which the rebase is performed. Additionally, the operation must be set to `REBASED`. The client now try to persist the _FORK-NEW_ state. If the forked feature was modified in the meantime, the rebased will fail with a conflict, but as this operation is idempotent, it can simply be repeated using the new _HEAD_ state.

This is a simplified description, but should allow to understand the basic concepts and meaning of the different properties, and states, involved in rebasing.

### Rebase SPLIT and JOIN
This is out of the scope of this documentation, as it is an even more complex operation, and often requries domain knowledge to be performed correctly. However, technically, due to the `origin`, and `target` properties, plus the capabilities of the Naksha-Hub _server_ to load extensions with custom rebase algorithms, a rebase can be done successfully, and automatically, even when features have been split. If such an automatic rebasing fails, a task for a moderation should be created, so that a human can solve the situation. 

## Long-Term Outlook
The lifecycle was designed so that every mobile phone, every car, every device, can be an own storage, and that users can split each storage logically into maps, and collections.

We intend to split the 64-bit storage-number into parts, for example:

```
  PA/SEG  BLOCK    NUMBER   
  [0000]:[0000:00][00:0000]

- PUBLIC: 1-bit
- ANONYM: 1-bit
- SEG: Segment, 14-bit
- BLOCK: Allocation block, 24-bit
- NUMBER: Allocation number, 24-bit
```

The idea is to split a storage-number logically into _segment_, _block_, and _number_. The suggested stringified human- and machine-readable notation would be:

`urn:here:naksha:sn:{pa}:{segment}:{block}:{number}`

For example `urn:here:naksha:sn::5:1:19`, but as this is for human-readable notation, the URN prefix (`urn:here:naksha`) is not necessary, and can be moved, truncating the string to `sn::5:1:19`, which means private storage, segment `5`, block `1`, and number `24`, resulting in the final 64-bit value `1407374900330520` (`(5*2^48)+(1*2^24)+19`).

If the `PUBLIC` bit is set, the prefix is changed to `public`, for example `sn:public:5:1:19`, if the segment is part of the privacy extension, it becomes `sn:anonym:5:1:19`. The public flag just changes the storage-number into the public shared namespace, but the anonym flag indicates a [privacy storage-number](#privacy-extension).

We would allocate segment `0` for HERE internal, and provide each team within HERE with an own block, so each team can create up to 16 million storages, while HERE can have up to 16 million teams.

For example, smaller customers are merged into shared blocks, while bigger customers will receive own blocks, so they can subdivide their storage-numbers. Some important huge customers may get a whole segment, or a part of a segment, when they have need for more than 16 million storages.

### Goal
For example, a car company could acquire a block or segment of storage-numbers from Naksha to allocate an individual storage-number to each car. They could synchronize this with a car identifier, and assign an own storage-number to each car. A company that manages buildings could use for each building an own dedicated storage-number, then create and own map for each flat, with one collection per room. These are just rough ideas.

The concept is to link all productive features, including their history, together into one huge virtual cloud, where it is always clear which data record comes from which source, but to decouple the sources, so that a device does not need to synchronize with other devices, before it modifies map data. For example, a car can collect data in a local collection, and then synchronize it back into the cloud, when there is a good and cheap internet connection available, and fetching new map data from the cloud. As all data is versioned, the car can keep track of the last version it had synchronized, then fetch just the difference between its version, and the latest version available in the cloud. When uploading, the same is done inverse, it queries what data the cloud has, calculate the difference to its local data, and only upload the difference. This as well allows rebasing of local modifications to the updated cloud map.

The concept allows to always be aware where a _Tuple_ is originally stored, and that each storage can create world-wide unique identifiers, without any distributed locks, except for the initial storage-number allocation.

The way tuples are created, allows to execute a query that only returns the tuple-numbers of the result-set. The data loading is then done asynchronously. This even allows peer-to-peer data transfer, ones a result-set was generated. This is a robust mechanism, so even when the internet connection breaks down, while performing the data loading, it can be recovered easily later, as long as the result-set was transferred correctly, and is cached locally. The tuples can be loaded in the background slowly, while the data in the source continues to get modified, which eventually is fixed with another synchronization, so the data is eventually consistent.

### Privacy extension
When each device gets an own dedicated fixed storage-number, this is as if it would get a dedicated fixed IP address. For example, when the device is a car, it has a licence plate, and therefore the owner would be identifiable by the storage-number, which is against the law in most countries, except the user consent to this.

For this reason, even while the car has a dedicated storage-number, it is allowed to assign itself a privacy storage-number, and use this when exporting data or connecting to other systems. So, instead of writing the true storage-number into the export, the storage will replace its own storage-number with its privacy storage-number. In all external communication it will expose this privacy storage number. This privacy number can be changed at any point in time, it can even be different for every export and connect, so a storage can have multiple privacy numbers at the same time, and these numbers are not unique, because they are random. To keep the data internally consistent, the storage will internally use its assigned (and fixed) storage-number, and can keep this number in encrypted backups, that are only accessible by the owner. So the storage will translate from privacy number back into internal real storage-number, and vice versa.

Basically this means, storages with privacy extension enabled, externally never expose their real storage-number, and they change their random privacy number regularly. It means as well, that it is not possible to link from other storages into ones with privacy extension enabled, because the _GUIDs_ are ambiguous, except for _HEAD_ only _GUIDs_. Furthermore, a privacy storage number is a contractual request to anonymize all data received. This essentially means, all _origins_ from privacy storages are existing, but useless for rebasing, because the history, and its _Tuple_, can't be fetched from anywhere.

The biggest disadvantage of the privacy extension is, that tuples can't be cached, because there are collisions between privacy storage numbers.

The privacy extension alone does not guarantee real privacy, because it only obfuscates the storage-number. The data itself can still leak information, more filters may be needed. However, legally, the privacy number expresses the will of the storage to stay anonymous, and requests the other participant to anonymize the data being exchanged.

**This need to be improved and is just a first concept draft.**

### Storage-Numbers
All storage-numbers between `0` and `9223372036854775807` are reserved for private usage, which means every vendor (like [HERE Technologies](https://www.here.com/)) can make an own dedicated namespace, and privately distribute storage-numbers to devices, services, or whatever.

The storage-numbers between `-9223372036854775808` and `-1` are reserved for a global public namespace. As every storage always has an `id` and `number`, the idea is to create some form of public DNS for storages, maybe in cooperation with the [OpenStreetMap Foundation](https://osmfoundation.org/). So that everybody can register namespaces the same way that domains can be registered.
