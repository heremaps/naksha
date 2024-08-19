package naksha.psql.executors

import naksha.base.Int64
import naksha.base.JsEnum
import naksha.model.*
import naksha.model.request.ExecutedOp
import naksha.model.request.ResultRow
import naksha.psql.*
import naksha.psql.COL_APP_ID
import naksha.psql.COL_AUTHOR
import naksha.psql.COL_AUTHOR_TS
import naksha.psql.COL_CREATED_AT
import naksha.psql.COL_FEATURE
import naksha.psql.COL_FLAGS
import naksha.psql.COL_FNVA1
import naksha.psql.COL_GEOMETRY
import naksha.psql.COL_GEO_GRID
import naksha.psql.COL_GEO_REF
import naksha.psql.COL_ID
import naksha.psql.COL_ORIGIN
import naksha.psql.COL_PTXN
import naksha.psql.COL_PUID
import naksha.psql.COL_TAGS
import naksha.psql.COL_TXN
import naksha.psql.COL_TXN_NEXT
import naksha.psql.COL_TYPE
import naksha.psql.COL_UID
import naksha.psql.COL_UPDATE_AT
import naksha.psql.COL_VERSION
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
            val txn = Version(cursor[COL_TXN])
            val uid = cursor.columnOr(COL_UID, 0)
            val flags: Flags = cursor[COL_FLAGS]
            val rowId = RowId(txn, uid, flags)
            val guid = Guid(storage.id(), collection, id, rowId)
            val type = cursor.column(COL_TYPE) as String?
            val meta = if (decodeMeta) {
                val updatedAt = cursor.column(COL_UPDATE_AT) as? Int64 ?: cursor[COL_CREATED_AT]
                Metadata(
                    updatedAt = updatedAt,
                    createdAt = cursor.column(COL_CREATED_AT) as? Int64 ?: cursor[COL_UPDATE_AT],
                    authorTs = cursor.columnOr(COL_AUTHOR_TS, updatedAt),
                    nextVersion = cursor.column(COL_TXN_NEXT) as Int64?,
                    version = txn.txn,
                    prevVersion = cursor.column(COL_PTXN) as Int64?,
                    uid = uid,
                    puid = cursor.columnOr(COL_PUID, 0),
                    hash = cursor[COL_FNVA1],
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
                referencePoint = cursor.column(COL_GEO_REF) as ByteArray?,
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

        /**
         * Converts [Row] to [PgRow]
         *
         * @param row
         * @return [PgRow]
         */
        @JsStatic
        @JvmStatic
        fun rowToPgRow(row: Row): PgRow {
            val pgRow = PgRow()
            if (row.meta != null) {
                val meta = row.meta!!
                pgRow.created_at = meta.createdAt
                pgRow.updated_at = meta.updatedAt
                pgRow.author_ts = meta.authorTs
                pgRow.txn_next = meta.nextVersion
                pgRow.txn = meta.version
                pgRow.ptxn = meta.prevVersion
                pgRow.uid = meta.uid
                pgRow.puid = meta.puid
                pgRow.fnva1 = meta.hash
                pgRow.version = meta.version
                pgRow.geo_grid = meta.geoGrid
                pgRow.flags = meta.flags
                pgRow.origin = meta.origin
                pgRow.app_id = meta.appId
                pgRow.author = meta.author
                pgRow.type = meta.type
            }
            pgRow.id = row.id
            pgRow.feature = row.feature
            pgRow.tags = row.tags
            pgRow.geo = row.geo
            pgRow.geo_ref = row.referencePoint
            return pgRow
        }

        /**
         * Converts [PgRow] into [Metadata].
         * Reveals optimized values into real.
         *
         * @param pgRow
         * @return [Metadata]
          */
        @JsStatic
        @JvmStatic
        fun pgRowToMetadata(pgRow: PgRow): Metadata {
            return with(pgRow) {
                val updatedAtFinal = updated_at ?: created_at!!
                Metadata(
                    updatedAt = updatedAtFinal,
                    createdAt = created_at ?: updated_at!!,
                    authorTs = author_ts ?: updatedAtFinal,
                    nextVersion = txn_next,
                    version = txn!!,
                    prevVersion = ptxn,
                    uid = uid ?: 0,
                    puid = puid ?: 0,
                    hash = fnva1!!,
                    version = version ?: 1,
                    geoGrid = geo_grid!!,
                    flags = flags ?: Flags(), // FIXME with default flags
                    origin = origin,
                    appId = app_id!!,
                    author = author ?: app_id,
                    type = type,
                    id = id!!,
                )
            }
        }

        /**
         * Converts [PgRow] to [Row].
         * Reveals optimized values into real.
         *
         * @param pgRow
         * @param storage
         * @param collection
         */
        @JsStatic
        @JvmStatic
        fun pgRowToRow(pgRow: PgRow, storage: IStorage, collection: String): Row {
            val meta = pgRowToMetadata(pgRow)
            val txn = Version(meta.version)
            val rowId = RowId(txn, meta.uid)
            val guid = Guid(storage.id(), collection, meta.id, rowId)
            return Row(
                storage = storage,
                meta = meta,
                guid = guid,
                id = meta.id,
                type = meta.type,
                flags = meta.flags,
                feature = pgRow.feature,
                referencePoint = pgRow.geo_ref,
                geo = pgRow.geo,
                tags = pgRow.tags
            )
        }
    }
}