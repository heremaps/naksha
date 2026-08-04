@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.PTypedArray
import kotlin.js.JsExport

/**
 * A list of [PgType's][PgType].
 * @since 3.0
 */
@JsExport
class PgTypeList : PTypedArray<PgType>(PgType::class)