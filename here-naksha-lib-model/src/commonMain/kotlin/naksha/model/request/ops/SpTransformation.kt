@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.ops

import naksha.base.PAnyMap
import kotlin.js.JsExport

/**
 * Base class for all transformations to be applied to client geometries.
 */
@JsExport
open class SpTransformation() : PAnyMap()