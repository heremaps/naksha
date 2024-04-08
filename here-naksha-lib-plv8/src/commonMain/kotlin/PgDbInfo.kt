package com.here.naksha.lib.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Information about the database, that need only to be queried ones per session.
 * @property sql The SQL API.
 * @property pageSize The page-size of the database (`current_setting('block_size')`).
 * @property brittleTableSpace The tablespace to use for storage-class "brittle"; if any.
 * @property tempTableSpace The tablespace to use for temporary tables and their indices; if any.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class PgDbInfo(val sql: IPlv8Sql, val pageSize: Int = 8192, val brittleTableSpace: String? = null, val tempTableSpace: String? = null) {
    init {
        // TODO: Load the information from the session.
    }
}