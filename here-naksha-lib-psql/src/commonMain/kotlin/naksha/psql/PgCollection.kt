package naksha.psql

import naksha.base.*
import naksha.base.fn.Fn1
import naksha.model.*
import naksha.base.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
import naksha.model.objects.Index
import naksha.model.objects.IndexList
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType.MemberType_C.BYTE_ARRAY
import naksha.model.objects.MemberType.MemberType_C.SPATIAL
import naksha.model.objects.MemberType.MemberType_C.STRING
import naksha.model.objects.MemberType.MemberType_C.TAG_LIST
import naksha.model.objects.MemberType.MemberType_C.TAG_MAP
import naksha.model.objects.MemberType.MemberType_C.TUPLE_NUMBER
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzIndices
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureBytes
import naksha.model.objects.StandardMembers.StandardMembers_C.GlobalBookFeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion
import naksha.model.objects.StandardMembers.StandardMembers_C.Tn
import naksha.model.objects.StoreMode
import naksha.psql.PgColumn.PgColumn_C.EXTENDED
import naksha.psql.PgColumn.PgColumn_C.EXTERNAL
import naksha.psql.PgColumn.PgColumn_C.MAIN
import naksha.psql.PgColumn.PgColumn_C.PLAIN
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A collection is a set of database tables that together form a logical feature container.
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class PgCollection @JvmOverloads constructor(
    /**
     * The [Id] of the catalog in which the collection is located.
     * @since 3.0
     */
    @JvmField
    val catalogId: Id,

    /**
     * The state of the collection.
     * @since 3.0
     */
    @JvmField
    val tuple: Tuple,

    /**
     * The cached **read-only** [NakshaCollection] representing of the [_HEAD_][tuple] state.
     *
     * ### DO NOT MODIFY THIS FEATURE
     * If you need a copy for modification, invoke [head.decodeCollection()][Tuple.decodeCollection].
     * @since 3.0
     */
    @JvmField // TODO: We need to make this immutable, we need better base classes!
    val naksha: NakshaCollection = tuple.decodeCollection(),
) {
    /**
     * The epoch time in milliseconds when created.
     * @since 3.0
     */
    @JvmField
    val cacheCreateMillis: Long = Platform.currentMillis()

    /**
     * The epoch time in milliseconds when last read.
     * @since 3.0
     */
    @JvmField
    var cacheReadMillis: Long = Platform.currentMillis()

    /**
     * Updates [cacheReadMillis] and returns this.
     * @since 3.0
     */
    fun readNow(): PgCollection {
        cacheReadMillis = Platform.currentMillis()
        return this
    }

    /**
     * The identifier of the collection.
     * @since 3.0
     */
    @JvmField
    val id: Id = tuple.id

    /**
     * The name of the table of the collection.
     * @since 3.0
     * @see quotedName
     */
    @JvmField
    val name: String = id.text

    /**
     * The name of the table of the collection, optionally quoted in double quotes.
     * @since 3.0
     * @see name
     */
    @JvmField
    val quotedName: String = quoteIdent(name)

    /**
     * The columns to expect in the table.
     *
     * The columns are filled from the members-book and vice versa. Most of the time the columns match the members, but there are details in which they can differ, i.e. for the tuple-number.
     * @since 3.0
     */
    @JvmField
    val columns: Array<PgColumn> = generateColumns(naksha)

    /**
     * The indices of the HISTORY table.
     * @since 3.0
     */
    @JvmField
    val indices: Array<PgIndex> = generateIndices(naksha, columns)

    /**
     * The cached [TupleNumber] of the _HEAD_ state of the collection.
     * @since 3.0
     */
    @JvmField
    val tupleNumber: TupleNumber = tuple.tupleNumber

    /**
     * The amount of bit the [next-version][PgColumn.NextVersionColumn] should be shifted right to calculate the history-partition. Must be a value between 12 and 42 _(exclusive)_. Some values have a special meaning for dated versions:
     *
     * - `41`: Partitions history by year.
     * - `37`: Partitions history by month _(default)_.
     * - `32`: Partitions history by day.
     *
     * Values being smaller than 32 will generate less than daily history partitions and are rarely useful. It is strongly recommended to leave this setting to the default value of `37`.
     * @since 3.0
     * @see [NakshaCollection.shift]
     */
    @JvmField
    val shift: Int = if (naksha.shift in 12..41) naksha.shift
                     else throw illegalArg("The shift value is out of range: ${naksha.shift}, expected 12 .. 41")

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

    /**
     * Tests if the history should be stored.
     * @since 3.0
     */
    @JvmField
    val storeHistory: Boolean = naksha.storeHistory === StoreMode.ON

    /**
     * Tests if deleted [Tuple] should be kept in _HEAD_; if not they are automatically purged when being deleted.
     * @since 3.0
     */
    @JvmField
    val storeDeleted: Boolean = naksha.storeDeleted === StoreMode.ON

    /**
     * The storage class of the collection.
     * @since 3.0
     */
    @JvmField
    val storageClass: PgStorageClass = JsEnum.getDefined(naksha.storageClass, PgStorageClass::class) ?: PgStorageClass.Unknown

    /**
     * The amount of performance partitions, a value between 1 and 256.
     * @since 3.0
     */
    @JvmField
    val partitions: Int = when (val partitions = naksha.partitions) {
        in 0..1 -> 1
        in 2..256 -> partitions
        else -> throw illegalArg("The partitions are out of range: $partitions, expected: 1 .. 256")
    }

    /**
     * Returns the [PgColumn] that corresponds to the given member.
     * @param member the [Member] for which to return the column.
     * @return the [PgColumn] that with the given name; `null` if no such column exists.
     * @since 3.0
     */
    @JsName("getColumnByMember")
    fun column(member: Member): PgColumn? = column(member.name)

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

    companion object PgCollectionCompanion {
        /**
         * Whether the collection is in backward-compatibility mode: the client declared no [members][NakshaCollection.members] (the raw property is `null`, before [NakshaCollection.validatedMembers] backfills the defaults). In that mode a `null` [indices][NakshaCollection.indices] list falls back to the standard [XyzIndices.ALL]. Captured up-front, because building [columns] materializes the default members and thereby clears the `null` state.
         * @param nakshaCollection the collection to test.
         * @return _true_ if the collection is in backward-compatibility mode; _false_ otherwise.
         * @since 3.0
         */
        private fun backwardCompatibilityRequested(nakshaCollection: NakshaCollection): Boolean = nakshaCollection.members == null

        /**
         * Convert the given member into a [PgColumn], only fails for the standard member [Tn].
         * @param member the member to convert, must not me [Tuple-Number][Tn].
         * @param index the real index in the physical table at which to place the member.
         * @return the [PgColumn] for the member.
         * @throws NakshaException with error [INTERNAL_ERROR], if the given member is [Tn].
         * @since 3.0
         */
        private fun fromMember(member: Member, index: Int): PgColumn {
            val memberType = member.dataType
            if (TUPLE_NUMBER == memberType) {
                throw NakshaException(INTERNAL_ERROR, "TUPLE_NUMBER member type can't be converted using PgCollection.fromMember()")
            }
            // These mandatory members get special storage handling.
            when (member.name) {
                GlobalBookFeatureNumber.name -> return PgColumn(index, member, "STORAGE $PLAIN")
                StandardMembers.Id.name -> return PgColumn(index, member, "STORAGE $PLAIN COLLATE \"C\"")
                FeatureBytes.name -> return PgColumn(index, member, "STORAGE $EXTERNAL")
            }

            return when (memberType) {
                // The standard tuple-number (tn) is handled differently, split into feature-number and version
                // Other tuple-numbers are currently not supported, but they will be, we will store them as byte-array
                BYTE_ARRAY, TUPLE_NUMBER -> PgColumn(index, member, "STORAGE $EXTENDED")
                STRING -> PgColumn(index, member, "STORAGE $MAIN COLLATE \"C\"")
                TAG_MAP, TAG_LIST -> PgColumn(index, member, "STORAGE $MAIN")
                SPATIAL -> PgColumn(index, member, "STORAGE $EXTERNAL")
                else -> PgColumn(index, member, "STORAGE $PLAIN")
            }
        }

        /**
         * Read the [members][NakshaCollection.members] from the given [NakshaCollection] and turn them into a [PgColumn] list. If the members of the given [NakshaCollection] and not yet [ordered by index][MemberList.isSortedByIndex], then the method will invoke a [sort by type][MemberList.verify].
         * @param naksha the collection from which to extract the [PgColumn]'s.
         * @return an array with all extracted [PgColumn]'s.
         * @since 3.0
         */
        private fun generateColumns(naksha: NakshaCollection): Array<PgColumn> {
            val members: MemberList = naksha.validatedMembers
            if (!members.isSortedByIndex()) {
                members.verify()
            }
            var i = 0
            // We split tuple-number into `fn` and `version`, therefore we need size + 1.
            return Array(members.size + 1) { index ->
                when (index) {
                    // The first three members are fixed to:
                    0 -> PgColumn.FnColumn
                    1 -> PgColumn.VersionColumn
                    2 -> PgColumn.NextVersionColumn
                    else -> {
                        val col: PgColumn
                        var member = members[i++] ?: throw illegalState("Member #${i-1} is null")
                        var name = member.name
                        // Tn is already added as `fn` and `version`, and `next_version` is as well already added.
                        // We loop, because Tn and NextVersion could be located behind each other, then we need to skip
                        // both (and we do not care about order, so instead of complicated two ifs one loop).
                        while (name==Tn.name || name==NextVersion.name) {
                            member = members[i++] ?: throw illegalState("Member #${i-1} is null")
                            name = member.name
                        }
                        col = fromMember(member, index)
                        col
                    }
                }
            }
        }

        private fun generateIndices(naksha: NakshaCollection, columns: Array<PgColumn>): Array<PgIndex> {
            val indices = IndexList()
            val declared: List<Index?> = naksha.indices?.toList() ?: XyzIndices.ALL
            indices.setCapacity(declared.size + 1)
            for (declaredIndex in declared) {
                if (declaredIndex != null && !indices.contains(declaredIndex)) indices.add(declaredIndex)
            }
            return Array(indices.size) { i ->
                val index = indices[i] ?: throw illegalState("Index #$i must not be null")
                val indexName = index.name

                val nakshaOn = index.on
                val nakshaOrder = index.order
                val on: ArrayList<Pair<PgColumn, PgOrder>> = ArrayList(nakshaOn.size)
                on@ for (i in 0 ..< nakshaOn.size) {
                    val name = nakshaOn[i] ?: throw illegalState("Index '$indexName->on[$i]' must not be null")
                    val order = if (nakshaOrder != null
                        && i < nakshaOrder.size
                        && PgOrder.ASC.name.equals(nakshaOrder[i], true)
                    ) PgOrder.ASC else PgOrder.DESC
                    var ci = -1
                    while (++ci < columns.size) {
                        val column = columns[ci]
                        if (column.name == name) {
                            on.add(Pair(column, order))
                            continue@on
                        }
                    }
                    throw illegalArg("Index '$indexName->on[$i]' refers to member '$name', but no such member exists")
                }
                val nakshaInclude = index.include
                val include: ArrayList<PgColumn>? = if (nakshaInclude.isNullOrEmpty()) null else {
                    val list = ArrayList<PgColumn>(nakshaInclude.size)
                    include@ for (i in 0 ..< nakshaInclude.size) {
                        val name = nakshaInclude[i] ?: throw illegalState("Index '$indexName->include[$i]' must not be null")
                        for (column in columns) {
                            if (column.name == name) {
                                list.add(column)
                                continue@include
                            }
                        }
                        throw illegalArg("Index '$indexName->include[$i]' refers to member '$name', but no such member exists")
                    }
                    list
                }
                PgIndex(
                    indexName,
                    on.toTypedArray(),
                    include?.toTypedArray() ?: emptyArray(),
                    index.isConditional()
                )
            }
        }
    }
}
