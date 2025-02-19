@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.request.FeatureTuple
import naksha.psql.PgColumn.PgColumnCompanion.app_id
import naksha.psql.PgColumn.PgColumnCompanion.attachment
import naksha.psql.PgColumn.PgColumnCompanion.author
import naksha.psql.PgColumn.PgColumnCompanion.author_ts
import naksha.psql.PgColumn.PgColumnCompanion.created_at
import naksha.psql.PgColumn.PgColumnCompanion.feature
import naksha.psql.PgColumn.PgColumnCompanion.flags
import naksha.psql.PgColumn.PgColumnCompanion.geo
import naksha.psql.PgColumn.PgColumnCompanion.here_tile
import naksha.psql.PgColumn.PgColumnCompanion.hash
import naksha.psql.PgColumn.PgColumnCompanion.id
import naksha.psql.PgColumn.PgColumnCompanion.origin
import naksha.psql.PgColumn.PgColumnCompanion.ref_point
import naksha.psql.PgColumn.PgColumnCompanion.tags
import naksha.psql.PgColumn.PgColumnCompanion.tn
import naksha.psql.PgColumn.PgColumnCompanion.txn_next
import naksha.psql.PgColumn.PgColumnCompanion.ft
import naksha.psql.PgColumn.PgColumnCompanion.updated_at
import kotlin.js.JsName

/*

  PgTupleQuery
    selectHead(collection, where)
    selectDeleted(collection, where)
    selectFromMeta(collection, where)
    selectVersion(collection, version, where) // basically is minVersion=0, maxVersion=version, versions=1
    selectHistoric(collection, minVersion, maxVersion, versions, where)
    execute() -> PgResultSet
  PgTupleLoader
    add(tupleNumber, force, fetchMode)
    addAll(resultSet: PgResultSet)
    addAll(list: FeatureTupleList)
    addAll(tupleNumbers: TupleNumberList)
    addAll(tupleNumbers: TupleNumberBinaryArray)
    execute()
    // After execute:
    getLoaded(): Array<PgTuple>
    copyInto(list: FeatureTupleList)

*/

/**
 * An internal helper class to load tuples from the database.
 *
 * We can optimize the loading to only what the client needs, which is a combination of:
 * - [meta][Tuple.meta] - which will always be loaded, when not already cached.
 * - [feature][Tuple.feature] and [tags][Tuple.tags].
 * - [geometry][GEOMETRY_BIT] and [reference-point][Tuple.referencePoint].
 * - [attachment][ATTACHMENT_BIT].
 *
 * This means there are actually 16 possible combinations (if we include the possibility that nothing need to be loaded). The thing is, we may have already some part of the information in the cache, and only want to load what is missing. This is the job of this loader. It checks the cache what we have already, and then calculate what is missing (if any), creating the needed queries for the missing data only, and then execute the queries, updating the cache.
 *
 * The loader may, if no connection to be used was given explicitly, use multiple connections to load the data in parallel.
 *
 * @constructor A tuple loader.
 * @property storage the storage from which to load.
 * @property loadHistory if tuples should be fetched from history.
 * @property connection the connection to use when loading data from database; if _null_, then the _admin-connection_ is used.
 * @since 3.0.0
 */
internal class PgTupleLoader(val storage: PgStorage, var loadHistory: Boolean, var connection: PgConnection? = null) {
//    companion object PgTupleLoader_C {
//        // when selecting tuple-numbers, do: int8send(naksha_storage_number())||bytea_agg($tuple_number||int8send($flags))
//        /**
//         * We select all metadata that is needed in a single row and column, using an aggregation function. This has the big advantage, that the used GZIP compression function is only invoked ones for all rows, even if we select thousands of them, and it can be more effective this way, because most of the values are repeating in all rows!
//         */
//        private val SELECT_META = """SELECT null AS $tn, gzip(bytea_agg(
//($tn
//||int8send($flags)
//||int8send($updated_at)
//||int8send(coalesce($created_at, 0::bigint))
//||int8send(coalesce($author_ts, 0::bigint))
//||int8send(coalesce($txn_next, 0::bigint))
//||int8send(coalesce($ptxn, 0::bigint))
//||int4send(coalesce($puid, 0))
//||int4send(coalesce($change_count, 1))
//||int4send(coalesce($hash, 0))
//||int4send(coalesce($here_tile,0))
//||$id::bytea||'\x00'::bytea
//||coalesce($app_id,'')::bytea||'\x00'::bytea
//||coalesce($author,'')::bytea||'\x00'::bytea
//||coalesce($ft,'')::bytea||'\x00'::bytea
//||coalesce($origin,'')::bytea||'\x00'::bytea)
//)) AS meta, null AS $geo, null AS $ref_point, null AS $feature, null AS $tags, null AS $attachment""".trimEnd() // FROM ...
//
//        /**
//         * Dependent on what parts are not yet cached, and which are needed, we will only fetch needed data.
//         */
//        private val SELECT = arrayOf(
//            // fetch nothing
//            null,
//            // 1 = FETCH_META (1)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            null,
//            // 2 = FETCH_GEOMETRY (2)
//            "SELECT $tn, null AS meta, $geo, $ref_point, null AS $feature, null AS $tags, null AS $attachment",
//            // 3 = FETCH_META (1) + FETCH_GEOMETRY (2)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            "SELECT $tn, null AS meta, $geo, $ref_point, null AS $feature, null AS $tags, null AS $attachment",
//            // 4 = FETCH_FEATURE (4)
//            "SELECT $tn, null AS meta, null AS $geo, null AS $ref_point, $feature, $tags, null AS $attachment",
//            // 5 = FETCH_META (1) + FETCH_FEATURE (4)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            "SELECT $tn, null AS meta, null AS $geo, null AS $ref_point, $feature, $tags, null AS $attachment",
//            // 6 = FETCH_GEOMETRY (2) + FETCH_FEATURE (4)
//            "SELECT $tn, null AS meta, $geo, $ref_point, $feature, $tags, null AS $attachment",
//            // 7 = FETCH_META (1) + FETCH_GEOMETRY (2) + FETCH_FEATURE (4)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            "SELECT $tn, null AS meta, $geo, $ref_point, $feature, $tags, null AS $attachment",
//            // 8 = FETCH_ATTACHMENT (8)
//            "SELECT $tn, null AS meta, null AS $geo, null AS $ref_point, null AS $feature, null AS $tags, $attachment",
//            // 9 = FETCH_META (1) + FETCH_ATTACHMENT (8)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            "SELECT $tn, null AS meta, null AS $geo, null AS $ref_point, null AS $feature, null AS $tags, $attachment",
//            // 10 = FETCH_GEOMETRY (2) + FETCH_ATTACHMENT (8)
//            "SELECT $tn, null AS meta, $geo, $ref_point, null AS $feature, null AS $tags, $attachment",
//            // 11 = FETCH_META (1) + FETCH_GEOMETRY (2) + FETCH_ATTACHMENT (8)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            "SELECT $tn, null AS meta, $geo, $ref_point, null AS $feature, null AS $tags, $attachment",
//            // 12 = FETCH_FEATURE (4) + FETCH_ATTACHMENT (8)
//            "SELECT $tn, null AS meta, null AS $geo, null AS $ref_point, $feature, $tags, $attachment",
//            // 13 = FETCH_META (1) + FETCH_FEATURE (4) + FETCH_ATTACHMENT (8)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            "SELECT $tn, null AS meta, null AS $geo, null AS $ref_point, $feature, $tags, $attachment",
//            // 14 = FETCH_GEOMETRY (2) + FETCH_FEATURE (4) + FETCH_ATTACHMENT (8)
//            "SELECT $tn, null AS meta, $geo, $ref_point, $feature, $tags, $attachment",
//            // 15 = FETCH_META (1) + FETCH_GEOMETRY (2) + FETCH_FEATURE (4) + FETCH_ATTACHMENT (8)
//            //                     meta is selected in a dedicated single row/column to improve compression rate!
//            "SELECT $tn, null AS meta, $geo, $ref_point, $feature, $tags, $attachment",
//        ) // FROM ...
//    }
//
//    /**
//     * The [TupleHeapCache] to use to check for existing tuples.
//     */
//    private val cache = NakshaCache.getTupleCache(storage)
//
//    /**
//     * The index of the last [PgTuple].
//     */
//    private var index = 0
//
//    /**
//     * The result-cache.
//     */
//    private val results: MutableList<PgTuple> = mutableListOf()
//
//    /**
//     * A map to quickly tests if a certain tuple is already scheduled for fetching, the value is the index into [results].
//     */
//    private val indexOf = HashMap<TupleNumber, Int>()
//
//    /**
//     * A map of all collections to query, and all tuple-numbers to query from this collection; basically used to group queries by collection.
//     */
//    private val fromCollection = HashMap<PgCollection,MutableList<TupleNumber>>()
//
//    /**
//     * Prepare to load the tuple with the given tuple-number.
//     * @param tupleNumber the [TupleNumber] of the tuple to load.
//     * @param fetchMode the parts that are needed by the client.
//     * @return this.
//     */
//    fun add(tupleNumber: TupleNumber, fetchMode: FetchMode = FETCH_ALL): PgTupleLoader {
//        val indexOf = this.indexOf
//        if (tupleNumber in indexOf) {
//            val i = indexOf[tupleNumber] ?: throw NakshaException(ILLEGAL_STATE, "Expected to find a valid index for tuple-number $tupleNumber")
//            if (i < 0 || i >= results.size) throw NakshaException(ILLEGAL_STATE, "Expected to find a valid index for tuple-number $tupleNumber")
//            val pgTuple = results[i]
//            pgTuple.fetchMode = (pgTuple.fetchMode or fetchMode) and FETCH_MASK
//            return this
//        }
//        indexOf[tupleNumber] = index++
//        val pgTuple = PgTuple(storage, tupleNumber, tuple = cache[tupleNumber])
//        results.add(pgTuple)
//
//        val map = storage[tupleNumber.mapNumber()] ?: return this
//        val collection = map.pgCollection(tupleNumber.collectionNumber()) ?: return this
//        var tupleNumberList = fromCollection[collection]
//        if (tupleNumberList == null) {
//            tupleNumberList = mutableListOf(tupleNumber)
//            fromCollection[collection] = tupleNumberList
//        } else if (!tupleNumberList.contains(tupleNumber)){
//            tupleNumberList.add(tupleNumber)
//        }
//        return this
//    }
//
//    /**
//     * Prepare to load more of the given tuple.
//     * @param tuple the [Tuple] to complete.
//     * @param fetchMode the parts that are needed by the client.
//     * @return this.
//     */
//    @JsName("addTuple")
//    fun add(tuple: Tuple, fetchMode: FetchMode = FETCH_ALL): PgTupleLoader {
//        add(tuple.tupleNumber, fetchMode)
//        return this
//    }
//
//    /**
//     * Prepare to load more of the given tuple.
//     * @param featureTuple the [FeatureTuple] to complete.
//     * @param fetchMode the parts that are needed by the client.
//     * @return this.
//     */
//    @JsName("addResultTuple")
//    fun add(featureTuple: FeatureTuple?, fetchMode: FetchMode = FETCH_ALL): PgTupleLoader {
//        if (featureTuple != null) add(featureTuple.tupleNumber, fetchMode) else results.add(PgTuple(storage, TupleNumber.HEAD))
//        return this
//    }
//
//    /**
//     * Load all prepared tuples from the database or cache.
//     * @return the loaded and merged tuples in order as given by calling [add], contains _null_ if no such [Tuple] was found.
//     */
//    fun execute(): List<Tuple?> {
//        val sb = StringBuilder()
//        val fromCollection = this.fromCollection
//        if (fromCollection.size > 0) {
//            var j = 1
//            val args = mutableListOf<Array<ByteArray>>()
//            for (e in fromCollection) {
//                val collection = e.key
//                val tupleNumberList = e.value
//                if (sb.isNotEmpty()) sb.append("UNION ALL ")
//                sb.append("$SELECT_META FROM ${collection.head.quotedName} WHERE $tn = ANY(\$$j::bytea[])\n")
//                sb.append("UNION ALL $SELECT_DATA FROM ${collection.head.quotedName} WHERE $tn = ANY(\$$j::bytea[])\n")
//                val history = collection.history
//                if (loadHistory && history != null) {
//                    sb.append("UNION ALL $SELECT_META FROM ${history.quotedName} WHERE $tn = ANY(\$$j::bytea[])\n")
//                    sb.append("UNION ALL $SELECT_DATA FROM ${history.quotedName} WHERE $tn = ANY(\$$j::bytea[])\n")
//                }
//                args.add(Array(tupleNumberList.size) { tupleNumberList[it].toByteArray() })
//                j++
//            }
//            val SQL = sb.toString()
//            val connection = this.connection
//            val conn = connection ?: storage.adminConnection(storage.adminOptions)
//            try {
//                val cursor = conn.execute(SQL, args.toTypedArray())
//                cursor.use {
//                    val INT64_NULL = Int64(0)
//                    while (cursor.next()) {
//                        val rawMeta = cursor.column("meta_all") as ByteArray?
//                        if (rawMeta != null) {
//                            // Decode metadata.
//                            val bytes = Platform.gzipInflate(rawMeta)
//                            val view = Platform.newDataView(bytes)
//                            var i = 0
//                            while (i < bytes.size) {
//                                val tupleNumber = TupleNumber.fromFullVariant(bytes, i); i += 20
//                                val flags = dataview_get_int32(view, i); i += 4
//                                val updated_at = dataview_get_int64(view, i); i += 8
//                                val created_at = dataview_get_int64(view, i); i += 8
//                                val author_ts = dataview_get_int64(view, i); i += 8
//                                val txn_next = dataview_get_int64(view, i); i += 8
//                                val ptxn = dataview_get_int64(view, i); i += 8
//                                val puid = dataview_get_int32(view, i); i += 4
//                                val change_count = dataview_get_int32(view, i); i += 4
//                                val hash = dataview_get_int32(view, i); i += 4
//                                val geo_grid = dataview_get_int32(view, i); i += 4
//
//                                // Note: The i++ after the string decoding skips the ASCII-0, by which all strings are terminated.
//                                var start = i
//                                i = bytes.indexOf(0, start);
//                                val id = bytes.decodeToString(start, i, false); i++
//
//                                start = i
//                                i = bytes.indexOf(0, start)
//                                val app_id = bytes.decodeToString(start, i, false); i++
//
//                                start = i
//                                i = bytes.indexOf(0, start)
//                                val author = bytes.decodeToString(start, i, false); i++
//
//                                start = i
//                                i = bytes.indexOf(0, start)
//                                val type = bytes.decodeToString(start, i, false); i++
//
//                                start = i
//                                i = bytes.indexOf(0, start)
//                                val origin = bytes.decodeToString(start, i, false); i++
//
//                                // Decoding done, now find the tuple and adjust.
//                                val index = indexOf[tupleNumber]
//                                val pgTuple = if (index != null) results[index] else null
//                                if (pgTuple != null) {
//                                    pgTuple.meta = Metadata(
//                                        storageNumber = storage.number,
//                                        storeNumber = tupleNumber.storeNumber,
//                                        updatedAt = updated_at,
//                                        createdAt = if (created_at == INT64_NULL) updated_at else created_at,
//                                        authorTs = if (author_ts == INT64_NULL) updated_at else author_ts,
//                                        nextVersion = if (txn_next == INT64_NULL) null else Version(txn_next),
//                                        version = tupleNumber.version,
//                                        prevVersion = if (ptxn == INT64_NULL) null else Version(ptxn),
//                                        uid = tupleNumber.uid,
//                                        puid = if (puid == 0) null else puid,
//                                        hash = hash,
//                                        changeCount = if (change_count <= 0) 1 else change_count,
//                                        hereTile = geo_grid,
//                                        flags = flags,
//                                        id = id,
//                                        appId = app_id,
//                                        author = if (author.isEmpty()) null else author,
//                                        ft = if (type.isEmpty()) null else type,
//                                        originTupleNumber = if (origin.isEmpty()) null else origin
//                                    )
//                                } else logger.error("Metadata with invalid tuple-number found, that is not in result, this must not happen: {}", RuntimeException())
//                            }
//                        } else {
//                            val tupleNumber = TupleNumber.fromFullVariant(cursor[tn])
//                            val index = indexOf[tupleNumber]
//                            val pgTuple = if (index != null) results[index] else null
//                            if (pgTuple != null) {
//                                pgTuple.geo = cursor.column(geo) as ByteArray?
//                                pgTuple.feature = cursor.column(feature) as ByteArray?
//                                pgTuple.referencePoint = cursor.column(ref_point) as ByteArray?
//                                pgTuple.tags = cursor.column(tags) as ByteArray?
//                                pgTuple.attachment = cursor.column(attachment) as ByteArray?
//                                pgTuple.fetchMode = pgTuple.fetchMode.withGeometry().withFeature().withAttachment()
//                            } else logger.error("Result with tuple-number that is not in result, this must not happen: {}", RuntimeException())
//                        }
//                    }
//                }
//            } finally {
//                // Close admin connection, but do not close custom connection.
//                if (connection == null) conn.close()
//            }
//        }
//        val r = mutableListOf<Tuple?>()
//        for (pgTuple in results) r.add(pgTuple.toTuple())
//        return r
//    }
//
//    /**
//     * Helper method to read a [Tuple] from a [PgCursor].
//     *
//     * It automatically detects which parts have been selected, but requires that at least:
//     * - either [tuple_number][PgColumn.tn] or [txn][PgColumn.txn], [store_number][PgColumn.store_number] and [uid][PgColumn.uid]
//     * - [flags][PgColumn.flags]
//     * - [id][PgColumn.id]
//     *
//     * Have been selected, because otherwise it is not possible to construct the [Tuple], which requires the `tuple-number`, `id` and `flags`. Without the `flags` decoding of parts is not possible.
//     * @param storage the storage from which to read.
//     * @param cursor the cursor to read.
//     * @return the read tuple.
//     */
//    fun readTupleFromCursor(storage: PgStorage, cursor: PgCursor): Tuple {
//        val tupleNumberByteArray: ByteArray? = cursor.column(tn) as ByteArray?
//        val tupleNumber = if (tupleNumberByteArray != null) TupleNumber.fromFullVariant(tupleNumberByteArray) else {
//            val _txn: Int64 = cursor[txn]
//            TupleNumber(
//                cursor[store_number],
//                Version(_txn),
//                cursor[uid]
//            )
//        }
//
//        // We always need at least tuple-number and id
//        var fetchMode: FetchMode = FetchMode.FETCH_ID
//        val id: String = cursor[id]
//        val flags: Flags = cursor[flags]
//
//        val updatedAt: Int64? = cursor.column(updated_at) as Int64?
//        val metadata = if (updatedAt != null) {
//            fetchMode = fetchMode.withMeta()
//            val createdAt = cursor.column(created_at) as Int64?
//            val authorTs = cursor.column(author_ts) as Int64?
//            val _txn_next = cursor.column(txn_next) as Int64?
//            val _ptxn = cursor.column(ptxn) as Int64?
//            Metadata(
//                storeNumber = tupleNumber.storeNumber,
//                updatedAt = updatedAt,
//                createdAt = createdAt ?: updatedAt,
//                authorTs = authorTs ?: updatedAt,
//                nextVersion = if (_txn_next != null) Version(_txn_next) else null,
//                version = tupleNumber.version,
//                prevVersion = if (_ptxn != null) Version(_ptxn) else null,
//                uid = tupleNumber.uid,
//                puid = cursor.column(puid) as Int?,
//                hash = cursor[hash],
//                changeCount = cursor[change_count],
//                hereTile = cursor[here_tile],
//                flags = flags,
//                id = id,
//                appId = cursor[app_id],
//                author = cursor.column(author) as String?,
//                ft = cursor.column(ft) as String?,
//                originTupleNumber = cursor.column(origin) as String?
//            )
//        } else null
//        if (feature in cursor) fetchMode = fetchMode.withFeature()
//        if (geo in cursor) fetchMode = fetchMode.withGeometry()
//        if (ref_point in cursor) fetchMode = fetchMode.withReferencePoint()
//        if (tags in cursor) fetchMode = fetchMode.withTags()
//        if (attachment in cursor) fetchMode = fetchMode.withAttachment()
//        return Tuple(
//            storage = storage,
//            tupleNumber = tupleNumber,
//            state = fetchMode,
//            meta = metadata,
//            id = id,
//            flags = flags,
//            feature = cursor.column(feature) as ByteArray?,
//            geo = cursor.column(geo) as ByteArray?,
//            referencePoint = cursor.column(ref_point) as ByteArray?,
//            tags = cursor.column(tags) as ByteArray?,
//            attachment = cursor.column(attachment) as ByteArray?
//        )
//    }
}