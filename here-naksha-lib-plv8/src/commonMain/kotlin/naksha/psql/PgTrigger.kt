@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

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
@Suppress("MemberVisibilityCanBePrivate", "PropertyName")
@JsExport
class PgTrigger(
    val TG_OP: String,
    val TG_NAME: String,
    val TG_WHEN: String,
    val TG_LEVEL: String,
    val TG_RELID: Int,
    val TG_TABLE_NAME: String,
    val TG_TABLE_SCHEMA: String,
    val NEW: Row?,
    val OLD: Row?
)