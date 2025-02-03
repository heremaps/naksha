@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import naksha.base.ListProxy
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A list of [PostgresQL instances configurations][PgInstanceConfig].
 * @since 3.0.0
 */
@JsExport
class PgInstanceConfigList : ListProxy<PgInstanceConfig>(PgInstanceConfig::class)