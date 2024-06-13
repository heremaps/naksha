package naksha.plv8

import naksha.base.Int64
import naksha.base.P_JsMap
import naksha.model.IStorage
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
     * Converts plv8 row result into map ROW
     *
     * @param row - Raw plv8 row.
     * @param storage - current storage reference
     * @return ID, ROW
     */
    fun toRow(row: P_JsMap, storage: IStorage): Row {
        val createdAt: Int64? = (row[COL_CREATED_AT] ?: row[COL_UPDATE_AT]) as Int64?
        check(createdAt != null) { "Missing $COL_CREATED_AT in row" }
        // for transactions update might be null, for features created at might be null
        val updatedAt: Int64 = (row[COL_UPDATE_AT] ?: row[COL_CREATED_AT]) as Int64

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
            meta = Metadata(
                id = row[COL_ID] as String,
                txnNext = row[COL_TXN_NEXT] as Int64?,
                txn = row[COL_TXN] as Int64,
                ptxn = row[COL_PTXN] as Int64?,
                fnva1 = null,// TODO null
                uid = row[COL_UID] as Int?,
                puid = row[COL_PUID] as Int?,
                version = (row[COL_VERSION] ?: 1) as Int?,
                geoGrid = row[COL_GEO_GRID] as Int,
                flags = row[COL_FLAGS] as Int,
                action = (row[COL_ACTION] ?: 0) as Short?,
                appId = row[COL_APP_ID] as String,
                author = (row[COL_AUTHOR] ?: row[COL_APP_ID]) as String?,
                createdAt = createdAt,
                // for transactions update might be null, for features created at might be null
                updatedAt = updatedAt,
                authorTs = (row[COL_AUTHOR] ?: updatedAt) as Int64?,
                origin = row[COL_ORIGIN] as String?
            )
        )
    }
}