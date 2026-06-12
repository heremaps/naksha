@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The canonical set of standard indices that every Naksha storage understands.
 * @since 3.0
 */
@JsExport
class XyzIndices private constructor() {

    companion object XyzIndices_C {

        // TODO: Please fix me, we need an own listOf(...)!
        @JvmField val ALL: List<Index> = StandardIndices.ALL
    }
}