@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.PlatformUtil
import naksha.model.*
import naksha.model.objects.IndexList
import naksha.model.objects.IndexType
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaTx
import naksha.model.objects.StandardMembers
import naksha.model.request.*
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A helper to write tuples into collections.
 *
 * This class is stateful and should be sticky until `commit` or `rollback`. It will remember which maps and collections have been created within the current transaction. After `commit`, the corresponding new objects should be visible, and the cache should automatically fetch them, but until this point, they are only visible within the current transaction!
 * @since 3.0
 * @see [PgWrite]
 */
@JsExport
open class PgWriter internal constructor(
    /**
     * The session to which the writer is bound.
     * @since 3.0
     */
    session: PgSession,

    /**
     * If the writer should use save-point's.
     * @since 3.0
     */
    @JvmField
    val useSavepoint: Boolean
) : PgReaderWriterBase(session) {
    /**
     * The storage to operate on.
     * @since 3.0
     */
    val storage: PgStorage = session.storage

    /**
     * The database connection to use for modifications.
     * @since 3.0
     */
    val conn: PgConnection
        get() = session.useConnection()

    /**
     * The transaction to update with what was done.
     * @since 3.0
     */
    val tx = session.useTx()

    /**
     * The Naksha transaction.
     * @since 3.0
     */
    val transaction: NakshaTx
        get() = tx.transaction

    /**
     * Performs the given writes.
     * @param writes the writes to perform.
     * @return the response.
     */
    fun execute(writes: WriteList) : Response {
        val tupleNumberList = executeWrites(writes.mapNotNull { it }.toMutableList())
        return SuccessResponse().withTupleNumberList(tupleNumberList)
    }

    /**
     * Add the given write into an ordered array-list, order by `id` ascending.
     * @since 3.0
     */
    private fun addSorted(arrayList: ArrayList<PgWrite>, value: PgWrite) {
        val index = arrayList.binarySearch { it.id.compareTo(value.id) }
        val insertIndex = if (index < 0) -index - 1 else index
        arrayList.add(insertIndex, value)
    }

    /**
     * Groups and orders writes by map, collection, partition, and eventually operation.
     *
     * @param map the map to write into.
     * @param collection the collection to write into.
     * @param writeOp the operation to perform, so `INSERT`, `UPSERT`, `UPDATE`, `DELETE` or `PURGE`.
     * @param write the write instruction.
     * @param byMap the map into which to store the write instruction.
     * @param writeListCapacity the capacity of the write array, when a new one need to be allocated.
     * @since 3.0
     */
    private fun addPgWrite(
        map: PgCatalog,
        collection: PgCollection,
        writeOp: WriteOp,
        write: PgWrite,
        byMap: MutableMap<PgCatalog, MutableMap<PgCollection, Array<MutableMap<WriteOp, ArrayList<PgWrite>>?>>>,
        writeListCapacity: Int) {
        var byCollection = byMap[map]
        if (byCollection == null) {
            byCollection = mutableMapOf()
            byMap[map] = byCollection
        }
        var byPartition = byCollection[collection]
        if (byPartition == null) {
            byPartition = arrayOfNulls(if (collection.partitions > 1) collection.partitions + 1 else 1)
            byCollection[collection] = byPartition
        }
        val partition = write.partition
        // Note: byPartition reserved the first entry (index #0) for the case that there is no partitioning,
        //       therefore when there is no partitioning, the index becomes `0`, otherwise `1` to `n`.
        var byWriteOp = byPartition[partition + 1]
        if (byWriteOp == null) {
            byWriteOp = mutableMapOf()
            byPartition[partition + 1] = byWriteOp
        }
        var writeList = byWriteOp[writeOp]
        if (writeList == null) {
            writeList = ArrayList(writeListCapacity)
            byWriteOp[writeOp] = writeList
        }
        addSorted(writeList, write)
    }

    /**
     * Group the writes by map, then by collection, and finally by partition with `-1` as partition number, when the table is not partitioned.
     * @param writes the writes that should be done.
     * @return a map by map, collection, and partition to write operations executed within.
     * @since 3.0
     */
    private fun groupOperations(writes: ArrayList<PgWrite>)
      : MutableMap<PgCatalog, MutableMap<PgCollection, Array<MutableMap<WriteOp, ArrayList<PgWrite>>?>>> {
        val byMap = mutableMapOf<PgCatalog, MutableMap<PgCollection, Array<MutableMap<WriteOp, ArrayList<PgWrite>>?>>>()
        var writeListCapacity = writes.size
        for (i in writes.indices) {
            val write = writes[i]
            val map = write.map
            val collection = write.collection
            val op = write.original.op
            when (op) {
                WriteOp.CREATE -> {
                    val f = write.feature ?: throw illegalArg("The feature #${write.i} is null")
                    // In a CREATE case, UNDEFINED means the same as `null`
                    val attachment = if (write.attachment === Write.UNDEFINED) null else write.attachment
                    val tuple = tx.created(write.map.head, write.collection.head, f, attachment)
                    write.tuple = tuple
                    val tupleNumber = tuple.tupleNumber
                    write.tupleNumber = tupleNumber
                }
                WriteOp.UPSERT -> {
                    // Note: We first try an INSERT, then, when that fails, we do an on-conflict UPDATE!
                    val f = write.feature ?: throw illegalArg("The feature #${write.i} is null")
                    val tuple = tx.created(write.map.head, write.collection.head, f, write.attachment)
                    write.tuple = tuple
                    val tupleNumber = tuple.tupleNumber
                    write.tupleNumber = tupleNumber
                }
                WriteOp.UPDATE -> {
                    val f = write.feature ?: throw illegalArg("The feature #${write.i} is null")
                    val tuple = tx.updated(write.map.head, write.collection.head, f, write.attachment, write.original.atomic)
                    write.tuple = tuple
                    val tupleNumber = tuple.tupleNumber
                    write.tupleNumber = tupleNumber
                }
                WriteOp.DELETE -> {
                    if (write.isTransactionModification) throw forbidden("Transactions must not be deleted or purged")
                }
                WriteOp.PURGE -> {
                    if (write.isTransactionModification) throw forbidden("Transactions must not be deleted or purged")
                    // Note: purge and delete are the same operation, except that a purge is not copied into deleted table!
                }
                else -> {
                    throw illegalArg("Unknown write operation: $op")
                }
            }
            // In the first run, there is a chance that all writes go into same partition, after that, we can have one less!
            addPgWrite(map, collection, op, write, byMap, writeListCapacity--)
        }
        // Sort all writes within the
        return byMap
    }

    /**
     * Performs the given writes.
     * @param writes the writes to perform.
     * @return the tuple-numbers of the
     */
    private fun executeWrites(writes: MutableList<Write>) : TupleNumberList {
        // Add the input-index.
        val targetWrites = ArrayList<PgWrite>(writes.size)
        for (i in 0 ..< writes.size) targetWrites.add(PgWrite(writes[i], i))
        val savepointId = PlatformUtil.randomString()
        var conn: PgConnection? = null
        try {
            // This can be time-consuming, unless the connection is already open, try not to open it before we do this!
            prepareWrite(targetWrites)
            val byMap = groupOperations(targetWrites)

            // Perform the writes, if any error happens, we will roll back the session to where it was before we started.
            // Note: We must not close the connection, therefore no `session.useConnection().use {}`!
            conn = this.conn
            if (useSavepoint) conn.execute("SAVEPOINT \"$savepointId\"").close()
            for (mapEntry in byMap) {
                val map = mapEntry.key
                val byCol = mapEntry.value
                for (colEntry in byCol) {
                    val collection = colEntry.key
                    val byPartition = colEntry.value
                    for (i in byPartition.indices) {
                        val byWriteOp = byPartition[i]
                        if (byWriteOp != null) {
                            val partition = i - 1 // index #0 represents no partitioning
                            executeWrite(map, collection, partition, byWriteOp)
                        }
                    }
                }
            }
            // If everything worked out as expected, we can drop the savepoint.
            if (useSavepoint) conn.execute("RELEASE SAVEPOINT \"$savepointId\"").close()
        } catch (t: Throwable) {
            if (conn != null && useSavepoint) conn.execute("ROLLBACK TO SAVEPOINT \"$savepointId\"").close()
            throw PgExceptionMapper.map(t)
        }

        // Reorder results to match input.
        val tupleNumbers = TupleNumberList()
        tupleNumbers.setCapacity(writes.size)
        val tupleList = ArrayList<Tuple>(writes.size)
        val transaction = tx.transaction
        var featuresModified = 0
        for (write in targetWrites) {
            val tupleNumber = write.tupleNumber
            tupleNumbers[write.i] = tupleNumber
            val tuple = write.tuple
            if (write.isFeatureModification) {
                val map = write.map
                val col = write.collection
                val txCol = transaction.useMap(map.id, map.number).useCollection(col.id, col.number)
                if (tupleNumber != null) {
                    txCol.add(tupleNumber, col.partitions)
                }
                featuresModified += 1
            } else if (write.isMapModification) {
                val map = write.asPgCatalog
                if (map != null) transaction.useMap(map.id, map.number, write.action)
            } else if (write.isCollectionModification) {
                val map = write.map
                val col = write.asPgCollection
                if (col != null) {
                    transaction.useMap(map.id, map.number).useCollection(col.id, col.number, write.action)
                    map.invalidateCollection(col)
                }
            }
            if (tuple != null) tupleList.add(tuple)
        }
        transaction.featuresModified += featuresModified
        // We do not put tuples into cache, before we are sure everything was successful!
        // Adding all together into the cache reduces the effort to iterate above all caches multiple times.
        Naksha.cache.store(tupleList)
        return tupleNumbers
    }

    // Ensure that the needed physical schema and tables are created.
    // Ensure that the PgTupleWrite data class is ready for action.
    // After this has run, only the `tuple` is missing!
    private fun prepareWrite(writes: ArrayList<PgWrite>) {
        for (write in writes) {
            val featureId = write.original.id

            // Detect tbe map into which to write.
            val mapId = write.original.mapId ?: throw illegalArg("The given write does not have a map-id")
            val map = storage.adminCatalog.getPgCatalogById(conn, mapId) ?:
                throw mapNotFound("The write #${write.i} refers to not existing map '$mapId'")
            write.map = map

            // Detect the collection into which to write.
            val colId = write.original.collectionId ?: throw illegalArg("The given write does not have a collection-id")
            val collection = map.getPgCollectionById(conn, colId) ?:
                throw collectionNotFound("The write #${write.i} refers to not existing collection '$colId'")
            write.collection = collection

            // If this operation modifies a map.
            if (write.isMapModification) {
                val op = write.op
                var pgMap = storage.adminCatalog.getPgCatalogById(null, write.id) ?: storage.adminCatalog.getPgCatalogById(conn, write.id)

                val nakshaMap: NakshaCatalog?
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT || op == WriteOp.UPDATE) {
                    val feature = write.feature ?: throw illegalArg("The write #${write.i} is $op, but the feature is null")
                    nakshaMap = if (feature is NakshaCatalog) feature else feature.proxy(NakshaCatalog::class)
                    nakshaMap.storageId = storage.id
                    if (pgMap == null) {
                        if (op == WriteOp.UPDATE) {
                            throw mapNotFound("The UPDATE (write #${write.i}) failed, because the map '$featureId' does not exist")
                        }
                        pgMap = PgCatalog(storage, nakshaMap)
                        createPgMap(pgMap)
                    } else if (op == WriteOp.CREATE) {
                        throw mapExists("The write #${write.i} failed, because the map '$featureId' does exist already")
                    }
                } else if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                    if (pgMap != null) {
                        deletePgMap(pgMap)
                    }
                    nakshaMap = null
                } else {
                    throw illegalState("The write #${write.i} refers to an unsupported operation: '$op'")
                }
                write.asPgCatalog = pgMap
                write.asNakshaMap = nakshaMap
            }

            // If this operation modifies a collection.
            if (write.isCollectionModification) {
                val op = write.op
                var pgCollection = map.getPgCollectionById(null, write.id) ?: map.getPgCollectionById(conn, write.id)

                val nakshaCollection: NakshaCollection?
                if (op == WriteOp.CREATE || op == WriteOp.UPSERT || op == WriteOp.UPDATE) {
                    val feature = write.feature ?: throw illegalArg("The write #${write.i} is $op, but the feature is null")
                    nakshaCollection = if (feature is NakshaCollection) feature else feature.proxy(NakshaCollection::class)
                    if (pgCollection == null) {
                        if (op == WriteOp.UPDATE) {
                            throw collectionNotFound(
                                "The UPDATE (write #${write.i}) failed, because the collection '$featureId' does not exist in map '$mapId'"
                            )
                        }
                        // Normalize members and indices (inject defaults, validate, sort) — CREATE only.
                        normalizeCollection(nakshaCollection)
                        pgCollection = PgCollection(map, nakshaCollection)
                        createPgCollection(pgCollection)
                    } else if (op == WriteOp.CREATE) {
                        throw collectionExists(
                            "The write #${write.i} failed, because the collection '$featureId' does exist already in map '$mapId'"
                        )
                    } else {
                        // UPSERT or UPDATE on an existing collection: diff schema (members + custom indexes) and apply.
                        pgCollection.applyMembersAndIndexes(conn, pgCollection.head, nakshaCollection, write.original.force)
                    }
                } else if (op == WriteOp.DELETE || op == WriteOp.PURGE) {
                    if (pgCollection != null) {
                        deletePgCollection(pgCollection)
                    }
                    nakshaCollection = null
                } else {
                    throw illegalState("The write #${write.i} refers to an unsupported operation: '$op'")
                }
                write.asPgCollection = pgCollection
                write.asNakshaCollection = nakshaCollection
            }
        }
    }

    private fun MutableMap<PgCollection, MutableList<PgWrite>>.getOrCreate(collection: PgCollection): MutableList<PgWrite> {
        var list = this[collection]
        if (list == null) {
            list = ArrayList()
            this[collection] = list
        }
        return list
    }

    private fun executeWrite(map: PgCatalog, collection: PgCollection, partition: Int, byWriteOp: Map<WriteOp, List<PgWrite>>) {
        // DELETE
        val deletes = byWriteOp[WriteOp.DELETE]
        if (deletes != null) {
            val tupleWriter = PgWriterDelete(this, collection, partition, deletes)
            tupleWriter.execute(conn)
        }

        // PURGE
        val purges = byWriteOp[WriteOp.PURGE]
        if (purges != null) {
            val tupleWriter = PgWriterDelete(this, collection, partition, purges, purge = true)
            tupleWriter.execute(conn)
        }

        // INSERT
        val inserts = byWriteOp[WriteOp.CREATE]
        if (inserts != null) {
            val tupleWriter = PgWriterInsert(this, collection, partition, inserts)
            tupleWriter.execute(conn)
        }

        // UPSERT
        val upserts = byWriteOp[WriteOp.UPSERT]
        if (upserts != null) {
            val tupleWriter = PgWriterUpsert(this, collection, partition, upserts)
            tupleWriter.execute(conn)
        }

        // UPDATE
        val updates = byWriteOp[WriteOp.UPDATE]
        if (updates != null) {
            val tupleWriter = PgWriterUpdate(this, collection, partition, updates)
            tupleWriter.execute(conn)
        }
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaCatalog] should be physically created.
     * @param map the map that should be physically created.
     * @since 3.0
     */
    protected open fun createPgMap(map: PgCatalog) {
        storage.adminCatalog.createPgCatalog(conn, map)
    }

    /**
     * Invoked when a [NakshaMap][naksha.model.objects.NakshaCatalog] was created.
     * @param map the map that was just created.
     * @since 3.0
     */
    protected open fun deletePgMap(map: PgCatalog) {
        storage.adminCatalog.deletePgCatalog(conn, map)
    }

    /**
     * Normalizes a [NakshaCollection] before it is physically created:
     *
     * **Members** — when the client provides an explicit (non-null) `members` list:
     * - Mandatory members (`fn`, `version`, `id`, `feature`) are silently deduplicated if declared
     *   with the exact same type, or rejected with [NakshaError.ILLEGAL_ARGUMENT] on type mismatch.
     * - Non-mandatory names that conflict with any reserved [PgColumn] name are rejected.
     * - The surviving client-declared members are sorted for optimal PostgreSQL column layout.
     * When `members` is **null** (backward-compatible) it is left as-is; the DDL layer will use the
     * full built-in column schema.
     *
     * **Indices** — when the client provides an explicit (non-null) `indices` list:
     * - Any entry whose [naksha.model.objects.Index.internal] flag is `true` is silently dropped
     *   (clients must not declare storage-managed indices).
     * When `indices` is **null** (backward-compatible) it is left as-is; the DDL layer will create
     * all default optional indices.
     *
     * This method modifies [collection] in place and returns it.
     */
    protected fun normalizeCollection(collection: NakshaCollection): NakshaCollection {
        // ── Members ─────────────────────────────────────────────────────────────────
        val clientMembers = collection.members
        if (clientMembers != null) {
            val mandatoryByName = PgColumn.mandatoryMembers.associateBy { it.name }
            val normalizedMembers = MemberList()
            for (m in clientMembers) {
                if (m == null) continue
                val mandatory = mandatoryByName[m.name]
                if (mandatory != null) {
                    // Mandatory column declared by client: exact type → silently drop; type conflict → reject.
                    if (m.dataType != mandatory.dataType) {
                        throw illegalArg(
                            "Member '${m.name}' is a mandatory column with type ${mandatory.dataType}; " +
                                "client declared it with type ${m.dataType}"
                        )
                    }
                } else {
                    normalizedMembers.add(m)
                }
            }
            PgMemberHelper.validateMemberNames(normalizedMembers)
            PgMemberHelper.sortMembersForStorage(normalizedMembers)
            collection.members = normalizedMembers
        }

        // ── Indices ──────────────────────────────────────────────────────────────────
        val clientIndices = collection.indices
        if (clientIndices != null) {
            val normalizedIndices = IndexList()
            for (idx in clientIndices) {
                if (idx == null) continue
                if (idx.internal) continue   // clients must not declare internal indices
                normalizedIndices.add(idx)
            }
            // When members are explicitly set, validate that every index column name
            // refers to a known member (standard built-in or custom declared).
            if (clientMembers != null) {
                val knownNames = buildSet<String> {
                    addAll(StandardMembers.ALL_NAMES)
                    for (m in collection.members ?: emptyList<Member>()) if (m != null) add(m.name)
                }
                // Build a lookup: member name → dataType (standard + custom)
                val memberTypeByName: Map<String, MemberType> = buildMap {
                    for (sm in StandardMembers.ALL) put(sm.name, sm.dataType)
                    for (m in collection.members ?: emptyList<Member>()) if (m != null) put(m.name, m.dataType)
                }
                for (idx in normalizedIndices) {
                    if (idx == null) continue
                    val firstColName = idx.on.firstOrNull { it != null }
                    for (colName in idx.on) {
                        if (colName != null && colName !in knownNames) {
                            throw illegalArg(
                                "Index '${idx.name}' references unknown member '$colName'. " +
                                    "Declare the member in the collection's members list, or use a standard member name."
                            )
                        }
                    }
                    // Type-compatibility: SPATIAL index requires a SPATIAL member as its first column;
                    // BTREE/other index must not target a SPATIAL member (no ordering defined for TWKB).
                    if (firstColName != null) {
                        val firstColType = memberTypeByName[firstColName]
                        when (idx.type) {
                            IndexType.SPATIAL -> if (firstColType != MemberType.SPATIAL) {
                                throw illegalArg(
                                    "SPATIAL index '${idx.name}' must target a member of type SPATIAL, " +
                                        "but '$firstColName' has type $firstColType."
                                )
                            }
                            IndexType.TAGS -> if (firstColType != MemberType.TAGS && firstColType != MemberType.TAGS_FROM_ARRAY) {
                                throw illegalArg(
                                    "TAGS index '${idx.name}' must target a member of type TAGS or TAGS_FROM_ARRAY, " +
                                        "but '$firstColName' has type $firstColType."
                                )
                            }
                            IndexType.SET -> if (firstColType != MemberType.SET) {
                                throw illegalArg(
                                    "SET index '${idx.name}' must target a member of type SET, " +
                                        "but '$firstColName' has type $firstColType."
                                )
                            }
                            else -> if (firstColType == MemberType.SPATIAL) {
                                throw illegalArg(
                                    "Index '${idx.name}' of type ${idx.type} cannot target SPATIAL member '$firstColName'. " +
                                        "Use IndexType.SPATIAL for geometry columns."
                                )
                            }
                        }
                    }
                }
            }
            collection.indices = normalizedIndices
        }

        return collection
    }

    /**
     * Invoked when a [NakshaCollection][naksha.model.objects.NakshaCollection] should be physically created.
     * @param collection the collection that should be physically created.
     * @since 3.0
     */
    protected open fun createPgCollection(collection: PgCollection) {
        collection.catalog.createPgCollection(conn, collection)
    }

    /**
     * Invoked when a [NakshaCollection][naksha.model.objects.NakshaCollection] should be physically created.
     * @param collection the collection that should be physically created.
     * @since 3.0
     */
    protected open fun deletePgCollection(collection: PgCollection) {
        collection.catalog.deletePgCollection(conn, collection)
    }
}