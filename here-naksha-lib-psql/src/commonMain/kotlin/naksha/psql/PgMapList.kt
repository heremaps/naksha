@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.ListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A list of [maps][PgMap].
 * @since 3.0.0
 */
@JsExport
class PgMapList : ListProxy<PgMap>(PgMap::class)
