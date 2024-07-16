@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Int64
import naksha.base.JsEnum
import naksha.model.*
import naksha.model.request.ResultRow
import naksha.model.response.ExecutedOp
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

@JsExport
internal class DbRowMapper {
    companion object DbRowMapperCompanion {

        /**
         * Decode a row from a cursor.
         * @param storage the storage from which is decoded.
         * @param collection the collection that is decoded (needed only for the `guid`).
         * @param cursor the cursor to read, must be positioned about a read database row.
         * @param decodeMeta if the meta-data should be decoded, if this is done, it must be complete.
         * @return the read row.
         */
        @JsStatic
        @JvmStatic
        fun readRow(storage: IStorage, collection: String, cursor: PgCursor, decodeMeta: Boolean): Row {
            val id: String = cursor[COL_ID]
            val txn = Txn(cursor[COL_TXN])
            val uid = cursor.columnOr(COL_UID, 0)
            val luid = Luid(txn, uid)
            val guid = Guid(storage.id(), collection, id, luid)
            val type = cursor.column(COL_TYPE) as String?
            val flags: Flags = cursor[COL_FLAGS]
            val meta = if (decodeMeta) {
                val updatedAt = cursor.column(COL_UPDATE_AT) as? Int64 ?: cursor[COL_CREATED_AT]
                Metadata(
                    updatedAt = updatedAt,
                    createdAt = cursor.column(COL_CREATED_AT) as? Int64 ?: cursor[COL_UPDATE_AT],
                    authorTs = cursor.columnOr(COL_AUTHOR_TS, updatedAt),
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
                    author = cursor.columnOr(COL_AUTHOR, cursor[COL_APP_ID]) as String?,
                    type = type,
                    id = id,
                )
            } else null
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
         * @param storage the storage from which is decoded.
         * @param collection the collection that is decoded (needed only for the `guid`).
         * @param cursor the cursor to read, must be positioned about a read database row.
         * @param decodeMeta if the meta-data should be decoded, if this is done, it must be complete.
         * @return the read row.
         */
        @JsStatic
        @JvmStatic
        fun toMap(storage: IStorage, collection: String, cursor: PgCursor, decodeMeta: Boolean): Map<String, Row> {
            val retMap = mutableMapOf<String, Row>()

            while (cursor.next()) {
                val row = readRow(storage, collection, cursor, decodeMeta)
                retMap[row.id] = row
            }
            return retMap
        }

        /**
         * Converts plv8 result into to List<ResultRow> with 'READ' op type.
         *
         * @param storage the storage from which is decoded.
         * @param collection the collection that is decoded (needed only for the `guid`).
         * @param cursor the cursor to read, must be positioned about a read database row.
         * @param decodeMeta if the meta-data should be decoded, if this is done, it must be complete.
         * @return the read row.
         */
        @JsStatic
        @JvmStatic
        fun toReadRows(storage: IStorage, collection: String, cursor: PgCursor, decodeMeta: Boolean): List<ResultRow> {
            val retList = mutableListOf<ResultRow>()
            while (cursor.next()) {
                val row = readRow(storage, collection, cursor, decodeMeta)
                ResultRow(JsEnum.get(XYZ_EXEC_READ, ExecutedOp::class), row)
            }
            return retList
        }
    }
}