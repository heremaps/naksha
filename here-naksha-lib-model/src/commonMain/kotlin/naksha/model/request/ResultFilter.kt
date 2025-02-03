@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.fn.Fn
import kotlin.js.JsExport

/**
 * Result filter type, which is a functional interface.
 */
@JsExport
interface ResultFilter : Fn {
    fun filter(featureTuple: FeatureTuple): FeatureTuple?
}
