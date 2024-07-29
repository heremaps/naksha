@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.AnyObject
import kotlin.js.JsExport

/**
 * Base request class.
 * @since 3.0.0
 */
@JsExport
open class Request : AnyObject() {
    companion object RequestCompanion {
        private val RESULT_FILTER_LIST = NotNullProperty<Request, ResultFilterList>(ResultFilterList::class)
    }

    /**
     * A list of lambdas, that should be invoked by the storage for every row that should be added into a [result-set][ResultSet]. The method can inspect the row, and should return either the unmodified row, a modified version to be added to the response, or _null_, if the row should be removed from the [result-set][ResultSet].
     *
     * The filter lambdas are called in LIFO order (last in, first out/called). The output of each lambda is used as input for the next one. Therefore, only if all filters return a valid new row, the last returned row will be added to the response. This means, each filter can modify the row, or cause it to be removed from the result-set.
     *
     * Adding filtering lambdas conflicts slightly with the [ReadRequest.limit], because the filter can remove an arbitrary amount of features, the storage will need to generate a full [result-set][ResultSet], and the to filter the [result-set][ResultSet] until it has enough results to fulfill the requested [ReadRequest.limit].
     * @since 3.0.0
     */
    var resultFilters by RESULT_FILTER_LIST
}