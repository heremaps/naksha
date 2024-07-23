@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Logical NOT of the given operation.
 * @property op the operation to negate.
 */
@JsExport
class LNot<T>(@JvmField var op: IQuery<T>) : LOp<T, LNot<T>>()
