@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.IMap
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

const val OP_INSERT = "INSERT"
const val OP_UPDATE = "UPDATE"
const val OP_DELETE = "DELETE"
const val OP_TRUNCATE = "TRUNCATE"
const val WHEN_BEFORE = "BEFORE"
const val WHEN_AFTER = "AFTER"
const val WHEN_INSTEAD_OF = "INSTEAD OF"
const val LEVEL_ROW = "ROW"
const val LEVEL_STATEMENT = "STATEMENT"

/**
 * A class that is created by a PostgresQL triggers and can be forwarded to a Kotlin implementation.
 * @property TG_OP operation for which the trigger was fired: [OP_INSERT], [OP_UPDATE], [OP_DELETE], or [OP_TRUNCATE].
 * @property TG_NAME name of the trigger which fired.
 * @property TG_WHEN [WHEN_BEFORE], [WHEN_AFTER], or [WHEN_INSTEAD_OF], depending on the trigger's definition.
 * @property TG_LEVEL [LEVEL_ROW] or [LEVEL_STATEMENT], depending on the trigger's definition.
 * @property TG_RELID object ID of the table that caused the trigger invocation.
 * @property TG_TABLE_NAME table that caused the trigger invocation.
 * @property TG_TABLE_SCHEMA schema of the table that caused the trigger invocation.
 * @property NEW new database row for [OP_INSERT]/[OP_UPDATE] operations in row-level triggers. This variable is _null_ in statement-level triggers and for [OP_DELETE] operations.
 * @property OLD database row for [OP_UPDATE]/[OP_DELETE] operations in row-level triggers. This variable is null in statement-level triggers and for [OP_INSERT] operations.
 */
@Suppress("MemberVisibilityCanBePrivate", "PropertyName")
@JsExport
class PgTrigger(
        val TG_OP: String,
        val TG_NAME: String,
        val TG_WHEN: String,
        val TG_LEVEL: String,
        val TG_RELID: Double, // TODO: Clarify is this is double or bigint!
        val TG_TABLE_NAME: String,
        val TG_TABLE_SCHEMA: String,
        val NEW: IMap?,
        val OLD: IMap?
)