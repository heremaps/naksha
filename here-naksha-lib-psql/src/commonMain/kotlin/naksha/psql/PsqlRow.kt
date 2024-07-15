package naksha.psql

import naksha.base.Int64
import naksha.model.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Optimized metadata values for DB. Please refer PSQL.md documentation to understand optimization.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class PsqlRow(
    var updatedAt: Int64? = null,
    var createdAt: Int64? = null,
    var authorTs: Int64? = null,
    var txnNext: Int64? = null,
    var txn: Int64? = null,
    var ptxn: Int64? = null,
    var uid: Int? = null,
    var puid: Int? = null,
    var fnva1: Int? = null,
    var version: Int? = null,
    var geoGrid: Int? = null,
    var flags: Int? = null,
    var origin: String? = null,
    var appId: String? = null,
    var author: String? = null,
    var type: String? = null,
    var id: String,
    var feature: ByteArray? = null,
    var geo: ByteArray? = null,
    var geoRef: ByteArray? = null,
    var tags: ByteArray? = null

) {

    fun toMetadata(): Metadata {
        val updatedAtFinal = updatedAt ?: createdAt!!
        return Metadata(
            updatedAt = updatedAtFinal,
            createdAt = createdAt ?: updatedAt!!,
            authorTs = authorTs ?: updatedAtFinal,
            txnNext = txnNext,
            txn = txn!!,
            ptxn = ptxn,
            uid = uid ?: 0,
            puid = puid ?: 0,
            fnva1 = fnva1!!,
            version = version ?: 1,
            geoGrid = geoGrid!!,
            flags = flags ?: Flags(), // FIXME with default flags
            origin = origin,
            appId = appId!!,
            author = author ?: appId,
            type = type,
            id = id,
        )
    }

    fun toRow(storage: IStorage, collection: String): Row {
        val meta = toMetadata()
        val txn = Txn(meta.txn)
        val luid = Luid(txn, meta.uid)
        val guid = Guid(storage.id(), collection, meta.id, luid)
        return Row(
            storage = storage,
            meta = meta,
            guid = guid,
            id = meta.id,
            type = meta.type,
            flags = meta.flags,
            feature = feature,
            geoRef = geoRef,
            geo = geo,
            tags = tags
        )
    }

    companion object {
        fun fromRow(row: Row): PsqlRow {
            val meta = row.meta
            return if (meta == null) {
                PsqlRow(
                    feature = row.feature,
                    geo = row.geo,
                    geoRef = row.geoRef,
                    tags = row.tags,
                    id = row.id
                )
            } else {
                PsqlRow(
                    updatedAt = meta.updatedAt,
                    createdAt = meta.createdAt,
                    authorTs = meta.authorTs,
                    txn = meta.txn,
                    txnNext = meta.txnNext,
                    ptxn = meta.ptxn,
                    uid = meta.uid,
                    puid = meta.puid,
                    fnva1 = meta.fnva1,
                    version = meta.version,
                    geoGrid = meta.geoGrid,
                    flags = meta.flags,
                    origin = meta.origin,
                    appId = meta.appId,
                    author = meta.author,
                    type = meta.type,
                    id = meta.id,
                    feature = row.feature,
                    geo = row.geo,
                    geoRef = row.geoRef,
                    tags = row.tags
                )
            }
        }
    }
}