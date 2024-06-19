package naksha.plv8

import naksha.base.Int64
import naksha.base.P_JsMap
import naksha.model.ACTION_CREATE
import naksha.model.IStorage
import naksha.model.XYZ_EXEC_READ
import naksha.model.request.ResultRow
import naksha.model.response.Metadata
import naksha.model.response.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object DbRowMapper {

    /**
     * Converts plv8 result into map of <ID, ROW>
     *
     * @param rows - Raw plv8 result.
     * @param storage - current storage reference
     * @return Map<ID, ROW>
     */
    fun toMap(rows: Array<P_JsMap>?, storage: IStorage): Map<String, Row> {
        val retMap = mutableMapOf<String, Row>()

        if (rows.isNullOrEmpty())
            return retMap

        for (row in rows) {
            val cols = toRow(row, storage)
            retMap[cols.id] = cols
        }
        return retMap
    }

    /**
     * Converts plv8 result into to List<ROW>
     *
     * @param rows - Raw plv8 result.
     * @param storage - current storage reference
     * @return List<ROW>
     */
    fun toRows(rows: Array<P_JsMap>?, storage: IStorage): List<Row> {
        if (rows.isNullOrEmpty())
            return emptyList()

        return rows.map { toRow(it, storage) }
    }

    /**
     * Converts plv8 result into to List<ResultRow> with 'READ' op type.
     *
     * @param rows - Raw plv8 result.
     * @param storage - current storage reference
     * @return List<ResultRow>
     */
    fun toReadRows(rows: Array<P_JsMap>?, storage: IStorage): List<ResultRow> {
        if (rows.isNullOrEmpty())
            return emptyList()

        return rows.map { toRow(it, storage) }.map { ResultRow(XYZ_EXEC_READ, it) }
    }

    /**
     * Converts plv8 row result into map ROW
     *
     * @param row - Raw plv8 row.
     * @param storage - current storage reference
     * @return ID, ROW
     */
    fun toRow(row: P_JsMap, storage: IStorage): Row {
        return Row(
            storage = storage,
            guid = null,
            flags = row[COL_FLAGS] as Int,
            id = row[COL_ID] as String,
            type = row[COL_TYPE] as String?,
            feature = row[COL_FEATURE] as ByteArray?,
            geoRef = row[COL_GEO_REF] as ByteArray?,
            geo = row[COL_GEOMETRY] as ByteArray,
            tags = row[COL_TAGS] as ByteArray?,
            meta = toMetadata(row)
        )
    }

    /**
     * Converts raw database row to Metadata object.
     * It restores optimized values as well
     * @see evaluateOptimizedValues
     *
     * @param row - raw database record
     * @return Metadata object
     */
    fun toMetadata(row: P_JsMap): Metadata {
        val meta = Metadata(
            id = row[COL_ID] as String,
            txnNext = row[COL_TXN_NEXT] as? Int64,
            txn = row[COL_TXN] as Int64,
            ptxn = row[COL_PTXN] as? Int64,
            fnva1 = row[COL_FNVA1] as? Int,
            uid = row[COL_UID] as? Int,
            puid = row[COL_PUID] as? Int,
            version = (row[COL_VERSION] ?: 1) as? Int,
            geoGrid = row[COL_GEO_GRID] as Int,
            flags = row[COL_FLAGS] as Int,
            action = row[COL_ACTION] as? Short,
            appId = row[COL_APP_ID] as String,
            author = row[COL_AUTHOR] as? String,
            createdAt = row[COL_CREATED_AT] as? Int64,
            updatedAt = row[COL_UPDATE_AT] as? Int64,
            authorTs = row[COL_AUTHOR_TS] as? Int64,
            origin = row[COL_ORIGIN] as String?
        )
        evaluateOptimizedValues(meta)
        return meta
    }

    /**
     * In database values are kept in optimized way, i.e. to avoid keeping 1M of action: 0, we keep action: null.
     * By calling this method you will bring back original values to life.
     *
     * @param Metadata to fix.
     */
    fun evaluateOptimizedValues(meta: Metadata) {
        meta.author = meta.author ?: meta.appId
        meta.version = meta.version ?: 1
        meta.action = meta.action ?: ACTION_CREATE.toShort()
        meta.uid = meta.uid ?: 0
        meta.puid = meta.puid ?: 0
        // for transactions update might be null, for features created at might be null
        meta.createdAt = meta.createdAt ?: meta.updatedAt
        meta.updatedAt = meta.updatedAt ?: meta.createdAt
        meta.authorTs = meta.authorTs ?: meta.updatedAt
    }
}