package naksha.psql

import naksha.base.*
import naksha.base.fn.Fn1
import naksha.model.*
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
import naksha.base.Base.BaseCompanion.FAL
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType.MemberType_C.BYTE_ARRAY
import naksha.model.objects.MemberType.MemberType_C.INT64
import naksha.model.objects.MemberType.MemberType_C.SPATIAL
import naksha.model.objects.MemberType.MemberType_C.STRING
import naksha.model.objects.MemberType.MemberType_C.TAG_LIST
import naksha.model.objects.MemberType.MemberType_C.TAG_MAP
import naksha.model.objects.MemberType.MemberType_C.TAG_MAP_FROM_ARRAY
import naksha.model.objects.MemberType.MemberType_C.TUPLE_NUMBER
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardIndices
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureBytesMember
import naksha.model.objects.StandardMembers.StandardMembers_C.GlobalBookFeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.IdMember
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersionMember
import naksha.model.objects.StandardMembers.StandardMembers_C.TnMember
import naksha.model.objects.StoreMode
import naksha.model.objects.XyzIndices
import naksha.psql.PgColumn.PgColumn_C.EXTENDED
import naksha.psql.PgColumn.PgColumn_C.EXTERNAL
import naksha.psql.PgColumn.PgColumn_C.MAIN
import naksha.psql.PgColumn.PgColumn_C.PLAIN
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * A collection is a set of database tables that together form a logical feature store. This lower level implementation supports methods to create the collection physically (so the whole set of tables), to refresh the information about the collection, drop the tables, to add, or remove indices at runtime, aso.
 *
 * This is a wrapper around the [NakshaCollection].
 *
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgCollection internal constructor(
    /**
     * The catalog in which the collection is located.
     * @since 3.0
     */
    val catalog: PgCatalog,

    /**
     * The HEAD state of the collection.
     * @since 3.0
     */
    nakshaCollection: NakshaCollection,
) {
    /**
     * The _HEAD_ state of the collection.
     * @since 3.0
     */
    @JvmField
    val headRef = AtomicNonNullRef(nakshaCollection)

    /**
     * The custom-identifier of the collection.
     * @since 3.0
     */
    @JvmField
    val id: Id = nakshaCollection.id

    /**
     * The amount of bit the [next-version][PgColumn.NextVersionColumn] should be shifted right to calculate the history-partition.
     * @since 3.0
     * @see [PgHistoryTable]
     */
    @JvmField
    val shift: Int = nakshaCollection.shift

    /**
     * If this is an internal collection storing catalogs (`naksha~catalogs`).
     * @since 3.0
     */
    @JvmField
    val storesCatalogs: Boolean = head.storesCatalogs()

    /**
     * If this is an internal collection storing collections (`naksha~collections`).
     * @since 3.0
     */
    @JvmField
    val storesCollections: Boolean = head.storesCollections()

    private val defaultXyz: Boolean = nakshaCollection.members == null

    /**
     * Returns the partition index of the [PgHistoryPartition] in which features can be found, that are modified in the given version.
     * @param version the version in which features are modified.
     * @return the partition index of the [PgHistoryPartition] into which _HEAD_ features will be moved, when modified in the given version.
     * @since 3.0
     */
    fun historyPartitionNumberOf(version: Long): Int = (version shr shift).toInt()

    /**
     * Convert the given member into a [PgColumn], only fails for the standard member [TnMember].
     * @param member the member to convert, must not me [Tuple-Number][TnMember].
     * @param index the real index in the physical table at which to place the member.
     * @return the [PgColumn] for the member.
     * @throws NakshaException with error [INTERNAL_ERROR], if the given member is [TnMember].
     * @since 3.0
     */
    private fun fromMember(member: Member, index: Int): PgColumn {
        if (TnMember.id == member.id) throw NakshaException(INTERNAL_ERROR, "The tuple-number can't be converted using fromMember")
        val memberName = member.id
        // These mandatory members get special storage handling.
        when (memberName) {
            GlobalBookFeatureNumber.id -> return PgColumn(index, memberName, INT64, "STORAGE $PLAIN")
            IdMember.id -> return PgColumn(index, memberName, STRING, "STORAGE $PLAIN COLLATE \"C\"")
            FeatureBytesMember.id -> return PgColumn(index, memberName, BYTE_ARRAY, "STORAGE $EXTERNAL")
        }
        val memberType = member.dataType
        return when (memberType) {
            BYTE_ARRAY, TUPLE_NUMBER -> PgColumn(index, memberName, STRING, "STORAGE $EXTENDED")
            STRING -> PgColumn(index, memberName, STRING, "STORAGE $MAIN COLLATE \"C\"")
            TAG_MAP, TAG_MAP_FROM_ARRAY, TAG_LIST -> PgColumn(index, memberName, memberType, "STORAGE $MAIN")
            SPATIAL -> PgColumn(index, memberName, memberType, "STORAGE $EXTERNAL")
            else -> PgColumn(index, memberName, memberType, "STORAGE $PLAIN")
        }
    }

    /**
     * Read the [members][NakshaCollection.members] from the given [NakshaCollection] and turn them into a [PgColumn] list. If the members of the given [NakshaCollection] and not yet [ordered by index][MemberList.isSortedByIndex], then the method will invoke a [sort by type][MemberList.sortByDataTypeAndAssignIndex].
     * @param nakshaCollection the collection from which to extract the [PgColumn]'s.
     * @return an array with all extracted [PgColumn]'s.
     */
    private fun generateColumns(nakshaCollection: NakshaCollection): Array<PgColumn> {
        val members: MemberList = nakshaCollection.useMembers()
        if (!members.isSortedByIndex()) {
            members.sortByDataTypeAndAssignIndex()
        }
        var i = 0
        // We split tuple-number (TnMember) into `fn` and `version`, therefore we need size + 1.
        return Array(members.size + 1) {
            when (it) {
                // The first three members are fixed to:
                0 -> PgColumn.FnColumn
                1 -> PgColumn.VersionColumn
                2 -> PgColumn.NextVersionColumn
                else -> {
                    val col: PgColumn
                    var member = members[i++] ?: throw illegalState("${FAL}Member #${i-1} is null")
                    var name = member.id
                    // Tn is already added as `fn` and `version`, and `next_version` is as well already added.
                    while (name==TnMember.id || name==NextVersionMember.id) {
                        member = members[i++] ?: throw illegalState("${FAL}Member #${i-1} is null")
                        name = member.id
                    }
                    col = fromMember(member, it)
                    col
                }
            }
        }
    }

    /**
     * The columns to expect in the table.
     *
     * The columns are filled from the members-book and vice versa. Most of the time the columns match the members, but there are details in which they can differ, i.e. for the tuple-number.
     * @since 3.0
     */
    @JvmField
    val columns: Array<PgColumn> = generateColumns(nakshaCollection)

    /**
     * Join the identities of all [columns], separated by comma, optionally filtered by the given filter.
     * @param prefix an optional prefix to be added in front of each column being joined.
     * @param toIdent an optional convertion lambda that turns the column into a string; if it returns `null`, the column is skipped.
     * @return a comma separated list of [ident][PgColumn.ident] strings.
     * @since 3.0
     */
    fun joinColumns(prefix: String? = null, toIdent: Fn1<String?, PgColumn>? = null): String {
        val sb = StringBuilder()
        for (column in columns) {
            val ident: String? = if (toIdent != null) toIdent.call(column) else column.ident
            if (ident == null) continue
            if (sb.isNotEmpty()) sb.append(", ")
            if (prefix != null) sb.append(prefix)
            sb.append(ident)
        }
        return sb.toString()
    }

    private fun indicesFor(nakshaCollection: NakshaCollection, onHead: Boolean): Array<PgIndex> {
        val indices = IndexList(StandardIndices.MANDATORY)
        val declared: IndexList? = nakshaCollection.indices
        val requested: List<Index> = when {
            declared != null -> List(declared.size) { declared[it] ?: throw NakshaException(ILLEGAL_STATE, "Index #$it must not be null") }
            defaultXyz -> XyzIndices.ALL
            else -> emptyList()
        }
        for (requestedIndex in requested) {
            if (!indices.contains(requestedIndex)) indices.add(requestedIndex)
        }
        return Array(indices.size) { i ->
            val index = indices[i] ?: throw NakshaException(ILLEGAL_STATE, "Index #$i must not be null")
            val indexName = index.name
            val nakshaOn = index.on
            val on: ArrayList<PgColumn> = ArrayList(nakshaOn.size)
            on@ for (i in 0 ..< nakshaOn.size) {
                val name = nakshaOn[i] ?: throw NakshaException(ILLEGAL_STATE, "Index '$indexName->on[$i]' must not be null")
                if (onHead && NextVersionMember.id == name) continue // no NEXT_VERSION in HEAD
                for (column in columns) {
                    if (column.name == name) {
                        on.add(column)
                        continue@on
                    }
                }
                throw NakshaException(ILLEGAL_ARGUMENT, "Index '$indexName->on[$i]' refers to member '$name', but no such member exists")
            }
            val nakshaInclude = index.include
            val include: ArrayList<PgColumn>?
            if (!nakshaInclude.isNullOrEmpty()) {
                include = ArrayList(nakshaInclude.size)
                include@ for (i in 0 ..< nakshaInclude.size) {
                    val name = nakshaInclude[i] ?: throw NakshaException(ILLEGAL_STATE, "Index '$indexName->include[$i]' must not be null")
                    if (onHead && NextVersionMember.id == name) continue // no NEXT_VERSION in HEAD
                    for (column in columns) {
                        if (column.name == name) {
                            include.add(column)
                            continue@include
                        }
                    }
                    throw NakshaException(ILLEGAL_ARGUMENT,"Index '$indexName->include[$i]' refers to member '$name', but no such member exists")
                }
            } else {
                include = null
            }
            PgIndex(indexName, on.toTypedArray(), include?.toTypedArray() ?: emptyArray())
        }
    }

    /**
     * The indices of the HEAD table.
     * @since 3.0
     */
    @JvmField
    val headIndices: Array<PgIndex> = indicesFor(nakshaCollection, onHead = true)

    /**
     * The indices of the HISTORY table.
     * @since 3.0
     */
    @JvmField
    val historyIndices: Array<PgIndex> = indicesFor(nakshaCollection, onHead = false)

    /**
     * Tests if the history should be stored.
     * @since 3.0
     */
    val storeHistory: Boolean
        get() = head.storeHistory === StoreMode.ON

    /**
     * Tests if deleted [Tuple] should be kept in _HEAD_; if not they are automatically purged when being deleted.
     * @since 3.0
     */
    val storeDeleted: Boolean
        get() = head.storeDeleted === StoreMode.ON

    /**
     * The weak-reference to this [PgCollection], should be used when the collection should be cached.
     * @since 3.0
     */
    @Suppress("LeakingThis")
    val weakRef = WeakRef(this)
    
    /**
     * The storage in which the collection is located.
     * @since 3.0
     */
    val storage: PgStorage
        get() = catalog.storage

    /**
     * Reads [headRef].
     * @see [headRef]
     * @since 3.0
     */
    val head: NakshaCollection
        get() = headRef.get()

    /**
     * The storage class of the collection.
     */
    var storageClass: PgStorageClass = BaseEnum.getDefined(nakshaCollection.storageClass, PgStorageClass::class) ?: PgStorageClass.Unknown
        internal set

    /**
     * The amount of performance partitions.
     */
    val partitions: Int
        get() = head.partitions

    /**
     * The `HEAD` table, so where to store features into.
     *
     * If this is an ordinary table, that can be partitioned using [PgPlatform.partitionNumber] above the [feature-number][PgColumn.FnColumn].
     *
     * Writing directly into partitions, or reading from them, is discouraged, but in some cases necessary to improve performance drastically. In AWS the speed of every [single-flow](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-network-bandwidth.html) connection is limited to 5 Gbps (10 Gbps when being in the same [cluster placement group](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/placement-strategies.html#placement-groups-cluster)), but still always limited. When the PostgresQL database and the client both have higher bandwidth, then multiple parallel connection need to be used, for example to saturate the HERE temporary or consistent store bandwidth of 200 Gbps, between 20 and 40 connections are needed.
     *
     * **Notes**:
     * - The `TRANSACTION` table is not designed for ultra-high throughput, but rather as a storage that allows easy and fast garbage collection and to query ordered by transaction numbers or _sequence numbers_.
     * - Internal tables do not allow to add or remove indices, while ordinary consumer tables do allow this.
     */
    @JvmField
    val headTable: PgHeadTable = PgHeadTable(this)

    /**
     * The history table of the collection.
     *
     * The history table is always partitioned by the [next-version][PgColumn.NextVersionColumn], which is shifted right by a [shift]. Each [history partition][PgHistoryPartition] is partitioned again the same way that [HEAD][headTable] is partitioned. Not doing this would create a bottleneck when modifying features in parallel, because then the parallel connections would have a congestion in the history, when moving old data into history. The history is therefore managed the same way as [HEAD][headTable], so using the [PgPlatform.partitionNumber] above the [feature-number][PgColumn.FnColumn].
     */
    @JvmField
    val historyTable: PgHistoryTable = PgHistoryTable(this)

    /**
     * Returns the [PgColumn] that corresponds to the given member.
     * @param member the [Member] for which to return the column.
     * @return the [PgColumn] that with the given name; `null` if no such column exists.
     * @since 3.0
     */
    @JsName("getColumnByMember")
    fun column(member: Member): PgColumn? = column(member.id)

    /**
     * Returns the [PgColumn] that has the given name.
     * @param name the name of the column to return.
     * @return the [PgColumn] that with the given name; `null` if no such column exists.
     * @since 3.0
     */
    @JsName("getColumnByName")
    fun column(name: String): PgColumn? {
        for (column in columns) {
            if (column.name == name) return column
        }
        return null
    }

    /**
     * If this is an internal collection.
     *
     * Internal collections have some limitation, for example it is not possible to add or drop indices, nor can they be created through normal methods. They are basically immutable by design, but the content can be read and modified to some degree.
     */
    @JvmField
    val internal: Boolean = id.text.startsWith("naksha~")

    /**
     * Ensures that the [PgHistoryPartition] for the given version exists.
     * @param conn the connection to use to create the partition, if needed.
     * @param version the version to be written.
     * @param session the write session.
     * @since 3.0
     */
    fun prepareWrite(conn: PgConnection, version: Long, session: PgSession) {
        if (!storeHistory) return
        ensureHistoryPartition(conn, historyPartitionNumberOf(version), session)
    }

    fun ensureHistoryPartition(conn: PgConnection, partitionNumber: Int, session: PgSession) {
        if (!storeHistory) return
        if (historyTable.partitions.containsKey(partitionNumber)) return
        if (session.isPartitionPrepared(this, partitionNumber)) return
        catalog.setSearchPath(conn)
        DEBUG_printConnection("ensureHistoryPartition", conn)
        historyTable.createPartition(conn, partitionNumber)
        session.markPartitionPrepared(this, partitionNumber)
    }

    /**
     * Verify the given new _HEAD_ state, ensure that none of the following values is modified:
     * - [NakshaCollection.members] - Ensure that they result in the same [columns].
     * - [NakshaCollection.indices] - Ensure that they result in the same [headIndices] and [historyIndices].
     * - [NakshaCollection.shift] - The shift must not change, because it impacts partitioning.
     * - [NakshaCollection.id] - Must match [id] and the resulting calculated [collectionNumber] must match as well.
     * - [NakshaCollection.storageClass] - Must match [storageClass], changing the storage class is not allowed.
     * - [NakshaCollection.partitions] - Must patch [partitions], changing the number of partitions is not allowed.
     *
     * Actually all other values can be changes, with only some having an impact to this object.
     * @param newHead the new _HEAD_ state to be verified.
     * @throws NakshaException with error [ILLEGAL_STATE] if the columns or indices in the given `newHead` have been changed.
     */
    fun verifyNewHeadState(newHead: NakshaCollection) {
        // TODO: Implement me!
    }
}