@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport

/**
 * Logical OR between operations.
 * @param children the children to init the operation with.
 */
@JsExport
class LOr<T>(vararg children: IQuery<T>) : LMulti<T, LOr<T>>(*children)
