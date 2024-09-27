@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.model.*
import naksha.model.FetchMode.FetchMode_C.FETCH_ID
import naksha.model.FetchMode.FetchMode_C.FETCH_META
import naksha.model.FetchMode.FetchMode_C.FETCH_FEATURE
import naksha.model.FetchMode.FetchMode_C.FETCH_GEOMETRY
import naksha.model.FetchMode.FetchMode_C.FETCH_REFERENCE_POINT
import naksha.model.FetchMode.FetchMode_C.FETCH_TAGS
import naksha.model.FetchMode.FetchMode_C.FETCH_ATTACHMENT
import naksha.model.request.ResultTuple
import naksha.psql.PgColumn.PgColumnCompanion
import naksha.psql.PgColumn.PgColumnCompanion.app_id
import naksha.psql.PgColumn.PgColumnCompanion.attachment
import naksha.psql.PgColumn.PgColumnCompanion.author
import naksha.psql.PgColumn.PgColumnCompanion.author_ts
import naksha.psql.PgColumn.PgColumnCompanion.change_count
import naksha.psql.PgColumn.PgColumnCompanion.created_at
import naksha.psql.PgColumn.PgColumnCompanion.feature
import naksha.psql.PgColumn.PgColumnCompanion.flags
import naksha.psql.PgColumn.PgColumnCompanion.geo
import naksha.psql.PgColumn.PgColumnCompanion.geo_grid
import naksha.psql.PgColumn.PgColumnCompanion.hash
import naksha.psql.PgColumn.PgColumnCompanion.id
import naksha.psql.PgColumn.PgColumnCompanion.origin
import naksha.psql.PgColumn.PgColumnCompanion.ptxn
import naksha.psql.PgColumn.PgColumnCompanion.puid
import naksha.psql.PgColumn.PgColumnCompanion.ref_point
import naksha.psql.PgColumn.PgColumnCompanion.selectMetaBinary
import naksha.psql.PgColumn.PgColumnCompanion.store_number
import naksha.psql.PgColumn.PgColumnCompanion.tags
import naksha.psql.PgColumn.PgColumnCompanion.tuple_number
import naksha.psql.PgColumn.PgColumnCompanion.txn
import naksha.psql.PgColumn.PgColumnCompanion.txn_next
import naksha.psql.PgColumn.PgColumnCompanion.type
import naksha.psql.PgColumn.PgColumnCompanion.uid
import naksha.psql.PgColumn.PgColumnCompanion.updated_at
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A helper class to load tuples from the database, exchanging information with the caching subsystem ([naksha.model.NakshaCache]).
 *
 * Every tuple persists out of the following parts that can be loaded:
 * - [id, tuple_number, flags][FETCH_ID] || [meta][FETCH_META] (which includes the first ones)
 * - [feature][FETCH_FEATURE]
 * - [geometry][FETCH_GEOMETRY]
 * - [reference-point][FETCH_REFERENCE_POINT]
 * - [tags][FETCH_TAGS]
 * - [attachment][FETCH_ATTACHMENT]
 *
 * This means there are theoretically 65 possible combinations. The thing is, we may have already some part of the information in the cache, and only want to load what is missing. This is the job of this loader. It will check the cache what we have already, and then calculate what is missing (if any), creating a query for this _fetch-bits_, adding all queries into a hash-map, eventually generating one big `union all` query out of all needed combinations. After loading the data, it will combine the loaded data with the data from the cache, merge it, put it back into cache, and return the new tuple.
 *
 * @constructor A tuple loader.
 * @property storage the storage from which to load.
 * @property loadHistory if tuples should be fetched from history.
 * @property connection the connection to use when loading data from database; if _null_, then the _admin-connection_ is used.
 * @since 3.0.0
 */
@JsExport
class PgTupleLoader(val storage: PgStorage, var loadHistory: Boolean, var connection: PgConnection? = null) {
    companion object PgTupleLoader_C {
        private val SELECT_META = "SELECT gzip(bytea_agg($selectMetaBinary)) AS meta_all, null AS $tuple_number, null AS $tags, null AS $ref_point, null AS $geo, null AS $feature, null AS $attachment"
        private val SELECT_OTHER = "SELECT null AS meta_all, $tuple_number, $tags, $ref_point, $geo, $feature, $attachment"
        private val NULL_TUPLE_NUMBER = TupleNumber(StoreNumber(0,Int64(0),0), Version(Int64(0)),0)
    }

    private val cache = NakshaCache.tupleCache(storage.id)
    private var index = 0
    private val indexOf = HashMap<TupleNumber, Int>()
    private val fromCollection = HashMap<PgCollection,MutableList<TupleNumber>>()
    private val results: MutableList<PgTuple> = mutableListOf()

    /**
     * Prepare to load the tuple with the given tuple-number.
     * @param tupleNumber the [TupleNumber] of the tuple to load.
     * @param fetchBits the parts that are needed by the client.
     * @return this.
     */
    fun add(tupleNumber: TupleNumber, fetchBits: FetchBits): PgTupleLoader {
        val indexOf = this.indexOf
        if (tupleNumber in indexOf) return this
        indexOf[tupleNumber] = index++
        val tuple = cache[tupleNumber]
        if (tuple != null && tuple.isComplete()) {
            results.add(PgTuple(storage, tupleNumber, tuple))
            return this
        }
        results.add(PgTuple(storage, tupleNumber))
        val map = storage[tupleNumber.mapNumber()] ?: return this
        val collection = map[tupleNumber.collectionNumber()] ?: return this
        var tupleNumberList = fromCollection[collection]
        if (tupleNumberList == null) {
            tupleNumberList = mutableListOf(tupleNumber)
            fromCollection[collection] = tupleNumberList
        } else if (!tupleNumberList.contains(tupleNumber)){
            tupleNumberList.add(tupleNumber)
        }
        return this
    }

    /**
     * Prepare to load more of the given tuple.
     * @param tuple the [Tuple] to complete.
     * @param fetchBits the parts that are needed by the client.
     * @return this.
     */
    @JsName("addTuple")
    fun add(tuple: Tuple, fetchBits: FetchBits): PgTupleLoader {
        add(tuple.tupleNumber, fetchBits)
        return this
    }

    /**
     * Prepare to load more of the given tuple.
     * @param resultTuple the [ResultTuple] to complete.
     * @param fetchBits the parts that are needed by the client.
     * @return this.
     */
    @JsName("addResultTuple")
    fun add(resultTuple: ResultTuple?, fetchBits: FetchBits): PgTupleLoader {
        if (resultTuple != null) add(resultTuple.tupleNumber, fetchBits) else results.add(PgTuple(storage, NULL_TUPLE_NUMBER))
        return this
    }

    /**
     * Load all prepared tuples from the database or cache.
     * @return the loaded and merged tuples in order as given by calling [add], contains _null_ if no such [Tuple] was found.
     */
    fun execute(): List<Tuple?> {
        val sb = StringBuilder()
        val fromCollection = this.fromCollection
        if (fromCollection.size > 0) {
            var i = 1
            val args = mutableListOf<Array<ByteArray>>()
            for (e in fromCollection) {
                val collection = e.key
                val tupleNumberList = e.value
                if (sb.isNotEmpty()) sb.append("UNION ALL ")
                sb.append("$SELECT_META FROM ${collection.head.quotedName} WHERE $tuple_number = ANY(\$$i::bytea[])\n")
                sb.append("UNION ALL $SELECT_OTHER FROM ${collection.head.quotedName} WHERE $tuple_number = ANY(\$$i::bytea[])\n")
                val history = collection.history
                if (loadHistory && history != null) {
                    sb.append("UNION ALL $SELECT_META FROM ${history.quotedName} WHERE $tuple_number = ANY(\$$i::bytea[])\n")
                    sb.append("UNION ALL $SELECT_OTHER FROM ${history.quotedName} WHERE $tuple_number = ANY(\$$i::bytea[])\n")
                }
                args.add(Array(tupleNumberList.size) { tupleNumberList[it].toByteArray() })
                i++
            }
            val SQL = sb.toString()
            val connection = this.connection
            val conn = connection ?: storage.adminConnection(storage.adminOptions)
            try {
                val cursor = conn.execute(SQL, args.toTypedArray())
                cursor.use {
                    val INT64_NULL = Int64(0)
                    while (cursor.next()) {
                        val rawMeta = cursor.column("meta_all") as ByteArray?
                        if (rawMeta != null) {
                            // Decode metadata.
                            val bytes = Platform.gzipInflate(rawMeta)
                            val view = Platform.newDataView(bytes)
                            var i = 0
                            while (i < bytes.size) {
                                val tupleNumber = TupleNumber.fromByteArray(bytes, i); i += 20
                                val flags = dataview_get_int32(view, i); i += 4
                                val updated_at = dataview_get_int64(view, i); i += 8
                                val created_at = dataview_get_int64(view, i); i += 8
                                val author_ts = dataview_get_int64(view, i); i += 8
                                val txn_next = dataview_get_int64(view, i); i += 8
                                val ptxn = dataview_get_int64(view, i); i += 8
                                val puid = dataview_get_int32(view, i); i += 4
                                val change_count = dataview_get_int32(view, i); i += 4
                                val hash = dataview_get_int32(view, i); i += 4
                                val geo_grid = dataview_get_int32(view, i); i += 4

                                // Note: The i++ after the string decoding skips the ASCII-0, by which all strings are terminated.
                                var start = i
                                i = bytes.indexOf(0, start);
                                val id = bytes.decodeToString(start, i, false); i++

                                start = i
                                i = bytes.indexOf(0, start)
                                val app_id = bytes.decodeToString(start, i, false); i++

                                start = i
                                i = bytes.indexOf(0, start)
                                val author = bytes.decodeToString(start, i, false); i++

                                start = i
                                i = bytes.indexOf(0, start)
                                val type = bytes.decodeToString(start, i, false); i++

                                start = i
                                i = bytes.indexOf(0, start)
                                val origin = bytes.decodeToString(start, i, false); i++

                                // Decoding done, now find the tuple and adjust.
                                val index = indexOf[tupleNumber]
                                val pgTuple = if (index != null) results[index] else null
                                if (pgTuple != null) {
                                    pgTuple.meta = Metadata(
                                        storeNumber = tupleNumber.storeNumber,
                                        updatedAt = updated_at,
                                        createdAt = if (created_at == INT64_NULL) updated_at else created_at,
                                        authorTs = if (author_ts == INT64_NULL) updated_at else author_ts,
                                        nextVersion = if (txn_next == INT64_NULL) null else Version(txn_next),
                                        version = tupleNumber.version,
                                        prevVersion = if (ptxn == INT64_NULL) null else Version(ptxn),
                                        uid = tupleNumber.uid,
                                        puid = if (puid == 0) null else puid,
                                        hash = hash,
                                        changeCount = if (change_count <= 0) 1 else change_count,
                                        geoGrid = geo_grid,
                                        flags = flags,
                                        id = id,
                                        appId = app_id,
                                        author = if (author.isEmpty()) null else author,
                                        type = if (type.isEmpty()) null else type,
                                        origin = if (origin.isEmpty()) null else origin
                                    )
                                    pgTuple.fetchBits = pgTuple.fetchBits.withMeta()
                                } else logger.error("Metadata with invalid tuple-number found, that is not in result, this must not happen: {}", RuntimeException())
                            }
                        } else {
                            val tupleNumber = TupleNumber.fromByteArray(cursor[tuple_number])
                            val index = indexOf[tupleNumber]
                            val pgTuple = if (index != null) results[index] else null
                            if (pgTuple != null) {
                                pgTuple.geo = cursor.column(geo) as ByteArray?
                                pgTuple.feature = cursor.column(feature) as ByteArray?
                                pgTuple.referencePoint = cursor.column(ref_point) as ByteArray?
                                pgTuple.tags = cursor.column(tags) as ByteArray?
                                pgTuple.attachment = cursor.column(attachment) as ByteArray?
                                pgTuple.fetchBits = pgTuple.fetchBits.withGeometry().withFeature().withReferencePoint().withTags().withAttachment()
                            } else logger.error("Result with tuple-number that is not in result, this must not happen: {}", RuntimeException())
                        }
                    }
                }
            } finally {
                // Close admin connection, but do not close custom connection.
                if (connection == null) conn.close()
            }
        }
        val r = mutableListOf<Tuple?>()
        for (pgTuple in results) r.add(pgTuple.toTuple())
        return r
    }

    /**
     * Helper method to read a [Tuple] from a [PgCursor].
     *
     * It automatically detects which parts have been selected, but requires that at least:
     * - either [tuple_number][PgColumn.tuple_number] or [txn][PgColumn.txn], [store_number][PgColumn.store_number] and [uid][PgColumn.uid]
     * - [flags][PgColumn.flags]
     * - [id][PgColumn.id]
     *
     * Have been selected, because otherwise it is not possible to construct the [Tuple], which requires the `tuple-number`, `id` and `flags`. Without the `flags` decoding of parts is not possible.
     * @param storage the storage from which to read.
     * @param cursor the cursor to read.
     * @return the read tuple.
     */
    fun readTupleFromCursor(storage: PgStorage, cursor: PgCursor): Tuple {
        val tupleNumberByteArray: ByteArray? = cursor.column(tuple_number) as ByteArray?
        val tupleNumber = if (tupleNumberByteArray != null) TupleNumber.fromByteArray(tupleNumberByteArray) else {
            val _txn: Int64 = cursor[txn]
            TupleNumber(
                cursor[store_number],
                Version(_txn),
                cursor[uid]
            )
        }

        // We always need at least tuple-number and id
        var fetchBits: FetchBits = FetchMode.FETCH_ID
        val id: String = cursor[id]
        val flags: Flags = cursor[flags]

        val updatedAt: Int64? = cursor.column(updated_at) as Int64?
        val metadata = if (updatedAt != null) {
            fetchBits = fetchBits.withMeta()
            val createdAt = cursor.column(created_at) as Int64?
            val authorTs = cursor.column(author_ts) as Int64?
            val _txn_next = cursor.column(txn_next) as Int64?
            val _ptxn = cursor.column(ptxn) as Int64?
            Metadata(
                storeNumber = tupleNumber.storeNumber,
                updatedAt = updatedAt,
                createdAt = createdAt ?: updatedAt,
                authorTs = authorTs ?: updatedAt,
                nextVersion = if (_txn_next != null) Version(_txn_next) else null,
                version = tupleNumber.version,
                prevVersion = if (_ptxn != null) Version(_ptxn) else null,
                uid = tupleNumber.uid,
                puid = cursor.column(puid) as Int?,
                hash = cursor[hash],
                changeCount = cursor[change_count],
                geoGrid = cursor[geo_grid],
                flags = flags,
                id = id,
                appId = cursor[app_id],
                author = cursor.column(author) as String?,
                type = cursor.column(type) as String?,
                origin = cursor.column(origin) as String?
            )
        } else null
        if (feature in cursor) fetchBits = fetchBits.withFeature()
        if (geo in cursor) fetchBits = fetchBits.withGeometry()
        if (ref_point in cursor) fetchBits = fetchBits.withReferencePoint()
        if (tags in cursor) fetchBits = fetchBits.withTags()
        if (attachment in cursor) fetchBits = fetchBits.withAttachment()
        return Tuple(
            storage = storage,
            tupleNumber = tupleNumber,
            fetchBits = fetchBits,
            meta = metadata,
            id = id,
            flags = flags,
            feature = cursor.column(feature) as ByteArray?,
            geo = cursor.column(geo) as ByteArray?,
            referencePoint = cursor.column(ref_point) as ByteArray?,
            tags = cursor.column(tags) as ByteArray?,
            attachment = cursor.column(attachment) as ByteArray?
        )
    }
}