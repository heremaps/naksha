@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.condition

import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Base class for all transformation to be applied to client geometries.
 * @property childTransformation an optional child transformation that should be executed before this one.
 */
@JsExport
abstract class SpTransformation protected constructor(@JvmField val childTransformation: SpTransformation? = null)
