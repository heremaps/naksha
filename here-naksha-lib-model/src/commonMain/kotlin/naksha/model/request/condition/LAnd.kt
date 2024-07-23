@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport

/**
 * Logical AND between operations.
 * @param children the children to init the operation with.
 */
@JsExport
class LAnd<T: Any>(vararg children: IQuery<T>) : LMulti<T, LAnd<T>>(*children)
