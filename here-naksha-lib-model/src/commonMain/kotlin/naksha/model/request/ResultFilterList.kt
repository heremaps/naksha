@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A mutable list of filters.
 */
@JsExport
class ResultFilterList : ListProxy<ResultFilter>(ResultFilter::class){

    companion object {
        @JvmStatic
        fun of(vararg resultFilter: ResultFilter): ResultFilterList {
            return ResultFilterList().apply { addAll(resultFilter) }
        }
    }
}