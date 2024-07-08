package naksha.psql

import naksha.base.Int64
import naksha.base.ObjectProxy
import naksha.model.*
import naksha.model.request.ResultRow
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
internal object DbRowMapper {
    /**
     * Decode a row from a cursor.
     * @param storage the storage from which is decoded.
     * @param collection the collection that is decoded (needed only for the `guid`).
     * @param cursor the cursor to read, must be positioned about a read database row.
     * @param decodeMeta if the meta-data should be decoded, if this is done, it must be complete.
     * @return the read row.
     */
    fun readRow(storage: IStorage, collection: String, cursor: PgCursor, decodeMeta: Boolean): Row {
        val id: String = cursor[COL_ID]
        val txn = Txn(cursor[COL_TXN])
        val uid = cursor.columnOr(COL_UID, 0)
        val luid = Luid(txn, uid)
        val guid = Guid(storage.id(), collection, id, luid)
        val type = cursor.column(COL_TYPE) as String?
        val flags: Flags = cursor[COL_FLAGS]
        val meta = if (decodeMeta) Metadata(
            updatedAt = cursor[COL_CREATED_AT],
            createdAt = cursor.columnOr(COL_UPDATE_AT, cursor[COL_UPDATE_AT]),
            authorTs = cursor.columnOr(COL_AUTHOR_TS, cursor[COL_UPDATE_AT]),
            txnNext = cursor.column(COL_TXN_NEXT) as Int64?,
            txn = txn.value,
            ptxn = cursor.column(COL_PTXN) as Int64?,
            uid = uid,
            puid = cursor.columnOr(COL_PUID, 0),
            fnva1 = cursor[COL_FNVA1],
            version = cursor.columnOr(COL_VERSION, 0),
            //changeCount = cursor.columnOr(COL_VERSION, 0),
            geoGrid = cursor[COL_GEO_GRID],
            flags = flags,
            origin = cursor.column(COL_ORIGIN) as String?,
            appId = cursor[COL_APP_ID],
            author = cursor.column(COL_AUTHOR) as String?,
            type = type,
            id = id
        ) else null
        val row = Row(
            storage = storage,
            id = id,
            guid = guid,
            type = type,
            meta = meta,
            flags = flags,
            feature = cursor.column(COL_FEATURE) as ByteArray?,
            geo = cursor.column(COL_GEOMETRY) as ByteArray?,
            geoRef = cursor.column(COL_GEO_REF) as ByteArray?,
            tags = cursor.column(COL_TAGS) as ByteArray?
        )
        return row
    }

    /**
     * Converts plv8 result into map of <ID, ROW>
     *
     * @param rows - Raw plv8 result.
     * @param storage - current storage reference
     * @return Map<ID, ROW>
     */
    fun toMap(rows: Array<ObjectProxy>?, storage: IStorage): Map<String, Row> {
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
    fun toRows(rows: Array<ObjectProxy>?, storage: IStorage): List<Row> {
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
    fun toReadRows(rows: Array<ObjectProxy>?, storage: IStorage): List<ResultRow> {
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
    fun toRow(row: ObjectProxy, storage: IStorage): Row {
        return Row(
            storage = storage,
            guid = null,
            flags = row[COL_FLAGS] as Int,
            id = row[COL_ID] as String,
            type = row[COL_TYPE] as String?,
            feature = row[COL_FEATURE] as ByteArray?,
            geoRef = row[COL_GEO_REF] as ByteArray?,
            geo = row[COL_GEOMETRY] as ByteArray?,
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
    fun toMetadata(row: ObjectProxy): Metadata {
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