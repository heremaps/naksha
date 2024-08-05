@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("MemberVisibilityCanBePrivate")

package naksha.psql

import kotlinx.js.JsPlainObject
import naksha.base.Int64
import naksha.model.Row
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

internal const val TG_OP_INSERT = "INSERT"
internal const val TG_OP_UPDATE = "UPDATE"
internal const val TG_OP_DELETE = "DELETE"
internal const val TG_OP_TRUNCATE = "TRUNCATE"
internal const val TG_WHEN_BEFORE = "BEFORE"
internal const val TG_WHEN_AFTER = "AFTER"
internal const val TG_WHEN_INSTEAD_OF = "INSTEAD OF"
internal const val TG_LEVEL_ROW = "ROW"
internal const val TG_LEVEL_STATEMENT = "STATEMENT"

/**
 * The raw row as returned by PostgresQL triggers. Check [PgColumn.allColumns], it should match this.
 */
@JsPlainObject
external interface Plv8Row {
    var created_at: Int64?
    var updated_at: Int64?
    var author_ts: Int64?
    var txn_next: Int64?
    var txn: Int64?
    var ptxn: Int64?
    var uid: Int?
    var puid: Int?
    var hash: Int?
    var change_count: Int?
    var geo_grid: Int?
    var flags: Int?
    var id: String?
    var app_id: String?
    var author: String?
    var type: String?
    var tags: ByteArray?
    var geo_ref: ByteArray?
    var geo: ByteArray?
    var feature: ByteArray?
    var attachment: ByteArray?
}

/**
 * A class that is created by a PostgresQL triggers and can be forwarded to a Kotlin implementation.
 * @property TG_OP operation for which the trigger was fired: [TG_OP_INSERT], [TG_OP_UPDATE], [TG_OP_DELETE], or [TG_OP_TRUNCATE].
 * @property TG_NAME name of the trigger which fired.
 * @property TG_WHEN [TG_WHEN_BEFORE], [TG_WHEN_AFTER], or [TG_WHEN_INSTEAD_OF], depending on the trigger's definition.
 * @property TG_LEVEL [TG_LEVEL_ROW] or [TG_LEVEL_STATEMENT], depending on the trigger's definition.
 * @property TG_RELID object ID of the table that caused the trigger invocation (oid).
 * @property TG_TABLE_NAME table that caused the trigger invocation.
 * @property TG_TABLE_SCHEMA schema of the table that caused the trigger invocation.
 * @property NEW new database row for [TG_OP_INSERT]/[TG_OP_UPDATE] operations in row-level triggers. This variable is _null_ in statement-level triggers and for [TG_OP_DELETE] operations.
 * @property OLD database row for [TG_OP_UPDATE]/[TG_OP_DELETE] operations in row-level triggers. This variable is null in statement-level triggers and for [TG_OP_INSERT] operations.
 */
@JsExport
data class Plv8Trigger(
    val TG_OP: String,
    val TG_NAME: String,
    val TG_WHEN: String,
    val TG_LEVEL: String,
    val TG_RELID: Int,
    val TG_TABLE_NAME: String,
    val TG_TABLE_SCHEMA: String,
    val NEW: Plv8Row?,
    val OLD: Plv8Row?
)

/**
 * To be called by PostgresQL before a row is modified.
 *
 * This trigger will be part of the `naksha.psql` namespace directly.
 *
 * This trigger needs to ensure that the [metadata][naksha.model.Metadata] is set up correctly. It needs a context to set `appId`, `author`, `author_ts` and others.
 */
@JsExport
fun naksha_trigger_before(trigger: Plv8Trigger) : Plv8Row {
    TODO("naksha_trigger_before is not implemented yet")
//    val collectionId = getBaseCollectionId(data.TG_TABLE_NAME)
//    if (data.TG_OP == TG_OP_INSERT) {
//        check(data.NEW != null) { "Missing NEW for INSERT" }
//        rowUpdater.xyzInsert(collectionId, data.NEW)
//    } else if (data.TG_OP == TG_OP_UPDATE) {
//        check(data.NEW != null) { "Missing NEW for UPDATE" }
//        check(data.OLD != null) { "Missing OLD for UPDATE" }
//        rowUpdater.xyzUpdateHead(collectionId, data.NEW, data.OLD)
//    }
    // We should not be called for delete, in that case do nothing.
}

/**
 * To be called by PostgresQL after a row is modified, and before the row is to be persisted.
 *
 * This trigger will be part of the `naksha.psql` namespace directly.
 *
 * This trigger should create history entries, update the transaction table, and the deletion table.
 */
@JsExport
fun naksha_trigger_after(trigger: Plv8Trigger) : Plv8Row {
    TODO("naksha_trigger_after is not implemented yet")
//        val collectionId = getBaseCollectionId(data.TG_TABLE_NAME)
//        if (data.TG_OP == TG_OP_DELETE && data.OLD != null) {
//            deleteFromDel(collectionId, data.OLD.id)
//            // save current head in hst
//            data.OLD.meta?.txnNext = data.OLD.meta?.txn
//            saveInHst(collectionId, data.OLD)
//            rowUpdater.xyzDel(data.OLD.meta!!)
//            copyToDel(collectionId, data.OLD)
//            // save del state in hst
//            saveInHst(collectionId, data.OLD)
//        }
//        if (data.TG_OP == TG_OP_UPDATE) {
//            check(data.NEW != null) { "Missing NEW for UPDATE" }
//            check(data.OLD != null) { "Missing OLD for UPDATE" }
//            deleteFromDel(collectionId, data.NEW.id)
//            data.OLD.meta?.txnNext = data.NEW.meta?.txn
//            saveInHst(collectionId, data.OLD)
//        }
//        if (data.TG_OP == TG_OP_INSERT) {
//            check(data.NEW != null) { "Missing NEW for INSERT" }
//            deleteFromDel(collectionId, data.NEW.id)
//        }
}


/**
 * Saves OLD in $hst.
 *
 * Uses the current session, does not commit or rollback the current session.
 */
internal fun saveInHst(collectionId: String, OLD: Row) {
//    if (isHistoryEnabled(collectionId)) {
//        // TODO move it outside and run it once
//        val collectionIdQuoted = quoteIdent("${collectionId}\$hst")
//        val session = usePgConnection()
//        val hstInsertPlan = session.prepare("INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($COL_ALL_DOLLAR)", COL_ALL_TYPES)
//        hstInsertPlan.use {
//            val oldMeta = OLD.meta!!
//            hstInsertPlan.execute(
//                arrayOf(
//                    oldMeta.nextVersion,
//                    oldMeta.version,
//                    oldMeta.uid,
//                    oldMeta.prevVersion,
//                    oldMeta.puid,
//                    oldMeta.flags,
//                    oldMeta.version,
//                    oldMeta.createdAt,
//                    oldMeta.updatedAt,
//                    oldMeta.authorTs,
//                    oldMeta.author,
//                    oldMeta.appId,
//                    oldMeta.geoGrid,
//                    OLD.id,
//                    OLD.tags,
//                    OLD.geo,
//                    OLD.feature,
//                    OLD.referencePoint,
//                    OLD.type,
//                    oldMeta.hash
//                )
//            )
//        }
//    }
}

/**
 * Delete the feature from the shadow table.
 *
 * Uses the current session, does not commit or rollback the current session.
 */
internal fun deleteFromDel(collectionId: String, id: String) {
//    val collectionIdQuoted = quoteIdent("${collectionId}\$del")
//    usePgConnection().execute("""DELETE FROM $collectionIdQuoted WHERE id = $1""", arrayOf(id))
}

/**
 * Updates xyz namespace and copies feature to $del table.
 *
 * Uses the current session, does not commit or rollback the current session.
 */
internal fun copyToDel(collectionId: String, OLD: Row) {
//    val collectionConfig = getCollectionConfig(collectionId)
//    val autoPurge: Boolean? = collectionConfig.autoPurge
//    if (autoPurge != true) {
//        val collectionIdQuoted = quoteIdent("${collectionId}\$del")
//        val oldMeta = OLD.meta!!
//        val conn = usePgConnection()
//        conn.execute(
//            "INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($COL_ALL_DOLLAR)",
//            arrayOf(
//                oldMeta.nextVersion,
//                oldMeta.version,
//                oldMeta.uid,
//                oldMeta.prevVersion,
//                oldMeta.puid,
//                oldMeta.flags,
//                oldMeta.version,
//                oldMeta.createdAt,
//                oldMeta.updatedAt,
//                oldMeta.authorTs,
//                oldMeta.author,
//                oldMeta.appId,
//                oldMeta.geoGrid,
//                OLD.id,
//                OLD.tags,
//                OLD.geo,
//                OLD.feature,
//                OLD.referencePoint,
//                OLD.type,
//                oldMeta.hash
//            )
//        ).close()
//    }
}
