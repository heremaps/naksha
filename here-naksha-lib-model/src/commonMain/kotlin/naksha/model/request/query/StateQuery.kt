@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.ListProxy
import naksha.model.RowAddr
import kotlin.js.JsExport

/**
 * A list of [ID tuples][RowAddr]. This requires to know the storage, map and collection, but is unique within a collection. It is mainly used when loading the full payload of results returned by the first phase of a database query.
 */
@JsExport
class StateQuery : ListProxy<RowAddr>(RowAddr::class)
