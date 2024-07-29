@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of [row-references][RowRef].
 */
@JsExport
class RowRefList : ListProxy<RowRef>(RowRef::class)